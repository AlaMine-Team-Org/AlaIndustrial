package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 contract for the storage→storage cascade arithmetic (MOD-314).
 *
 * <p>The properties asserted here are the ones that keep the cascade from becoming an energy leak:
 * it must never run backwards, never overshoot, refuse sub-deadband jitter, and terminate. A test that
 * only checked "some energy moves" would pass on an implementation that oscillates forever.
 */
class CascadeShareTest {

	private static final long BOX = 20_000L;   // Config.batteryBoxBuffer default
	private static final long CABLE = 12L;     // Config.cableBuffer default — the deadband
	private static final long PACKET = 32L;    // LV tier packet cap

	// --- compareFill ---

	@Test
	void comparesByFractionNotByAbsoluteCharge() {
		// The whole point of comparing fractions: a small store that is proportionally fuller must count
		// as fuller, even though it holds far less energy.
		assertTrue(CascadeShare.compareFill(90, 100, 500, 1000) > 0, "90% must outrank 50%");
		assertTrue(CascadeShare.compareFill(500, 1000, 90, 100) < 0);
		assertEquals(0, CascadeShare.compareFill(50, 100, 5_000, 10_000), "equal fractions compare equal");
	}

	@Test
	void zeroCapacityIsNeverFullerOrEmptier() {
		assertEquals(0, CascadeShare.compareFill(0, 0, 10, 100));
		assertEquals(0, CascadeShare.compareFill(10, 100, 0, 0));
	}

	@Test
	void extremeCapacitiesDoNotFlipTheSign() {
		// Guards the overflow path: two operator-set capacities near Integer.MAX_VALUE make the naive
		// cross-product exceed Long.MAX_VALUE, and a wrapped product would silently reverse the answer —
		// energy would flow from the emptier store to the fuller one.
		long huge = Integer.MAX_VALUE;
		assertTrue(CascadeShare.compareFill(huge, huge, 1, huge) > 0, "full must outrank nearly-empty");
		assertTrue(CascadeShare.compareFill(1, huge, huge, huge) < 0);
	}

	// --- equalisingTransfer ---

	@Test
	void equalisingTransferLevelsTwoIdenticalStores() {
		// 10000 vs 0 over equal capacities: half of the gap each way lands both on 5000.
		assertEquals(5_000, CascadeShare.equalisingTransfer(10_000, BOX, 0, BOX));
	}

	@Test
	void equalisingTransferIsZeroWhenDonorIsNotFuller() {
		assertEquals(0, CascadeShare.equalisingTransfer(5_000, BOX, 5_000, BOX), "equal fractions");
		assertEquals(0, CascadeShare.equalisingTransfer(1_000, BOX, 9_000, BOX), "donor is emptier");
	}

	@Test
	void equalisingTransferRespectsDifferingCapacities() {
		// donor 100% of 1000, sink 0% of 3000 → common fraction 1000/4000 = 25%, so the donor keeps 250
		// and hands over 750.
		assertEquals(750, CascadeShare.equalisingTransfer(1_000, 1_000, 0, 3_000));
	}

	// --- allowance: the deadband ---

	@Test
	void refusesGapsBelowTheDeadband() {
		// A gap smaller than one cable-buffer packet is exactly the jitter the deadband exists to refuse:
		// energy already in flight down the line is invisible here, so chasing such a gap is what produces
		// a two-cycle that burns MOD-021 loss on every leg.
		long justUnder = CascadeShare.equalisingTransfer(BOX / 2 + 10, BOX, BOX / 2, BOX);
		assertTrue(justUnder < CABLE, "precondition: this pair's gap is below the deadband");
		assertEquals(0, CascadeShare.allowance(BOX / 2 + 10, BOX, BOX / 2, BOX, CABLE, PACKET));
	}

	@Test
	void deadbandIsMeasuredOnTheFullGapNotTheHalfStep() {
		// Boundary: a pair whose equalising transfer is exactly the deadband may move (half of it), and a
		// pair one EU below it may not. Checking the deadband against the halved value instead would open
		// the tap for gaps down to half the threshold.
		long amount = BOX / 2 + 2 * CABLE;
		assertEquals(CABLE, CascadeShare.equalisingTransfer(amount, BOX, BOX / 2, BOX),
				"precondition: this pair's gap is exactly the deadband");
		assertEquals(CABLE / 2, CascadeShare.allowance(amount, BOX, BOX / 2, BOX, CABLE, PACKET));
	}

	// --- allowance: half step, cap, direction ---

	@Test
	void neverExceedsTheTierPacketCap() {
		assertEquals(PACKET, CascadeShare.allowance(BOX, BOX, 0, BOX, CABLE, PACKET),
				"a full box next to an empty one is still limited to one packet per tick");
	}

	@Test
	void neverRunsBackwards() {
		assertEquals(0, CascadeShare.allowance(0, BOX, BOX, BOX, CABLE, PACKET));
		assertEquals(0, CascadeShare.allowance(5_000, BOX, 5_000, BOX, CABLE, PACKET), "already level");
	}

	@Test
	void movesAtMostHalfTheGapSoItCannotOvershoot() {
		// With a packet cap wide enough not to bind, the allowance is exactly half the equalising
		// transfer — the property that makes the pair converge instead of crossing over and coming back.
		long allowance = CascadeShare.allowance(10_000, BOX, 0, BOX, CABLE, Long.MAX_VALUE);
		assertEquals(2_500, allowance);
		assertTrue(allowance < CascadeShare.equalisingTransfer(10_000, BOX, 0, BOX),
				"a half step must stay strictly short of levelling the pair in one go");
	}

	@Test
	void repeatedApplicationConvergesAndStops() {
		// The termination proof, run as arithmetic: hand the allowance over tick after tick and the pair
		// must settle by itself, without any "stop exactly at equality" rule — and must not end up with
		// the donor below the sink, which is what an oscillation looks like.
		long donor = BOX;
		long sink = 0;
		int ticks = 0;
		for (; ticks < 10_000; ticks++) {
			long move = CascadeShare.allowance(donor, BOX, sink, BOX, CABLE, PACKET);
			if (move == 0) {
				break;
			}
			donor -= move;
			sink += move;
		}
		assertTrue(ticks < 10_000, "cascade must terminate on its own");
		assertTrue(donor >= sink, "the donor must never end up emptier than the sink it fed");
		assertTrue(donor - sink < 2 * CABLE,
				"the pair must settle within a deadband of level, not stall far apart; got "
						+ donor + " vs " + sink);
		assertEquals(BOX, donor + sink, "the cascade arithmetic itself must conserve energy");
	}

	@Test
	void zeroOrNegativePacketCapMovesNothing() {
		assertEquals(0, CascadeShare.allowance(BOX, BOX, 0, BOX, CABLE, 0));
		assertEquals(0, CascadeShare.allowance(BOX, BOX, 0, BOX, CABLE, -5));
	}
}
