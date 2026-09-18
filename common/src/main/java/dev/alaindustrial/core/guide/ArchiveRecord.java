package dev.alaindustrial.core.guide;

import java.util.UUID;

/**
 * A player's personal archive record (MOD-513, {@code docs/LORE.md}): one letter, a hyphen and three
 * digits — {@code <letter>-<NNN>} — worked out from the world seed and the player's profile id.
 *
 * <p><b>Minecraft-free on purpose.</b> The server bridge ({@link ArchiveRecordSync}) hands in a seed
 * and a {@link UUID}; everything that defines the record lives here, where the L1 suite can pin it
 * without a game on the classpath.
 *
 * <p><b>Derived, never stored.</b> The record is a pure function of its two inputs, so it cannot
 * depend on who joined first, needs no saved state, and is the same in every session. The price is
 * stated rather than hidden: 21 000 values cannot give every player of a large server a distinct
 * record, and nothing here claims they do. What to do about a collision is a separate policy.
 *
 * <p><b>The mixing steps are a save-format contract.</b> Change a constant and every player in every
 * existing world silently gets another record. {@code ArchiveRecordTest} pins golden values so such a
 * change fails the build instead of shipping.
 */
public final class ArchiveRecord {

	/** The letters a record may start with, in order: A to X without I, O, V, Y and Z. */
	public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUWX";

	/** How many distinct records exist: one per letter per number from 000 to 999. */
	public static final int SPACE = ALPHABET.length() * 1000;

	/** Length of every record: letter, hyphen, three digits. */
	public static final int LENGTH = 5;

	/**
	 * Mixed in before anything else, so a later value derived from the same seed — a lab's historic
	 * number, say — starts from a different state instead of repeating this one. ASCII {@code ALA-REC1}.
	 */
	private static final long DOMAIN = 0x414C412D52454331L;

	/** The same separation for a lab's historic number, so it never walks in step with a record. ASCII {@code ALA-LAB1}. */
	private static final long LAB_DOMAIN = 0x414C412D4C414231L;

	private ArchiveRecord() {
	}

	/**
	 * The three digits on the plaque of the abandoned lab whose hatch is at ({@code x}, {@code y},
	 * {@code z}) in the world generated from {@code worldSeed}: 0 to 999, written with leading zeros.
	 *
	 * <p>Like a record, derived rather than stored — every player who finds that lab reads the same
	 * number, and a lab regenerated from the same seed carries it again. The letter before it is lost
	 * by design, so this is only the number. Pinned by golden values for the same reason as {@link #of}.
	 */
	public static int labNumber(long worldSeed, int x, int y, int z) {
		long h = mix(worldSeed ^ LAB_DOMAIN);
		h = mix(h ^ x);
		h = mix(h ^ y);
		h = mix(h ^ z);
		return (int) Long.remainderUnsigned(h, 1000);
	}

	/** The record of the player with {@code profileId} in the world generated from {@code worldSeed}. */
	public static String of(long worldSeed, UUID profileId) {
		long h = mix(worldSeed ^ DOMAIN);
		h = mix(h ^ profileId.getMostSignificantBits());
		h = mix(h ^ profileId.getLeastSignificantBits());
		return format((int) Long.remainderUnsigned(h, SPACE));
	}

	/**
	 * The record at position {@code index} of the {@link #SPACE}: the thousands pick the letter, the
	 * rest is the number.
	 *
	 * <p>Digits are written by hand rather than through {@code String.format}: that call formats with
	 * the default locale, and under a locale whose numbering system is not Latin the record would come
	 * out in another script.
	 */
	static String format(int index) {
		if (index < 0 || index >= SPACE) {
			throw new IllegalArgumentException("record index " + index + " is outside 0.." + (SPACE - 1));
		}
		int number = index % 1000;
		return new String(new char[] {
				ALPHABET.charAt(index / 1000),
				'-',
				(char) ('0' + number / 100),
				(char) ('0' + number / 10 % 10),
				(char) ('0' + number % 10),
		});
	}

	/** True only for a string in the record's exact shape: an allowed letter, a hyphen, three ASCII digits. */
	public static boolean isValid(String record) {
		if (record == null || record.length() != LENGTH || record.charAt(1) != '-'
				|| ALPHABET.indexOf(record.charAt(0)) < 0) {
			return false;
		}
		for (int i = 2; i < LENGTH; i++) {
			char c = record.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}

	/** SplitMix64's output function (Stafford's variant 13): every input bit reaches every output bit. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
