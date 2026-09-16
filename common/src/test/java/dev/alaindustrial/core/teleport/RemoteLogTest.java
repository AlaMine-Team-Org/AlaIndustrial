package dev.alaindustrial.core.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.teleport.RemoteLog.Entry;
import dev.alaindustrial.core.teleport.RemoteLog.Group;
import dev.alaindustrial.core.teleport.RemoteLog.Kind;
import dev.alaindustrial.core.teleport.RemoteLog.Station;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MOD-631 — the remote's log: order, eviction, the refusal merge, the read mark and the filters. */
class RemoteLogTest {

	private static final Station HOME = new Station("Home base", 0);
	private static final Station MINE = new Station("", 2);

	private static RemoteLog refuse(RemoteLog log, long time, Kind kind, Station station) {
		return log.append(time, kind, false, station, Station.NONE, 0, 0, 0);
	}

	@Test
	void entriesGetGrowingNumbersAndTheNewestComesFirst() {
		RemoteLog log = RemoteLog.EMPTY
				.append(10, Kind.BOUND, false, HOME, Station.NONE, 1, 16, 0)
				.append(20, Kind.JUMPED, false, HOME, Station.NONE, 7_160, 0, 0);
		assertEquals(List.of(2, 1), log.newestFirst(null).stream().map(Entry::seq).toList());
		assertEquals(2, log.newestSeq());
		assertEquals(3, log.nextSeq());
	}

	@Test
	void theFiftyFirstEntryPushesTheOldestOut() {
		RemoteLog log = RemoteLog.EMPTY;
		for (int i = 0; i < RemoteLog.CAPACITY + 1; i++) {
			log = log.append(i * 1_000L, Kind.JUMPED, false, HOME, Station.NONE, i, 0, 0);
		}
		assertEquals(RemoteLog.CAPACITY, log.entries().size());
		assertEquals(2, log.entries().get(0).seq(), "entry 1 was the one to go");
		assertEquals(RemoteLog.CAPACITY + 1, log.newestSeq());
	}

	@Test
	void aRepeatedRefusalForTheSameStationWithinTenSecondsReplacesTheLastLine() {
		RemoteLog log = refuse(RemoteLog.EMPTY, 100, Kind.REFUSED_NO_POWER, HOME);
		log = refuse(log, 100 + RemoteLog.MERGE_WINDOW, Kind.REFUSED_NO_POWER, HOME);
		assertEquals(1, log.entries().size());
		assertEquals(2, log.newestSeq(), "the merged line is new again");
		assertEquals(100 + RemoteLog.MERGE_WINDOW, log.entries().get(0).time());
	}

	@Test
	void aRefusalIsKeptApartWhenAnythingDiffers() {
		RemoteLog log = refuse(RemoteLog.EMPTY, 100, Kind.REFUSED_NO_POWER, HOME);
		log = refuse(log, 101 + RemoteLog.MERGE_WINDOW, Kind.REFUSED_NO_POWER, HOME);
		log = refuse(log, 402, Kind.REFUSED_NO_POWER, MINE);
		log = refuse(log, 403, Kind.REFUSED_NOT_FORMED, MINE);
		log = log.append(404, Kind.REFUSED_NOT_FORMED, true, MINE, Station.NONE, 0, 0, 0);
		assertEquals(5, log.entries().size(), "too late, another station, another reason, another kind of jump");
	}

	@Test
	void onlyRefusalsMerge() {
		RemoteLog log = RemoteLog.EMPTY
				.append(100, Kind.JUMPED, false, HOME, Station.NONE, 10, 0, 0)
				.append(101, Kind.JUMPED, false, HOME, Station.NONE, 10, 0, 0)
				.append(102, Kind.CANCELLED_MOVED, false, HOME, Station.NONE, 0, 0, 0)
				.append(103, Kind.CANCELLED_MOVED, false, HOME, Station.NONE, 0, 0, 0);
		assertEquals(4, log.entries().size());
	}

	@Test
	void theBadgeIsLitOnlyByAnUnreadRefusal() {
		RemoteLog log = RemoteLog.EMPTY.append(1, Kind.CANCELLED_HURT, false, HOME, Station.NONE, 0, 0, 0)
				.append(2, Kind.DELETED, false, HOME, Station.NONE, 0, 0, 0);
		assertFalse(log.hasRefusalAfter(0), "a cancellation or an edit is not a refusal");
		log = refuse(log, 3, Kind.REFUSED_COOLDOWN, HOME);
		assertTrue(log.hasRefusalAfter(log.seenSeq()));
		log = log.markSeen(log.newestSeq());
		assertFalse(log.hasRefusalAfter(log.seenSeq()));
	}

	@Test
	void theReadMarkOnlyMovesForwardAndNeverPastTheNewestLine() {
		RemoteLog log = RemoteLog.EMPTY.append(1, Kind.JUMPED, false, HOME, Station.NONE, 0, 0, 0)
				.append(2, Kind.JUMPED, false, HOME, Station.NONE, 0, 0, 0);
		log = log.markSeen(99);
		assertEquals(2, log.seenSeq(), "a forged number stops at the newest line");
		assertSame(log, log.markSeen(1), "an older mark changes nothing");
	}

	@Test
	void aSavedLogIsPutBackInOrderAndItsNumbersStayAhead() {
		List<Entry> saved = new ArrayList<>();
		saved.add(new Entry(7, 70, Kind.JUMPED, false, HOME, Station.NONE, 0, 0, 0));
		saved.add(new Entry(3, 30, Kind.BOUND, false, HOME, Station.NONE, 0, 0, 0));
		RemoteLog log = new RemoteLog(1, 50, saved);
		assertEquals(List.of(3, 7), log.entries().stream().map(Entry::seq).toList());
		assertEquals(8, log.nextSeq(), "a lost counter never reuses a saved number");
		assertEquals(7, log.seenSeq(), "a read mark past the newest line is pulled back");
	}

	@Test
	void theFiltersSplitJumpsRefusalsAndTheRest() {
		RemoteLog log = RemoteLog.EMPTY
				.append(1, Kind.JUMPED, false, HOME, Station.NONE, 0, 0, 0)
				.append(2, Kind.RANDOM_JUMPED, true, HOME, Station.NONE, 0, 0, 0)
				.append(3, Kind.REFUSED_NO_CHIP, true, HOME, Station.NONE, 0, 0, 0)
				.append(4, Kind.RENAMED, false, HOME, MINE, 0, 0, 0);
		assertEquals(4, log.newestFirst(null).size());
		assertEquals(2, log.newestFirst(Group.JUMP).size());
		assertEquals(List.of(3), log.newestFirst(Group.REFUSAL).stream().map(Entry::seq).toList());
	}

	@Test
	void kindsSurviveTheirSavedNameAndAnUnknownOneReadsAsUnknown() {
		for (Kind kind : Kind.values()) {
			assertEquals(kind, Kind.byId(kind.id()));
			assertEquals(kind, Kind.byOrdinal(kind.ordinal()));
		}
		assertEquals(Kind.UNKNOWN, Kind.byId("teleported_to_the_moon"));
		assertEquals(Kind.UNKNOWN, Kind.byOrdinal(Kind.values().length));
	}

	@Test
	void aStationNameIsCutToTheWireLimit() {
		assertEquals(RemoteLog.NAME_MAX, new Station("x".repeat(RemoteLog.NAME_MAX + 10), 0).name().length());
		assertEquals("", new Station(null, 3).name());
	}
}
