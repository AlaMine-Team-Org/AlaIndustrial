package dev.alaindustrial.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.structure.ReactorLog.Entry;
import dev.alaindustrial.core.structure.ReactorLog.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** L1 coverage for {@link ReactorLog} (MOD-622): order, eviction, the throttle merge, readers and saved names. */
class ReactorLogTest {

	private static final long WINDOW = 100;

	@Test
	void entriesGetGrowingNumbersAndTheNewestIsLast() {
		ReactorLog log = new ReactorLog();
		log.append(10, Kind.ROOM_SEALED, 6, 4, 6, "");
		log.append(10, Kind.REACTION_STARTED, 8, 75, 0, "");
		List<Entry> entries = log.entries();
		assertEquals(List.of(1, 2), entries.stream().map(Entry::seq).toList(), "one game tick, two ordered entries");
		assertEquals(2, log.newestSeq());
		assertEquals(3, log.nextSeq());
	}

	@Test
	void theOldestEntryLeavesPastTheCapacity() {
		ReactorLog log = new ReactorLog();
		for (int i = 0; i < ReactorLog.CAPACITY + 5; i++) {
			log.append(i, Kind.OVERHEAT, i, 0, 0, "");
		}
		List<Entry> entries = log.entries();
		assertEquals(ReactorLog.CAPACITY, entries.size());
		assertEquals(6, entries.get(0).seq(), "the first five are gone");
		assertEquals(ReactorLog.CAPACITY + 5, entries.get(entries.size() - 1).seq());
	}

	/** Holding an arrow key on the throttle is one decision: the burst folds into one line from where it started. */
	@Test
	void aBurstOfOnePlayersThrottleClicksIsOneLine() {
		ReactorLog log = new ReactorLog();
		log.appendOrMerge(100, Kind.DEPTH_CHANGED, 50, 55, 0, "Alex", WINDOW);
		log.appendOrMerge(120, Kind.DEPTH_CHANGED, 55, 60, 0, "Alex", WINDOW);
		log.appendOrMerge(200, Kind.DEPTH_CHANGED, 60, 70, 0, "Alex", WINDOW);
		List<Entry> entries = log.entries();
		assertEquals(1, entries.size());
		assertEquals(50, entries.get(0).a(), "where the burst started");
		assertEquals(70, entries.get(0).b(), "where it ended");
		assertEquals(3, entries.get(0).seq(), "the folded line moves to the head with a new number");
	}

	/** Nudging the throttle and putting it back changed nothing, and the log says nothing about it. */
	@Test
	void aBurstThatEndsWhereItStartedLeavesNoLine() {
		ReactorLog log = new ReactorLog();
		log.append(10, Kind.ROOM_SEALED, 6, 4, 6, "");
		log.appendOrMerge(100, Kind.DEPTH_CHANGED, 50, 60, 0, "Alex", WINDOW);
		assertEquals(null, log.appendOrMerge(110, Kind.DEPTH_CHANGED, 60, 50, 0, "Alex", WINDOW));
		assertEquals(List.of(Kind.ROOM_SEALED), log.entries().stream().map(Entry::kind).toList());
	}

	@Test
	void theAgeIsTheLargestWholeUnit() {
		assertEquals(new ReactorLog.Age(ReactorLog.Age.Unit.NOW, 0), ReactorLog.Age.of(19));
		assertEquals(new ReactorLog.Age(ReactorLog.Age.Unit.NOW, 0), ReactorLog.Age.of(-40), "a client clock a moment behind");
		assertEquals(new ReactorLog.Age(ReactorLog.Age.Unit.SECONDS, 59), ReactorLog.Age.of(20 * 60 - 1));
		assertEquals(new ReactorLog.Age(ReactorLog.Age.Unit.MINUTES, 1), ReactorLog.Age.of(20 * 60));
		assertEquals(new ReactorLog.Age(ReactorLog.Age.Unit.HOURS, 23), ReactorLog.Age.of(20L * 3600 * 24 - 1));
		assertEquals(new ReactorLog.Age(ReactorLog.Age.Unit.DAYS, 2), ReactorLog.Age.of(20L * 3600 * 48));
	}

	@Test
	void anotherPlayerALaterClickOrAnotherKindStartsANewLine() {
		ReactorLog log = new ReactorLog();
		log.appendOrMerge(100, Kind.DEPTH_CHANGED, 50, 55, 0, "Alex", WINDOW);
		log.appendOrMerge(110, Kind.DEPTH_CHANGED, 55, 60, 0, "Steve", WINDOW);
		log.appendOrMerge(111 + WINDOW, Kind.DEPTH_CHANGED, 60, 65, 0, "Steve", WINDOW);
		log.appendOrMerge(250, Kind.OVERHEAT, 70, 0, 0, "", WINDOW);
		log.appendOrMerge(251, Kind.OVERHEAT, 71, 0, 0, "", WINDOW);
		assertEquals(5, log.entries().size());
	}

	/** Reloaded out of order or past the capacity, the log keeps its newest entries and never reuses a number. */
	@Test
	void restoringKeepsTheNewestAndTheNextNumber() {
		List<Entry> saved = new ArrayList<>();
		for (int seq = 120; seq >= 1; seq--) {
			saved.add(new Entry(seq, seq, Kind.OVERHEAT, 0, 0, 0, ""));
		}
		ReactorLog log = new ReactorLog();
		log.restore(saved, 0, Map.of());
		List<Entry> entries = log.entries();
		assertEquals(ReactorLog.CAPACITY, entries.size());
		assertEquals(21, entries.get(0).seq());
		assertEquals(120, entries.get(entries.size() - 1).seq());
		assertEquals(121, log.nextSeq(), "a lost counter does not reuse a saved number");
		assertEquals(121, log.append(0, Kind.BARE_LEFT, 0, 0, 0, "").seq());
	}

	@Test
	void eachPlayerReadsSeparatelyAndProgressOnlyMovesForward() {
		ReactorLog log = new ReactorLog();
		UUID alex = new UUID(0, 1);
		UUID steve = new UUID(0, 2);
		assertTrue(log.markSeen(alex, 5));
		assertFalse(log.markSeen(alex, 3), "a late acknowledgement cannot bring an alarm back");
		assertEquals(5, log.seenBy(alex));
		assertEquals(0, log.seenBy(steve), "one player reading does not clear another's badge");
	}

	@Test
	void theReaderWhoReadLeastRecentlyIsForgottenFirst() {
		ReactorLog log = new ReactorLog();
		for (int i = 0; i <= ReactorLog.MAX_READERS; i++) {
			log.markSeen(new UUID(0, i), i + 1);
		}
		assertEquals(ReactorLog.MAX_READERS, log.readers().size());
		assertEquals(0, log.seenBy(new UUID(0, 0)), "the first reader is gone");
		assertEquals(ReactorLog.MAX_READERS + 1, log.seenBy(new UUID(0, ReactorLog.MAX_READERS)));

		Map<UUID, Integer> saved = new LinkedHashMap<>(log.readers());
		ReactorLog reloaded = new ReactorLog();
		reloaded.restore(List.of(), 1, saved);
		assertEquals(saved, reloaded.readers(), "progress survives a reload in the same order");
	}

	@Test
	void onlyAnAlarmNewerThanWhatThePlayerSawLightsTheBadge() {
		ReactorLog log = new ReactorLog();
		log.append(0, Kind.OVERHEAT, 70, 0, 0, "");
		log.append(0, Kind.MELTDOWN_STARTED, 86, 0, 0, "");
		log.append(0, Kind.MELTDOWN_ENDED, 3, 0, 0, "");
		assertTrue(ReactorLog.unseenAlarm(log.entries(), 1));
		assertFalse(ReactorLog.unseenAlarm(log.entries(), 2), "the alarm was seen; a warning after it is no badge");
	}

	/** The saved names are a world format: pinned here, so renaming a kind fails a test instead of an old world. */
	@Test
	void savedNamesArePinnedAndAnUnknownOneIsNeutral() {
		assertEquals(List.of("unknown", "room_sealed", "room_unsealed", "reaction_started", "reaction_scrammed",
				"rods_withdrawn", "out_of_fuel", "reaction_stopped", "overheat", "meltdown_started", "meltdown_ended",
				"countdown_armed", "countdown_cancelled", "countdown_expired", "bare_entered", "bare_left",
				"depth_changed"), Arrays.stream(Kind.values()).map(Kind::id).toList());
		assertEquals(Kind.UNKNOWN, Kind.byId("a_kind_from_a_newer_build"));
		assertEquals(Kind.UNKNOWN, Kind.byOrdinal(-1));
		assertEquals(Kind.UNKNOWN, Kind.byOrdinal(Kind.values().length));
		assertEquals(ReactorLog.Severity.INFO, Kind.UNKNOWN.severity(), "never an invented alarm");
		assertTrue(ReactorLog.Severity.WARNING.needsAttention());
		assertFalse(ReactorLog.Severity.INFO.needsAttention());
	}

	@Test
	void anActorNameIsKeptToAProfileNamesLength() {
		assertEquals(ReactorLog.ACTOR_MAX, new Entry(1, 0, Kind.DEPTH_CHANGED, 0, 0, 0, "x".repeat(40)).actor().length());
		assertEquals("", new Entry(1, 0, null, 0, 0, 0, null).actor());
		assertEquals(Kind.UNKNOWN, new Entry(1, 0, null, 0, 0, 0, null).kind());
	}
}
