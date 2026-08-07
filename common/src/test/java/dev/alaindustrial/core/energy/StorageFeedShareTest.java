package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 contract for the storage→sink feed arithmetic (MOD-353).
 *
 * <p>The properties pinned here are the ones that keep this channel from becoming the MOD-314 bug in a
 * new coat: the donor's reserve must be inviolable, the rate must be a ceiling rather than a target,
 * and a disabled channel must move nothing at all. A test that only checked "some energy moves" would
 * pass on an implementation that drains the player's bank.
 */
class StorageFeedShareTest {

	private static final long BOX = 20_000L;    // Config.batteryBoxBuffer default
	private static final long RATE = 12L;       // Config.teleporterStorageFeedRate default
	private static final double HALF = 0.5;     // Config.storageFeedReserveFraction default
	private static final long PACKET = 512L;    // HV tier packet cap — deliberately not the binding limit
	private static final long ROOM = 500_000L;  // an empty teleporter fund

	// --- the reserve, which is the whole safety argument ---

	@Test
	void donorAtReserveGivesNothing() {
		assertEquals(0L, StorageFeedShare.feedAllowance(BOX / 2, BOX, HALF, ROOM, RATE, PACKET),
				"a donor sitting exactly on its reserve must give nothing");
	}

	@Test
	void donorBelowReserveGivesNothing() {
		assertEquals(0L, StorageFeedShare.feedAllowance(BOX / 4, BOX, HALF, ROOM, RATE, PACKET));
		assertEquals(0L, StorageFeedShare.feedAllowance(1, BOX, HALF, ROOM, RATE, PACKET));
	}

	@Test
	void fullDonorGivesTheRateAndNoMore() {
		assertEquals(RATE, StorageFeedShare.feedAllowance(BOX, BOX, HALF, ROOM, RATE, PACKET),
				"the rate is a ceiling: a full donor still hands over exactly one tick's worth");
	}

	@Test
	void lastEuAboveReserveIsCappedByWhatIsSpendable() {
		// One EU above the line: the reserve, not the rate, is what binds here.
		assertEquals(1L, StorageFeedShare.feedAllowance(BOX / 2 + 1, BOX, HALF, ROOM, RATE, PACKET));
	}

	@Test
	void drainingStopsExactlyAtTheReserveFloor() {
		// Walk the donor down tick by tick and assert it lands ON the floor, never through it. This is
		// the property the player sees ("the box stops feeding at half") and the one a truncation bug
		// would break by a single EU.
		long amount = BOX;
		long floor = StorageFeedShare.reserveFloor(BOX, HALF);
		for (int tick = 0; tick < 10_000; tick++) {
			long move = StorageFeedShare.feedAllowance(amount, BOX, HALF, ROOM, RATE, PACKET);
			if (move == 0) {
				break;
			}
			amount -= move;
		}
		assertEquals(floor, amount, "draining must terminate exactly on the reserve floor");
		assertTrue(amount >= floor, "the donor must never end below its reserve");
	}

	// --- the other three ceilings ---

	@Test
	void sinkRoomBindsWhenItIsTheSmallest() {
		assertEquals(3L, StorageFeedShare.feedAllowance(BOX, BOX, HALF, 3L, RATE, PACKET));
	}

	@Test
	void packetCapBindsWhenItIsTheSmallest() {
		assertEquals(2L, StorageFeedShare.feedAllowance(BOX, BOX, HALF, ROOM, RATE, 2L));
	}

	@Test
	void zeroFeedRateDisablesTheChannel() {
		assertEquals(0L, StorageFeedShare.feedAllowance(BOX, BOX, HALF, ROOM, 0L, PACKET),
				"rate 0 is the default for every block that does not opt in — it must move nothing");
	}

	// --- degenerate inputs ---

	@Test
	void nonPositiveInputsYieldZero() {
		assertEquals(0L, StorageFeedShare.feedAllowance(0, BOX, HALF, ROOM, RATE, PACKET));
		assertEquals(0L, StorageFeedShare.feedAllowance(BOX, 0, HALF, ROOM, RATE, PACKET));
		assertEquals(0L, StorageFeedShare.feedAllowance(BOX, BOX, HALF, 0, RATE, PACKET));
		assertEquals(0L, StorageFeedShare.feedAllowance(BOX, BOX, HALF, ROOM, RATE, 0));
		assertEquals(0L, StorageFeedShare.feedAllowance(-5, BOX, HALF, ROOM, RATE, PACKET));
	}

	@Test
	void reserveFractionIsClampedSoABrokenConfigCannotOpenTheGate() {
		// Above 1: everything is reserved, nothing flows — a config typo must fail closed, not open.
		assertEquals(0L, StorageFeedShare.feedAllowance(BOX, BOX, 5.0, ROOM, RATE, PACKET));
		// Below 0: clamps to "no reserve", which is the documented drain-dry behaviour, not a negative
		// floor that would let the donor go below empty.
		assertEquals(RATE, StorageFeedShare.feedAllowance(BOX, BOX, -1.0, ROOM, RATE, PACKET));
		assertEquals(0L, StorageFeedShare.reserveFloor(BOX, -1.0));
		assertEquals(BOX, StorageFeedShare.reserveFloor(BOX, 5.0));
	}

	@Test
	void hugeCapacitiesDoNotOverflow() {
		// A capacity near Integer.MAX_VALUE must not wrap through the double multiply in the reserve.
		long huge = Integer.MAX_VALUE;
		long allowance = StorageFeedShare.feedAllowance(huge, huge, HALF, ROOM, RATE, PACKET);
		assertEquals(RATE, allowance);
		assertTrue(StorageFeedShare.reserveFloor(huge, HALF) > 0, "reserve floor must stay positive");
	}

	@Test
	void reserveFloorMatchesWhatFeedAllowanceEnforces() {
		// The two must agree — the GUI quotes reserveFloor while the network enforces feedAllowance.
		long floor = StorageFeedShare.reserveFloor(BOX, HALF);
		assertEquals(0L, StorageFeedShare.feedAllowance(floor, BOX, HALF, ROOM, RATE, PACKET));
		assertTrue(StorageFeedShare.feedAllowance(floor + 1, BOX, HALF, ROOM, RATE, PACKET) > 0);
	}
}
