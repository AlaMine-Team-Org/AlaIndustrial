package dev.alaindustrial.entity;

import dev.alaindustrial.settlement.shift.ShiftMode;
import dev.alaindustrial.settlement.shift.ShiftSchedule;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
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
 */
final class ShiftWorkerWalkGoal extends Goal {

	/** Ticks a worker that could not plan a walk waits before trying again. */
	private static final int RETRY_TICKS = 10;

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
		if (!outside && now < nextWalkAt) {
			return false;
		}
		Vec3 target = outside ? homeward() : LandRandomPos.getPos(worker, Math.max(1, worker.getHomeRadius()), 4);
		if (target == null || !ShiftWorkers.tryPlanPath(level, worker)) {
			nextWalkAt = now + RETRY_TICKS;
			return false;
		}
		path = worker.getNavigation().createPath(target.x, target.y, target.z, 0);
		if (path == null) {
			nextWalkAt = now + RETRY_TICKS;
			return false;
		}
		return true;
	}

	/** A point toward home; home itself when no stand is found that way. */
	private Vec3 homeward() {
		BlockPos home = worker.getHomePosition();
		Vec3 toward = LandRandomPos.getPosTowards(worker, 16, 7, Vec3.atBottomCenterOf(home));
		return toward != null ? toward : Vec3.atBottomCenterOf(home);
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
		int interval = ShiftSchedule.strollInterval(worker.mode());
		long wait = interval == Integer.MAX_VALUE ? interval : interval / 2 + worker.getRandom().nextInt(interval);
		nextWalkAt = worker.level().getGameTime() + wait;
	}
}
