package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.energy.CableNode;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.FloorPortLender;
import dev.alaindustrial.core.environment.GeneratorConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * One group of horizontally touching piezo plates (MOD-764): the plates share one store — the sum of their own
 * buffers — and any plate with a receiver beside it or under its floor block is an outlet that hands the
 * group's energy on.
 *
 * <p><b>Never saved.</b> A group is a view of the world, rebuilt by breadth-first walk at most once per
 * {@link #REFRESH_TICKS} and whenever a plate joins, leaves or gains a neighbour. Each plate keeps its own
 * charge, so there is no "main" plate whose chunk can unload or whose breaking would lose the whole store;
 * breaking one plate loses only what that plate held.
 *
 * <p><b>One object per group.</b> The walk hands the object it built to every member, and a plate that
 * already holds a fresh group reuses it. That is what lets two outlets of one group spend one shared
 * {@link GeneratorConfig#piezoGroupOutputPerTick} budget, and it partitions a path longer than
 * {@link GeneratorConfig#piezoGroupMaxPlates}: the walk does not take a plate that belongs to another fresh
 * group.
 */
public final class PiezoPlateGroup {
	/** Ticks a group stays fresh; an outlet appearing only under the floor is noticed within this. */
	public static final int REFRESH_TICKS = 40;
	/** Hard ceiling for the walk, whatever the operator's config says. */
	public static final int HARD_MAX_PLATES = 256;

	/** What the group is doing, as the wrench overlay colours it. */
	public enum Status {
		/** Has an outlet and room to store new presses. */
		OK,
		/** No receiver anywhere: presses are stored, nothing leaves. */
		NO_OUTLET,
		/** Every plate is full: a new press is lost. */
		FULL;

		public static Status byOrdinal(int ordinal) {
			Status[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OK;
		}
	}

	/** Bit of a plate's outlet mask for "a receiver two blocks down, under the floor block". */
	public static final int OUTLET_FLOOR = 1 << 4;

	private final List<PiezoPlateBlockEntity> members;
	private final List<PiezoPlateBlockEntity> outlets;
	private final long builtTick;
	private boolean invalid;
	private long budgetTick = Long.MIN_VALUE;
	private long budgetLeft;

	private PiezoPlateGroup(List<PiezoPlateBlockEntity> members, List<PiezoPlateBlockEntity> outlets, long builtTick) {
		this.members = members;
		this.outlets = outlets;
		this.builtTick = builtTick;
	}

	/** The fresh group {@code plate} belongs to, walking the world again only when the cached one went stale. */
	public static PiezoPlateGroup of(Level level, PiezoPlateBlockEntity plate) {
		PiezoPlateGroup cached = plate.group();
		long now = level.getGameTime();
		if (cached != null && cached.isFresh(now)) {
			return cached;
		}
		return build(level, plate, now);
	}

	private boolean isFresh(long now) {
		return !invalid && now - builtTick < REFRESH_TICKS && now >= builtTick;
	}

	/** Make every member rebuild on its next use — a plate joined, left or got a new neighbour. */
	public void invalidate() {
		invalid = true;
	}

	private static PiezoPlateGroup build(Level level, PiezoPlateBlockEntity start, long now) {
		int cap = Math.min(HARD_MAX_PLATES, Math.max(1, GeneratorConfig.piezoGroupMaxPlates));
		List<PiezoPlateBlockEntity> members = new ArrayList<>();
		Deque<PiezoPlateBlockEntity> queue = new ArrayDeque<>();
		List<BlockPos> seen = new ArrayList<>();
		queue.add(start);
		seen.add(start.getBlockPos());
		while (!queue.isEmpty() && members.size() < cap) {
			PiezoPlateBlockEntity plate = queue.poll();
			members.add(plate);
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos next = plate.getBlockPos().relative(dir);
				if (seen.contains(next) || !level.isLoaded(next)) {
					continue;
				}
				if (level.getBlockEntity(next) instanceof PiezoPlateBlockEntity neighbour && !neighbour.isRemoved()
						&& !belongsToOtherFreshGroup(neighbour, now)) {
					seen.add(next);
					queue.add(neighbour);
				}
			}
		}
		List<PiezoPlateBlockEntity> outlets = new ArrayList<>();
		long stored = 0;
		long capacity = 0;
		for (PiezoPlateBlockEntity plate : members) {
			int mask = outletMask(level, plate.getBlockPos());
			plate.setOutletMask(mask);
			if (mask != 0) {
				outlets.add(plate);
			}
			stored += plate.getEnergyStorage().getAmount();
			capacity += plate.getEnergyStorage().getCapacity();
		}
		PiezoPlateGroup group = new PiezoPlateGroup(List.copyOf(members), List.copyOf(outlets), now);
		Status status = outlets.isEmpty() ? Status.NO_OUTLET : stored >= capacity ? Status.FULL : Status.OK;
		for (PiezoPlateBlockEntity plate : members) {
			plate.joinGroup(group, status, members.size(), outlets.size(), stored, capacity);
		}
		return group;
	}

	private static boolean belongsToOtherFreshGroup(PiezoPlateBlockEntity plate, long now) {
		PiezoPlateGroup other = plate.group();
		return other != null && other.isFresh(now) && other.members.size() > 0;
	}

	/**
	 * Which of the plate's sides lead to a receiver: bits 0-3 for the horizontal faces in
	 * {@link Direction#get2DDataValue()} order, {@link #OUTLET_FLOOR} for a receiver two blocks down. Another
	 * piezo plate is never a receiver — the group already shares its store.
	 */
	static int outletMask(Level level, BlockPos pos) {
		EnergyLookup lookup = EnergyLookup.get();
		int mask = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(dir);
			if (!level.isLoaded(next) || level.getBlockEntity(next) instanceof PiezoPlateBlockEntity) {
				continue;
			}
			EnergyPort port = lookup.find(level, next, dir.getOpposite());
			if (port != null && port.supportsInsertion()) {
				mask |= 1 << dir.get2DDataValue();
			}
		}
		BlockPos receiver = floorReceiverPos(level, pos);
		if (receiver != null) {
			EnergyPort port = lookup.find(level, receiver, Direction.UP);
			if (port != null && port.supportsInsertion()) {
				mask |= OUTLET_FLOOR;
			}
		}
		return mask;
	}

	/**
	 * The block two below the plate, when the floor between is a plain block that lends the plate's port
	 * ({@link FloorPortLender}); {@code null} when the floor is a block with its own block entity.
	 */
	static BlockPos floorReceiverPos(Level level, BlockPos pos) {
		BlockPos floor = pos.below();
		BlockPos receiver = floor.below();
		if (!level.isLoaded(receiver) || level.getBlockState(floor).hasBlockEntity()) {
			return null;
		}
		return receiver;
	}

	/** True when {@code pos} holds a cable — the network pulls from an outlet there, so it is not pushed. */
	static boolean isCable(Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof CableNode;
	}

	/**
	 * Move up to {@code room} EU from the other members into {@code outlet}, out of the group's per-tick budget.
	 * Plain internal moves on the server thread: a drain from one plate and the same amount received by
	 * another is energy changing hands inside one store, not a transfer anything else could observe.
	 */
	long gatherInto(PiezoPlateBlockEntity outlet, long room, long now) {
		if (budgetTick != now) {
			budgetTick = now;
			budgetLeft = Math.max(0, GeneratorConfig.piezoGroupOutputPerTick);
		}
		long want = Math.min(room, budgetLeft);
		long got = 0;
		for (PiezoPlateBlockEntity member : members) {
			if (got >= want) {
				break;
			}
			if (member == outlet || member.isRemoved()) {
				continue;
			}
			got += member.getEnergyStorage().drainInternal(want - got);
		}
		long kept = outlet.getEnergyStorage().receiveInternal(got);
		budgetLeft -= kept;
		return kept;
	}

	/** Wake the outlets so a fresh press is handed on now rather than at their next idle check. */
	void wakeOutlets() {
		for (PiezoPlateBlockEntity outlet : outlets) {
			if (!outlet.isRemoved()) {
				outlet.wake();
			}
		}
	}

	boolean isOutlet(PiezoPlateBlockEntity plate) {
		return outlets.contains(plate);
	}

	public int size() {
		return members.size();
	}
}
