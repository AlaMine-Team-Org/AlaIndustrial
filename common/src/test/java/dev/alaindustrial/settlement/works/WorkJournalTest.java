package dev.alaindustrial.settlement.works;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.settlement.works.WorkJournal.Entry;
import dev.alaindustrial.settlement.works.WorkJournal.Kind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * L1 for the dispatcher's work journal (MOD-860, epic decision R88): lines with their game time, merging of repeated
 * work, a «missing» line that does not repeat, a bounded ring, the words and the clock of a line.
 */
class WorkJournalTest {

	private static final String BRICKS = "block.minecraft.bricks";
	private static final String GLASS = "block.minecraft.glass";
	private static final String HALL = "settlement.alaindustrial.building.town_hall";

	@Test
	void linesKeepTheirTimeAndOrder() {
		WorkJournal journal = new WorkJournal();
		journal.cleared(100, "block.minecraft.cobweb");
		journal.placed(200, BRICKS);
		journal.stageDone(300, BuildStage.WALLS);
		assertEquals(List.of(new Entry(100, Kind.CLEARED, "block.minecraft.cobweb", 1),
				new Entry(200, Kind.PLACED, BRICKS, 1),
				new Entry(300, Kind.STAGE_DONE, BuildStage.WALLS.key(), 0)), journal.entries());
	}

	@Test
	void repeatedWorkOfOneKindAndSubjectReadsAsOneLine() {
		WorkJournal journal = new WorkJournal();
		journal.placed(100, BRICKS);
		journal.placed(150, BRICKS);
		journal.placed(150 + WorkJournal.MERGE_WINDOW, BRICKS);
		assertEquals(List.of(new Entry(150 + WorkJournal.MERGE_WINDOW, Kind.PLACED, BRICKS, 3)), journal.entries());

		journal.placed(151 + 2 * WorkJournal.MERGE_WINDOW, BRICKS);
		assertEquals(2, journal.entries().size(), "a line past the window starts a new one");
		journal.placed(152 + 2 * WorkJournal.MERGE_WINDOW, GLASS);
		journal.placed(153 + 2 * WorkJournal.MERGE_WINDOW, BRICKS);
		assertEquals(4, journal.entries().size(), "another subject in between breaks the run");
		journal.cleared(154 + 2 * WorkJournal.MERGE_WINDOW, BRICKS);
		assertEquals(5, journal.entries().size(), "another kind breaks the run");
	}

	@Test
	void aLineFromBeforeTheNewestIsNotMergedBackwards() {
		WorkJournal journal = new WorkJournal();
		journal.placed(500, BRICKS);
		journal.placed(400, BRICKS);
		assertEquals(2, journal.entries().size());
	}

	@Test
	void closedStagesNeverMerge() {
		WorkJournal journal = new WorkJournal();
		journal.stageDone(10, BuildStage.ROOF);
		journal.stageDone(11, BuildStage.ROOF);
		assertEquals(2, journal.entries().size());
	}

	@Test
	void aMissingLineDoesNotRepeatWhileNothingElseHappened() {
		WorkJournal journal = new WorkJournal();
		journal.missing(100, GLASS, 6);
		journal.missing(200, "block.minecraft.bricks", 2);
		journal.missing(300, GLASS, 6);
		assertEquals(2, journal.entries().size(), "the same shortage asked again adds nothing");
		journal.missing(400, GLASS, 4);
		assertEquals(new Entry(100, Kind.MISSING, GLASS, 4), journal.entries().getFirst(),
				"a changed count updates the line, keeping its time");

		journal.placed(500, BRICKS);
		journal.missing(600, GLASS, 4);
		assertEquals(4, journal.entries().size(), "after other work the shortage is news again");
	}

	@Test
	void theJournalKeepsOnlyTheNewestLines() {
		WorkJournal journal = new WorkJournal();
		for (int i = 0; i < WorkJournal.CAPACITY + 10; i++) {
			journal.add(new Entry(i, Kind.STAGE_DONE, "s" + i, 0));
		}
		assertEquals(WorkJournal.CAPACITY, journal.entries().size());
		assertEquals("s10", journal.entries().getFirst().subject());
		assertEquals("s" + (WorkJournal.CAPACITY + 9), journal.entries().getLast().subject());

		List<Entry> saved = new ArrayList<>();
		for (int i = 0; i < WorkJournal.CAPACITY + 3; i++) {
			saved.add(new Entry(i, Kind.PLACED, "b" + i, 1));
		}
		WorkJournal read = new WorkJournal(saved);
		assertEquals(WorkJournal.CAPACITY, read.entries().size());
		assertEquals("b3", read.entries().getFirst().subject());
	}

	@Test
	void everyChangeCallsTheHookAndNothingElseDoes() {
		WorkJournal journal = new WorkJournal();
		AtomicInteger calls = new AtomicInteger();
		journal.onChange(calls::incrementAndGet);
		journal.placed(1, BRICKS);
		journal.placed(2, BRICKS);
		journal.missing(3, GLASS, 1);
		journal.missing(4, GLASS, 1);
		assertEquals(3, calls.get(), "an unchanged missing line is not a change");
		journal.missing(5, GLASS, 2);
		assertEquals(4, calls.get());
		assertTrue(!journal.isEmpty() && new WorkJournal().isEmpty());
	}

	@Test
	void aLineReadsItsCountAndSubject() {
		assertEquals(ConstructionRow.Text.of(Kind.PLACED.key(), "12*" + BRICKS),
				new Entry(0, Kind.PLACED, BRICKS, 12).text());
		assertEquals(ConstructionRow.Text.of(Kind.STAGE_DONE.key(), BuildStage.ROOF.key()),
				new Entry(0, Kind.STAGE_DONE, BuildStage.ROOF.key(), 0).text());
		assertEquals(0, new Entry(0, Kind.PLACED, BRICKS, -4).count());
	}

	@Test
	void theClockStartsAtSixOnDayOne() {
		assertEquals(ConstructionRow.Text.of(WorkJournal.TIME_KEY, "1", "06:00"), WorkJournal.timeText(0));
		assertEquals(ConstructionRow.Text.of(WorkJournal.TIME_KEY, "1", "18:30"), WorkJournal.timeText(12500));
		assertEquals(ConstructionRow.Text.of(WorkJournal.TIME_KEY, "1", "00:00"), WorkJournal.timeText(18000));
		assertEquals(ConstructionRow.Text.of(WorkJournal.TIME_KEY, "4", "06:00"), WorkJournal.timeText(72000));
		assertEquals(ConstructionRow.Text.of(WorkJournal.TIME_KEY, "1", "06:00"), WorkJournal.timeText(-50));
		assertEquals(WorkJournal.timeText(12500), new Entry(12500, Kind.PLACED, BRICKS, 1).timeText());
	}

	@Test
	void everyKindHasItsKeyAndComesBackFromItsName() {
		for (Kind kind : Kind.values()) {
			assertEquals("settlement.alaindustrial.journal." + kind.serializedName(), kind.key());
			assertEquals(Optional.of(kind), Kind.byName(kind.serializedName()));
		}
		assertEquals(Optional.empty(), Kind.byName("repaired"));
	}

	@Test
	void aRestoredLineIsNeverMergedAndKeepsItsName() {
		WorkJournal journal = new WorkJournal();
		journal.restored(100, HALL);
		journal.restored(110, HALL);
		assertEquals(List.of(new Entry(100, Kind.RESTORED, HALL, 0), new Entry(110, Kind.RESTORED, HALL, 0)),
				journal.entries());
		assertEquals(ConstructionRow.Text.of(Kind.RESTORED.key(), HALL), journal.entries().getFirst().text());
	}

	/**
	 * The hall's chat lines (owner's round 3 of stage 6, {@code HallBuildNews}) and the restored journal line are
	 * worded in every locale the mod ships, with the same placeholders as in English.
	 */
	@Test
	void theHallNewsLinesAreWordedInEveryLocale() throws IOException {
		Map<String, String> placeholders = Map.of(
				"settlement.alaindustrial.chat.header", "%1$s %2$s",
				"settlement.alaindustrial.chat.stage_done", "%1$s",
				"settlement.alaindustrial.chat.next.needs", "%1$s",
				"settlement.alaindustrial.chat.next.ready", "%1$s",
				"settlement.alaindustrial.chat.hall_restored", "",
				"settlement.alaindustrial.chat.hall_restored.reward", "%s",
				Kind.RESTORED.key(), "%s");
		Path dir = Path.of("src/main/resources/assets/alaindustrial/lang");
		List<Path> files;
		try (Stream<Path> list = Files.list(dir)) {
			files = list.filter(f -> f.toString().endsWith(".json")).sorted().toList();
		}
		assertTrue(files.size() > 1, "the mod ships its locale files");
		Pattern holder = Pattern.compile("%(\\d+\\$)?s");
		for (Path file : files) {
			String lang = Files.readString(file, StandardCharsets.UTF_8);
			for (Map.Entry<String, String> key : placeholders.entrySet()) {
				Pattern entry = Pattern.compile("\"" + Pattern.quote(key.getKey()) + "\": \"([^\"]*)\"");
				Matcher line = entry.matcher(lang);
				assertTrue(line.find(), file.getFileName() + " has no " + key.getKey());
				List<String> found = new ArrayList<>();
				Matcher m = holder.matcher(line.group(1));
				while (m.find()) {
					found.add(m.group());
				}
				found.sort(String::compareTo);
				assertEquals(key.getValue(), String.join(" ", found), file.getFileName() + " " + key.getKey());
				assertTrue(!line.group(1).contains("—") && !line.group(1).contains("–"),
						file.getFileName() + " " + key.getKey() + " has a dash");
			}
		}
	}
}
