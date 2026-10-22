package dev.alaindustrial.entity;

import dev.alaindustrial.settlement.SettlementTerritory;
import dev.alaindustrial.settlement.shift.ShiftMode;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A shift worker runs from hostile mobs and keeps inside its town (owner decision 2026-10-22, MOD-824): the mobs
 * hunt workers as they hunt players ({@link ShiftWorkerHunters}), and a worker that notices one within
 * {@link #NOTICE_RADIUS} — or was just hit by one — runs away from it until it is {@link #SAFE_RADIUS} off.
 *
 * <p><b>Never past the territory.</b> A refuge is a stand inside the town's territory with at least
 * {@link #EDGE_MARGIN} column to its edge ({@link ShiftWorkers#territory}); a worker of no town keeps to its wander
 * radius around its anchor. Points straight away from the threat are tried first; when they all lie outside — the
 * worker is pressed to the edge — any stand within reach is tried, and the one farthest from the threat wins, so a
 * cornered worker runs along the edge instead of over it. A refuge no farther from the threat than the worker stands
 * now is no refuge: then it stays and waits for the next try.
 *
 * <p>The highest goal after floating: it takes the legs from the walk ({@link ShiftWorkerWalkGoal}), which picks the
 * shift up again once the worker is safe — and walks it home if the run took it out of its stroll radius. Its path
 * goes through the town's dispatcher like every other (ADR-050 point 11). A hibernating worker runs no goals and
 * does not run.
 */
final class ShiftWorkerFleeGoal extends Goal {

	/** How near a hostile mob a worker notices it, in blocks. */
	static final double NOTICE_RADIUS = 8.0;
	/** How far from the threat a worker stops running, in blocks. */
	static final double SAFE_RADIUS = 12.0;
	/** Columns a refuge keeps from the territory's edge, so the body stays inside. */
	static final int EDGE_MARGIN = 1;
	/** Speed factor of a run: faster than a stroll, faster than a zombie. */
	static final double RUN_SPEED = 0.9;
	/** Ticks between looks around while the worker is calm; every tick while it is running or just ran. */
	private static final int CALM_SCAN_TICKS = 4;
	/** Ticks after a run during which the worker keeps looking every tick. */
	private static final int ALERT_TICKS = 40;
	/** Ticks since the last hit by a mob that still count it as a threat at any distance up to the safe radius. */
	private static final int HIT_MEMORY_TICKS = 100;
	/** Stands tried per way of looking for a refuge. */
	private static final int TRIES = 10;
	/** How far a refuge may lie from the worker, horizontally and vertically. */
	private static final int REFUGE_REACH = 12;
	private static final int REFUGE_HEIGHT = 7;

	private final ShiftWorker worker;
	private @Nullable Mob threat;
	private @Nullable Path path;
	private long alertUntil;

	ShiftWorkerFleeGoal(ShiftWorker worker) {
		this.worker = worker;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	/** Whether the worker is running from a threat now. */
	boolean running() {
		return threat != null;
	}

	@Override
	public boolean canUse() {
		if (!(worker.level() instanceof ServerLevel level) || worker.isPassenger()
				|| worker.mode() == ShiftMode.HIBERNATE) {
			return false;
		}
		long now = level.getGameTime();
		if (now >= alertUntil && (worker.tickCount + worker.getId()) % CALM_SCAN_TICKS != 0) {
			return false;
		}
		Mob found = nearestThreat(level);
		if (found == null) {
			return false;
		}
		alertUntil = now + ALERT_TICKS;
		Vec3 refuge = refuge(level, found.position());
		if (refuge == null || !ShiftWorkers.tryPlanPath(level, worker)) {
			return false;
		}
		path = worker.getNavigation().createPath(refuge.x, refuge.y, refuge.z, 0);
		if (path == null) {
			return false;
		}
		threat = found;
		return true;
	}

	@Override
	public void start() {
		worker.getNavigation().moveTo(path, RUN_SPEED);
	}

	@Override
	public boolean canContinueToUse() {
		Mob current = threat;
		return current != null && current.isAlive() && HostileThreat.isActiveThreat(current)
				&& worker.distanceToSqr(current) < SAFE_RADIUS * SAFE_RADIUS
				&& !worker.getNavigation().isDone() && worker.mode() != ShiftMode.HIBERNATE;
	}

	@Override
	public void stop() {
		worker.getNavigation().stop();
		path = null;
		threat = null;
		alertUntil = worker.level().getGameTime() + ALERT_TICKS;
	}

	/** The nearest hostile mob the worker sees within {@link #NOTICE_RADIUS}, or the one that just hit it. */
	private @Nullable Mob nearestThreat(ServerLevel level) {
		Mob nearest = null;
		double best = NOTICE_RADIUS * NOTICE_RADIUS;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, worker.getBoundingBox().inflate(NOTICE_RADIUS, 4.0,
				NOTICE_RADIUS), mob -> mob != worker && HostileThreat.isActiveThreat(mob))) {
			double distance = worker.distanceToSqr(mob);
			if (distance <= best && worker.hasLineOfSight(mob)) {
				best = distance;
				nearest = mob;
			}
		}
		if (nearest == null) {
			LivingEntity hitter = worker.getLastHurtByMob();
			if (hitter instanceof Mob mob && HostileThreat.isActiveThreat(mob)
					&& worker.tickCount - worker.getLastHurtByMobTimestamp() < HIT_MEMORY_TICKS
					&& worker.distanceToSqr(mob) < SAFE_RADIUS * SAFE_RADIUS) {
				nearest = mob;
			}
		}
		return nearest;
	}

	/** A stand inside the town farther from {@code from} than the worker is now, the farthest found; null — none. */
	private @Nullable Vec3 refuge(ServerLevel level, Vec3 from) {
		Optional<SettlementTerritory.Area> territory = ShiftWorkers.territory(level, worker);
		BlockPos anchor = worker.getHomePosition();
		int homeRadius = worker.getHomeRadius();
		boolean hadHome = worker.hasHome();
		// The random stands keep to the home radius, which is the planner's stroll radius (a third of the wander
		// radius at night); a run keeps to the territory instead, so the home is lifted for the search only.
		worker.clearHome();
		try {
			double here = worker.position().distanceToSqr(from);
			Vec3 best = null;
			double bestDistance = here;
			for (int i = 0; i < TRIES * 2; i++) {
				Vec3 stand = i < TRIES ? LandRandomPos.getPosAway(worker, REFUGE_REACH, REFUGE_HEIGHT, from)
						: LandRandomPos.getPos(worker, REFUGE_REACH, REFUGE_HEIGHT);
				if (stand == null || !inside(territory, anchor, stand)) {
					continue;
				}
				double distance = stand.distanceToSqr(from);
				if (distance > bestDistance) {
					best = stand;
					bestDistance = distance;
				}
			}
			return best;
		} finally {
			if (hadHome) {
				worker.setHomeTo(anchor, homeRadius);
			}
		}
	}

	/** Whether {@code stand} lies where the worker may run: its town's territory, or its wander radius without one. */
	private boolean inside(Optional<SettlementTerritory.Area> territory, BlockPos anchor, Vec3 stand) {
		BlockPos pos = BlockPos.containing(stand);
		if (territory.isPresent()) {
			return territory.get().reach(pos.getX(), pos.getZ()) >= EDGE_MARGIN;
		}
		double dx = pos.getX() - anchor.getX();
		double dz = pos.getZ() - anchor.getZ();
		return dx * dx + dz * dz <= (double) worker.wanderRadius() * worker.wanderRadius();
	}
}
