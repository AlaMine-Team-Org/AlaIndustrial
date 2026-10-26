package dev.alaindustrial.entity;

import dev.alaindustrial.settlement.shift.RoutineState;
import dev.alaindustrial.settlement.shift.ShiftMode;
import dev.alaindustrial.settlement.shift.ShiftRoutine;
import dev.alaindustrial.settlement.shift.ShiftSchedule;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How a shift worker walks (MOD-832): short walks inside its home radius, and back home when it is outside — the
 * one goal of the first slice that moves the worker, and the only one that plans a path. Every path goes through
 * the town's dispatcher first ({@link ShiftWorkers#tryPlanPath}, ADR-050 point 11); a denied worker tries again on a
 * later tick.
 *
 * <p>How often and how far follow the planner's mode ({@link ShiftSchedule#strollInterval},
 * {@link ShiftSchedule#strollRadius} through the home radius the planner sets). A walk ends when the navigation is
 * done — reached, or given up by the navigation's own stuck detection — so a worker that has somewhere to go never
 * stands on one block for long: the next walk is planned once the interval has passed.
 *
 * <p><b>Toward a place it cannot reach</b> (MOD-889): a walk home whose path is not found, or ends no closer to home,
 * is a failed try; the worker waits out a growing backoff before it asks the dispatcher again for the same home
 * ({@link RoutineState#mayPlan}) instead of planning every tick. Whether it gets anywhere at all is the routine's
 * watch ({@code ShiftRoutine}); this goal never moves the worker but by walking.
 */
final class ShiftWorkerWalkGoal extends Goal {

	/** Ticks a worker that could not plan a walk waits before trying again. */
	private static final int RETRY_TICKS = 10;
	/** How near home, in blocks, a walk home plans its path to home itself. */
	static final double DIRECT_REACH = 24.0;

	private final ShiftWorker worker;
	private @Nullable Path path;
	private long nextWalkAt;

	ShiftWorkerWalkGoal(ShiftWorker worker) {
		this.worker = worker;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!(worker.level() instanceof ServerLevel level) || worker.isPassenger()
				|| worker.mode() == ShiftMode.HIBERNATE || worker.lyingOn().isPresent()) {
			return false;
		}
		long now = level.getGameTime();
		boolean outside = !worker.isWithinHome();
		RoutineState routine = worker.routine();
		long home = worker.getHomePosition().asLong();
		if (outside ? !routine.mayPlan(home, now) : now < nextWalkAt) {
			return false;
		}
		Vec3 target = outside ? homeward() : LandRandomPos.getPos(worker, Math.max(1, worker.getHomeRadius()), 4);
		if (target != null && !outside && ShiftRoutine.onCapsuleRoof(level, target)) {
			target = null; // MOD-893: a stand lifted out of the capsule's cell onto its roof is no place to walk to
		}
		if (target == null || !ShiftWorkers.tryPlanPath(level, worker)) {
			nextWalkAt = now + RETRY_TICKS;
			return false;
		}
		routine.notePathPlanned();
		path = worker.getNavigation().createPath(target.x, target.y, target.z, 0);
		if (outside) {
			if (path == null || !getsCloserHome(path)) {
				routine.planFailed(home, now);
				path = null;
				return false;
			}
			routine.planFound(home);
		}
		if (path == null) {
			nextWalkAt = now + RETRY_TICKS;
			return false;
		}
		return true;
	}

	/** Whether {@code path} reaches its target, or at least ends a block nearer home than the worker stands. */
	private boolean getsCloserHome(Path path) {
		if (path.canReach()) {
			return true;
		}
		Vec3 home = Vec3.atBottomCenterOf(worker.getHomePosition());
		Vec3 end = Vec3.atBottomCenterOf(path.getEndNode() != null ? path.getEndNode().asBlockPos()
				: worker.blockPosition());
		return end.distanceTo(home) <= worker.position().distanceTo(home) - 1.0;
	}

	/**
	 * Home itself when it lies within {@link #DIRECT_REACH} — a path searched to the very place goes round a wall to
	 * the door of a house, where a random stand toward it stops at the wall (MOD-889) — else a point toward home, or
	 * home itself when no stand is found that way.
	 */
	private Vec3 homeward() {
		Vec3 home = Vec3.atBottomCenterOf(worker.getHomePosition());
		if (worker.position().distanceTo(home) <= DIRECT_REACH) {
			return home;
		}
		Vec3 toward = LandRandomPos.getPosTowards(worker, 16, 7, home);
		return toward != null ? toward : home;
	}

	@Override
	public void start() {
		worker.getNavigation().moveTo(path, ShiftWorker.STROLL_SPEED * ShiftSchedule.speedFactor(worker.mode()));
	}

	@Override
	public boolean canContinueToUse() {
		return !worker.getNavigation().isDone() && worker.mode() != ShiftMode.HIBERNATE
				&& worker.lyingOn().isEmpty();
	}

	@Override
	public void stop() {
		worker.getNavigation().stop();
		path = null;
		int interval = worker.routine().strollInterval(worker.mode());
		long wait = interval == Integer.MAX_VALUE ? interval : interval / 2 + worker.getRandom().nextInt(interval);
		nextWalkAt = worker.level().getGameTime() + wait;
	}
}
