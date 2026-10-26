package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.TownWorksService;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The town's chronicle (MOD-886, plan 0.1.220 block B3): what changed the town, line by line, with the game day and the
 * worker — a worker arrived, moved into a house, died; a building restored; a house's inlet broken. It is the town
 * hall's log ({@link ShiftWorkerBook#log(int)}), kept in the towns' registry and not in a block: a broken hall loses
 * nothing. A repeat of the town's newest line is merged into it, and a town keeps {@link ChronicleRules#CAPACITY} lines
 * ({@link ChronicleRules}).
 *
 * <p><b>Writing an event.</b> {@link #record} is the one call for a new kind of line: a translation key of the
 * sentence, its arguments (each shown as a translation — a building's name key, a number as itself), and the worker it
 * is about, if any. The death, the re-registration, the restoration and the town's notes are written where they
 * happen, as before; this class words them all for the core's «Chronicle» tab ({@link #text}).
 */
public final class TownChronicle {

	private static final String KEY = "settlement.alaindustrial.chronicle.";
	/** «Arrived through the reception capsule» — a new worker, or one brought back. */
	public static final String ARRIVED = KEY + "arrived";
	/** «Moved into %s» — the house's name key. */
	public static final String SETTLED = KEY + "settled";
	/** «%s: the house inlet is broken, the house has no power» — the house's name key. */
	public static final String INLET_BROKEN = KEY + "inlet_broken";
	/** «Died» — a line about a death. */
	public static final String DIED = KEY + "died";
	/** «Did not reach its goal: %s» — a worker gave a place of its day up (MOD-889, R32); the place's name key. */
	public static final String STUCK = KEY + "stuck";
	/** «Day %1$s» — a line's day. */
	public static final String DAY = KEY + "day";
	/** «%1$s (×%2$s)» — a sentence that happened several times in a row. */
	public static final String REPEATED = KEY + "repeated";
	/** The chronicle tab when the town has no line yet. */
	public static final String EMPTY = KEY + "empty";
	/** Every key of the chronicle's own words. */
	public static final List<String> KEYS = List.of(ARRIVED, SETTLED, INLET_BROKEN, STUCK, DIED, DAY, REPEATED,
			EMPTY);

	/** Game ticks in a day. */
	public static final long TICKS_PER_DAY = 24000L;

	private TownChronicle() {
	}

	/**
	 * Writes an event of town {@code settlement} to its chronicle: the sentence {@code key} with {@code args}, about
	 * {@code worker} when one is given, at {@code gameTime}. A town of number 0 (a worker of no town) has no chronicle:
	 * nothing is written. Returns whether a new line was added — false for a repeat merged into the newest line, or
	 * for no town.
	 */
	public static boolean record(MinecraftServer server, int settlement, @Nullable ShiftWorkerRecord worker,
			String key, long gameTime, String... args) {
		if (settlement <= 0) {
			return false;
		}
		ShiftWorkerBook.Note note = new ShiftWorkerBook.Note(key, gameTime, List.of(args), 1);
		ShiftWorkerBook book = SettlementRegistry.get(server).workers();
		return book.addToLog(worker == null ? ShiftWorkerBook.LogLine.noted(settlement, note)
				: ShiftWorkerBook.LogLine.noted(settlement, worker.number(), worker.name(), note));
	}

	/** {@code worker} has just arrived through its town's reception capsule (or a command restored it). */
	public static void arrived(MinecraftServer server, ShiftWorkerRecord worker, long gameTime) {
		record(server, worker.settlement(), worker, ARRIVED, gameTime);
	}

	/** {@code worker} has just moved into the house whose name key is {@code house}. */
	public static void settled(MinecraftServer server, ShiftWorkerRecord worker, String house, long gameTime) {
		record(server, worker.settlement(), worker, SETTLED, gameTime, house);
	}

	/** The inlet of town {@code settlement}'s house whose name key is {@code house} is broken (R110, MOD-876). */
	public static void inletBroken(MinecraftServer server, int settlement, String house, long gameTime) {
		record(server, settlement, null, INLET_BROKEN, gameTime, house);
	}

	/** The day of a line written at {@code gameTime}: 1 on the world's first day, as the dispatcher counts. */
	public static long day(long gameTime) {
		return Math.max(0L, gameTime) / TICKS_PER_DAY + 1;
	}

	/** When {@code line} was written (its latest repeat, for a merged note). */
	public static long gameTime(ShiftWorkerBook.LogLine line) {
		if (line.death().isPresent()) {
			return line.death().get().gameTime();
		}
		if (line.renumbering().isPresent()) {
			return line.renumbering().get().gameTime();
		}
		if (line.building().isPresent()) {
			return line.building().get().gameTime();
		}
		return line.note().map(ShiftWorkerBook.Note::gameTime).orElse(0L);
	}

	/** How many times {@code line}'s event happened in a row: a merged note's repeats, 1 for any other line. */
	public static int repeats(ShiftWorkerBook.LogLine line) {
		return line.note().map(ShiftWorkerBook.Note::repeats).orElse(1);
	}

	/** The sentence of {@code line}, without its day and its worker — what the «Chronicle» tab shows after them. */
	public static ConstructionRow.Text text(ShiftWorkerBook.LogLine line) {
		if (line.death().isPresent()) {
			return ConstructionRow.Text.of(DIED);
		}
		if (line.renumbering().isPresent()) {
			return ConstructionRow.Text.of(ShiftWorkerRenumbering.LOG_LINE_KEY,
					line.number().map(RecordNumber::toString).orElse(""),
					line.renumbering().get().number().toString());
		}
		if (line.building().isPresent()) {
			String name = line.building().get().name();
			return ConstructionRow.Text.of(TownWorksService.LOG_LINE_KEY, name.isEmpty() ? ConstructionRows.UNNAMED
					: name);
		}
		Optional<ShiftWorkerBook.Note> note = line.note();
		return note.map(n -> new ConstructionRow.Text(n.key(), n.args(), false))
				.orElseGet(() -> ConstructionRow.Text.of(EMPTY));
	}

	/**
	 * The worker a line is about, as the tab shows it: its number at the time, and its name. A renumbering shows only
	 * the sentence (it names both numbers); a line about a building or a note of the town has none.
	 */
	public static Optional<String> worker(ShiftWorkerBook.LogLine line) {
		if (line.number().isEmpty() || line.renumbering().isPresent()) {
			return Optional.empty();
		}
		return Optional.of(line.name().isEmpty() ? line.number().get().toString()
				: line.number().get() + " " + line.name());
	}
}
