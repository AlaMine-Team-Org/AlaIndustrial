package dev.alaindustrial.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * L1 for the settlement core's upkeep (MOD-831): when the core lights, what it drains, and that it goes dark exactly
 * {@code graceTicks} unpaid ticks after the energy is gone. The block entity only feeds it its buffer, so these are
 * the core's rules without a world.
 */
class SettlementCoreUpkeepTest {

	private static final int COST = 8;
	private static final int GRACE = 20;
	private static final long CAPACITY = 2000;

	/** A buffer the test drives by hand: what arrives each tick, minus what the core drains. */
	private static final class Buffer {
		long stored;

		long tick(SettlementCoreUpkeep upkeep, long arriving) {
			stored = Math.min(CAPACITY, stored + arriving);
			long drained = upkeep.tick(stored, CAPACITY, COST, GRACE);
			assertTrue(drained >= 0 && drained <= stored, "drained " + drained + " of " + stored);
			stored -= drained;
			return drained;
		}
	}

	@Test
	void aDarkCoreWaitsForGraceTicksOfUpkeepBeforeItLights() {
		SettlementCoreUpkeep upkeep = new SettlementCoreUpkeep();
		Buffer buffer = new Buffer();
		buffer.stored = COST * GRACE - 1L;
		assertEquals(0, buffer.tick(upkeep, 0));
		assertFalse(upkeep.active(), "one EU short of the reserve: still dark");
		assertEquals(COST, buffer.tick(upkeep, 1));
		assertTrue(upkeep.active(), "the reserve is there: lit, and the first tick paid");
	}

	@Test
	void aFullySuppliedCoreStaysLitAndPaysExactlyItsUpkeep() {
		SettlementCoreUpkeep upkeep = new SettlementCoreUpkeep();
		Buffer buffer = new Buffer();
		for (int tick = 0; tick < 500; tick++) {
			long drained = buffer.tick(upkeep, 32);
			if (upkeep.active()) {
				assertEquals(COST, drained, "a lit core pays its upkeep, no more");
			}
		}
		assertTrue(upkeep.active());
		assertEquals(0, upkeep.debt());
	}

	/** The acceptance rule: the energy is gone — the core goes dark on the graceTicks-th unpaid tick, not before. */
	@Test
	void aBlackoutDarkensTheCoreOnTheGraceTicksThUnpaidTick() {
		SettlementCoreUpkeep upkeep = new SettlementCoreUpkeep(true, 0);
		Buffer buffer = new Buffer();
		for (int unpaid = 1; unpaid < GRACE; unpaid++) {
			buffer.tick(upkeep, 0);
			assertTrue(upkeep.active(), "still lit after " + unpaid + " unpaid ticks");
		}
		buffer.tick(upkeep, 0);
		assertFalse(upkeep.active(), "dark on unpaid tick " + GRACE);
		assertEquals(0, upkeep.debt(), "a dark core owes nothing");
	}

	/** Three quarters of the upkeep is not enough: owing accumulates, and the core goes dark. */
	@Test
	void aSupplyBelowTheUpkeepDarkensTheCoreToo() {
		SettlementCoreUpkeep upkeep = new SettlementCoreUpkeep(true, 0);
		Buffer buffer = new Buffer();
		int ticks = 0;
		while (upkeep.active() && ticks < 10_000) {
			buffer.tick(upkeep, COST * 3 / 4);
			ticks++;
		}
		assertFalse(upkeep.active(), "a 75 % supply held the territory for " + ticks + " ticks");
		// It owed a quarter of the upkeep per tick, so it went dark after 4 × grace ticks.
		assertEquals(4 * GRACE, ticks);
	}

	@Test
	void debtIsPaidBackFirstWhenEnergyReturns() {
		SettlementCoreUpkeep upkeep = new SettlementCoreUpkeep(true, 0);
		Buffer buffer = new Buffer();
		for (int i = 0; i < 5; i++) {
			buffer.tick(upkeep, 0);
		}
		assertEquals(5L * COST, upkeep.debt());
		assertEquals(6L * COST, buffer.tick(upkeep, 100), "this tick's upkeep and the five owed");
		assertEquals(0, upkeep.debt());
	}

	/** A buffer smaller than the reserve still lights the core once it is full. */
	@Test
	void theReserveIsCappedByTheBuffer() {
		SettlementCoreUpkeep upkeep = new SettlementCoreUpkeep();
		assertEquals(COST, upkeep.tick(100, 100, COST, GRACE));
		assertTrue(upkeep.active());
	}

	@Test
	void aSavedDarkCoreOwesNothing() {
		assertEquals(0, new SettlementCoreUpkeep(false, 500).debt());
		assertEquals(40, new SettlementCoreUpkeep(true, 40).debt());
		assertEquals(0, new SettlementCoreUpkeep(true, -3).debt());
	}

	/** Never drains more than the buffer holds, and a lit core never owes a full grace or more. */
	@Property
	void drainStaysWithinTheBufferAndDebtUnderTheLimit(@ForAll @IntRange(min = 0, max = 64) int supply,
			@ForAll @IntRange(min = 1, max = 64) int cost, @ForAll @IntRange(min = 1, max = 60) int grace) {
		SettlementCoreUpkeep upkeep = new SettlementCoreUpkeep();
		long stored = 0;
		for (int tick = 0; tick < 400; tick++) {
			stored = Math.min(CAPACITY, stored + supply);
			long drained = upkeep.tick(stored, CAPACITY, cost, grace);
			assertTrue(drained >= 0 && drained <= stored);
			stored -= drained;
			if (upkeep.active()) {
				assertTrue(upkeep.debt() < (long) cost * grace, "a lit core owes less than its grace");
			} else {
				assertEquals(0, upkeep.debt());
			}
		}
	}
}
