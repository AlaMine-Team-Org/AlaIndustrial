package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.machine.StatusLine;
import dev.alaindustrial.core.world.DropSeek;
import dev.alaindustrial.core.world.StuckDropTracker;
import dev.alaindustrial.menu.VacuumCollectorMenu;
import dev.alaindustrial.network.ZonePreviewPayload;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Vacuum Collector, tier I (MOD-802): draws loose drops within reach into a nine-slot buffer, which item
 * pipes empty.
 *
 * <p><b>A pulse, not a stream.</b> Every {@link Config#vacuumCollectorPulseTicks} ticks the collector looks
 * for dropped items in a cube {@link Config#vacuumCollectorRadius} blocks out from the centre of its block,
 * and acts on at most {@link Config#vacuumCollectorItemsPerPulse} of them, the nearest first (equal distances
 * by UUID, so the choice never depends on the order the world returns them in). A drop that touches the
 * block is taken — as many of its items as the buffer has room for, the rest stays in the world as the same
 * entity; one further out is set moving towards the block ({@link DropSeek}, the electromagnet's pull). No
 * teleport: a drop arrives by physics or not at all.
 *
 * <p><b>What it leaves alone.</b> A drop still on its pickup delay (a fresh {@code Q}-drop, the never-pickup
 * sentinel), one in lava, and one the buffer has no room for. It never changes a drop's lifetime, pickup
 * delay or owner, never touches a block, and looks only where the world is loaded: the entity query does not
 * load chunks. A drop that has stopped getting closer — behind a wall, in a hole — is let go for a while
 * ({@link StuckDropTracker}), so it does not cost energy at every pulse for nothing.
 *
 * <p><b>Energy.</b> {@link Config#vacuumCollectorEuPerTick} per tick of the pulse period, paid at once and only
 * for a pulse that pulled or took something. An empty field costs nothing.
 *
 * <p><b>Out through pipes, not by pushing; connections on the lid and the floor only.</b> The collector never
 * pushes into a neighbour: an item pipe pulls out of a container by itself. The four sides are the suction
 * funnels and take neither a pipe nor a cable; on the top and bottom faces all nine slots are extract-only and
 * energy comes in. Nothing can be put in — not by hand, not by a hopper, not by a pipe — so a plain pipe on the
 * lid or under the floor carries items away with no configuration ({@code ItemNetwork.mayFeed}: a container
 * that gives and never takes sorts its slots by face). Drops are drawn in from every direction regardless.
 */
public class VacuumCollectorBlockEntity extends MachineBlockEntity implements MenuProvider, NoUpgradePanel {

	/** The buffer: nine slots, all of them output. */
	public static final int SLOT_COUNT = 9;
	private static final int[] BUFFER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private static final int[] NO_SLOTS = new int[0];

	/** How close the block's box counts as "touching": a drop resting against a face is taken. */
	private static final double TOUCH_MARGIN = 0.25;
	/** Pull speeds: the electromagnet's, which reads as a real magnet without tunnelling through blocks. */
	private static final double SEEK_SPEED = 0.55;
	private static final double SEEK_GAIN = 0.45;
	private static final double SEEK_SNAP = 0.1;
	/** Pulls without getting closer before a drop is let go, and for how long: two seconds each. */
	private static final int STALL_PULLS = 8;
	private static final int RELEASE_TICKS = 40;

	/** The four base channels, then the status line. */
	public enum Channel { ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, STATUS }

	public static final int DATA_COUNT = Channel.values().length;

	/** What the last pulse did, shown on the screen. */
	public enum Status implements StatusLine {
		/** It pulled or took a drop. */
		WORKING(false),
		/** Nothing to pull within reach. */
		IDLE(false),
		/** Drops are within reach but the buffer has no room for any of them. */
		BUFFER_FULL(true),
		/** Drops are within reach but the buffer cannot pay for a pulse. */
		NO_POWER(true);

		private final boolean blocking;

		Status(boolean blocking) {
			this.blocking = blocking;
		}

		@Override
		public String translationKey() {
			return "gui.alaindustrial.vacuum_collector.status." + name().toLowerCase(Locale.ROOT);
		}

		@Override
		public boolean isBlocking() {
			return blocking;
		}

		public static Status byOrdinal(int ordinal) {
			Status[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : IDLE;
		}
	}

	/** What one drop got from the collector. */
	public enum Action {
		/** Not a drop it acts on: out of reach, still on its delay, in lava, or no room for it. */
		NONE,
		/** Set moving towards the block. */
		PULLED,
		/** Taken into the buffer, whole or in part. */
		TAKEN
	}

	private final StuckDropTracker stuck = new StuckDropTracker(STALL_PULLS, RELEASE_TICKS);
	private Status status = Status.IDLE;
	/**
	 * Game time of the next pulse — a schedule, not a countdown, so a sleep cut short by an energy delivery
	 * does not pulse early. Not saved: after a reload the collector pulses on its first tick.
	 */
	private long nextPulseTick;

	public VacuumCollectorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.VACUUM_COLLECTOR_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.vacuumCollectorBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		int period = pulsePeriod();
		long now = level.getGameTime();
		if (now < nextPulseTick) {
			return (int) Math.min(period - 1, nextPulseTick - now - 1);
		}
		nextPulseTick = now + period;
		if (level instanceof ServerLevel serverLevel) {
			setStatus(pulse(serverLevel));
		}
		// New drops do not wake a block, so the collector never sleeps past its next pulse.
		return period - 1;
	}

	private static int pulsePeriod() {
		return Math.max(1, Config.vacuumCollectorPulseTicks);
	}

	private void setStatus(Status next) {
		if (next != status) {
			status = next;
			setChanged();
		}
		updateLit(next == Status.WORKING);
	}

	/**
	 * One pulse, outside the schedule: find the drops within reach, pay for the pulse if any of them will be
	 * pulled or taken, and act on the nearest ones. Public for the gametests, which drive pulses one by one.
	 */
	public Status pulse(ServerLevel level) {
		long now = level.getGameTime();
		List<ItemEntity> inReach = level.getEntitiesOfClass(ItemEntity.class, zone(),
				item -> isLooseDrop(item) && inZone(item) && !stuck.isReleased(item, now));
		if (inReach.isEmpty()) {
			recordEuRate(0);
			return Status.IDLE;
		}
		List<ItemEntity> fitting = new ArrayList<>();
		for (ItemEntity item : inReach) {
			if (roomFor(item.getItem()) > 0) {
				fitting.add(item);
			}
		}
		if (fitting.isEmpty()) {
			recordEuRate(0);
			return Status.BUFFER_FULL;
		}
		int euPerTick = Math.max(1, Config.vacuumCollectorEuPerTick);
		long cost = (long) euPerTick * pulsePeriod();
		if (energy.getAmount() < cost) {
			recordEuRate(0);
			return Status.NO_POWER;
		}
		Vec3 centre = centre();
		fitting.sort(Comparator.<ItemEntity>comparingDouble(item -> item.distanceToSqr(centre))
				.thenComparing(ItemEntity::getUUID));
		int limit = Math.max(1, Config.vacuumCollectorItemsPerPulse);
		int acted = 0;
		boolean took = false;
		for (ItemEntity item : fitting.subList(0, Math.min(limit, fitting.size()))) {
			Action action = collectSingle(item);
			if (action != Action.NONE) {
				acted++;
				took |= action == Action.TAKEN;
			}
		}
		if (acted == 0) {
			recordEuRate(0);
			return Status.IDLE;
		}
		energy.drainInternal(cost);
		recordEuRate(euPerTick);
		if (took) {
			completeOperation(level, cost);
		}
		setChanged();
		return Status.WORKING;
	}

	/**
	 * Act on one drop: take it if it touches the block, pull it if it is further out. Spends no energy — the
	 * pulse pays. The single source of truth for "what does the collector do to this drop", public so a
	 * gametest can drive it on a detached entity the live scan of a neighbouring test cannot see.
	 */
	public Action collectSingle(ItemEntity item) {
		if (level == null || !isLooseDrop(item) || !inZone(item)) {
			return Action.NONE;
		}
		long now = level.getGameTime();
		if (stuck.isReleased(item, now) || roomFor(item.getItem()) <= 0) {
			return Action.NONE;
		}
		if (item.getBoundingBox().intersects(new AABB(getBlockPos()).inflate(TOUCH_MARGIN))) {
			take(item);
			return Action.TAKEN;
		}
		Vec3 centre = centre();
		double distance = Math.sqrt(item.distanceToSqr(centre));
		if (!DropSeek.seek(item, centre, SEEK_SPEED, SEEK_GAIN, SEEK_SNAP)) {
			return Action.NONE;
		}
		stuck.recordPull(item, distance, now);
		return Action.PULLED;
	}

	/** Whether the collector acts on {@code item} at all: a live, non-empty drop past its delay, not in lava. */
	private static boolean isLooseDrop(ItemEntity item) {
		return item.isAlive() && !item.getItem().isEmpty() && !item.hasPickUpDelay() && !item.isInLava();
	}

	/** The reach: the cube around the block centre, checked by position, not by the entity's box. */
	private boolean inZone(ItemEntity item) {
		Vec3 centre = centre();
		double r = radius();
		return Math.abs(item.getX() - centre.x) <= r && Math.abs(item.getY() - centre.y) <= r
				&& Math.abs(item.getZ() - centre.z) <= r;
	}

	private AABB zone() {
		return AABB.ofSize(centre(), radius() * 2.0, radius() * 2.0, radius() * 2.0);
	}

	private static double radius() {
		return reach();
	}

	/** The reach: half the side of the zone cube, from the centre of the block — also what the preview draws. */
	public static double reach() {
		return Math.max(1, Config.vacuumCollectorRadius);
	}

	private Vec3 centre() {
		return Vec3.atCenterOf(getBlockPos());
	}

	/**
	 * Move as many of the drop's items into the buffer as fit; the rest stays in the world as the same
	 * entity. The buffer is written first and exactly that many leave the drop.
	 */
	private void take(ItemEntity item) {
		ItemStack stack = item.getItem();
		int moved = insert(stack);
		if (moved <= 0) {
			return;
		}
		stack.shrink(moved);
		if (stack.isEmpty()) {
			item.discard();
		} else {
			item.setItem(stack);
		}
		setChanged();
	}

	// --- the buffer ---

	/** The cap of one buffer slot for {@code stack}: its own stack size, never above the machine family's. */
	private static int slotLimit(ItemStack stack) {
		return Math.min(OUTPUT_MAX, stack.getMaxStackSize());
	}

	/** How many items of {@code stack} the buffer has room for, up to its count. */
	int roomFor(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		int room = 0;
		int limit = slotLimit(stack);
		for (int slot = 0; slot < SLOT_COUNT && room < stack.getCount(); slot++) {
			ItemStack held = items.get(slot);
			if (held.isEmpty()) {
				room += limit;
			} else if (ItemStack.isSameItemSameComponents(held, stack)) {
				room += Math.max(0, limit - held.getCount());
			}
		}
		return Math.min(room, stack.getCount());
	}

	/** Put as many of {@code stack}'s items as fit into the buffer, matching stacks first; returns how many. */
	private int insert(ItemStack stack) {
		int left = roomFor(stack);
		int moved = 0;
		int limit = slotLimit(stack);
		for (int slot = 0; slot < SLOT_COUNT && left > 0; slot++) {
			ItemStack held = items.get(slot);
			if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack)) {
				int add = Math.min(left, limit - held.getCount());
				if (add > 0) {
					held.grow(add);
					left -= add;
					moved += add;
				}
			}
		}
		for (int slot = 0; slot < SLOT_COUNT && left > 0; slot++) {
			if (items.get(slot).isEmpty()) {
				int add = Math.min(left, limit);
				items.set(slot, stack.copyWithCount(add));
				left -= add;
				moved += add;
			}
		}
		return moved;
	}

	/** Nothing goes in by hand, hopper or pipe: the buffer is filled only from the world. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return false;
	}

	/** Every buffer slot gives its items to automation, through the faces of {@link #getSlotsForFace}. */
	@Override
	protected boolean isOutputSlot(int slot) {
		return slot >= 0 && slot < SLOT_COUNT;
	}

	/**
	 * The four sides are the suction funnels and nothing else: items leave only through the lid and the
	 * floor. An empty answer for a side is also what keeps an item pipe from joining it at all
	 * ({@code ItemPipeBlock.hasEndpointCandidate}), so the side shows no arm.
	 */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return side.getAxis().isVertical() ? BUFFER_SLOTS : NO_SLOTS;
	}

	/** Energy comes in through the lid and the floor only; the funnels in the sides take none. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace.getAxis().isVertical() ? EnergyRole.IN : EnergyRole.NONE;
	}

	// --- sync and menu ---

	/** The status the last pulse left, as the screen reads it. */
	public Status status() {
		return status;
	}

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.read(Channel.STATUS, () -> status.ordinal())
				.build();
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		// Tell the opening player which block the screen belongs to, so the preview button shows its state.
		ZonePreviewPayload.send(player, level, worldPosition, reach(), false);
		return new VacuumCollectorMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	/** Its per-tick need for the network (MOD-789, ADR-049): the upkeep while it pulls. */
	@Override
	public long perTickDemand() {
		return Math.max(1, Config.vacuumCollectorEuPerTick);
	}
}
