package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.MobWheelControllerBlock;
import dev.alaindustrial.block.MobWheelGateBlock;
import dev.alaindustrial.block.MobWheelStructure;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.MobWheelFeed;
import dev.alaindustrial.core.environment.MobWheelOutput;
import dev.alaindustrial.core.environment.MobWheelProfile;
import dev.alaindustrial.core.environment.MobWheelStamina;
import dev.alaindustrial.core.environment.MobWheelStatus;
import dev.alaindustrial.menu.MobWheelMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The mob wheel's drive (MOD-763): holds the one mob shut in the wheel, counts how tired it is, feeds it and
 * turns its running into LV EU.
 *
 * <p><b>No storage.</b> The buffer is one tick of the wheel's highest possible output (D4): whatever the
 * network does not take this tick is gone, and the mob keeps spending stamina all the same.
 *
 * <p><b>The occupant.</b> Referenced by UUID ({@link MobWheelOccupant}); while the gate is closed it is held on
 * the running deck every server tick — position, horizontal motion, navigation, heading — without switching
 * its goals off, so opening the gate simply stops holding it (D3). A hostile occupant with a player target close
 * by is distracted (D9): held in place but not turned along the run, it fights or shoots and makes nothing.
 *
 * <p><b>What the client reads.</b> The renderer gets the structure facing and {@code formed} from the block
 * state and, through the block-entity update packet, {@link #wood()}, {@link #isRunning()},
 * {@link #speedPercent()}, {@link #staminaPermille()}, {@link #occupantNetworkId()} and {@link #status()}. An
 * open screen gets the menu channels of {@link Channel}.
 */
public class MobWheelBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider, NoUpgradePanel {
	public static final int FEED_SLOT = 0;
	/** Machine-slot count; no upgrade panel is appended (the drive takes no chips, D4). */
	public static final int SLOT_COUNT = 1;
	/** The family every new wheel is built of. */
	public static final String DEFAULT_WOOD = "oak";
	/** Ticks an occupant may go unfound after its chunk loaded before it counts as lost (D3). */
	public static final int LOST_AFTER_TICKS = 100;
	/** How far from the structure a hay bale helps the occupant rest (D5). */
	public static final int HAY_RADIUS = 2;
	/** How often the hay scan runs; the bales rarely move. */
	private static final int HAY_SCAN_INTERVAL = 20;
	/** An occupant farther than this from the deck (squared blocks) has left by other means: let it go. */
	private static final double ESCAPED_DISTANCE_SQR = 4.0;
	/** How often an empty wheel behind a closed gate looks for a mob that got in (ticks). */
	public static final int CAPTURE_INTERVAL = 5;
	/** LV packet cap. */
	private static final int MAX_EXTRACT = 32;

	/** Network id of a missing occupant on the client. */
	public static final int NO_OCCUPANT = -1;

	private String wood = DEFAULT_WOOD;
	private final MobWheelOccupant occupant = new MobWheelOccupant();
	private boolean nearHay;
	private int hayScanCounter;
	private int captureCounter;
	private int productionRate;
	private MobWheelStatus status = MobWheelStatus.UNFORMED;
	private boolean running;
	private int speedPercent;
	/** Server: the resolved occupant's id; client: what the last update packet said. */
	private int occupantNetworkId = NO_OCCUPANT;

	/** Client only: the drawn wheel's spin, the gate's swing and the occupant's legs. */
	private final MobWheelAnimation animation = new MobWheelAnimation();

	public MobWheelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.MOB_WHEEL_CONTROLLER_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT, bufferSize(),
				MAX_EXTRACT);
	}

	/** One tick of the wheel's highest output after the global rate multiplier — and never less than 1. */
	private static long bufferSize() {
		double scale = Math.max(1.0, Config.globalEuRateMultiplier);
		return Math.max(1L, (long) Math.ceil(MobWheelOutput.peakEuPerTick() * scale));
	}

	// --- client-readable state ---

	public String wood() {
		return wood;
	}

	public boolean isRunning() {
		return running;
	}

	/**
	 * Wheel speed in percent of nominal: 0 stopped, 100 a fresh mob at pace 1; a ragged runner's burst reaches
	 * 120 and a fast one (the goat) turns the wheel twice as fast, 200.
	 */
	public int speedPercent() {
		return speedPercent;
	}

	/** Effective EU/t of the last tick, after the global rate multiplier — what the screen shows. */
	public int productionRate() {
		return productionRate;
	}

	public int staminaPermille() {
		return occupant.stamina == null ? 0 : occupant.stamina.permille();
	}

	public int occupantNetworkId() {
		return occupantNetworkId;
	}

	public MobWheelStatus status() {
		return status;
	}

	@Nullable
	public MobWheelProfile species() {
		return occupant.ref == null ? null : occupant.species;
	}

	/** The occupant's entity type id, or {@code null} when the wheel is empty. */
	@Nullable
	public String runnerType() {
		return occupant.ref == null ? null : occupant.runnerType;
	}

	/** Recolour the wheel (D2); an unknown family is refused rather than stored. */
	public void setWood(String family) {
		if (family.equals(wood) || WoodType.values().noneMatch(type -> type.name().equals(family))) {
			return;
		}
		wood = family;
		setChanged();
		syncBlockEntityToClient();
	}

	// --- client animation ---

	/** Client tick, from the drive's own ticker: animation only, see {@link MobWheelAnimation}. */
	public void clientTick(Level clientLevel, BlockState state) {
		animation.tick(clientLevel, worldPosition, state, running, speedPercent, occupantNetworkId,
				status == MobWheelStatus.DISTRACTED);
	}

	/** The client-side animation clocks the renderer reads. */
	public MobWheelAnimation animation() {
		return animation;
	}

	// --- structure events ---

	/** The structure just assembled: a closed gate catches whoever already stands on the deck. */
	public void onFormed() {
		if (level != null && !isGateOpen(level, getBlockState().getValue(MobWheelControllerBlock.FACING))) {
			capture();
		}
		syncBlockEntityToClient();
	}

	/** The structure was taken apart: let the occupant go. */
	public void onUnformed() {
		release();
		setStatus(MobWheelStatus.UNFORMED, false, 0);
	}

	/**
	 * Shut in the compatible mob nearest the deck's middle whose box touches {@link MobWheelStructure#passage},
	 * if nobody is in yet: when the gate closes, and every {@link #CAPTURE_INTERVAL} ticks while it stays closed.
	 */
	public void capture() {
		if (level == null || level.isClientSide() || occupant.ref != null
				|| !getBlockState().getValue(MobWheelStructure.FORMED)) {
			return;
		}
		Direction facing = getBlockState().getValue(MobWheelControllerBlock.FACING);
		Vec3 anchor = MobWheelStructure.anchor(worldPosition, facing);
		List<Mob> candidates = level.getEntitiesOfClass(Mob.class, MobWheelStructure.passage(worldPosition, facing),
				mob -> mob.isAlive() && MobWheelRoster.profileOf(mob) != null);
		candidates.stream().min(Comparator.comparingDouble(m -> m.position().distanceToSqr(anchor)))
				.ifPresent(this::shutIn);
	}

	/**
	 * The lead shortcut: put {@code mob}, led by {@code player}, on the deck, shut the gate, give the lead back.
	 * {@code false} when the wheel is taken or loose, or the mob does not run wheels.
	 */
	public boolean leadIn(Mob mob, Player player) {
		if (level == null || level.isClientSide() || occupant.ref != null || MobWheelRoster.profileOf(mob) == null
				|| !getBlockState().getValue(MobWheelStructure.FORMED)) {
			return false;
		}
		Direction facing = getBlockState().getValue(MobWheelControllerBlock.FACING);
		BlockPos gatePos = MobWheelStructure.at(worldPosition, facing, MobWheelStructure.GATE);
		BlockState gate = level.getBlockState(gatePos);
		if (!gate.is(ModContent.MOB_WHEEL_GATE.get())) {
			return false;
		}
		MobWheelRunner.takeLeadBack(mob, player);
		mob.snapTo(MobWheelStructure.anchor(worldPosition, facing), MobWheelStructure.runYaw(facing), 0.0F);
		shutIn(mob);
		if (gate.getValue(MobWheelGateBlock.OPEN)) { // swing, not toggle: toggle would look for a mob again
			MobWheelGateBlock.swing(level, gatePos, gate, false);
		}
		return true;
	}

	/** Make {@code mob} the occupant and snap it onto the deck. */
	private void shutIn(Mob mob) {
		MobWheelProfile profile = MobWheelRoster.profileOf(mob);
		if (profile == null) {
			return;
		}
		occupant.shutIn(mob, profile, MobWheelRoster.typeIdOf(mob));
		mob.setPersistenceRequired();
		Direction facing = getBlockState().getValue(MobWheelControllerBlock.FACING);
		MobWheelRunner.hold(mob, MobWheelStructure.anchor(worldPosition, facing), MobWheelStructure.runYaw(facing),
				!MobWheelRunner.isDistracted(mob));
		occupantNetworkId = mob.getId();
		setChanged();
		syncBlockEntityToClient();
	}

	/**
	 * The gate just opened (or the wheel came apart): stop holding the occupant. Its stamina is kept; a
	 * zombification immunity the wheel granted a piglin is taken back (D8).
	 */
	public void release() {
		boolean had = occupant.ref != null || occupant.lost;
		if (occupant.grantedImmunity && occupant.ref != null && level != null) {
			Mob mob = occupant.ref.getEntity(level, Mob.class);
			if (mob != null) {
				MobWheelRunner.revokeGrantedImmunity(mob);
			}
		}
		occupant.clear();
		occupantNetworkId = NO_OCCUPANT;
		if (had) {
			setChanged();
			syncBlockEntityToClient();
		}
	}

	// --- the tick ---

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		if (!state.getValue(MobWheelStructure.FORMED)) {
			setStatus(MobWheelStatus.UNFORMED, false, 0);
			return 0;
		}
		Direction facing = state.getValue(MobWheelControllerBlock.FACING);
		if (occupant.ref == null && captureCounter++ % CAPTURE_INTERVAL == 0 && !isGateOpen(level, facing)) {
			capture();
		}
		Mob mob = heldOccupant(level, pos, facing);
		MobWheelProfile profile = mob == null ? null
				: occupant.species != null ? occupant.species : MobWheelRoster.profileOf(mob);
		MobWheelStamina stamina = occupant.stamina;
		if (mob == null || profile == null || stamina == null) {
			if (mob != null) {
				release();
			}
			setStatus(occupant.lost ? MobWheelStatus.LOST : MobWheelStatus.NO_MOB, false, 0);
			return 0;
		}
		boolean distracted = MobWheelRunner.isDistracted(mob);
		MobWheelRunner.hold(mob, MobWheelStructure.anchor(pos, facing), MobWheelStructure.runYaw(facing), !distracted);
		if (distracted) {
			applyTrait(mob, profile, false);
			setStatus(MobWheelStatus.DISTRACTED, false, 0);
			return 0;
		}
		if (hayScanCounter++ % HAY_SCAN_INTERVAL == 0) {
			nearHay = MobWheelRunner.hayNearby(level, MobWheelStructure.bounds(pos, facing), HAY_RADIUS);
		}
		if (stamina.exhausted() && !feed(profile, stamina)) {
			stamina.restTick(profile.restPermille(), nearHay ? GeneratorConfig.mobWheelHayRestMultiplier : 1,
					GeneratorConfig.mobWheelRestTicks);
			applyTrait(mob, profile, false);
			setChanged();
			setStatus(nearHay ? MobWheelStatus.RESTING : MobWheelStatus.EXHAUSTED, false, 0);
			return 0;
		}
		long seed = mob.getUUID().getLeastSignificantBits() ^ pos.asLong();
		double pace = profile.pace(occupant.runTick, seed);
		if (!(pace > 0)) {
			occupant.runTick++;
			applyTrait(mob, profile, false);
			setStatus(MobWheelStatus.PAUSED, false, 0);
			return 0;
		}
		return run(level, MobWheelStructure.at(pos, facing, 1, MobWheelStructure.SIZE, 1), mob, profile, stamina,
				seed, pace);
	}

	/**
	 * One running tick (the occupant can run and is not pausing): EU at this pace and stamina, with the
	 * species' bonus where {@code top} — the block above the wheel's middle — meets its condition; the witch's
	 * once-per-run sip; the speed the client spins the wheel at.
	 */
	private int run(Level level, BlockPos top, Mob mob, MobWheelProfile profile, MobWheelStamina stamina, long seed,
			double pace) {
		applyTrait(mob, profile, true);
		boolean bonus = profile.bonus() != MobWheelProfile.Bonus.NONE
				&& profile.bonusApplies(MobWheelRunner.surroundings(level, top));
		int made = MobWheelOutput.euFor(profile, true, bonus, occupant.runTick, seed, stamina.fraction());
		int speed = (int) Math.round(100.0 * profile.spinFactor() * pace * (0.5 + 0.5 * stamina.fraction()));
		stamina.runTick();
		if (profile.trait() == MobWheelProfile.Trait.SELF_RESTORE
				&& stamina.selfRestore(MobWheelProfile.SELF_RESTORE_PERCENT, MobWheelProfile.SELF_RESTORE_PERCENT)) {
			MobWheelRunner.playSelfRestore(mob);
		}
		occupant.runTick++;
		setChanged();
		setStatus(MobWheelStatus.RUNNING, true, speed);
		return made;
	}

	/** This tick's species trait (D8); remembers a granted piglin immunity so release can take it back. */
	private void applyTrait(Mob mob, MobWheelProfile profile, boolean runningNow) {
		if (MobWheelRunner.applyTrait(mob, profile, runningNow)) {
			occupant.grantedImmunity = true;
			setChanged();
		}
	}

	/**
	 * The occupant, found for this tick (the caller holds it) — or {@code null} when there is none to hold. A
	 * mob not found yet after a chunk load is waited for {@link #LOST_AFTER_TICKS} and then counted as lost;
	 * a dead one, one behind an open gate, or one that left the deck by other means is let go (D3).
	 */
	@Nullable
	private Mob heldOccupant(Level level, BlockPos pos, Direction facing) {
		if (occupant.ref == null) {
			return null;
		}
		Mob mob = occupant.ref.getEntity(level, Mob.class);
		if (mob == null) {
			occupant.missingTicks++;
			if (occupant.missingTicks >= LOST_AFTER_TICKS) {
				release();
				occupant.lost = true;
			}
			return null;
		}
		occupant.missingTicks = 0;
		if (!mob.isAlive() || mob.isRemoved() || isGateOpen(level, facing)
				|| mob.position().distanceToSqr(MobWheelStructure.anchor(pos, facing)) > ESCAPED_DISTANCE_SQR) {
			release();
			return null;
		}
		if (occupantNetworkId != mob.getId()) {
			occupantNetworkId = mob.getId();
			syncBlockEntityToClient();
		}
		return mob;
	}

	/**
	 * Spend one portion from the feeder on an exhausted occupant (D5). Food the occupant does not eat, or too
	 * few items for a portion, is left where it is.
	 */
	private boolean feed(MobWheelProfile profile, MobWheelStamina stamina) {
		ItemStack food = items.get(FEED_SLOT);
		if (food.isEmpty()) {
			return false;
		}
		int portion = MobWheelFeed.portion(profile, MobWheelRunner.itemIdOf(food));
		if (portion <= 0 || food.getCount() < portion || !stamina.feed()) {
			return false;
		}
		food.shrink(portion);
		setChanged();
		return true;
	}

	private boolean isGateOpen(Level level, Direction facing) {
		BlockState gate = level.getBlockState(MobWheelStructure.at(worldPosition, facing, MobWheelStructure.GATE));
		return !gate.is(ModContent.MOB_WHEEL_GATE.get()) || gate.getValue(MobWheelGateBlock.OPEN);
	}

	/** Record what the renderer and the screen show; a block-entity update goes out only when it changed. */
	private void setStatus(MobWheelStatus next, boolean nowRunning, int speed) {
		boolean changed = next != status || nowRunning != running || speed != speedPercent;
		status = next;
		running = nowRunning;
		speedPercent = speed;
		progress = speed;
		maxProgress = next.ordinal();
		if (changed) {
			syncBlockEntityToClient();
		}
	}

	@Override
	protected void publishEffectiveRate(int effectiveEuPerTick) {
		this.productionRate = effectiveEuPerTick;
	}

	// --- energy ---

	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		Direction facing = getBlockState().getValue(MobWheelControllerBlock.FACING);
		return MobWheelControllerBlock.isPortFace(facing, worldFace) ? EnergyRole.OUT : EnergyRole.NONE;
	}

	// --- inventory ---

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot != FEED_SLOT) {
			return false;
		}
		return MobWheelFeed.anyFoodAccepts(MobWheelRunner.itemIdOf(stack));
	}

	// --- GUI channels ---

	/**
	 * GUI sync channels: the base four — PROGRESS is the wheel speed in percent, MAX_PROGRESS the
	 * {@link MobWheelStatus} ordinal — then the effective EU/t, the stamina in thousandths and the occupant's
	 * entity type (its index in {@link MobWheelProfile#RUNNER_TYPE_IDS} + 1, 0 when the wheel is empty).
	 */
	public enum Channel { ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, RATE, STAMINA, SPECIES }

	/** Width of {@link #getDataAccess()}, which the menu's client stub sizes itself from. */
	public static final int DATA_COUNT = Channel.values().length;

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.readWrite(Channel.RATE, () -> productionRate, value -> productionRate = value)
				.read(Channel.STAMINA, this::staminaPermille)
				.read(Channel.SPECIES, () -> MobWheelProfile.runnerIndex(runnerType()) + 1)
				.build();
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new MobWheelMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	// --- persistence ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Wood", wood);
		occupant.save(output);
		output.putInt("Status", status.ordinal());
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		String family = input.getStringOr("Wood", DEFAULT_WOOD);
		wood = WoodType.values().anyMatch(type -> type.name().equals(family)) ? family : DEFAULT_WOOD;
		occupant.load(input);
		status = MobWheelStatus.byOrdinal(input.getIntOr("Status", 0));
		// Only the update packet carries these; a save never does, so a reload starts them afresh.
		running = input.getBooleanOr("Running", false);
		speedPercent = input.getIntOr("Speed", 0);
		occupantNetworkId = input.getIntOr("OccupantNetId", NO_OCCUPANT);
	}

	/** The update packet adds what only a watching client needs: the live occupant id and whether it runs. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		CompoundTag tag = super.getUpdateTag(provider);
		tag.putInt("OccupantNetId", occupant.ref == null ? NO_OCCUPANT : occupantNetworkId);
		tag.putBoolean("Running", running);
		tag.putInt("Speed", speedPercent);
		return tag;
	}
}
