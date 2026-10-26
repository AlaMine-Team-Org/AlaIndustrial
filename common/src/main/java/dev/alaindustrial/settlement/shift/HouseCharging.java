package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.block.ChargingBedBlock;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.settlement.HousePowerSource;
import dev.alaindustrial.settlement.works.TownHousing;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * A resident sleeps and charges at home (MOD-862, MOD-889; epic MOD-824, decisions R7, R101, R156): at night, by its
 * day's routine ({@link ShiftRoutine}), a worker with a house walks to the charging bed of its house, lies down and
 * takes EU from the house's inlet ({@link HousePowerSource}) all night, full or not, and gets up in the morning. By day
 * it goes to its bed only with its battery under {@link #LOW_SHARE} of its capacity, and gets up once full. Sleep comes
 * by the routine, not by the charge: a big battery does not keep a worker up through the night (R75). A homeless worker
 * charges from the core, as before ({@link ShiftWorkerBattery#recharge}); a resident does not — its house is its only
 * charger, so a house whose inlet holds nothing leaves it to run down.
 *
 * <p><b>Lying down (owner's round 4 of 0.1.219, R4-9).</b> At the bed the worker lies down along it, its head on the
 * pillow ({@link ShiftWorker#lieDown}), and charges lying; up, it steps off beside the bed. A worker running from a
 * hostile mob does not lie down, and a wound gets it up.
 *
 * <p>Runs on the worker's think ticks. The «going to bed» state lives on the entity and is not saved: after a reload
 * the routine sends the worker to bed again if it is night, or its battery is low.
 */
public final class HouseCharging {

	/** The share of its capacity under which a resident goes to bed by day (R156). */
	public static final double LOW_SHARE = 0.10;
	/** How close to the bed, in blocks, a worker lies on it and charges. */
	public static final double BED_REACH = 2.5;

	private HouseCharging() {
	}

	/** The head of the charging bed of {@code record}'s house, standing and loaded; empty for a homeless worker. */
	public static Optional<BlockPos> bed(ServerLevel level, Optional<ShiftWorkerRecord> record) {
		return record.filter(ShiftWorkerRecord::housed)
				.flatMap(found -> TownHousing.bed(level, found.settlement(), found.home()))
				.filter(pos -> level.isLoaded(pos) && isBedHead(level.getBlockState(pos)));
	}

	/**
	 * One think of {@code worker}: go to bed, charge, or get up.
	 *
	 * @param night     the routine has the worker sleep now
	 * @param reachable the routine has not given the bed up in this activity (it could not get there)
	 */
	public static void think(ServerLevel level, ShiftWorker worker, Optional<ShiftWorkerRecord> found,
			Optional<BlockPos> bed, boolean night, boolean reachable) {
		if (found.isEmpty() || bed.isEmpty() || !reachable) {
			getUp(worker);
			return;
		}
		ShiftWorkerRecord record = found.get();
		ShiftWorkerProfile profile = record.profile();
		long charge = profile.chargeNow();
		boolean low = charge < profile.capacity() * LOW_SHARE;
		RoutineState routine = worker.routine();
		if (!worker.goingToBed()) {
			if (!night && !low) {
				return;
			}
			worker.setGoingToBed(true);
			routine.setChargingByDay(!night);
		} else if (night) {
			routine.setChargingByDay(false);
		} else if (!routine.chargingByDay() && !low) {
			getUp(worker); // morning: the night is over, the battery holds enough for the day
			return;
		}
		worker.setHomeTo(bed.get(), 1);
		if (worker.isFleeing()) {
			worker.getUp();
			return;
		}
		if (worker.lyingOn().isEmpty()) {
			if (worker.position().distanceTo(Vec3.atBottomCenterOf(bed.get())) > BED_REACH
					|| !inside(level, record, worker.blockPosition())) {
				return; // MOD-889: lies down from inside its house, never through the wall
			}
			worker.lieDown(bed.get());
		}
		long room = profile.capacity() - charge;
		long taken = room <= 0 ? 0L : TownHousing.inlet(level, record.settlement(), record.home())
				.map(inlet -> inlet.drawEnergy(room)).orElse(0L);
		if (taken > 0) {
			ShiftWorkers.book(level.getServer()).put(record.withProfile(profile.withCharge(charge + taken)));
		}
		if (!night && charge + taken >= profile.capacity()) {
			getUp(worker);
		}
	}

	/** Whether {@code pos} stands inside the house of {@code record}: within the box of its plan (walls included). */
	private static boolean inside(ServerLevel level, ShiftWorkerRecord record, BlockPos pos) {
		return TownWorksScanner.plan(level, record.settlement(), record.home()).map(plan -> plan.contains(pos))
				.orElse(true);
	}

	/** Whether {@code state} is the head of a standing charging bed, the cell a worker lies down on. */
	private static boolean isBedHead(BlockState state) {
		return state.getBlock() instanceof ChargingBedBlock && state.getValue(ChargingBedBlock.PART) == BedPart.HEAD;
	}

	/** Off the bed and back to its day: the routine gives the worker its place on the same think. */
	private static void getUp(ShiftWorker worker) {
		worker.getUp();
		if (!worker.goingToBed()) {
			return;
		}
		worker.setGoingToBed(false);
		worker.routine().setChargingByDay(false);
		worker.setHomeTo(worker.townAnchor(), ShiftSchedule.strollRadius(worker.mode(), worker.wanderRadius()));
	}
}
