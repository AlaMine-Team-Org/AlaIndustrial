package dev.alaindustrial.settlement.drone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.works.BlockReason;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/**
 * L1 for who works in a town's construction (MOD-858; epic MOD-824, R91, R93): the first dispatcher to report works,
 * the first nests up to the limit work, a block that stops reporting drops out, and a cell has one taker at a time.
 */
class TownDroneRosterTest {

	@Test
	void theFirstDispatcherWorksAndASilentOneDropsOut() {
		TownDroneRoster roster = new TownDroneRoster();
		assertTrue(roster.dispatcherCheckIn(1, 10L, 0L));
		assertFalse(roster.dispatcherCheckIn(1, 20L, 1L));
		assertTrue(roster.dispatcherCheckIn(2, 20L, 1L), "another town's first dispatcher works");
		assertEquals(OptionalLong.of(10L), roster.dispatcherOf(1, 2L));
		// The first one goes silent: past the alive window the second takes over.
		long later = 1L + TownDroneRoster.ALIVE_TICKS + 1L;
		assertTrue(roster.dispatcherCheckIn(1, 20L, later));
		assertEquals(OptionalLong.of(20L), roster.dispatcherOf(1, later));
		assertEquals(OptionalLong.empty(), roster.dispatcherOf(3, later));
		roster.forget(1, 20L);
		assertEquals(OptionalLong.empty(), roster.dispatcherOf(1, later));
	}

	@Test
	void nestsRankInTheOrderTheyFirstReported() {
		TownDroneRoster roster = new TownDroneRoster();
		assertEquals(0, roster.nestCheckIn(1, 100L, 0L, true));
		assertEquals(1, roster.nestCheckIn(1, 200L, 0L, false));
		assertEquals(2, roster.nestCheckIn(1, 300L, 0L, true));
		assertEquals(0, roster.nestCheckIn(1, 100L, 5L, true), "reporting again keeps the place");
		assertEquals(3, roster.nests(1, 5L));
		assertEquals(1, roster.busyNests(1, 5L, 2), "only the first two count, and one of them is busy");
		assertEquals(2, roster.busyNests(1, 5L, 3));
		assertEquals(0, roster.busyNests(9, 5L, 3));
		assertEquals(0, roster.nests(9, 5L));
	}

	@Test
	void aCellHasOneTakerUntilItIsGivenBack() {
		TownDroneRoster roster = new TownDroneRoster();
		assertTrue(roster.reserve(7L, "nest:a"));
		assertTrue(roster.reserve(7L, "nest:a"), "asking again for one's own cell is fine");
		assertFalse(roster.reserve(7L, "nest:b"));
		assertEquals("nest:a", roster.holder(7L));
		roster.release("nest:a");
		assertNull(roster.holder(7L));
		assertTrue(roster.reserve(7L, "nest:b"));
	}

	/** A stop is told once per building, whichever drone and job stop for it, till its cell is worked or done. */
	@Test
	void aStopIsToldOncePerBuildingTillItsCellIsWorked() {
		TownDroneRoster roster = new TownDroneRoster();
		BlockReason far = BlockReason.noRoute(1, 2, 3);
		assertTrue(roster.firstStop(1, 0, far));
		assertFalse(roster.firstStop(1, 0, BlockReason.noRoute(1, 2, 3)), "the same stop again is no news");
		assertTrue(roster.firstStop(1, 1, far), "another building's stop is its own");
		assertTrue(roster.firstStop(1, 0, BlockReason.noRoute(4, 5, 6)));
		assertFalse(roster.firstStop(1, 0, far), "a second reason does not make the first one news again");
		roster.stopResolved(1, 0, "9 9 9");
		assertFalse(roster.firstStop(1, 0, far), "work on another cell leaves the stop told");
		roster.stopResolved(1, 0, BlockReason.at(1, 2, 3));
		assertTrue(roster.firstStop(1, 0, far), "once its cell was worked, the stop is news again");
		roster.forgetStops(1, 0);
		assertTrue(roster.firstStop(1, 0, BlockReason.noRoute(4, 5, 6)), "a done job forgets its stops");
	}

	@Test
	void dispatcherStatusesHaveKeysAndSayWhetherTheyServe() {
		assertEquals("settlement.alaindustrial.dispatcher.status.second", DispatcherStatus.SECOND.key());
		assertTrue(DispatcherStatus.SECOND.blocking());
		assertFalse(DispatcherStatus.WORKING.blocking());
		assertTrue(DispatcherStatus.IDLE.serves() && DispatcherStatus.WORKING.serves());
		assertFalse(DispatcherStatus.OUTSIDE_HALL.serves());
		assertEquals(DispatcherStatus.NO_TOWN, DispatcherStatus.byOrdinal(-1));
		assertEquals(DispatcherStatus.WORKING, DispatcherStatus.byOrdinal(4));
	}
}
