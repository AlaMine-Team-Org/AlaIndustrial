package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.QuarryControllerBlock;
import dev.alaindustrial.block.QuarryFoundationBlock;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.block.entity.quarry.QuarryDigger;
import dev.alaindustrial.block.entity.quarry.QuarryFrameLayout;
import dev.alaindustrial.block.entity.quarry.QuarryFrameShapes;
import dev.alaindustrial.block.entity.quarry.QuarryFrameWorld;
import dev.alaindustrial.block.entity.quarry.QuarryWorkStatus;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.structure.QuarryFrameScan.Status;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.menu.QuarryControllerMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The brain of a quarry (MOD-950, stage A of MOD-637): it reads the portal frame around it, lights up when the frame
 * stands, opens its tower's foundation as the power socket, and tells the player what is wrong when it does not.
 * Since stage B (MOD-951) it digs: a {@link QuarryDigger} works the pit into the {@value #BUFFER_SLOTS}-slot buffer,
 * wearing the drill bit in slot {@value #BIT_SLOT}; the buffer is output only, the bit slot input only, so automation
 * through the socket foundation can take the haul and bring a fresh bit.
 *
 * <p><b>Never asleep.</b> The periodic scan is the only thing that notices a frame taken apart out of neighbour range
 * — a beam broken at the far tower is nobody's neighbour here — so a sleeping controller would leave a broken frame
 * looking whole, the lesson of the crystal farm.
 *
 * <p><b>What the frame paints, and what it does not.</b> Only two blocks change: this head's {@code formed} panel and
 * the {@code port} of the socket foundation straight below it, in the bottom layer of the tower. Both sit at fixed
 * offsets from this block, so taking the frame apart needs no remembered box: the socket is closed by the next failed
 * scan, or at once when this block goes. The rest of the frame turns into the designer's model later (stages C/D).
 */
public class QuarryControllerBlockEntity extends MachineBlockEntity implements MenuProvider, NoUpgradePanel {

	/** The drill bit's slot. */
	public static final int BIT_SLOT = 0;
	/** The first slot of the buffer the haul goes into. */
	public static final int BUFFER_START = 1;
	/** Slots of the buffer. */
	public static final int BUFFER_SLOTS = 27;
	public static final int SLOT_COUNT = BUFFER_START + BUFFER_SLOTS;

	/** The screen's channels: the four of every machine, then the digger's and the frame's state. */
	public enum Channel {
		ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, STATUS, FRAME_STATUS, LAYER, LAYERS, LAYER_PERMILLE
	}

	public static final int DATA_COUNT = Channel.values().length;

	private final QuarryDigger digger = new QuarryDigger();

	private QuarryFrameScan.Result result = QuarryFrameScan.Result.unscanned();
	private int scanCooldown;
	/** The frame this controller painted formed, or {@code null}: what to unpaint, and what the renderer draws. */
	private @Nullable QuarryFrameLayout layout;

	public QuarryControllerBlockEntity(BlockPos pos, BlockState state) {
		// HV in, nothing out: the quarry is a consumer, and the spec puts it on a 512 EU/t line.
		super(ModContent.QUARRY_CONTROLLER_BE.get(), pos, state, EnergyTier.HV, SLOT_COUNT,
				Math.max(0, Config.quarryBuffer), EnergyTier.HV.maxVoltage(), 0L);
	}

	/** Every face takes power: the head has no front (see {@link QuarryControllerBlock}). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.IN;
	}

	/** Re-arms the scan for the next tick — called when a neighbour changes or the block is placed. */
	public void requestScan() {
		scanCooldown = 0;
		wake();
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (scanCooldown > 0) {
			scanCooldown--;
		} else {
			scanCooldown = Math.max(1, Config.quarryScanIntervalTicks);
			rescan(level, pos, state);
		}
		if (level instanceof ServerLevel server && layout != null && result.formed()) {
			if (Config.quarryDiggingEnabled) {
				digger.tick(server, pos, layout, result.tier(), host);
			} else {
				digger.switchedOff();
			}
		} else {
			digger.notFormed();
		}
		progress = digger.opElapsed();
		maxProgress = digger.opTicks();
		return 0;
	}

	/** The digger's view of this controller: the bit slot, the buffer, the energy and the owner. */
	private final QuarryDigger.Host host = new QuarryDigger.Host() {
		@Override
		public ItemStack bit() {
			return items.get(BIT_SLOT);
		}

		@Override
		public void setBit(ItemStack bit) {
			items.set(BIT_SLOT, bit);
			setChanged();
		}

		@Override
		public boolean fits(List<ItemStack> stacks) {
			List<ItemStack> buffer = new ArrayList<>(BUFFER_SLOTS);
			for (int i = BUFFER_START; i < SLOT_COUNT; i++) {
				buffer.add(items.get(i).copy());
			}
			for (ItemStack stack : stacks) {
				ItemStack rest = stack.copy();
				merge(buffer, rest);
				if (!rest.isEmpty()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public void store(ItemStack stack) {
			List<ItemStack> buffer = items.subList(BUFFER_START, SLOT_COUNT);
			merge(buffer, stack);
			setChanged();
		}

		@Override
		public long energy() {
			return energy.getAmount();
		}

		@Override
		public void spend(long eu) {
			energy.drainInternal(eu);
		}

		@Override
		public @Nullable UUID owner() {
			return getOwner();
		}

		@Override
		public String ownerName() {
			String name = getOwnerName();
			return name == null || name.isBlank() ? "AlaQuarry" : name;
		}

		@Override
		public void changed() {
			setChanged();
		}
	};

	/** Merges {@code stack} into {@code slots}: onto matching stacks first, then into empty slots. */
	private static void merge(List<ItemStack> slots, ItemStack stack) {
		for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
			ItemStack slot = slots.get(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
				int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
				if (moved > 0) {
					slot.grow(moved);
					stack.shrink(moved);
				}
			}
		}
		for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
			if (slots.get(i).isEmpty()) {
				slots.set(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
			}
		}
	}

	private void rescan(Level level, BlockPos pos, BlockState state) {
		result = QuarryFrameWorld.scan(level, pos);
		boolean formed = result.formed();
		boolean wasFormed = layout != null;
		QuarryFrameLayout now = formed ? QuarryFrameLayout.of(result, pos.getY()) : null;
		if (layout != null && !layout.equals(now)) {
			QuarryFrameWorld.paint(level, layout, shapesOf(level, pos, layout), false, null);
			QuarryFrameShapes.unregister(level, pos);
		}
		if (now != null) {
			if (QuarryFrameShapes.registered(level, pos) == null || !now.equals(layout)) {
				QuarryFrameShapes.register(level, pos, now);
			}
			// Every scan, not only on the change: a cell broken and placed back while the frame stood comes in loose.
			QuarryFrameWorld.paint(level, now, shapesOf(level, pos, now), true, null);
		}
		if (!Objects.equals(layout, now)) {
			layout = now;
			setChanged();
			syncBlockEntityToClient();
		}
		// The controller is a cell of its frame, so the paint sets its flag; this catches a flag no frame owns.
		BlockState current = level.getBlockState(pos);
		if (!formed && current.getValue(QuarryControllerBlock.FORMED)) {
			level.setBlock(pos, current.setValue(QuarryControllerBlock.FORMED, false), Block.UPDATE_ALL);
		}
		setPort(level, QuarryFoundationBlock.foundationBelow(pos), formed);
		if (level instanceof ServerLevel server) {
			if (formed && !wasFormed) {
				server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.2f);
			} else if (!formed) {
				markProblem(server, wasFormed);
			}
		}
	}

	/** The shapes registered for this controller's frame, or the frame's own if none are registered yet. */
	private static Map<Long, VoxelShape> shapesOf(Level level, BlockPos pos, QuarryFrameLayout frame) {
		Map<Long, VoxelShape> registered = QuarryFrameShapes.registered(level, pos);
		return registered != null ? registered : QuarryFrameShapes.cells(frame);
	}

	/**
	 * Registers the remembered frame's shapes as soon as the block entity knows its level: on the server after a chunk
	 * load, on the client when the chunk arrives, so the frame is solid before the first scan.
	 */
	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (layout != null) {
			QuarryFrameShapes.register(level, getBlockPos(), layout);
		}
	}

	/** The frame as last painted, for the renderer; {@code null} while the frame is not formed. */
	public @Nullable QuarryFrameLayout layout() {
		return layout;
	}

	/**
	 * Opens or closes the foundation's port. Written with neighbour updates, so the cable next to it re-draws its arm
	 * and the energy network re-reads the endpoint on the same tick.
	 */
	private static void setPort(Level level, BlockPos foundation, boolean open) {
		if (!level.isLoaded(foundation)) {
			return;
		}
		BlockState state = level.getBlockState(foundation);
		if (state.getBlock() instanceof QuarryFoundationBlock && state.getValue(QuarryFoundationBlock.PORT) != open) {
			level.setBlock(foundation, state.setValue(QuarryFoundationBlock.PORT, open), Block.UPDATE_ALL);
		}
	}

	/**
	 * Smoke where the player has to look, every scan while the frame is not whole; one note when it has just come
	 * apart, because the player is usually looking elsewhere when a creeper takes a tower out.
	 */
	private void markProblem(ServerLevel level, boolean justBroke) {
		BlockPos where = new BlockPos(result.x(), result.y(), result.z());
		level.sendParticles(ParticleTypes.SMOKE, where.getX() + 0.5, where.getY() + 0.5, where.getZ() + 0.5,
				12, 0.3, 0.3, 0.3, 0.01);
		if (justBroke) {
			level.playSound(null, getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5f, 1.4f);
		}
	}

	/**
	 * The frame cannot outlive its brain: the port of the foundation below closes the moment this block goes, or a
	 * cable would stay joined to a socket that leads nowhere.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			setPort(level, QuarryFoundationBlock.foundationBelow(pos), false);
			if (layout != null) {
				// The rest of the frame comes back as blocks; this cell is on its way out and is not touched.
				QuarryFrameWorld.paint(level, layout, shapesOf(level, pos, layout), false, pos);
				QuarryFrameShapes.unregister(level, pos);
			}
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (layout != null) {
			output.putIntArray("Frame", layout.toArray());
		}
		digger.save(output);
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		digger.load(input);
		layout = QuarryFrameLayout.fromArray(input.getIntArray("Frame").orElse(null));
		// On the client this is the update tag: the frame's shapes follow it, formed or not.
		if (level != null) {
			if (layout != null) {
				QuarryFrameShapes.register(level, getBlockPos(), layout);
			} else {
				QuarryFrameShapes.unregister(level, getBlockPos());
			}
		}
	}

	/** The status of the last scan, for gametests. */
	public Status status() {
		return result.status();
	}

	/** Whether a drill bit is in its slot; the renderer hangs the drill on the cart only then. */
	public boolean hasDrillBit() {
		return !items.get(BIT_SLOT).isEmpty();
	}

	/**
	 * A change to the bit slot reaches the client at once, whoever made it — a hand, a pipe, the drill wearing out —
	 * so the drill on the cart appears and goes with the bit.
	 */
	@Override
	public void setChanged() {
		super.setChanged();
		boolean bit = hasDrillBit();
		if (level != null && !level.isClientSide() && bit != syncedBit) {
			syncedBit = bit;
			syncBlockEntityToClient();
		}
	}

	/** Whether the client was last told a bit is in the slot. */
	private boolean syncedBit;

	/** What the digger is doing, or why it stands. */
	public QuarryWorkStatus workStatus() {
		return digger.status();
	}

	/** The work cycle itself, for gametests that read its step. */
	public QuarryDigger digger() {
		return digger;
	}

	// --- slots, screen and network need ---

	/** Only a drill bit goes in, into an empty bit slot; the buffer takes nothing from outside. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == BIT_SLOT && items.get(BIT_SLOT).isEmpty() && stack.is(ModContent.QUARRY_DRILL_BIT.get());
	}

	/** Automation takes the haul from the buffer, never the bit. */
	@Override
	protected boolean isOutputSlot(int slot) {
		return slot >= BUFFER_START && slot < SLOT_COUNT;
	}

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.read(Channel.STATUS, () -> digger.status().ordinal())
				.read(Channel.FRAME_STATUS, () -> result.status().ordinal())
				.read(Channel.LAYER, digger::layerShown)
				.read(Channel.LAYERS, digger::layers)
				.read(Channel.LAYER_PERMILLE, digger::layerPermille)
				.build();
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		ContainerLevelAccess access = ContainerLevelAccess.create(getLevel(), getBlockPos());
		return new QuarryControllerMenu(syncId, inventory, this, access);
	}

	/**
	 * Its per-tick need for the network (ADR-049): a stone block's operation spread over its ticks. The buffer fills
	 * from what the line has left over, the way every big-buffer machine's does.
	 */
	@Override
	public long perTickDemand() {
		int ticks = Math.max(1, Config.quarryStoneDrillTicks);
		long stone = Math.max(Config.quarryDrillMinEu, Math.round(Config.quarryDrillEuPerHardness * 1.5D));
		return Math.max(1L, stone / ticks);
	}

	/**
	 * What the head says when clicked: the frame's size and tier, or the first broken rule and where. Green means the
	 * frame stands, red that it does not; the numbers are lifted out in white, because they are what the player goes
	 * looking for (the crystal farm's convention, MOD-522).
	 */
	public Component describeStatus() {
		String key = "message.alaindustrial.quarry." + statusKey(result.status());
		Component line = switch (result.status()) {
			case FORMED -> Component.translatable(key, value(result.spanX()), value(result.spanZ()),
					value(result.tier()), value(result.limit()));
			case TOO_SMALL -> Component.translatable(key, value(result.spanX()), value(result.spanZ()),
					value(Math.max(1, Config.quarryMinSpan)));
			case TOO_LARGE -> Component.translatable(key, value(result.spanX()), value(result.spanZ()),
					value(result.tier()), value(result.limit()));
			case NO_BRIDGE, CONTROLLER_NOT_ON_EDGE -> Component.translatable(key);
			case UNSCANNED -> Component.translatable(key);
			case TOWERS_NOT_FOUND -> result.x() == getBlockPos().getX() && result.z() == getBlockPos().getZ()
					? Component.translatable(key + ".vague")
					: at(key);
			default -> at(key);
		};
		return line.copy().withStyle(result.formed() ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private Component at(String key) {
		return Component.translatable(key, value(result.x()), value(result.y()), value(result.z()));
	}

	private static Component value(int number) {
		return Component.literal(String.valueOf(number)).withStyle(ChatFormatting.WHITE);
	}

	private static String statusKey(Status status) {
		return switch (status) {
			case UNSCANNED -> "unscanned";
			case FORMED -> "formed";
			case CONTROLLER_NOT_ON_TOWER -> "not_on_tower";
			case CONTROLLER_NOT_ON_EDGE -> "not_on_edge";
			case TOWERS_NOT_FOUND -> "towers_not_found";
			case TOWER_INCOMPLETE -> "tower_incomplete";
			case TOO_SMALL -> "too_small";
			case TOO_LARGE -> "too_large";
			case PATH_BROKEN -> "path_broken";
			case NO_BRIDGE -> "no_bridge";
			case BRIDGE_BROKEN -> "bridge_broken";
			case SECOND_BRIDGE -> "second_bridge";
			case NO_CART -> "no_cart";
			case SECOND_CART -> "second_cart";
			case SECOND_CONTROLLER -> "second_controller";
		};
	}
}
