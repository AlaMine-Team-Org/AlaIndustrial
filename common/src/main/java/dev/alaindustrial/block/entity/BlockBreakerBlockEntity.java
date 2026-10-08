package dev.alaindustrial.block.entity;

import com.mojang.authlib.GameProfile;
import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.BlockBreakerBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.machine.StatusLine;
import dev.alaindustrial.core.world.FakePlayers;
import dev.alaindustrial.menu.BlockBreakerMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Block Breaker (MOD-787): breaks the block in front of it with the tool in its slot, the way a
 * player holding that tool would.
 *
 * <p><b>The tool decides, as it does for a player.</b> A pickaxe takes stone quickly, an axe takes wood,
 * a shovel takes dirt; the wrong tool still breaks the block, only slowly, and a block that needs a
 * better tier breaks without dropping anything. The slot takes tools up to stone tier only
 * ({@code #alaindustrial:block_breaker_tools}): this is a first-tier machine.
 *
 * <p><b>The break itself is a player's.</b> When the progress fills, the owner's fake player
 * ({@link FakePlayers}) holds the tool and calls {@code ServerPlayerGameMode.destroyBlock}, the path the
 * electric drill and the scythe use for their extra blocks. That one call is what makes the machine
 * honest: both loaders fire their block-break event there (a land-claim mod can refuse the break),
 * {@code playerWillDestroy} runs (a cable gives back its clamp and its stand), the drop table rolls with
 * the tool (Fortune, Silk Touch), an ore drops its experience, and the tool wears with Unbreaking
 * applied. A machine with no owner breaks nothing.
 *
 * <p><b>The time is the player's formula, not the player's situation.</b> {@code Player.getDestroySpeed}
 * divides by five for a player not standing on the ground and again under water; a fake player is
 * never on the ground, so the progress is computed here from the tool alone: speed (plus Efficiency)
 * over hardness over 30, or over 100 when the tool is not the right one.
 *
 * <p>Energy is paid only while breaking ({@link Config#blockBreakerEuPerTick}); between blocks there is
 * a short pause, and an idle machine sleeps until a neighbour, its slot or a redstone change wakes it.
 */
public class BlockBreakerBlockEntity extends MachineBlockEntity implements MenuProvider {

	public static final int TOOL_SLOT = 0;
	/** Machine slots before the upgrade panel: the tool alone. */
	public static final int SLOT_COUNT = 1;

	/** What the slot accepts: tools up to stone tier, and shears. */
	public static final TagKey<Item> TOOLS =
			TagKey.create(Registries.ITEM, Industrialization.id("block_breaker_tools"));

	/** The progress bar's length on the sync channel, in steps. */
	public static final int PROGRESS_STEPS = 100;
	/** How long a refused break (a claim, spawn protection) waits before asking again. */
	private static final int REFUSED_BACKOFF = 40;

	/** The four base channels, then the machine's own two. */
	public enum Channel { ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, STATUS, REDSTONE }

	public static final int DATA_COUNT = Channel.values().length;

	/** Why the machine is or is not breaking, shown on its screen. */
	public enum Status implements StatusLine {
		WORKING(false),
		NO_TOOL(true),
		NO_TARGET(true),
		UNBREAKABLE(true),
		NO_POWER(true),
		REDSTONE(true),
		REFUSED(true),
		NO_OWNER(true);

		private final boolean blocking;

		Status(boolean blocking) {
			this.blocking = blocking;
		}

		@Override
		public String translationKey() {
			return "gui.alaindustrial.block_breaker.status." + name().toLowerCase(java.util.Locale.ROOT);
		}

		@Override
		public boolean isBlocking() {
			return blocking;
		}

		public static Status byOrdinal(int ordinal) {
			Status[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NO_TARGET;
		}
	}

	/** How a redstone signal on the machine is read; a lever on it is the on/off switch. */
	public enum RedstoneMode {
		/** Works whatever the signal. */
		IGNORE,
		/** Works only while powered. */
		WITH_SIGNAL,
		/** Works only while NOT powered: the lever switches it off. */
		WITHOUT_SIGNAL;

		public static RedstoneMode byOrdinal(int ordinal) {
			RedstoneMode[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : IGNORE;
		}

		public RedstoneMode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		boolean allows(boolean powered) {
			return switch (this) {
				case IGNORE -> true;
				case WITH_SIGNAL -> powered;
				case WITHOUT_SIGNAL -> !powered;
			};
		}
	}

	private RedstoneMode redstoneMode = RedstoneMode.IGNORE;
	private Status status = Status.NO_TOOL;
	/** Break progress of the current target, 0..1; not saved, a reload starts the block again. */
	private float breakProgress;
	/** The state the progress belongs to; any other state in front starts over. */
	private BlockState progressState;
	/** The crack stage last sent to clients, -1 for none. */
	private int crackStage = -1;
	/** EU spent on the current block, credited to the owner when it breaks. */
	private long euOnBlock;
	/** Ticks left before the next block may start. */
	private int pause;

	public BlockBreakerBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.BLOCK_BREAKER_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT, Config.blockBreakerBuffer,
				EnergyTier.LV.maxVoltage(), 0L);
	}

	/**
	 * Takes energy on every face but its working one. Stated here because the core's facing-aware
	 * default reads the horizontal facing property, which this six-way block does not have.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace == facing() ? EnergyRole.NONE : EnergyRole.IN;
	}

	private Direction facing() {
		BlockState state = getBlockState();
		return state.hasProperty(BlockBreakerBlock.FACING) ? state.getValue(BlockBreakerBlock.FACING) : Direction.NORTH;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		Status next = work(level, pos);
		if (next != status) {
			status = next;
			setChanged();
		}
		boolean working = next == Status.WORKING;
		updateLit(working);
		if (!working) {
			recordEuRate(0);
			maxProgress = 0;
			progress = 0;
			if (next != Status.REFUSED && next != Status.NO_POWER) {
				resetProgress(level);
			}
		}
		return working || pause > 0 ? 0 : IDLE_SLEEP_TICKS;
	}

	/** One tick of work; the answer is the status the machine is in afterwards. */
	private Status work(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return Status.NO_TARGET;
		}
		// The pause runs down whatever else happens, so a machine with nothing in front of it sleeps
		// instead of ticking through a pause it never gets to finish.
		boolean pausing = pause > 0;
		if (pausing) {
			pause--;
		}
		if (!redstoneMode.allows(level.hasNeighborSignal(pos))) {
			return Status.REDSTONE;
		}
		ItemStack tool = items.get(TOOL_SLOT);
		if (tool.isEmpty()) {
			return Status.NO_TOOL;
		}
		BlockPos target = pos.relative(facing());
		BlockState targetState = level.getBlockState(target);
		if (targetState.isAir() || targetState.getBlock() instanceof LiquidBlock) {
			return Status.NO_TARGET;
		}
		float hardness = targetState.getDestroySpeed(level, target);
		if (hardness < 0.0F) {
			return Status.UNBREAKABLE;
		}
		if (getOwner() == null) {
			return Status.NO_OWNER;
		}
		if (pausing) {
			return status == Status.REFUSED ? Status.REFUSED : Status.WORKING;
		}
		if (targetState != progressState) {
			resetProgress(level);
			progressState = targetState;
		}
		int cost = Config.blockBreakerEuPerTick;
		if (energy.getAmount() < cost) {
			return Status.NO_POWER;
		}
		energy.drainInternal(cost);
		euOnBlock += cost;
		recordEuRate(cost);
		breakProgress += progressPerTick(serverLevel, tool, targetState, hardness);
		maxProgress = PROGRESS_STEPS;
		progress = Math.min(PROGRESS_STEPS, (int) (breakProgress * PROGRESS_STEPS));
		setChanged();
		if (breakProgress < 1.0F) {
			showCrack(serverLevel, target, Math.min(9, (int) (breakProgress * 10.0F)));
			return Status.WORKING;
		}
		return finishBreak(serverLevel, target);
	}

	/**
	 * The progress is full: break the block as the owner, then pause. A refused break (a claim, spawn
	 * protection) earns nothing and waits longer before it asks again.
	 */
	private Status finishBreak(ServerLevel level, BlockPos target) {
		boolean broken = breakAsOwner(level, target);
		long spent = euOnBlock;
		resetProgress(level);
		if (!broken) {
			pause = REFUSED_BACKOFF;
			return Status.REFUSED;
		}
		completeOperation(level, spent);
		pause = Math.max(0, Config.blockBreakerPauseTicks);
		return Status.WORKING;
	}

	/**
	 * The share of the block one tick breaks: the player's {@code getDestroyProgress} with the tool in
	 * hand, without the player's on-the-ground and under-water penalties.
	 */
	static float progressPerTick(ServerLevel level, ItemStack tool, BlockState state, float hardness) {
		if (hardness <= 0.0F) {
			return 1.0F;
		}
		float speed = tool.getDestroySpeed(state);
		if (speed > 1.0F) {
			int efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.EFFICIENCY)
					.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, tool))
					.orElse(0);
			if (efficiency > 0) {
				speed += efficiency * efficiency + 1;
			}
		}
		boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		return speed / hardness / (correct ? 30.0F : 100.0F);
	}

	/**
	 * Breaks {@code target} as the owner holding the tool. The tool is lent to the fake player for the
	 * one call and taken back, worn — or gone, if that was its last use.
	 */
	private boolean breakAsOwner(ServerLevel level, BlockPos target) {
		UUID owner = getOwner();
		if (owner == null) {
			return false;
		}
		ServerPlayer player = FakePlayers.get().get(level, new GameProfile(owner, ownerName()));
		BlockPos pos = getBlockPos();
		player.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
		// A fake player starts in survival on both loaders; a creative one would break without drops.
		if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
			player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, items.get(TOOL_SLOT));
		boolean broken;
		try {
			broken = player.gameMode.destroyBlock(target);
		} finally {
			ItemStack back = player.getMainHandItem();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			items.set(TOOL_SLOT, back);
			setChanged();
		}
		return broken;
	}

	/** A fake player's name has to be a valid profile name; an owner recorded without one gets a stand-in. */
	private String ownerName() {
		String name = getOwnerName();
		return name == null || name.isEmpty() ? "[Block Breaker]" : name;
	}

	private void showCrack(ServerLevel level, BlockPos target, int stage) {
		if (stage != crackStage) {
			crackStage = stage;
			level.destroyBlockProgress(crackId(), target, stage);
		}
	}

	private void resetProgress(Level level) {
		breakProgress = 0.0F;
		progressState = null;
		euOnBlock = 0L;
		if (crackStage >= 0 && level instanceof ServerLevel serverLevel) {
			serverLevel.destroyBlockProgress(crackId(), getBlockPos().relative(facing()), -1);
		}
		crackStage = -1;
	}

	/**
	 * The breaker id the crack is sent under: one per machine, negative so it can never be an entity's,
	 * and far below the column bore's mirrored ids ({@code -(id * 2 + 1)}, {@code -(id * 2 + 2)}).
	 */
	private int crackId() {
		return Integer.MIN_VALUE + Math.floorMod(getBlockPos().hashCode(), 1 << 30);
	}

	/** The machine is about to leave the world: its crack goes with it. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			resetProgress(level);
		}
		super.preRemoveSideEffects(pos, state);
	}

	// --- slot ---

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == TOOL_SLOT && items.get(TOOL_SLOT).isEmpty() && stack.is(TOOLS);
	}

	// --- redstone mode ---

	public RedstoneMode redstoneMode() {
		return redstoneMode;
	}

	public void setRedstoneMode(RedstoneMode mode) {
		if (mode != redstoneMode) {
			redstoneMode = mode;
			setChanged();
			wake();
		}
	}

	// --- sync and persistence ---

	@Override
	protected dev.alaindustrial.block.entity.machine.SyncChannels createChannels() {
		return channels(Channel.class)
				.read(Channel.STATUS, () -> status.ordinal())
				.readWrite(Channel.REDSTONE, () -> redstoneMode.ordinal(),
						v -> redstoneMode = RedstoneMode.byOrdinal(v))
				.build();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("RedstoneMode", redstoneMode.ordinal());
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		redstoneMode = RedstoneMode.byOrdinal(input.getIntOr("RedstoneMode", 0));
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new BlockBreakerMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
