package dev.alaindustrial.core.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The reactor controller's event log (MOD-622): the last {@link #CAPACITY} things that happened to the reactor, and
 * how far each player who reads it has read.
 *
 * <p><b>Transitions, not levels.</b> The controller appends an entry when a state changes — a room sealing, a meltdown
 * starting — never while a state merely holds. Deciding what is a change is the controller's job; this class only
 * keeps order, drops the oldest past the capacity and folds a burst of the same player's throttle clicks into one line.
 *
 * <p><b>Sequence numbers, not time, order the log.</b> Several events can share one game tick, so every entry carries
 * a number that only grows, and a reader's progress is the newest number it has seen. The number survives a save, so
 * a reload neither reuses one nor shows an old alarm as new.
 *
 * <p><b>A reader per player.</b> Two players watching the same controller read it separately: one looking at the log
 * does not clear the other's badge. Progress is kept for the {@link #MAX_READERS} players who read most recently.
 *
 * <p>Minecraft-free so the ordering, the eviction and the merge are L1-tested; the controller persists and syncs it.
 */
public final class ReactorLog {

	/** Entries kept: the owner's choice (2026-09-14). */
	public static final int CAPACITY = 100;

	/** Players whose progress is kept; the one who read least recently is forgotten first. */
	public static final int MAX_READERS = 32;

	/** Longest actor name kept — a Minecraft profile name is at most sixteen characters. */
	public static final int ACTOR_MAX = 16;

	/** How loudly an entry reads. */
	public enum Severity {
		INFO, WARNING, ALARM;

		/** Whether the «Alarms» filter shows it: everything that needs a player's attention. */
		public boolean needsAttention() {
			return this != INFO;
		}
	}

	/**
	 * What happened.
	 *
	 * <p><b>Persisted by {@link #id()}, sent by ordinal.</b> The id is what a saved world holds, so it never changes
	 * once shipped; the ordinal is only a wire format between one server and its clients, so new kinds are appended.
	 * An id or ordinal this build does not know reads as {@link #UNKNOWN} — a neutral line, never an invented alarm.
	 *
	 * @param mergesBursts whether a new entry of this kind by the same actor soon after the last one replaces it
	 */
	public enum Kind {
		UNKNOWN(Severity.INFO, false),
		/** Arguments: interior size x, y, z. */
		ROOM_SEALED(Severity.INFO, false),
		ROOM_UNSEALED(Severity.WARNING, false),
		/** Arguments: rods, depth in percent. */
		REACTION_STARTED(Severity.INFO, false),
		/** The redstone signal went away. */
		REACTION_SCRAMMED(Severity.INFO, false),
		/** The control rods were pulled all the way out. */
		RODS_WITHDRAWN(Severity.INFO, false),
		/** The last fuelled rod burnt out. */
		OUT_OF_FUEL(Severity.WARNING, false),
		/** Stopped for another reason, or for one a reload forgot. */
		REACTION_STOPPED(Severity.INFO, false),
		/** Arguments: the percentage the siren sounded at; then 1 when that scale was a bare pile's instability, 0 for heat. */
		OVERHEAT(Severity.WARNING, false),
		/** Arguments: heat in percent. */
		MELTDOWN_STARTED(Severity.ALARM, false),
		/** Arguments: blocks melted during the episode. */
		MELTDOWN_ENDED(Severity.WARNING, false),
		COUNTDOWN_ARMED(Severity.ALARM, false),
		COUNTDOWN_CANCELLED(Severity.WARNING, false),
		/** The countdown ran out with the core still critical. Written before any blast, which may take the log with it. */
		COUNTDOWN_EXPIRED(Severity.ALARM, false),
		/** Arguments: fuelled racks found. */
		BARE_ENTERED(Severity.WARNING, false),
		BARE_LEFT(Severity.INFO, false),
		/** Arguments: depth before, depth after, in percent. Actor: the player. */
		DEPTH_CHANGED(Severity.INFO, true);

		private final Severity severity;
		private final boolean mergesBursts;

		Kind(Severity severity, boolean mergesBursts) {
			this.severity = severity;
			this.mergesBursts = mergesBursts;
		}

		public Severity severity() {
			return severity;
		}

		public boolean mergesBursts() {
			return mergesBursts;
		}

		/** The saved name. Never change one that has shipped: a world written by an older build holds it. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			return UNKNOWN;
		}

		public static Kind byOrdinal(int ordinal) {
			Kind[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : UNKNOWN;
		}
	}

	/**
	 * One line of the log.
	 *
	 * @param seq   position in the log, growing by one per entry and never reused
	 * @param time  game time the event happened at, for "how long ago"
	 * @param a     first argument, meaning per {@link Kind}
	 * @param actor the player who caused it, or empty
	 */
	public record Entry(int seq, long time, Kind kind, int a, int b, int c, String actor) {

		public Entry {
			kind = kind == null ? Kind.UNKNOWN : kind;
			actor = actor == null ? "" : actor.length() > ACTOR_MAX ? actor.substring(0, ACTOR_MAX) : actor;
		}
	}

	/** Oldest first. */
	private final List<Entry> entries = new ArrayList<>();
	private int nextSeq = 1;
	/** Reader progress, in order of last update: the eldest is the first forgotten. */
	private final LinkedHashMap<UUID, Integer> seen = new LinkedHashMap<>();

	/** Appends an event and returns its entry, dropping the oldest past {@link #CAPACITY}. */
	public Entry append(long time, Kind kind, int a, int b, int c, String actor) {
		Entry entry = new Entry(nextSeq++, time, kind, a, b, c, actor);
		entries.add(entry);
		while (entries.size() > CAPACITY) {
			entries.remove(0);
		}
		return entry;
	}

	/**
	 * Appends an event, or folds it into the newest entry when that one is the same {@link Kind#mergesBursts() kind}
	 * by the same actor no more than {@code window} ticks earlier: the folded entry keeps the first argument of the
	 * burst (where it started), takes the rest from the new event, and moves to the head with a new sequence number.
	 * Holding an arrow key on the throttle is one decision, not twenty lines — and a burst that ends where it started
	 * changed nothing, so it leaves no line at all.
	 *
	 * @return the entry written, or {@code null} when the burst folded back to nothing
	 */
	public Entry appendOrMerge(long time, Kind kind, int a, int b, int c, String actor, long window) {
		Entry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
		Entry candidate = new Entry(0, time, kind, a, b, c, actor);
		if (last != null && candidate.kind().mergesBursts() && last.kind() == candidate.kind()
				&& last.actor().equals(candidate.actor()) && time >= last.time() && time - last.time() <= window) {
			entries.remove(entries.size() - 1);
			return last.a() == b ? null : append(time, kind, last.a(), b, c, actor);
		}
		return append(time, kind, a, b, c, actor);
	}

	/**
	 * How long ago something happened, in the largest whole unit — under a second reads as "now". Counted in game
	 * ticks, so time the game spent paused, or the chunk spent unloaded on a stopped server, does not count.
	 */
	public record Age(Unit unit, long value) {

		public enum Unit {
			NOW, SECONDS, MINUTES, HOURS, DAYS
		}

		private static final long SECOND = 20;
		private static final long MINUTE = 60 * SECOND;
		private static final long HOUR = 60 * MINUTE;
		private static final long DAY = 24 * HOUR;

		public static Age of(long ticks) {
			long t = Math.max(0, ticks);
			if (t < SECOND) {
				return new Age(Unit.NOW, 0);
			}
			if (t < MINUTE) {
				return new Age(Unit.SECONDS, t / SECOND);
			}
			if (t < HOUR) {
				return new Age(Unit.MINUTES, t / MINUTE);
			}
			if (t < DAY) {
				return new Age(Unit.HOURS, t / HOUR);
			}
			return new Age(Unit.DAYS, t / DAY);
		}
	}

	/** The entries, oldest first. */
	public List<Entry> entries() {
		return Collections.unmodifiableList(new ArrayList<>(entries));
	}

	/** The number the next entry will get; saved so a reload never reuses one. */
	public int nextSeq() {
		return nextSeq;
	}

	/** The newest entry's number, or 0 while the log is empty. */
	public int newestSeq() {
		return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).seq();
	}

	/** The newest entry this player has seen, or 0. */
	public int seenBy(UUID reader) {
		return seen.getOrDefault(reader, 0);
	}

	/**
	 * Records that a player has seen the log up to {@code seq}. Progress only moves forward — a late or repeated
	 * acknowledgement cannot bring an alarm back — and the reader becomes the most recent one.
	 *
	 * @return whether anything changed
	 */
	public boolean markSeen(UUID reader, int seq) {
		int previous = seenBy(reader);
		if (seq <= previous) {
			return false;
		}
		seen.remove(reader);
		seen.put(reader, seq);
		while (seen.size() > MAX_READERS) {
			seen.remove(seen.keySet().iterator().next());
		}
		return true;
	}

	/** Reader progress, least recently updated first — the order {@link #restore} expects back. */
	public Map<UUID, Integer> readers() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(seen));
	}

	/**
	 * Replaces the log with a saved one. Entries are put back in sequence order and cut to the newest
	 * {@link #CAPACITY}; the next number is never behind a saved entry, even if the saved counter was lost.
	 */
	public void restore(List<Entry> saved, int savedNextSeq, Map<UUID, Integer> savedReaders) {
		entries.clear();
		List<Entry> sorted = new ArrayList<>(saved);
		sorted.sort(Comparator.comparingInt(Entry::seq));
		int from = Math.max(0, sorted.size() - CAPACITY);
		entries.addAll(sorted.subList(from, sorted.size()));
		nextSeq = Math.max(Math.max(1, savedNextSeq), newestSeq() + 1);
		seen.clear();
		savedReaders.forEach((reader, seq) -> {
			if (reader != null && seq != null && seq > 0) {
				seen.put(reader, seq);
			}
		});
		while (seen.size() > MAX_READERS) {
			seen.remove(seen.keySet().iterator().next());
		}
	}

	/** Whether any alarm in {@code entries} is newer than {@code seenSeq} — the red badge on the «Log» tab. */
	public static boolean unseenAlarm(List<Entry> entries, int seenSeq) {
		for (Entry entry : entries) {
			if (entry.seq() > seenSeq && entry.kind().severity() == Severity.ALARM) {
				return true;
			}
		}
		return false;
	}
}
