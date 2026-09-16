package dev.alaindustrial.core.teleport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The teleporter remote's own log (MOD-631): the last {@link #CAPACITY} things that happened to jumps made with it, and
 * how far its owner has read.
 *
 * <p><b>The log lives in the remote.</b> It is a value inside the item's data component, so it leaves with the item —
 * dropped, stored, lost on death — and a new remote starts empty. Every change returns a new instance: a component value
 * must never be edited in place.
 *
 * <p><b>Sequence numbers order it,</b> as in the reactor's log: several events can share a tick, and "read up to" is a
 * number that only grows. A refusal repeated for the same station within {@link #MERGE_WINDOW} replaces the previous
 * one, so a burst of clicks is one line rather than fifty.
 *
 * <p>Minecraft-free, so the ordering, the eviction and the merge are L1-tested; the item's codecs live elsewhere.
 */
public record RemoteLog(int nextSeq, int seenSeq, List<Entry> entries) {

	/** Entries kept: the owner's choice (2026-09-15). */
	public static final int CAPACITY = 50;

	/** A same refusal for the same station within this many ticks replaces the last line: ten seconds. */
	public static final long MERGE_WINDOW = 200;

	/** Longest station name kept — the wire limit of a remote's point names. */
	public static final int NAME_MAX = 32;

	public static final RemoteLog EMPTY = new RemoteLog(1, 0, List.of());

	/** The colour of an entry's lamp. */
	public enum Tone {
		GOOD, WARN, ALARM, IDLE
	}

	/** What the log's filters split entries into. */
	public enum Group {
		JUMP, REFUSAL, OTHER
	}

	/**
	 * What happened.
	 *
	 * <p><b>Saved by {@link #id()}, sent by ordinal.</b> The id is what a saved remote holds, so it never changes once
	 * shipped; the ordinal only travels between one server and its clients, so new kinds are appended. An id or ordinal
	 * this build does not know reads as {@link #UNKNOWN}.
	 */
	public enum Kind {
		UNKNOWN(Tone.IDLE, Group.OTHER),
		/** Arguments: EU spent. */
		JUMPED(Tone.GOOD, Group.JUMP),
		/** Station: the one that paid. Arguments: landing X, landing Z, EU spent. */
		RANDOM_JUMPED(Tone.GOOD, Group.JUMP),
		REFUSED_NO_POWER(Tone.ALARM, Group.REFUSAL),
		REFUSED_NO_ACCESS(Tone.ALARM, Group.REFUSAL),
		REFUSED_NO_STATION(Tone.ALARM, Group.REFUSAL),
		REFUSED_NOT_FORMED(Tone.WARN, Group.REFUSAL),
		REFUSED_NO_CHIP(Tone.WARN, Group.REFUSAL),
		/** Arguments: seconds left. */
		REFUSED_COOLDOWN(Tone.WARN, Group.REFUSAL),
		REFUSED_CROSS_DIM(Tone.WARN, Group.REFUSAL),
		REFUSED_WRONG_DIMENSION(Tone.WARN, Group.REFUSAL),
		REFUSED_NO_SAFE_SPOT(Tone.WARN, Group.REFUSAL),
		REFUSED_MOUNTED(Tone.WARN, Group.REFUSAL),
		/** The player's own doing, not the server's refusal: grey, and no badge. */
		CANCELLED_MOVED(Tone.IDLE, Group.OTHER),
		CANCELLED_HURT(Tone.IDLE, Group.OTHER),
		/** Arguments: stations bound now, the most a remote holds. */
		BOUND(Tone.IDLE, Group.OTHER),
		/** Station: the old name. Renamed to: the new one. */
		RENAMED(Tone.IDLE, Group.OTHER),
		DELETED(Tone.IDLE, Group.OTHER);

		private static final Kind[] VALUES = values();

		private final Tone tone;
		private final Group group;

		Kind(Tone tone, Group group) {
			this.tone = tone;
			this.group = group;
		}

		public Tone tone() {
			return tone;
		}

		public Group group() {
			return group;
		}

		/** Whether it lights the badge on the «Log» tab until read. */
		public boolean isRefusal() {
			return group == Group.REFUSAL;
		}

		/** The saved name. Never change one that has shipped: a remote written by an older build holds it. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind byId(String id) {
			for (Kind kind : VALUES) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			return UNKNOWN;
		}

		public static Kind byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : UNKNOWN;
		}
	}

	/**
	 * A station as the log names it: the player's name for it, or, while it has none, the number its default name
	 * carries. Kept as the two parts so the default name is shown in the reader's language, not the server's.
	 */
	public record Station(String name, int number) {

		public static final Station NONE = new Station("", 0);

		public Station {
			name = name == null ? "" : name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
		}
	}

	/**
	 * One line of the log.
	 *
	 * @param seq       position in the log, growing by one per entry and never reused
	 * @param time      game time it happened at, for "how long ago"
	 * @param random    whether it was about a random jump — named in the line's tooltip
	 * @param station   the station it was about, or {@link Station#NONE}
	 * @param renamedTo the new name of a renamed station, else {@link Station#NONE}
	 */
	public record Entry(int seq, long time, Kind kind, boolean random, Station station, Station renamedTo, int a, int b,
			int c) {

		public Entry {
			kind = kind == null ? Kind.UNKNOWN : kind;
			station = station == null ? Station.NONE : station;
			renamedTo = renamedTo == null ? Station.NONE : renamedTo;
		}
	}

	/**
	 * Entries are kept oldest first and cut to the newest {@link #CAPACITY}; the next number is never behind a saved
	 * entry, and the read mark never ahead of the newest one, whatever a saved remote says.
	 */
	public RemoteLog {
		List<Entry> sorted = new ArrayList<>(entries == null ? List.of() : entries);
		sorted.sort(Comparator.comparingInt(Entry::seq));
		entries = List.copyOf(sorted.subList(Math.max(0, sorted.size() - CAPACITY), sorted.size()));
		int newest = entries.isEmpty() ? 0 : entries.get(entries.size() - 1).seq();
		nextSeq = Math.max(Math.max(1, nextSeq), newest + 1);
		seenSeq = Math.max(0, Math.min(seenSeq, nextSeq - 1));
	}

	/** The newest entry's number, or 0 while the log is empty. */
	public int newestSeq() {
		return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).seq();
	}

	/**
	 * Adds an event, dropping the oldest past {@link #CAPACITY}. A refusal that repeats the newest entry — same kind,
	 * same station, same kind of jump, no more than {@link #MERGE_WINDOW} later — replaces it, taking a new number so it
	 * reads as unread again.
	 */
	public RemoteLog append(long time, Kind kind, boolean random, Station station, Station renamedTo, int a, int b, int c) {
		Entry entry = new Entry(nextSeq, time, kind, random, station, renamedTo, a, b, c);
		List<Entry> next = new ArrayList<>(entries);
		Entry last = next.isEmpty() ? null : next.get(next.size() - 1);
		if (last != null && entry.kind().isRefusal() && last.kind() == entry.kind() && last.random() == entry.random()
				&& last.station().equals(entry.station()) && time >= last.time() && time - last.time() <= MERGE_WINDOW) {
			next.remove(next.size() - 1);
		}
		next.add(entry);
		return new RemoteLog(nextSeq + 1, seenSeq, next);
	}

	/** Records that the owner has read up to {@code seq}. The mark only moves forward and never past the newest entry. */
	public RemoteLog markSeen(int seq) {
		int clamped = Math.min(seq, newestSeq());
		return clamped <= seenSeq ? this : new RemoteLog(nextSeq, clamped, entries);
	}

	/** Whether a refusal newer than {@code seen} waits — the badge on the «Log» tab. */
	public boolean hasRefusalAfter(int seen) {
		for (Entry entry : entries) {
			if (entry.seq() > seen && entry.kind().isRefusal()) {
				return true;
			}
		}
		return false;
	}

	/** The entries of a group, or all of them for {@code null}, newest first. */
	public List<Entry> newestFirst(Group group) {
		List<Entry> out = new ArrayList<>();
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry entry = entries.get(i);
			if (group == null || entry.kind().group() == group) {
				out.add(entry);
			}
		}
		return out;
	}
}
