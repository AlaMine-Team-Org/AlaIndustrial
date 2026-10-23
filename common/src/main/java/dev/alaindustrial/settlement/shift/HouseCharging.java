package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.block.ChargingBedBlock;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.settlement.HousePowerSource;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.TownHousing;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * A resident charges at home (MOD-862; epic MOD-824, decision R101): when its battery runs low, a worker with a house
 * walks to the charging bed of its house and takes EU from the house's inlet ({@link HousePowerSource}) until the
 * battery is full, then goes back to its town. A homeless worker charges from the core, as before
 * ({@link ShiftWorkerBattery#recharge}); a resident does not — its house is its only charger, so a house whose inlet
 * holds nothing leaves it to run down.
 *
 * <p><b>Lying down (owner's round 4 of 0.1.219, R4-9).</b> At the bed the worker lies down along it, its head on the
 * pillow ({@link ShiftWorker#lieDown}), and charges lying; full, it gets up beside the bed. A worker running from a
 * hostile mob does not lie down, and a wound gets it up.
 *
 * <p>Runs on the worker's think ticks. The «going to bed» state lives on the entity and is not saved: after a reload
 * a low battery sends the worker to bed again.
 */
public final class HouseCharging {

	/** The share of its capacity under which a resident goes to bed. */
	public static final double LOW_SHARE = 0.25;
	/** How close to the bed, in blocks, a worker lies on it and charges. */
	public static final double BED_REACH = 2.5;

	private HouseCharging() {
	}

	/** One think of {@code worker}: go to bed, charge, or get up. */
	public static void think(ServerLevel level, ShiftWorker worker) {
		ShiftWorkerBook book = ShiftWorkers.book(level.getServer());
		Optional<ShiftWorkerRecord> found = book.byEntity(worker.getUUID())
				.filter(record -> record.status() == ShiftWorkerRecord.Status.ALIVE);
		Optional<BlockPos> bed = found.filter(ShiftWorkerRecord::housed)
				.flatMap(record -> TownHousing.bed(level, record.settlement(), record.home()))
				.filter(pos -> level.isLoaded(pos) && isBedHead(level.getBlockState(pos)));
		if (found.isEmpty() || bed.isEmpty()) {
			getUp(level, worker);
			return;
		}
		ShiftWorkerRecord record = found.get();
		ShiftWorkerProfile profile = record.profile();
		long charge = profile.chargeNow();
		if (!worker.goingToBed()) {
			if (charge >= profile.capacity() * LOW_SHARE) {
				return;
			}
			worker.setGoingToBed(true);
		}
		worker.setHomeTo(bed.get(), 1);
		if (worker.isFleeing()) {
			worker.getUp();
			return;
		}
		if (worker.lyingOn().isEmpty()) {
			if (worker.position().distanceTo(Vec3.atBottomCenterOf(bed.get())) > BED_REACH) {
				return;
			}
			worker.lieDown(bed.get());
		}
		long room = profile.capacity() - charge;
		long taken = room <= 0 ? 0L : TownHousing.inlet(level, record.settlement(), record.home())
				.map(inlet -> inlet.drawEnergy(room)).orElse(0L);
		if (taken > 0) {
			book.put(record.withProfile(profile.withCharge(charge + taken)));
		}
		if (charge + taken >= profile.capacity()) {
			getUp(level, worker);
		}
	}

	/** Whether {@code state} is the head of a standing charging bed, the cell a worker lies down on. */
	private static boolean isBedHead(BlockState state) {
		return state.getBlock() instanceof ChargingBedBlock && state.getValue(ChargingBedBlock.PART) == BedPart.HEAD;
	}

	/** Back to the town: off the bed, and the worker's anchor is its town's core again. */
	private static void getUp(ServerLevel level, ShiftWorker worker) {
		worker.getUp();
		if (!worker.goingToBed()) {
			return;
		}
		worker.setGoingToBed(false);
		Optional<Settlement> town = worker.settlement() > 0
				? SettlementRegistry.get(level.getServer()).find(worker.settlement()) : Optional.empty();
		town.ifPresent(settlement -> worker.setHomeTo(settlement.corePos(),
				ShiftSchedule.strollRadius(worker.mode(), worker.wanderRadius())));
	}
}
