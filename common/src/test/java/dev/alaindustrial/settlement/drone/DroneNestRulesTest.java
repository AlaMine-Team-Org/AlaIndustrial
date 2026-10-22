package dev.alaindustrial.settlement.drone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementTier;
import org.junit.jupiter.api.Test;

/**
 * L1 for the rules of the drone nests (MOD-858; epic MOD-824, R87, R91, R94): the nests a town's tier lets work, the
 * flight path over the roofs, its cost and the take-off reserve that lets a drone finish a flight when the core goes
 * dark.
 */
class DroneNestRulesTest {

	/** R91: LV 1, MV 2, HV 3, atomic 4 working nests, and one more once the town hall is restored. */
	@Test
	void nestLimitFollowsTheTierAndTheRestoredHall() {
		assertEquals(1, DroneNestRules.nestLimit(SettlementTier.LV, false));
		assertEquals(2, DroneNestRules.nestLimit(SettlementTier.MV, false));
		assertEquals(3, DroneNestRules.nestLimit(SettlementTier.HV, false));
		assertEquals(4, DroneNestRules.nestLimit(SettlementTier.ATOMIC, false));
		assertEquals(2, DroneNestRules.nestLimit(SettlementTier.LV, true));
		assertEquals(5, DroneNestRules.nestLimit(SettlementTier.ATOMIC, true));
	}

	/** The first nests to come work, the rest stand idle. */
	@Test
	void onlyTheFirstNestsWork() {
		assertTrue(DroneNestRules.works(0, 1));
		assertFalse(DroneNestRules.works(1, 1));
		assertFalse(DroneNestRules.works(0, 0));
		assertTrue(DroneNestRules.works(2, 3));
	}

	/** The legacy cruise height stands over the higher end; a route's length rounds up to whole blocks. */
	@Test
	void cruiseHeightAndRouteBlocks() {
		assertEquals(12, DroneNestRules.cruiseY(10, 0));
		assertEquals(14, DroneNestRules.cruiseY(0, 12));
		assertEquals(5, DroneNestRules.routeBlocks(5.0));
		assertEquals(6, DroneNestRules.routeBlocks(5.01));
		assertEquals(0, DroneNestRules.routeBlocks(-3.0));
	}

	/** A leg takes the tier's ticks per block, never less than the floor. */
	@Test
	void flightTicksHaveAFloor() {
		int perBlock = SettlementConfig.settlementDroneFlightTicksPerBlock;
		assertEquals(Math.max(DroneNestRules.MIN_FLIGHT_TICKS, 20 * perBlock), DroneNestRules.flightTicks(20));
		assertEquals(DroneNestRules.MIN_FLIGHT_TICKS, DroneNestRules.flightTicks(1));
	}

	/** R94: a drone takes off only with the round trip AND one block in its battery. */
	@Test
	void takeOffNeedsTheRoundTripAndOneBlock() {
		long need = DroneNestRules.flightCost(20) + DroneNestRules.blockPrice();
		assertEquals(2L * 20 * SettlementConfig.settlementDroneEuPerBlockFlown, DroneNestRules.flightCost(20));
		assertTrue(DroneNestRules.canTakeOff(need, 20));
		assertFalse(DroneNestRules.canTakeOff(need - 1, 20));
	}

	/** The nest asks for its charge rate, never more than the battery has room for. */
	@Test
	void chargeStopsAtAFullBattery() {
		long capacity = DroneNestRules.batteryCapacity();
		assertEquals(0, DroneNestRules.chargeWanted(capacity));
		assertEquals(1, DroneNestRules.chargeWanted(capacity - 1));
		assertEquals(SettlementConfig.settlementDroneChargePerTick, DroneNestRules.chargeWanted(0));
	}

	/** Every status has its own key, and an ordinal out of range reads as no town. */
	@Test
	void statusesHaveKeys() {
		assertEquals("settlement.alaindustrial.drone_nest.status.extra", DroneNestRules.Status.EXTRA.key());
		assertEquals(DroneNestRules.Status.NO_TOWN, DroneNestRules.Status.byOrdinal(99));
		assertFalse(DroneNestRules.Status.WORKING.blocking());
		assertTrue(DroneNestRules.Status.CORE_DARK.blocking());
	}
}
