package dev.alaindustrial.settlement.record;

import dev.alaindustrial.core.guide.ArchiveRecord;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * What a world knows about its record numbers, and the one place a new one is handed out (MOD-827) — the
 * Minecraft-free state behind {@link RecordNumberLedger}, which saves it and is the only way the game
 * reaches it.
 *
 * <p><b>What is kept.</b> How far the world has walked its {@link RecordNumberSequence}; the numbers held by
 * living shift workers; the closed ones, whose holders died (R10a); the player records known to be in use in
 * this world; and the three-digit numbers of the labs known to stand in it (R35). Every set is a
 * {@link BitSet} over the space, so the saved order is the order of the space and does not depend on who came
 * first.
 *
 * <p><b>The issue rule.</b> {@link #issue()} walks the sequence forward from where it stopped and hands out
 * the first number that is not live, not closed, not a known player's record and does not share its three
 * digits with a known lab (R3a, R3b, R35). Each position is consumed whether its number was handed out or
 * skipped, so a skipped number is never offered again either. The walk only moves forward, so with an
 * intact save a live or closed number could not come round twice anyway; checking them as well is what keeps
 * the rule true if the sequence's constants ever change, or a number was closed before the walk reached it.
 *
 * <p><b>A late coincidence</b> (R41 of MOD-824, MOD-834). A player's record and a town lab's digits become known only
 * when the player joins or the town generates, so a living worker may already hold what turns up later — and a
 * town plaque may engrave it (R40). Such a number is marked {@linkplain #isDue due}: its worker gets a new number
 * from {@link #renumber} when no player can see it, and the old one is closed for good. A book read back from a
 * save marks again every living number that clashes with a known player or lab, so a clash logged by a build
 * before MOD-834 is caught up on too.
 *
 * <p><b>When the space runs out.</b> After the last position {@link #issue()} returns empty, for good:
 * every number has been handed out or skipped, and none of them may be handed out again. The ledger logs it
 * once; what a capsule does with "no number left" is its own decision (MOD-833).
 */
final class RecordNumberBook {

	/** How many three-digit lab numbers exist: 000 to 999. */
	static final int LAB_NUMBERS = 1000;

	/** Mixing domain of a former resident's number ("ALA-FRMR"), apart from the player's and the lab's. */
	private static final long FORMER_RESIDENT_DOMAIN = 0x414C412D46524D52L;

	private final long worldSeed;
	private final RecordNumberSequence sequence;
	/** The next position of {@link #sequence} to look at; {@code SPACE} when the walk is over. */
	private int next;
	private final BitSet live = new BitSet(ArchiveRecord.SPACE);
	private final BitSet closed = new BitSet(ArchiveRecord.SPACE);
	private final BitSet playerCodes = new BitSet(ArchiveRecord.SPACE);
	private final BitSet labNumbers = new BitSet(LAB_NUMBERS);
	/** Living numbers marked for a new one (R41); always a subset of {@link #live}. */
	private final BitSet due = new BitSet(ArchiveRecord.SPACE);

	/** A fresh world: nothing issued, nothing known. */
	RecordNumberBook(long worldSeed) {
		this.worldSeed = worldSeed;
		this.sequence = new RecordNumberSequence(worldSeed);
	}

	/**
	 * A world read back from its save. {@code next} is clamped into the walk; a number listed both as live
	 * and closed is closed — a tombstone is never undone; a lab number outside 0..999 is dropped.
	 */
	RecordNumberBook(long worldSeed, int next, Collection<RecordNumber> live, Collection<RecordNumber> closed,
			Collection<RecordNumber> playerCodes, Collection<Integer> labNumbers) {
		this(worldSeed, next, live, closed, playerCodes, labNumbers, List.of());
	}

	/**
	 * A world read back from its save, with the numbers that were marked for a new one. Beside the saved marks, every
	 * living number that is a known player's record or shares its digits with a known lab is marked; a mark on a
	 * number that is not living is dropped.
	 */
	RecordNumberBook(long worldSeed, int next, Collection<RecordNumber> live, Collection<RecordNumber> closed,
			Collection<RecordNumber> playerCodes, Collection<Integer> labNumbers, Collection<RecordNumber> due) {
		this(worldSeed);
		this.next = Math.clamp(next, 0, ArchiveRecord.SPACE);
		closed.forEach(number -> this.closed.set(number.index()));
		live.forEach(number -> this.live.set(number.index()));
		this.live.andNot(this.closed);
		playerCodes.forEach(number -> this.playerCodes.set(number.index()));
		labNumbers.stream().filter(lab -> lab >= 0 && lab < LAB_NUMBERS).forEach(this.labNumbers::set);
		due.forEach(number -> this.due.set(number.index()));
		this.live.stream().filter(this::clashes).forEach(this.due::set);
		this.due.and(this.live);
	}

	/** Whether the number at {@code index} is a known player's record or has a known lab's digits. */
	private boolean clashes(int index) {
		return playerCodes.get(index) || labNumbers.get(index % LAB_NUMBERS);
	}

	/** The next number nobody in this world has had, or empty when the space is spent. See the class comment. */
	Optional<RecordNumber> issue() {
		while (next < ArchiveRecord.SPACE) {
			int index = sequence.indexAt(next);
			next++;
			if (!isBlocked(index)) {
				live.set(index);
				return Optional.of(RecordNumber.ofIndex(index));
			}
		}
		return Optional.empty();
	}

	private boolean isBlocked(int index) {
		return live.get(index) || closed.get(index) || playerCodes.get(index)
				|| labNumbers.get(index % LAB_NUMBERS);
	}

	/**
	 * Closes {@code number} for good: its holder died (R10a). Also closes a number this book never issued —
	 * a worker that outlived a lost save still must not see its number handed out again.
	 *
	 * @return the status the number had before
	 */
	RecordNumber.Status close(RecordNumber number) {
		RecordNumber.Status before = statusOf(number);
		live.clear(number.index());
		due.clear(number.index());
		closed.set(number.index());
		return before;
	}

	/**
	 * Whether the living {@code number} is marked for a new one (R41): a player with this record, a town lab with
	 * these digits or a town plaque with this number turned up after it was handed out.
	 */
	boolean isDue(RecordNumber number) {
		return due.get(number.index());
	}

	/** The numbers marked for a new one, in the order of the space. */
	List<RecordNumber> due() {
		return numbers(due);
	}

	/**
	 * The new number of the worker whose number {@code old} is marked for one (R41): the next number of the issue
	 * rule; {@code old} is closed for good and is no longer marked. Empty — nothing changes — when {@code old} is
	 * not marked, or when the space is spent (it stays marked).
	 */
	Optional<RecordNumber> renumber(RecordNumber old) {
		if (!isDue(old)) {
			return Optional.empty();
		}
		Optional<RecordNumber> fresh = issue();
		fresh.ifPresent(number -> close(old));
		return fresh;
	}

	/**
	 * The number of the former resident whose plaque stands at ({@code x}, {@code y}, {@code z}) in a world with
	 * this seed (R40 of MOD-824): the same plaque in the same world always gives the same number, any letter of
	 * the space. A pure function, so world generation can engrave it on its own thread.
	 */
	static RecordNumber formerResident(long worldSeed, int x, int y, int z) {
		long hash = mix(worldSeed ^ FORMER_RESIDENT_DOMAIN);
		hash = mix(hash + x);
		hash = mix(hash + y);
		hash = mix(hash + z);
		return RecordNumber.ofIndex((int) Math.floorMod(hash, (long) ArchiveRecord.SPACE));
	}

	/**
	 * Closes the number of a former resident of a generated town (R40): a tombstone of the previous era, never
	 * handed out. A number a living worker holds stays live and is marked for a new one — a town generated after the
	 * worker was given it is a late coincidence (R41, MOD-834), and closing it here would make the worker look like
	 * a copy; the renumbering closes it.
	 *
	 * @return the status the number had before; {@code LIVE} means it is marked, not closed
	 */
	RecordNumber.Status closeFormerResident(RecordNumber number) {
		RecordNumber.Status before = statusOf(number);
		if (before == RecordNumber.Status.LIVE) {
			due.set(number.index());
		} else {
			closed.set(number.index());
		}
		return before;
	}

	RecordNumber.Status statusOf(RecordNumber number) {
		if (closed.get(number.index())) {
			return RecordNumber.Status.CLOSED;
		}
		return live.get(number.index()) ? RecordNumber.Status.LIVE : RecordNumber.Status.NOT_ISSUED;
	}

	/** Records that a player with this record plays in this world. @return true if it was not known yet */
	boolean notePlayerCode(RecordNumber code) {
		boolean known = playerCodes.get(code.index());
		playerCodes.set(code.index());
		if (live.get(code.index())) {
			due.set(code.index());
		}
		return !known;
	}

	boolean isKnownPlayerCode(RecordNumber code) {
		return playerCodes.get(code.index());
	}

	/** Records that a lab with these three digits stands in this world. @return true if it was not known yet */
	boolean noteLabNumber(int labNumber) {
		requireLabNumber(labNumber);
		boolean known = labNumbers.get(labNumber);
		labNumbers.set(labNumber);
		for (int index = labNumber; index < ArchiveRecord.SPACE; index += LAB_NUMBERS) {
			if (live.get(index)) {
				due.set(index);
			}
		}
		return !known;
	}

	boolean isKnownLabNumber(int labNumber) {
		requireLabNumber(labNumber);
		return labNumbers.get(labNumber);
	}

	/** The issued numbers, live or closed, whose three digits are {@code labNumber}, in the order of the space. */
	List<RecordNumber> issuedWithDigits(int labNumber) {
		requireLabNumber(labNumber);
		List<RecordNumber> out = new ArrayList<>();
		for (int index = labNumber; index < ArchiveRecord.SPACE; index += LAB_NUMBERS) {
			if (live.get(index) || closed.get(index)) {
				out.add(RecordNumber.ofIndex(index));
			}
		}
		return out;
	}

	long worldSeed() {
		return worldSeed;
	}

	int next() {
		return next;
	}

	List<RecordNumber> live() {
		return numbers(live);
	}

	List<RecordNumber> closed() {
		return numbers(closed);
	}

	List<RecordNumber> playerCodes() {
		return numbers(playerCodes);
	}

	List<Integer> labNumbers() {
		return labNumbers.stream().boxed().toList();
	}

	private static List<RecordNumber> numbers(BitSet set) {
		return set.stream().mapToObj(RecordNumber::ofIndex).toList();
	}

	/** SplitMix64's finaliser: spreads a seed and coordinates over the whole space. */
	private static long mix(long value) {
		long z = value + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private static void requireLabNumber(int labNumber) {
		if (labNumber < 0 || labNumber >= LAB_NUMBERS) {
			throw new IllegalArgumentException("lab number " + labNumber + " is outside 0.." + (LAB_NUMBERS - 1));
		}
	}
}
