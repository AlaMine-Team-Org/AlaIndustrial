package dev.alaindustrial.block.entity;

import static dev.alaindustrial.block.entity.ChargePadChime.Payout.CHARGING;
import static dev.alaindustrial.block.entity.ChargePadChime.Payout.EMPTY;
import static dev.alaindustrial.block.entity.ChargePadChime.Payout.READY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.block.entity.ChargePadChime.Payout;
import org.junit.jupiter.api.Test;

/** The rules of the Charging Station's "all charged" chime (MOD-668), payout by payout. */
class ChargePadChimeTest {

	/** Plays the payouts on a fresh visit and returns how many chimes they produced. */
	private static int chimes(ChargePadChime chime, Payout... payouts) {
		int count = 0;
		for (Payout payout : payouts) {
			if (chime.onPayout(payout)) {
				count++;
			}
		}
		return count;
	}

	private static ChargePadChime visit() {
		ChargePadChime chime = new ChargePadChime();
		chime.onArrive();
		return chime;
	}

	@Test
	void finishedChargePlaysExactlyOnce() {
		assertEquals(1, chimes(visit(), CHARGING, CHARGING, CHARGING, READY, READY, READY, READY, READY));
	}

	@Test
	void chimeWaitsForReadyToHold() {
		ChargePadChime chime = visit();
		chime.onPayout(CHARGING);
		assertEquals(false, chime.onPayout(READY), "one READY payout is not yet a finished charge");
		assertEquals(true, chime.onPayout(READY));
	}

	@Test
	void arrivingFullIsSilent() {
		assertEquals(0, chimes(visit(), READY, READY, READY, READY, READY, READY));
	}

	@Test
	void flickerDoesNotRingASeries() {
		// Charged, chimed, then an item keeps losing a sliver on the station: CHARGING for one payout,
		// READY for the next, over and over.
		ChargePadChime chime = visit();
		assertEquals(1, chimes(chime, CHARGING, READY, READY));
		assertEquals(0, chimes(chime, CHARGING, READY, READY, CHARGING, READY, READY, CHARGING, READY, READY));
	}

	@Test
	void flickerBeforeTheFirstChimeIsDebounced() {
		assertEquals(0, chimes(visit(), CHARGING, READY, CHARGING, READY, CHARGING));
	}

	@Test
	void aRealNewChargeRearmsWithoutSteppingOff() {
		ChargePadChime chime = visit();
		assertEquals(1, chimes(chime, CHARGING, READY, READY));
		Payout[] newItem = new Payout[ChargePadChime.REARM_PAYOUTS];
		java.util.Arrays.fill(newItem, CHARGING);
		assertEquals(0, chimes(chime, newItem));
		assertEquals(1, chimes(chime, READY, READY));
	}

	@Test
	void chargingShorterThanRearmDoesNotRearm() {
		ChargePadChime chime = visit();
		assertEquals(1, chimes(chime, CHARGING, READY, READY));
		Payout[] shortTopUp = new Payout[ChargePadChime.REARM_PAYOUTS - 1];
		java.util.Arrays.fill(shortTopUp, CHARGING);
		assertEquals(0, chimes(chime, shortTopUp));
		assertEquals(0, chimes(chime, READY, READY, READY));
	}

	@Test
	void steppingOffAndOnRearms() {
		ChargePadChime chime = visit();
		assertEquals(1, chimes(chime, CHARGING, READY, READY));
		chime.onArrive();
		assertEquals(1, chimes(chime, CHARGING, READY, READY));
	}

	@Test
	void steppingOnAgainWithNothingToChargeIsSilent() {
		ChargePadChime chime = visit();
		assertEquals(1, chimes(chime, CHARGING, READY, READY));
		chime.onArrive();
		assertEquals(0, chimes(chime, READY, READY, READY));
	}

	@Test
	void emptyStationNeverChimes() {
		assertEquals(0, chimes(visit(), CHARGING, EMPTY, EMPTY, EMPTY, EMPTY));
	}

	@Test
	void emptyBreaksAReadyRun() {
		assertEquals(0, chimes(visit(), CHARGING, READY, EMPTY, READY));
	}

	@Test
	void chargeFinishedAfterTheGridRecoversStillChimes() {
		assertEquals(1, chimes(visit(), CHARGING, EMPTY, EMPTY, CHARGING, READY, READY));
	}
}
