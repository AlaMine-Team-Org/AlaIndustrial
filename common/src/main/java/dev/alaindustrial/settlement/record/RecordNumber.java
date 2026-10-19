package dev.alaindustrial.settlement.record;

import dev.alaindustrial.core.guide.ArchiveRecord;
import java.util.Optional;

/**
 * A record number in the archive's shape — one letter, a hyphen and three digits, {@code <letter>-<NNN>}
 * (MOD-827; decisions R3a, R3b of MOD-824) — as a typed value.
 *
 * <p><b>The same space as the player's record.</b> The letters are {@link ArchiveRecord#ALPHABET}, the
 * {@link #index()} runs over {@link ArchiveRecord#SPACE} in the order {@code ArchiveRecord} formats it
 * (the thousands pick the letter, the rest is the three digits), so a shift worker's number and a
 * player's record can be compared as numbers. {@code RecordNumberFormatParityTest} holds the two
 * formats together over the whole space.
 *
 * <p><b>Who makes one.</b> A new number comes only from {@link RecordNumberLedger#issue()}: the minting
 * factory {@link #ofIndex} is package-private, and {@code RecordNumberRules} keeps it, and the
 * permutation behind it, inside the issuing service. {@link #parse} only reads back a number someone
 * already holds — a saved worker, a player's record; whether it is a live worker's number is the
 * ledger's question ({@link RecordNumberLedger#statusOf}).
 *
 * <p>Minecraft-free on purpose, so the L1 suite can pin it.
 */
public final class RecordNumber implements Comparable<RecordNumber> {

	/** What the ledger knows about a number. */
	public enum Status {
		/** Never handed out in this world. */
		NOT_ISSUED,
		/** Held by a living shift worker. */
		LIVE,
		/** Its holder died: the number is a tombstone and is never handed out again (R10a). */
		CLOSED
	}

	private final int index;

	private RecordNumber(int index) {
		this.index = index;
	}

	/** The number at {@code index} of the space. Package-private: only the issuing service mints numbers. */
	static RecordNumber ofIndex(int index) {
		if (index < 0 || index >= ArchiveRecord.SPACE) {
			throw new IllegalArgumentException("record index " + index + " is outside 0.." + (ArchiveRecord.SPACE - 1));
		}
		return new RecordNumber(index);
	}

	/**
	 * Reads back a number written as {@code <letter>-<NNN>}: the exact shape of
	 * {@link ArchiveRecord#isValid}, or empty. Never mints a new one — see the class comment.
	 */
	public static Optional<RecordNumber> parse(String text) {
		if (!ArchiveRecord.isValid(text)) {
			return Optional.empty();
		}
		int letter = ArchiveRecord.ALPHABET.indexOf(text.charAt(0));
		int digits = (text.charAt(2) - '0') * 100 + (text.charAt(3) - '0') * 10 + (text.charAt(4) - '0');
		return Optional.of(new RecordNumber(letter * 1000 + digits));
	}

	/** Position in the space, 0 to {@code ArchiveRecord.SPACE - 1}. */
	public int index() {
		return index;
	}

	/** The letter before the hyphen. */
	public char letter() {
		return ArchiveRecord.ALPHABET.charAt(index / 1000);
	}

	/** The three digits as a number, 0 to 999 — what a lab's plaque carries ({@code ArchiveRecord.labNumber}). */
	public int digits() {
		return index % 1000;
	}

	/**
	 * {@code <letter>-<NNN>}. Digits are written by hand, as in {@code ArchiveRecord}: {@code String.format}
	 * would use the default locale and could print them in another script.
	 */
	@Override
	public String toString() {
		int number = digits();
		return new String(new char[] {
				letter(),
				'-',
				(char) ('0' + number / 100),
				(char) ('0' + number / 10 % 10),
				(char) ('0' + number % 10),
		});
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RecordNumber that && that.index == index;
	}

	@Override
	public int hashCode() {
		return Integer.hashCode(index);
	}

	@Override
	public int compareTo(RecordNumber other) {
		return Integer.compare(index, other.index);
	}
}
