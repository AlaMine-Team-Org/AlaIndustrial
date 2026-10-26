package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ReceptionCapsuleBlock;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * A shift worker's day (MOD-889; epic MOD-824, decisions R14, R32, R75, R150, R156): what it does at this hour
 * ({@link DayRoutine}) and where that takes it. Runs on the worker's think ticks, after the shift planner let it act.
 *
 * <p><b>Where each activity goes</b> (the town's core is the worker's town anchor; its wander radius is W):
 * <ul>
 *   <li>wake — up from the bed ({@link HouseCharging}), out of the house into the town: the core, W/2;</li>
 *   <li>work — its workplace ({@link #workplaceAnchor}), 3 blocks; with no work it strolls the town (R150): the
 *       core, W;</li>
 *   <li>break — where the break found it, 4 blocks: it steps aside and stands; a break that finds it still in its
 *       reception capsule takes it to the core, W/2 ({@link #breakSpot});</li>
 *   <li>gathering — the town hall round the core, 3 blocks when the hall is restored, 4 by the core otherwise; the
 *       workers face each other ({@code ShiftWorkerGatherGoal});</li>
 *   <li>rest — round its own house, W/2; a homeless worker round the core;</li>
 *   <li>night — on its charging bed, charging (R7, R101); a homeless worker by the hall, 3 blocks, charged by the
 *       core as before.</li>
 * </ul>
 * In rain a worker out of doors goes under a roof — the restored hall, else its own house; work at a workplace goes
 * on. By day a worker goes to its bed only with its battery under a tenth (R156, {@link HouseCharging#LOW_SHARE}).
 *
 * <p><b>Never stuck, never moved</b> (R32): the walk plans its paths through the town's dispatcher and waits out
 * unreachable targets ({@link RoutineState}); a worker that gets no closer to its place for
 * {@link RoutineState#STUCK_TICKS} gives the place up until its next activity, and the town hall's log gets one line
 * «did not reach its goal» ({@link TownChronicle#STUCK}). It is never teleported.
 */
public final class ShiftRoutine {

	/** How far round the town hall's core the gathering stands when the hall is restored. */
	static final int HALL_RADIUS = 3;
	/** How far round the core the gathering stands when the town has no restored hall yet. */
	static final int CORE_RADIUS = 4;
	/** How far round a workplace a worker keeps while it works. */
	static final int WORKPLACE_RADIUS = 3;
	/** How far from where its break found it a worker steps. */
	static final int BREAK_RADIUS = 4;
	/** How far round its bed a worker sheltering from rain in its house keeps. */
	static final int HOUSE_RADIUS = 2;
	/** How far a worker strays from where it gave up a place. */
	static final int GIVEN_UP_RADIUS = 3;

	private static final String TARGET = "settlement.alaindustrial.routine.target.";
	/** Where a worker did not get to, as the hall's log names it: its workplace, the hall, its house, the town. */
	public enum Target {
		WORKPLACE, HALL, HOUSE, TOWN;

		/** The translation key of the place's name in the hall's log line. */
		public String key() {
			return TARGET + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Every key of the routine's own words. */
	public static final List<String> KEYS = List.of(Target.WORKPLACE.key(), Target.HALL.key(), Target.HOUSE.key(),
			Target.TOWN.key());

	/** Towns whose clock a game test holds (a test must not move the world's shared clock); empty outside the tests. */
	private static final Map<Integer, Long> PINNED_CLOCK = new ConcurrentHashMap<>();

	private ShiftRoutine() {
	}

	/** A game test's switch: the workers of town {@code town} read {@code clock} as the time of day. */
	public static void pinClockForTest(int town, long clock) {
		PINNED_CLOCK.put(town, clock);
	}

	/** Lets town {@code town}'s clock run with the world's again. */
	public static void unpinClock(int town) {
		PINNED_CLOCK.remove(town);
	}

	/** The time of day the workers of town {@code town} live by: the world's clock, or a test's pinned one. */
	public static long clock(ServerLevel level, int town) {
		Long pinned = PINNED_CLOCK.get(town);
		return pinned != null ? pinned : level.getOverworldClockTime();
	}

	/**
	 * Where the worker of {@code record} works (MOD-888 gives the record its trade and building): the cell just inside
	 * its workplace's door — the building's plan names the door; a plan with no door gives the middle of its floor.
	 * Empty for a worker with no work, who strolls the town by its routine (R150), and while the building's plan is
	 * not at hand.
	 */
	public static Optional<BlockPos> workplaceAnchor(ServerLevel level, ShiftWorkerRecord record) {
		if (!record.employed()) {
			return Optional.empty();
		}
		return TownWorksScanner.plan(level, record.settlement(), record.workplace()).flatMap(ShiftRoutine::inside);
	}

	/**
	 * The cell inside a building's door: of the two cells the lower door cell opens between, the one inside the
	 * building's box (a template's door may face either way); with no door, the middle of its floor.
	 */
	private static Optional<BlockPos> inside(BuildingPlan plan) {
		for (int i = 0; i < plan.size(); i++) {
			BlockState door = plan.expected(i);
			if (door.getBlock() instanceof DoorBlock && door.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				BlockPos front = plan.pos(i).relative(door.getValue(DoorBlock.FACING));
				return Optional.of(plan.contains(front) ? front
						: plan.pos(i).relative(door.getValue(DoorBlock.FACING).getOpposite()));
			}
		}
		return plan.box().map(box -> new BlockPos(box.getCenter().getX(), plan.groundLevel(), box.getCenter().getZ()));
	}

	/** One think of {@code worker} at {@code clock}: its activity, its bed, the place it keeps to. */
	public static void think(ServerLevel level, ShiftWorker worker, Optional<Settlement> town, long clock) {
		RoutineState state = worker.routine();
		long now = level.getGameTime();
		Optional<ShiftWorkerRecord> record = ShiftWorkers.book(level.getServer()).byEntity(worker.getUUID())
				.filter(found -> found.status() == ShiftWorkerRecord.Status.ALIVE);
		long day = DayRoutine.day(clock);
		if (!state.rankCountedOn(day)) {
			state.setRank(record.map(found -> rank(level, found)).orElse(0), day);
		}
		RoutineActivity wanted = DayRoutine.activityAt(clock, state.rank(), worker.settlement());
		boolean busy = worker.getNavigation().isInProgress() || worker.isFleeing();
		if (state.shouldSwitch(wanted, now, busy)) {
			state.adopt(wanted, now);
			if (wanted == RoutineActivity.BREAK) {
				state.setSpot(breakSpot(level, worker.blockPosition()));
			}
		}
		RoutineActivity activity = state.activity();
		worker.setMode(activity.mode());

		Optional<BlockPos> bed = HouseCharging.bed(level, record);
		long bedKey = bed.map(BlockPos::asLong).orElse(RoutineState.NONE);
		boolean night = activity == RoutineActivity.SLEEP;
		HouseCharging.think(level, worker, record, bed, night, !state.gaveUp(bedKey));
		if (worker.isFleeing()) {
			state.paused(now);
			return;
		}
		if (worker.goingToBed()) {
			if (worker.lyingOn().isPresent()) {
				state.arrived();
			} else if (bed.isPresent()) {
				follow(level, worker, record, bed.get(), Target.HOUSE, now);
			}
			return;
		}
		Place place = place(level, worker, record, town, activity, bed);
		if (state.gaveUp(place.anchor().asLong())) {
			BlockPos stay = state.spot() != RoutineState.NONE ? BlockPos.of(state.spot()) : worker.blockPosition();
			worker.setHomeTo(stay, GIVEN_UP_RADIUS);
			return;
		}
		int radius = Math.max(1, Math.min(place.radius(), worker.wanderRadius()));
		worker.setHomeTo(place.anchor(), radius);
		if (worker.isWithinHome()) {
			state.arrived();
		} else if (ledElsewhere(worker, place.anchor(), radius)) {
			state.paused(now); // another goal walks the worker (a storekeeper's trip, MOD-888): not stuck in its day
		} else {
			follow(level, worker, record, place.anchor(), place.target(), now);
		}
	}

	/** One step of the worker's walk to {@code anchor}: progress, or the place given up with a hall's log line. */
	private static void follow(ServerLevel level, ShiftWorker worker, Optional<ShiftWorkerRecord> record,
			BlockPos anchor, Target target, long now) {
		RoutineState state = worker.routine();
		double distance = worker.position().distanceTo(Vec3.atBottomCenterOf(anchor));
		if (state.track(anchor.asLong(), distance, now) != RoutineState.Progress.STUCK) {
			return;
		}
		BlockPos here = worker.blockPosition();
		state.setSpot(here.asLong());
		if (worker.goingToBed()) {
			worker.setGoingToBed(false);
		}
		worker.getNavigation().stop();
		worker.setHomeTo(here, GIVEN_UP_RADIUS);
		record.ifPresent(found -> TownChronicle.record(level.getServer(), found.settlement(), found,
				TownChronicle.STUCK, level.getGameTime(), target.key()));
		Industrialization.LOGGER.info("[ala-towns] shift worker {} did not reach its {} at {} from {}",
				worker.number(), target.name().toLowerCase(Locale.ROOT), anchor, here);
	}

	/** Whether the worker walks now toward somewhere other than its place: another goal leads it. */
	private static boolean ledElsewhere(ShiftWorker worker, BlockPos anchor, int radius) {
		if (!worker.getNavigation().isInProgress()) {
			return false;
		}
		BlockPos target = worker.getNavigation().getTargetPos();
		return target != null && target.distSqr(anchor) > (double) (radius + 2) * (radius + 2);
	}

	/** Where an activity takes a worker: the anchor it walks to and keeps round, and what the log calls it. */
	record Place(BlockPos anchor, int radius, Target target) {
	}

	private static Place place(ServerLevel level, ShiftWorker worker, Optional<ShiftWorkerRecord> record,
			Optional<Settlement> town, RoutineActivity activity, Optional<BlockPos> bed) {
		BlockPos core = worker.townAnchor();
		int wander = worker.wanderRadius();
		boolean hall = town.isPresent() && TownHall.restored(level, town.get().id());
		Optional<BlockPos> workplace = record.flatMap(found -> workplaceAnchor(level, found));
		if (activity.outdoors() && !(activity == RoutineActivity.WORK && workplace.isPresent())
				&& rainingOn(level, core)) {
			if (hall) {
				return new Place(core, HALL_RADIUS, Target.HALL);
			}
			if (bed.isPresent()) {
				return new Place(bed.get(), HOUSE_RADIUS, Target.HOUSE);
			}
		}
		return switch (activity) {
			case WAKE -> new Place(core, wander / 2, Target.TOWN);
			case WORK -> workplace.map(at -> new Place(at, WORKPLACE_RADIUS, Target.WORKPLACE))
					.orElseGet(() -> new Place(core, wander, Target.TOWN));
			case BREAK -> breakPlace(worker, core, wander);
			case GATHER -> new Place(core, hall ? HALL_RADIUS : CORE_RADIUS, Target.HALL);
			case REST -> bed.map(at -> new Place(at, wander / 2, Target.HOUSE))
					.orElseGet(() -> new Place(core, wander / 2, Target.TOWN));
			case SLEEP -> new Place(core, HALL_RADIUS, Target.HALL);
		};
	}

	/** Round where the break found the worker, if that spot keeps the step inside its wander radius; else the core. */
	private static Place breakPlace(ShiftWorker worker, BlockPos core, int wander) {
		long spot = worker.routine().spot();
		if (spot != RoutineState.NONE) {
			BlockPos at = BlockPos.of(spot);
			double reach = Math.max(0, wander - BREAK_RADIUS);
			if (at.distSqr(core) <= reach * reach) {
				return new Place(at, BREAK_RADIUS, Target.TOWN);
			}
		}
		return new Place(core, wander / 2, Target.TOWN);
	}

	/**
	 * Where a break that finds the worker standing at {@code at} keeps it: that cell, or {@link RoutineState#NONE} —
	 * the core then — when the worker stands inside a block taller than one cell that the game counts as solid, as a
	 * worker just released stands on the floor of its reception capsule (MOD-893). The game's walk and its random
	 * stands both aim above a solid cell ({@code GroundPathNavigation}, {@code LandRandomPos}), here onto the
	 * capsule's roof three blocks up: a place round such a cell is one the worker can only leave, never come back to.
	 * A road, a slab or a carpet is solid one cell high only, and a walk to it ends on it: the break keeps it.
	 */
	public static long breakSpot(ServerLevel level, BlockPos at) {
		return insideTallSolid(level, at) ? RoutineState.NONE : at.asLong();
	}

	/** Whether {@code at} and the cell above it both hold blocks the game counts as solid. */
	private static boolean insideTallSolid(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).isSolid() && level.getBlockState(at.above()).isSolid();
	}

	/**
	 * Whether a short walk to {@code target} would end on the roof of a reception capsule (MOD-893). A worker standing
	 * in its capsule gets such a target from the game's random stands: the capsule's own cell counts as solid, so the
	 * stand is lifted out of it onto the roof. No worker can climb there; a path toward it leads up whatever stands
	 * next to the capsule (a gallery in the town hall) and leaves the worker there, out of its place.
	 */
	public static boolean onCapsuleRoof(ServerLevel level, Vec3 target) {
		BlockPos under = BlockPos.containing(target).below();
		return level.getBlockState(under).getBlock() instanceof ReceptionCapsuleBlock;
	}

	/** Whether rain (or snow) falls on the town round {@code at} now. */
	private static boolean rainingOn(ServerLevel level, BlockPos at) {
		return level.isRaining() && level.getBiome(at).value().hasPrecipitation();
	}

	/**
	 * The rank of {@code record}'s worker in its town: how many of the town's living workers have a smaller number.
	 * Counted once a day per worker; a worker arriving later in the day may share a slot with one other until the
	 * next day (the order of the switches stays spread all the same).
	 */
	static int rank(ServerLevel level, ShiftWorkerRecord record) {
		int rank = 0;
		for (ShiftWorkerRecord other : ShiftWorkers.book(level.getServer()).all()) {
			if (other.settlement() == record.settlement() && other.status() == ShiftWorkerRecord.Status.ALIVE
					&& other.number().compareTo(record.number()) < 0) {
				rank++;
			}
		}
		return rank;
	}
}
