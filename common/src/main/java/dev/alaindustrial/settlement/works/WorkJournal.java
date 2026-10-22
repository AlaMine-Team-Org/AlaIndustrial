package dev.alaindustrial.settlement.works;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The work journal of a town's dispatcher (MOD-860, epic decision R88): what its builders cleared and placed, what
 * they lacked and which stages they closed, each line with its game time. A bounded ring — the newest
 * {@link #CAPACITY} lines; older ones fall off.
 *
 * <p><b>It reads, not counts.</b> A drone places one block a flight, so «placed Bricks» a hundred times would bury
 * everything else: a line of the same kind and subject as the newest line, within {@link #MERGE_WINDOW} ticks of it,
 * adds its count to that line instead. A «missing» line repeats nothing: while nothing else happened since the last
 * line about the same item, the asking builder only updates its count.
 *
 * <p>The town hall's restoration («Town hall restored») is not written here — it goes to the hall's own log
 * (MOD-857, R86).
 *
 * <p>Minecraft-free on purpose: the L1 lane checks the ring ({@code WorkJournalTest}); its save codec sits beside it in
 * {@code WorkJournalCodec}. Server thread only; every change calls the hook installed by {@link #onChange}.
 */
public final class WorkJournal {

	/** How many lines a journal keeps. */
	public static final int CAPACITY = 64;
	/** How close in game ticks two lines of one kind and subject must be to read as one (one real minute). */
	public static final long MERGE_WINDOW = 1200;
	/** The translation key of a line's time: «Day %1$s, %2$s». */
	public static final String TIME_KEY = "settlement.alaindustrial.journal.time";
	/** Game ticks in a day. */
	public static final long DAY = 24000;

	/** What a line is about. A save names it by {@link #serializedName()}, so a constant is never renamed. */
	public enum Kind {
		/** Debris taken away: the subject is the block's translation key. */
		CLEARED,
		/** A block put in place: the subject is the block's translation key. */
		PLACED,
		/** Items the buffer lacked: the subject is the item's translation key, the count how many. */
		MISSING,
		/** A stage closed: the subject is the stage's translation key ({@link BuildStage#key()}). */
		STAGE_DONE,
		/** A building restored (owner's round 3 of stage 6): the subject is the building's name key. */
		RESTORED,
		/** A building restored before is damaged, its repair queued (owner's round 4): the subject is its name key. */
		DAMAGED,
		/** A damaged building stands whole again (owner's round 4): the subject is its name key. */
		MENDED;

		/** Whether a line of this kind is an event of its own, never merged into the line before it. */
		boolean single() {
			return this == STAGE_DONE || this == RESTORED || this == DAMAGED || this == MENDED;
		}

		/** The name in saves and translation keys. */
		public String serializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The translation key of the line, one placeholder for its subject. */
		public String key() {
			return "settlement.alaindustrial.journal." + serializedName();
		}

		/** The kind saved as {@code name}, if this build knows it. */
		public static Optional<Kind> byName(String name) {
			for (Kind kind : values()) {
				if (kind.serializedName().equals(name)) {
					return Optional.of(kind);
				}
			}
			return Optional.empty();
		}
	}

	/**
	 * One line.
	 *
	 * @param gameTime the game time of the line (of its newest part, for a merged line)
	 * @param kind     what it is about
	 * @param subject  the translation key of its subject
	 * @param count    how many; 0 for a line that counts nothing (a closed stage)
	 */
	public record Entry(long gameTime, Kind kind, String subject, int count) {

		public Entry {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(subject, "subject");
			count = Math.max(0, count);
		}

		/** The line's words: «Placed: Bricks ×12», «Not enough: Glass ×6», «Stage closed: Walls». */
		public ConstructionRow.Text text() {
			String arg = count > 0 ? ConstructionRow.Text.counted(count, subject) : subject;
			return ConstructionRow.Text.of(kind.key(), arg);
		}

		/** The line's time: «Day 3, 08:30». */
		public ConstructionRow.Text timeText() {
			return WorkJournal.timeText(gameTime);
		}
	}

	private final List<Entry> entries = new ArrayList<>();
	private Runnable onChange = () -> { };

	/** An empty journal. */
	public WorkJournal() {
	}

	/** A journal read back from a save, oldest line first; only the newest {@link #CAPACITY} are kept. */
	public WorkJournal(Collection<Entry> saved) {
		List<Entry> list = new ArrayList<>(saved);
		entries.addAll(list.subList(Math.max(0, list.size() - CAPACITY), list.size()));
	}

	/** The owner's dirty hook: called after every change. */
	public void onChange(Runnable hook) {
		this.onChange = Objects.requireNonNull(hook, "hook");
	}

	/** The lines, oldest first. */
	public List<Entry> entries() {
		return Collections.unmodifiableList(entries);
	}

	/** Whether the journal holds no line. */
	public boolean isEmpty() {
		return entries.isEmpty();
	}

	/** Debris taken away at {@code gameTime}: one block whose translation key is {@code block}. */
	public void cleared(long gameTime, String block) {
		add(new Entry(gameTime, Kind.CLEARED, block, 1));
	}

	/** A block whose translation key is {@code block} put in place at {@code gameTime}. */
	public void placed(long gameTime, String block) {
		add(new Entry(gameTime, Kind.PLACED, block, 1));
	}

	/** The buffer lacked {@code count} of the item whose translation key is {@code item}, at {@code gameTime}. */
	public void missing(long gameTime, String item, int count) {
		add(new Entry(gameTime, Kind.MISSING, item, count));
	}

	/** Stage {@code stage} closed at {@code gameTime}. */
	public void stageDone(long gameTime, BuildStage stage) {
		add(new Entry(gameTime, Kind.STAGE_DONE, stage.key(), 0));
	}

	/** Building {@code name} (its name's translation key) was restored at {@code gameTime}. */
	public void restored(long gameTime, String name) {
		add(new Entry(gameTime, Kind.RESTORED, name, 0));
	}

	/** Adds {@code entry}, merged into an earlier line where the class's rules say so. */
	public void add(Entry entry) {
		if (entry.kind() == Kind.MISSING) {
			for (int i = entries.size() - 1; i >= 0 && entries.get(i).kind() == Kind.MISSING; i--) {
				Entry old = entries.get(i);
				if (old.subject().equals(entry.subject())) {
					if (old.count() != entry.count()) {
						entries.set(i, new Entry(old.gameTime(), Kind.MISSING, old.subject(), entry.count()));
						onChange.run();
					}
					return;
				}
			}
		} else if (!entry.kind().single() && !entries.isEmpty()) {
			Entry last = entries.getLast();
			if (last.kind() == entry.kind() && last.subject().equals(entry.subject())
					&& entry.gameTime() >= last.gameTime() && entry.gameTime() - last.gameTime() <= MERGE_WINDOW) {
				entries.set(entries.size() - 1, new Entry(entry.gameTime(), entry.kind(), entry.subject(),
						last.count() + entry.count()));
				onChange.run();
				return;
			}
		}
		entries.add(entry);
		while (entries.size() > CAPACITY) {
			entries.removeFirst();
		}
		onChange.run();
	}

	/** «Day 3, 08:30» for {@code gameTime}: the day counted from 1, the clock with tick 0 at 06:00. */
	public static ConstructionRow.Text timeText(long gameTime) {
		long time = Math.max(0, gameTime);
		long day = time / DAY + 1;
		long minutes = ((time + 6000) % DAY) * 60 / 1000;
		String clock = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
		return ConstructionRow.Text.of(TIME_KEY, String.valueOf(day), clock);
	}
}
