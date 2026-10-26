package dev.alaindustrial.entity;

import dev.alaindustrial.settlement.shift.RoutineActivity;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * At the day's gathering in the town hall (MOD-889; epic MOD-824, plan 0.1.220 block B5) a standing worker faces the
 * nearest other worker of the gathering, so the group stands face to face rather than staring at the wall. A player who
 * comes up still wins its eyes ({@link ShiftWorkerLookGoal} runs first); a walking worker looks where it goes.
 *
 * <p>Picks its peer again every {@link #REPICK_TICKS}, from the workers within {@link #PEER_RANGE} blocks that are at
 * the gathering too. Server only, nothing saved.
 */
final class ShiftWorkerGatherGoal extends Goal {

	/** How far a worker looks for a peer of the gathering, in blocks. */
	static final double PEER_RANGE = 8.0;
	/** Ticks between two picks of the peer. */
	static final int REPICK_TICKS = 60;

	private final ShiftWorker worker;
	private @Nullable ShiftWorker peer;
	private int untilRepick;

	ShiftWorkerGatherGoal(ShiftWorker worker) {
		this.worker = worker;
		setFlags(EnumSet.of(Flag.LOOK));
	}

	private boolean gathering() {
		return worker.routine().activity() == RoutineActivity.GATHER && worker.getNavigation().isDone()
				&& worker.lyingOn().isEmpty() && !worker.isFleeing();
	}

	@Override
	public boolean canUse() {
		if (worker.level().isClientSide() || !gathering() || worker.getRandom().nextInt(10) != 0) {
			return false;
		}
		peer = nearestPeer();
		return peer != null;
	}

	@Override
	public boolean canContinueToUse() {
		return gathering() && peer != null && peer.isAlive()
				&& peer.distanceToSqr(worker) <= PEER_RANGE * PEER_RANGE;
	}

	@Override
	public void start() {
		untilRepick = REPICK_TICKS;
	}

	@Override
	public void stop() {
		peer = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (--untilRepick <= 0) {
			untilRepick = REPICK_TICKS;
			ShiftWorker nearest = nearestPeer();
			if (nearest != null) {
				peer = nearest;
			}
		}
		if (peer != null) {
			worker.getLookControl().setLookAt(peer.getX(), peer.getEyeY(), peer.getZ());
		}
	}

	/** The nearest other worker within {@link #PEER_RANGE} who is at the gathering too; null when there is none. */
	@Nullable ShiftWorker nearestPeer() {
		AABB around = worker.getBoundingBox().inflate(PEER_RANGE);
		List<ShiftWorker> near = worker.level().getEntitiesOfClass(ShiftWorker.class, around,
				other -> other != worker && other.isAlive()
						&& other.routine().activity() == RoutineActivity.GATHER);
		ShiftWorker best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ShiftWorker other : near) {
			double distance = other.distanceToSqr(worker);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = other;
			}
		}
		return best;
	}
}
