package dev.alaindustrial.settlement.shift;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 for how a line joins a town's chronicle (MOD-886): a repeat of the town's newest line is merged into it, the town
 * keeps its newest {@link ChronicleRules#CAPACITY} lines, and other towns' lines are never touched.
 */
class ChronicleRulesTest {

	/** A plain line: its town, what happened, how many times, when. */
	private record Line(int town, String event, int repeats, long time) {
	}

	private static ChronicleRules.Result add(List<Line> log, Line line, int capacity) {
		return ChronicleRules.append(log, line, Line::town,
				(newest, next) -> newest.event().equals(next.event()),
				(newest, next) -> new Line(newest.town(), newest.event(), newest.repeats() + next.repeats(),
						next.time()),
				capacity);
	}

	@Test
	void aNewEventIsAddedAtTheEnd() {
		List<Line> log = new ArrayList<>();
		assertEquals(ChronicleRules.Result.ADDED, add(log, new Line(1, "arrived", 1, 10), 200));
		assertEquals(ChronicleRules.Result.ADDED, add(log, new Line(1, "settled", 1, 20), 200));
		assertEquals(List.of(new Line(1, "arrived", 1, 10), new Line(1, "settled", 1, 20)), log);
	}

	@Test
	void aRepeatOfTheNewestLineIsMergedWithItsLatestTime() {
		List<Line> log = new ArrayList<>(List.of(new Line(1, "inlet", 1, 10)));
		assertEquals(ChronicleRules.Result.MERGED, add(log, new Line(1, "inlet", 1, 50), 200));
		assertEquals(ChronicleRules.Result.MERGED, add(log, new Line(1, "inlet", 1, 90), 200));
		assertEquals(List.of(new Line(1, "inlet", 3, 90)), log);
	}

	@Test
	void onlyTheNewestLineOfTheSameTownCountsAsTheOneRepeated() {
		// Another town's line between does not break the repeat; the town's own older line does not count.
		List<Line> log = new ArrayList<>(List.of(new Line(1, "inlet", 1, 10), new Line(1, "died", 1, 20),
				new Line(2, "inlet", 1, 30)));
		assertEquals(ChronicleRules.Result.ADDED, add(log, new Line(1, "inlet", 1, 40), 200));
		assertEquals(ChronicleRules.Result.MERGED, add(log, new Line(2, "inlet", 1, 45), 200));
		assertEquals(ChronicleRules.Result.MERGED, add(log, new Line(1, "inlet", 1, 50), 200));
		assertEquals(List.of(new Line(1, "inlet", 1, 10), new Line(1, "died", 1, 20), new Line(2, "inlet", 2, 45),
				new Line(1, "inlet", 2, 50)), log);
	}

	@Test
	void aTownKeepsItsNewestLinesAndDropsItsOldest() {
		List<Line> log = new ArrayList<>();
		add(log, new Line(2, "other", 1, 0), 200);
		for (int i = 0; i < ChronicleRules.CAPACITY + 1; i++) {
			add(log, new Line(1, "event " + i, 1, i), ChronicleRules.CAPACITY);
		}
		List<Line> town = log.stream().filter(line -> line.town() == 1).toList();
		assertEquals(ChronicleRules.CAPACITY, town.size());
		assertEquals("event 1", town.getFirst().event(), "the oldest line went first");
		assertEquals("event " + ChronicleRules.CAPACITY, town.getLast().event());
		assertEquals(new Line(2, "other", 1, 0), log.getFirst(), "another town's line is never dropped");
	}

	@Test
	void trimDropsOnlyWhatIsOverTheCapacity() {
		List<Line> log = new ArrayList<>(List.of(new Line(1, "a", 1, 1), new Line(3, "x", 1, 2),
				new Line(1, "b", 1, 3), new Line(1, "c", 1, 4)));
		ChronicleRules.trim(log, 1, Line::town, 2);
		assertEquals(List.of(new Line(3, "x", 1, 2), new Line(1, "b", 1, 3), new Line(1, "c", 1, 4)), log);
		ChronicleRules.trim(log, 1, Line::town, 2);
		assertEquals(3, log.size(), "a town at its capacity loses nothing");
		ChronicleRules.trim(log, 1, Line::town, 0);
		assertEquals(List.of(new Line(3, "x", 1, 2)), log);
	}
}
