package dev.alaindustrial.entity;

import dev.alaindustrial.settlement.shift.Profession;
import dev.alaindustrial.settlement.shift.ShiftMode;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import dev.alaindustrial.settlement.works.Storekeeping;
import dev.alaindustrial.settlement.works.TownStorage;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The storekeeper's work (MOD-888; epic MOD-824, decision R155, plan 0.1.220 block B6): in working hours a worker of
 * the storekeeper's trade walks between the town's storage and the dispatcher, a batch a trip ({@link Storekeeping}).
 * It walks to where the batch lies, picks it up — what it carries shows in its hand — walks to where it goes and puts
 * it down there; then it looks for the next trip. Nothing to carry: the goal stays off and the worker strolls as
 * any other ({@code ShiftWorkerWalkGoal}).
 *
 * <p>Every path goes through the town's path dispatcher ({@link ShiftWorkers#tryPlanPath}, ADR-050 point 11). A trip
 * whose way cannot be found is given up and the storekeeper waits {@link #GIVE_UP_TICKS} before it plans again, so an
 * unreachable shelf is not asked for every tick. The trip lives on the entity and is not saved; the batch is a promise
 * until it is put down ({@link Storekeeping}), so a reload costs a walk and nothing else.
 */
final class ShiftWorkerStorekeeperGoal extends Goal {

	/** Ticks between two looks for a trip while there was nothing to carry. */
	static final int LOOK_TICKS = 40;
	/** Ticks a storekeeper waits after a trip it had to give up. */
	static final int GIVE_UP_TICKS = 200;
	/** How close, in blocks, to a shelf or the dispatcher the storekeeper picks a batch up or puts it down. */
	static final double REACH = 2.5;
	/** Walks it may plan again on one leg of a trip before it gives the trip up. */
	private static final int REPLANS = 4;
	/** Speed factor of a storekeeper on a trip: a little quicker than a stroll ({@link ShiftWorker#STROLL_SPEED}). */
	private static final double TRIP_SPEED = 0.75;

	private final ShiftWorker worker;
	private Storekeeping.@Nullable Trip trip;
	/** False: on the way to pick the batch up; true: carrying it to its target. */
	private boolean carrying;
	private int replans;
	private long nextLookAt;

	ShiftWorkerStorekeeperGoal(ShiftWorker worker) {
		this.worker = worker;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	/** Whether the worker may work as a storekeeper now: its trade, working hours, on its feet, its town's. */
	private boolean onDuty() {
		return worker.profession() == Profession.STOREKEEPER && worker.settlement() > 0
				&& worker.mode() == ShiftMode.WORK && worker.lyingOn().isEmpty() && !worker.goingToBed()
				&& !worker.isFleeing() && !worker.isPassenger();
	}

	@Override
	public boolean canUse() {
		if (!(worker.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!worker.getMainHandItem().isEmpty()) {
			worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); // a picture left from a trip before a reload
		}
		long now = level.getGameTime();
		if (now < nextLookAt || !onDuty()) {
			return false;
		}
		nextLookAt = now + LOOK_TICKS;
		Optional<Storekeeping.Trip> next = Storekeeping.next(level, worker.settlement());
		if (next.isEmpty()) {
			return false;
		}
		trip = next.get();
		carrying = false;
		replans = 0;
		return true;
	}

	@Override
	public void start() {
		walkTo(target());
	}

	@Override
	public boolean canContinueToUse() {
		return trip != null && onDuty();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	@Override
	public void tick() {
		if (trip == null || !(worker.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos at = target();
		if (worker.position().distanceTo(Vec3.atCenterOf(at)) <= REACH) {
			worker.getNavigation().stop();
			worker.getLookControl().setLookAt(Vec3.atCenterOf(at));
			if (!carrying) {
				carrying = true;
				replans = 0;
				showLoad();
				walkTo(target());
			} else {
				Storekeeping.deliver(level, worker.settlement(), trip);
				finish(level, 0);
			}
			return;
		}
		if (worker.getNavigation().isDone()) {
			if (++replans > REPLANS) {
				finish(level, GIVE_UP_TICKS);
				return;
			}
			walkTo(at);
		}
	}

	/** Where the storekeeper walks now: the batch's source, then its target. */
	private BlockPos target() {
		return carrying ? trip.to() : trip.from();
	}

	/** Plans a walk to beside {@code at}; a town that plans no more paths this tick is asked again later. */
	private void walkTo(BlockPos at) {
		if (!(worker.level() instanceof ServerLevel level) || !ShiftWorkers.tryPlanPath(level, worker)) {
			return;
		}
		Path path = worker.getNavigation().createPath(at, 1);
		if (path != null) {
			worker.getNavigation().moveTo(path, TRIP_SPEED);
		}
	}

	/** What it carries shows in its hand: the batch's first item — a picture, the items are moved on delivery. */
	private void showLoad() {
		if (trip.batch().isEmpty()) {
			return;
		}
		TownStorage.byId(trip.batch().getFirst().item())
				.ifPresent(item -> worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item)));
	}

	/** The trip is over: hand empty, the next look in {@code wait} ticks (at once, after a delivery). */
	private void finish(ServerLevel level, int wait) {
		trip = null;
		carrying = false;
		worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		worker.getNavigation().stop();
		nextLookAt = level.getGameTime() + wait;
	}

	@Override
	public void stop() {
		if (worker.level() instanceof ServerLevel level && trip != null) {
			finish(level, LOOK_TICKS);
		}
		worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}
}
