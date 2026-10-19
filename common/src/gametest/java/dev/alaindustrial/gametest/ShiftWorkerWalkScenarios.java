package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * How shift workers walk (MOD-832), on both gametest lanes: a worker never leaves its wander radius and never
 * stands on one block for long while it has somewhere to go — staying stuck is a defect the tests catch, not a
 * case a safety net hides (R32) — and what ten of them cost per tick.
 *
 * <p><b>Room.</b> A worker walks up to {@code shiftWorkerWanderRadius} (12) blocks from its anchor, far past the
 * 8 × 8 rig, and an entity outside the force-loaded chunks of the test's structure does not tick at all. So these
 * scenarios borrow the demo stand's 90 × 14 × 52 envelope ({@code demo_stand_area}, an air volume) and lay a stone
 * floor of their own in the middle of it.
 */
public final class ShiftWorkerWalkScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ShiftWorkerWalkScenarios::staysWithinItsRadiusAndNeverSticks,
						"shift_worker_stays_within_its_radius").ticks(640)
						.structure(Industrialization.id("demo_stand_area")),
				RosterEntry.of(ShiftWorkerWalkScenarios::tenWorkersTickCost, "ala_town_ten_workers_tick_cost")
						.ticks(60).structure(Industrialization.id("demo_stand_area")));

		private Roster() {}
	}

	private ShiftWorkerWalkScenarios() {}

	/** How long the walk is watched: half a minute, several walks in any mode of the day. */
	static final int WALK_TICKS = 600;
	/** Ticks a worker with a path may stand on one block before it counts as stuck (the epic's K). */
	static final int STUCK_TICKS = 200;
	/** How far past the radius a walk may overshoot: the navigation's last step and the body's width. */
	static final double RADIUS_SLACK = 1.5;
	/** The middle of the envelope: the anchor of every worker here. */
	private static final BlockPos CENTRE = new BlockPos(45, 1, 26);
	/** Half the floor's side: the radius plus a margin, so the floor's edge is never what stops a walk. */
	private static final int FLOOR_HALF = 16;

	private static void floor(GameTestHelper helper) {
		for (int dx = -FLOOR_HALF; dx <= FLOOR_HALF; dx++) {
			for (int dz = -FLOOR_HALF; dz <= FLOOR_HALF; dz++) {
				helper.setBlock(CENTRE.offset(dx, -1, dz), Blocks.STONE);
			}
		}
	}

	/**
	 * One worker, watched every tick for {@link #WALK_TICKS}: never further than its wander radius (plus
	 * {@link #RADIUS_SLACK}) from its anchor, never on one block for more than {@link #STUCK_TICKS} while its
	 * navigation has a path — and it does walk, or the first two checks would hold for a statue.
	 *
	 * @implements MOD-832-WALK — the worker stays within its radius and does not stand still with a goal
	 */
	public static void staysWithinItsRadiusAndNeverSticks(GameTestHelper helper) {
		floor(helper);
		ShiftWorker worker = ShiftWorkers.spawn(helper.getLevel(), helper.absolutePos(CENTRE), 0)
				.orElseThrow(() -> helper.assertionException("ShiftWorkers.spawn made no worker"));
		Vec3 anchor = Vec3.atBottomCenterOf(worker.getHomePosition());
		int radius = worker.wanderRadius();
		BlockPos start = worker.blockPosition();
		BlockPos[] last = {start};
		int[] still = {0};
		double[] furthest = {0.0};
		boolean[] walked = {false};
		helper.startSequence().thenExecuteFor(WALK_TICKS, () -> {
			double dx = worker.getX() - anchor.x;
			double dz = worker.getZ() - anchor.z;
			double distance = Math.sqrt(dx * dx + dz * dz);
			furthest[0] = Math.max(furthest[0], distance);
			if (distance > radius + RADIUS_SLACK) {
				throw helper.assertionException("the worker walked " + String.format(Locale.ROOT, "%.2f", distance)
						+ " blocks from its anchor, its radius is " + radius);
			}
			BlockPos here = worker.blockPosition();
			still[0] = worker.getNavigation().isInProgress() && here.equals(last[0]) ? still[0] + 1 : 0;
			if (still[0] > STUCK_TICKS) {
				throw helper.assertionException("the worker stood on " + here + " for " + still[0]
						+ " ticks while its navigation had a path");
			}
			walked[0] |= !here.equals(start);
			last[0] = here;
		}).thenExecute(() -> {
			// The planner's mode follows the shared world clock: at night (HOME) the stroll keeps to a third of the
			// radius, so the furthest distance is small; the bound checked above is the radius in every mode.
			Industrialization.LOGGER.info("[ala-towns] walk check: furthest {} of radius {}, mode at the end {}",
					String.format(Locale.ROOT, "%.2f", furthest[0]), radius, worker.mode());
			if (!walked[0]) {
				throw helper.assertionException("the worker never left the block it was made on in " + WALK_TICKS
						+ " ticks");
			}
			worker.discard();
		}).thenSucceed();
	}

	/** Ticks driven before the clock starts, so the JIT has compiled the path. */
	private static final int WARMUP_TICKS = 120;
	/** Measured ticks: enough to average the scheduler out, short enough for the lane. */
	private static final int MEASURED_TICKS = 200;
	/**
	 * Ceiling for one tick of ten workers. The epic's budget is 0.5–1 ms; this is a structural tripwire with
	 * headroom for a loaded runner — a path search every tick, a planner that stopped skipping ticks — not the
	 * budget itself, which the printed median shows.
	 */
	private static final long TICK_CEILING_NS = 5_000_000L;

	/**
	 * Ten workers of one town on one floor, each ticked by hand in a timed loop (their regular tick runs too, so they
	 * walk twice as fast while measured): the median cost of one tick of all ten, above the noise of the loop itself,
	 * stays under {@link #TICK_CEILING_NS}.
	 *
	 * @implements MOD-832-PERF — a town of ten workers stays within the tick budget
	 */
	public static void tenWorkersTickCost(GameTestHelper helper) {
		floor(helper);
		List<ShiftWorker> workers = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			workers.add(ShiftWorkers.spawn(helper.getLevel(), helper.absolutePos(CENTRE.offset(i % 5 - 2, 0, i / 5)),
					0).orElseThrow(() -> helper.assertionException("ShiftWorkers.spawn made no worker")));
		}
		helper.runAfterDelay(2, () -> {
			for (int i = 0; i < WARMUP_TICKS; i++) {
				workers.forEach(ShiftWorker::tick);
			}
			long[] noise = new long[MEASURED_TICKS];
			long[] cost = new long[MEASURED_TICKS];
			for (int i = 0; i < MEASURED_TICKS; i++) {
				long t0 = System.nanoTime();
				long t1 = System.nanoTime();
				noise[i] = t1 - t0;
				long t2 = System.nanoTime();
				workers.forEach(ShiftWorker::tick);
				cost[i] = System.nanoTime() - t2;
			}
			long medianNoise = median(noise);
			long medianCost = Math.max(0L, median(cost) - medianNoise);
			Industrialization.LOGGER.info("[ala-towns] ten workers: median {} us per tick (noise {} us), ceiling {} us",
					medianCost / 1000, medianNoise / 1000, TICK_CEILING_NS / 1000);
			for (ShiftWorker worker : workers) {
				if (worker.isRemoved()) {
					throw helper.assertionException("a worker left the world during the measurement");
				}
				worker.discard();
			}
			helper.assertTrue(medianCost < TICK_CEILING_NS, "ten workers cost " + medianCost / 1000
					+ " us per tick, the ceiling is " + TICK_CEILING_NS / 1000 + " us");
			helper.succeed();
		});
	}

	/** Median, not mean: one scheduler hiccup must not move the figure. */
	private static long median(long[] samples) {
		long[] sorted = samples.clone();
		Arrays.sort(sorted);
		return sorted[sorted.length / 2];
	}
}
