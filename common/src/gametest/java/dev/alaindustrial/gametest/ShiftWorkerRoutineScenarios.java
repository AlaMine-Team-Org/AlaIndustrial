package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.gametest.TownDroneScenarios.ChunkHold;
import dev.alaindustrial.gametest.TownDroneScenarios.Rig;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.drone.TownRoadNetwork;
import dev.alaindustrial.settlement.shift.RoutineActivity;
import dev.alaindustrial.settlement.shift.RoutineState;
import dev.alaindustrial.settlement.shift.ShiftRoutine;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.settlement.shift.TownChronicle;
import dev.alaindustrial.settlement.shift.ShiftMode;
import dev.alaindustrial.settlement.works.BuildingPlan;
import dev.alaindustrial.settlement.works.TownHousing;
import dev.alaindustrial.settlement.works.TownWorkplaces;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * L2 of the shift worker's day (MOD-889; epic MOD-824, decisions R32, R75, R156), on both gametest lanes: a resident
 * walks home at night, lies on its charging bed and gets up in the morning; the day's gathering stands its workers in
 * the town hall face to face; a place the worker cannot reach is tried rarely and given up with one line of the hall's
 * log, the worker never moved; and what twenty workers cost per tick.
 *
 * <p><b>The clock.</b> The world's clock is shared by every test running at once, so these cases pin the clock of
 * their own town ({@link ShiftRoutine#pinClockForTest}) and let it go when they end, passed or failed. The rig's hall
 * stands far from the test's structure, in chunks kept loaded but not simulated, so a worker there gets its ticks from
 * the case, one per game tick.
 */
public final class ShiftWorkerRoutineScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ShiftWorkerRoutineScenarios::sleepsOnItsBedAtNightAndGetsUpInTheMorning,
						"shift_worker_routine_sleeps_on_its_bed").ticks(2400),
				RosterEntry.of(ShiftWorkerRoutineScenarios::gatheringStandsInTheHallFaceToFace,
						"shift_worker_routine_gathers_in_the_hall").ticks(1600),
				RosterEntry.of(ShiftWorkerRoutineScenarios::workerWithAWorkplaceWorksThere,
						"shift_worker_routine_works_at_its_workplace").ticks(1200),
				RosterEntry.of(ShiftWorkerRoutineScenarios::unreachablePlaceIsTriedRarelyAndGivenUpOnce,
						"shift_worker_routine_unreachable_place_backs_off").ticks(1400),
				RosterEntry.of(ShiftWorkerRoutineScenarios::twentyWorkersTickCost, "ala_town_twenty_workers_tick_cost")
						.ticks(400).structure(Industrialization.id("demo_stand_area")));

		private Roster() {}
	}

	private ShiftWorkerRoutineScenarios() {}

	/** Midnight: every worker sleeps, whatever its offsets. */
	static final long NIGHT = 18_000;
	/** Mid-morning: every worker is up (getting up, or at work), whatever its offsets. */
	static final long MORNING = 3_000;
	/** Mid-gathering: every worker is at the gathering, whatever its offsets. */
	static final long GATHERING = 10_100;

	/** Pins town {@code town}'s clock for this test, let go when the test ends however it ends. */
	static void pin(GameTestHelper helper, int town, long clock) {
		ShiftRoutine.pinClockForTest(town, clock);
		ConfigOverrides.testInfo(helper).addListener(new GameTestListener() {
			@Override
			public void testStructureLoaded(GameTestInfo info) {}

			@Override
			public void testPassed(GameTestInfo info, GameTestRunner runner) {
				ShiftRoutine.unpinClock(town);
			}

			@Override
			public void testFailed(GameTestInfo info, GameTestRunner runner) {
				ShiftRoutine.unpinClock(town);
			}

			@Override
			public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) {
				ShiftRoutine.unpinClock(town);
			}
		});
	}

	/**
	 * One game tick of a worker the level does not tick (its chunk is loaded, not simulated): the level's own count of
	 * the entity's ticks first, as {@code ServerLevel} keeps it, then the entity's tick.
	 */
	static void live(ShiftWorker worker) {
		worker.tickCount++;
		worker.tick();
	}

	/** A worker of {@code town} made standing at {@code at}. */
	private static ShiftWorker spawn(GameTestHelper helper, BlockPos at, int town) {
		return ShiftWorkers.spawn(helper.getLevel(), Vec3.atBottomCenterOf(at), 0.0F, town)
				.orElseThrow(() -> helper.assertionException("ShiftWorkers.spawn made no worker at " + at));
	}

	/** A cell a worker can stand in near {@code near}: air with air above and a solid block below; the nearest one. */
	private static BlockPos standAt(GameTestHelper helper, ServerLevel level, BlockPos near) {
		for (int r = 0; r <= 3; r++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						BlockPos cell = near.offset(dx, dy, dz);
						if (level.getBlockState(cell).isAir() && level.getBlockState(cell.above()).isAir()
								&& level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
							return cell;
						}
					}
				}
			}
		}
		throw helper.assertionException("no cell to stand in near " + near);
	}

	/**
	 * A homeless worker who moves into the restored, wired house stands on the road outside it at midnight: it walks
	 * in (through the house's door), lies down on its charging bed and stays lying; at mid-morning it gets up and steps
	 * off the bed.
	 *
	 * @implements MOD-889-NIGHT — at night a resident goes home and lies on its charging bed; in the morning it
	 *     gets up
	 */
	public static void sleepsOnItsBedAtNightAndGetsUpInTheMorning(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		ChunkHold hold = TownHouseScenarios.addIntactHouse(helper, rig, true);
		int town = rig.town();
		pin(helper, town, NIGHT);
		// The test's road ground stands a block over the house's floor, burying its doorstep; a town on real ground
		// has the doorstep free. Dig it out: the door of plains house 1 is at (2, 1, 5) of its template, facing south.
		BlockPos doorstep = rig.origin().offset(TownHouseScenarios.HOUSE_OFFSET).offset(2, 1, 6);
		level.setBlock(doorstep, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(doorstep.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(doorstep.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		BlockPos bed = TownHousing.bed(level, town, 1)
				.orElseThrow(() -> helper.assertionException("control: the house has no charging bed"));
		List<BlockPos> course = TownRoadNetwork.course(level, town, 1)
				.orElseThrow(() -> helper.assertionException("control: no road to the house"));
		BlockPos start = null;
		for (int i = course.size() - 1; i >= 0 && start == null; i--) {
			if (course.get(i).distSqr(bed) > 7 * 7) {
				start = standAt(helper, level, course.get(i).above());
			}
		}
		helper.assertTrue(start != null, "control: no road cell 7 blocks from the bed: " + course);
		ShiftWorker worker = spawn(helper, start, town);
		TownHousing.settle(level, town);
		ShiftWorkerRecord record = ShiftWorkers.book(level.getServer()).byEntity(worker.getUUID()).orElseThrow();
		helper.assertTrue(record.home() == 1, "control: the worker must move into the house: " + record.home());
		BlockPos from = start;
		int[] ticks = {0};
		helper.startSequence().thenWaitUntil(() -> {
			live(worker);
			ticks[0]++;
			if (worker.lyingOn().isEmpty()) {
				throw helper.assertionException("the resident is not on its bed yet after " + ticks[0] + " ticks, at "
						+ worker.blockPosition() + " (activity " + worker.routine().activity() + ", bed " + bed + ")");
			}
		}).thenExecute(() -> {
			Industrialization.LOGGER.info("[ala-towns] routine: the resident walked from {} to its bed {} in {} ticks",
					from, bed, ticks[0]);
			helper.assertTrue(worker.lyingOn().get().equals(bed) && worker.getPose() == Pose.SLEEPING,
					"the resident must lie on its own bed: " + worker.lyingOn() + ", " + worker.getPose());
			helper.assertTrue(worker.routine().activity() == RoutineActivity.SLEEP, "the night's activity is sleep");
		}).thenExecuteFor(100, () -> {
			live(worker);
			helper.assertTrue(worker.lyingOn().isPresent(),
					"a resident with a full battery stays on its bed all night");
		}).thenExecute(() -> ShiftRoutine.pinClockForTest(town, MORNING)).thenWaitUntil(() -> {
			live(worker);
			if (worker.lyingOn().isPresent() || worker.goingToBed()) {
				throw helper.assertionException("the resident is still in bed in the morning");
			}
		}).thenExecute(() -> {
			helper.assertTrue(worker.getPose() == Pose.STANDING, "up in the morning: " + worker.getPose());
			helper.assertTrue(worker.routine().activity() != RoutineActivity.SLEEP,
					"the morning's activity: " + worker.routine().activity());
			ShiftWorkers.book(level.getServer()).remove(worker.getUUID());
			worker.discard();
			hold.release();
			rig.release();
		}).thenSucceed();
	}

	/**
	 * Three workers standing in the corners of the restored town hall at the gathering hour walk to the core and stand
	 * there, within the gathering's radius, each looking at another of them.
	 *
	 * @implements MOD-889-GATHER — the gathering puts the workers in the restored hall, facing each other
	 */
	public static void gatheringStandsInTheHallFaceToFace(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		int town = rig.town();
		helper.assertTrue(TownHall.restored(level, town), "control: the rig's hall must count as restored");
		pin(helper, town, GATHERING);
		BlockPos core = rig.core().getBlockPos();
		List<ShiftWorker> workers = new ArrayList<>();
		for (BlockPos corner : List.of(core.offset(-4, 0, -3), core.offset(4, 0, -3), core.offset(0, 0, 4))) {
			workers.add(spawn(helper, standAt(helper, level, corner), town));
		}
		double reach = 3.0 + 0.5;
		int[] turning = {0};
		helper.startSequence().thenWaitUntil(() -> {
			workers.forEach(ShiftWorkerRoutineScenarios::live);
			for (ShiftWorker worker : workers) {
				double distance = Math.sqrt(worker.blockPosition().distSqr(core));
				if (distance > reach || !worker.getNavigation().isDone()) {
					throw helper.assertionException("a worker is " + String.format(Locale.ROOT, "%.1f", distance)
							+ " blocks from the core (activity " + worker.routine().activity() + ")");
				}
			}
		}).thenWaitUntil(() -> {
			// The gathering goal starts by chance (one in ten of the goal selector's evaluations, every second tick),
			// so the moment each worker turns to a peer varies run to run (MOD-909): wait for the state, not a tick.
			workers.forEach(ShiftWorkerRoutineScenarios::live);
			turning[0]++;
			for (ShiftWorker worker : workers) {
				Vec3 wanted = new Vec3(worker.getLookControl().getWantedX(), 0, worker.getLookControl().getWantedZ());
				boolean facesPeer = worker.getNavigation().isDone() && workers.stream().filter(other -> other != worker)
						.anyMatch(other -> new Vec3(other.getX(), 0, other.getZ()).distanceTo(wanted) < 1.0);
				if (!facesPeer) {
					throw helper.assertionException("a worker of the gathering does not face another: looks at "
							+ wanted + " (activity " + worker.routine().activity() + ")");
				}
			}
		}).thenExecute(() -> {
			Industrialization.LOGGER.info("[ala-towns] routine: the gathering faced each other {} ticks after arriving",
					turning[0]);
			for (ShiftWorker worker : workers) {
				helper.assertTrue(worker.routine().activity() == RoutineActivity.GATHER,
						"the activity is the gathering: " + worker.routine().activity());
				helper.assertTrue(TownHall.contains(level, town, worker.blockPosition()),
						"a worker of the gathering stands outside the hall: " + worker.blockPosition());
			}
			for (ShiftWorker worker : workers) {
				ShiftWorkers.book(level.getServer()).remove(worker.getUUID());
				worker.discard();
			}
			rig.release();
		}).thenSucceed();
	}

	/**
	 * The town's only worker, made at the far end of the restored warehouse, becomes its storekeeper (MOD-888); at
	 * mid-morning its routine takes it to the cell just inside the warehouse's door — its workplace — in the
	 * working mode a storekeeper's trips ask for.
	 *
	 * @implements MOD-889-WORKPLACE — a worker with a workplace goes there in working hours
	 */
	public static void workerWithAWorkplaceWorksThere(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		ChunkHold hold = TownWarehouseScenarios.addWarehouse(helper, rig, true);
		int town = rig.town();
		pin(helper, town, MORNING);
		BuildingPlan plan = TownWorksScanner.plan(level, town, 1)
				.orElseThrow(() -> helper.assertionException("control: the warehouse has no plan"));
		BoundingBox box = plan.box().orElseThrow();
		BlockPos far = standAt(helper, level, new BlockPos(box.maxX() - 2, plan.groundLevel(), box.getCenter().getZ()));
		ShiftWorker worker = spawn(helper, far, town);
		TownWorkplaces.assign(level, town);
		ShiftWorkerRecord record = ShiftWorkers.book(level.getServer()).byEntity(worker.getUUID()).orElseThrow();
		helper.assertTrue(record.employed() && record.workplace() == 1,
				"control: the worker must take the warehouse: " + record);
		BlockPos anchor = ShiftRoutine.workplaceAnchor(level, record)
				.orElseThrow(() -> helper.assertionException("an employed worker must have a workplace anchor"));
		helper.assertTrue(plan.contains(anchor), "the workplace anchor must be inside the warehouse: " + anchor);
		helper.assertTrue(far.distSqr(anchor) > 5 * 5, "control: the worker must start away from the door: " + far);
		helper.startSequence().thenWaitUntil(() -> {
			live(worker);
			double distance = Math.sqrt(worker.blockPosition().distSqr(anchor));
			if (distance > 3.5) {
				throw helper.assertionException("the storekeeper is " + String.format(Locale.ROOT, "%.1f", distance)
						+ " blocks from its workplace (activity " + worker.routine().activity() + ")");
			}
		}).thenExecute(() -> {
			helper.assertTrue(worker.routine().activity() == RoutineActivity.WORK, "the morning's activity is work");
			helper.assertTrue(worker.mode() == ShiftMode.WORK, "a worker at work shows the working mode");
			helper.assertTrue(worker.getHomePosition().equals(anchor), "its place is its workplace");
			ShiftWorkers.book(level.getServer()).remove(worker.getUUID());
			worker.discard();
			hold.release();
			rig.release();
		}).thenSucceed();
	}

	/**
	 * A worker shut in a glass cage in the hall at the gathering hour cannot reach the core: it plans a path a few
	 * times only, waiting longer each time, then gives the place up and the hall's log gets one line «did not reach
	 * its goal»
	 * about it — and nothing moves it out of the cage.
	 *
	 * @implements MOD-889-UNREACHABLE — an unreachable place backs off, is given up once with one log line, no
	 *     teleport
	 */
	public static void unreachablePlaceIsTriedRarelyAndGivenUpOnce(GameTestHelper helper) {
		Rig rig = TownDroneScenarios.rig(helper, 0);
		ServerLevel level = rig.level();
		int town = rig.town();
		pin(helper, town, GATHERING);
		BlockPos core = rig.core().getBlockPos();
		BlockPos cell = standAt(helper, level, core.offset(-5, 0, 4));
		helper.assertTrue(Math.sqrt(cell.distSqr(core)) > 4.5, "control: the cage must stand outside the gathering: "
				+ cell);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					if (dx != 0 || dz != 0 || dy == 2) {
						level.setBlock(cell.offset(dx, dy, dz), Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		ShiftWorker worker = spawn(helper, cell, town);
		ShiftWorkerBook book = ShiftWorkers.book(level.getServer());
		int ticks = RoutineState.STUCK_TICKS + 400;
		helper.startSequence().thenExecuteFor(ticks, () -> {
			live(worker);
			helper.assertTrue(Math.abs(worker.getX() - (cell.getX() + 0.5)) < 0.75
					&& Math.abs(worker.getZ() - (cell.getZ() + 0.5)) < 0.75,
					"the caged worker was moved out of its cage to " + worker.position());
		}).thenExecute(() -> {
			int planned = worker.routine().pathsPlanned();
			Industrialization.LOGGER.info("[ala-towns] routine: the caged worker planned {} paths in {} ticks", planned,
					ticks);
			helper.assertTrue(planned >= 2 && planned <= 8, "the unreachable hall must be tried a few times, waiting "
					+ "longer each time; it was tried " + planned + " times in " + ticks + " ticks");
			List<ShiftWorkerBook.LogLine> stuck = book.log(town).stream()
					.filter(line -> line.note().map(note -> TownChronicle.STUCK.equals(note.key())).orElse(false))
					.toList();
			helper.assertTrue(stuck.size() == 1 && TownChronicle.repeats(stuck.getFirst()) == 1
					&& stuck.getFirst().note().get().args().equals(List.of(ShiftRoutine.Target.HALL.key())),
					"the hall's log must get one line «did not reach its goal: the town hall»: " + stuck);
			book.remove(worker.getUUID());
			worker.discard();
			rig.release();
		}).thenSucceed();
	}

	/** Ticks driven before the clock starts, so the JIT has compiled the path. */
	private static final int WARMUP_TICKS = 120;
	/** Measured ticks: enough to average the scheduler out, short enough for the lane. */
	private static final int MEASURED_TICKS = 200;
	/** Ceiling for one tick of twenty workers: a structural tripwire (twice the ten-worker one), not the budget. */
	private static final long TICK_CEILING_NS = 10_000_000L;
	/** The middle of the demo stand's envelope. */
	private static final BlockPos CENTRE = new BlockPos(45, 1, 26);

	/**
	 * Twenty workers of no town on one floor, each ticked by hand in a timed loop across game ticks of their own (their
	 * regular tick runs too): the median cost of one tick of all twenty, routine included, stays under
	 * {@link #TICK_CEILING_NS}. The figure is printed for the task's record.
	 *
	 * @implements MOD-889-PERF — a town of twenty workers with its routine stays within the tick budget
	 */
	public static void twentyWorkersTickCost(GameTestHelper helper) {
		for (int dx = -16; dx <= 16; dx++) {
			for (int dz = -16; dz <= 16; dz++) {
				helper.setBlock(CENTRE.offset(dx, -1, dz), Blocks.STONE);
			}
		}
		List<ShiftWorker> workers = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			workers.add(ShiftWorkers.spawn(helper.getLevel(), helper.absolutePos(CENTRE.offset(i % 5 * 2 - 4, 0,
					i / 5 * 2 - 4)), 0)
					.orElseThrow(() -> helper.assertionException("ShiftWorkers.spawn made no worker")));
		}
		long[] noise = new long[MEASURED_TICKS];
		long[] cost = new long[MEASURED_TICKS];
		int[] tick = {0};
		helper.startSequence().thenIdle(2).thenExecuteFor(WARMUP_TICKS + MEASURED_TICKS, () -> {
			int i = tick[0]++;
			long t0 = System.nanoTime();
			long t1 = System.nanoTime();
			long t2 = System.nanoTime();
			workers.forEach(ShiftWorker::tick);
			long spent = System.nanoTime() - t2;
			if (i >= WARMUP_TICKS) {
				noise[i - WARMUP_TICKS] = t1 - t0;
				cost[i - WARMUP_TICKS] = spent;
			}
		}).thenExecute(() -> {
			long medianNoise = median(noise);
			long medianCost = Math.max(0L, median(cost) - medianNoise);
			long p95 = percentile(cost, 0.95);
			int planned = workers.stream().mapToInt(worker -> worker.routine().pathsPlanned()).sum();
			Industrialization.LOGGER.info("[ala-towns] twenty workers with the routine: median {} us, p95 {} us per "
					+ "tick (noise {} us), {} paths planned, activity {}, ceiling {} us", medianCost / 1000, p95 / 1000,
					medianNoise / 1000, planned, workers.getFirst().routine().activity(), TICK_CEILING_NS / 1000);
			for (ShiftWorker worker : workers) {
				if (worker.isRemoved()) {
					throw helper.assertionException("a worker left the world during the measurement");
				}
				worker.discard();
			}
			helper.assertTrue(medianCost < TICK_CEILING_NS, "twenty workers cost " + medianCost / 1000
					+ " us per tick, the ceiling is " + TICK_CEILING_NS / 1000 + " us");
		}).thenSucceed();
	}

	private static long median(long[] samples) {
		return percentile(samples, 0.5);
	}

	private static long percentile(long[] samples, double share) {
		long[] sorted = samples.clone();
		Arrays.sort(sorted);
		return sorted[Math.min(sorted.length - 1, (int) (sorted.length * share))];
	}
}
