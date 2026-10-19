package dev.alaindustrial.settlement.record;

import dev.alaindustrial.core.guide.ArchiveRecord;

/**
 * The order in which a world hands out record numbers (MOD-827): a permutation of the whole space of
 * {@link ArchiveRecord#SPACE} numbers, keyed by the world seed. The {@code position}-th issue of a world
 * lands on {@link #indexAt indexAt(position)}; since the map is a bijection, walking the positions forward
 * never meets a number twice and, after {@code SPACE} steps, has met every one.
 *
 * <p><b>How.</b> A balanced four-round Feistel network on 16 bits (two 8-bit halves) is a bijection of
 * 0..65535 whatever its round function; the space is the first 21 000 of those values, and a result outside
 * it is fed back in ("cycle walking") until it lands inside. The walk always ends — the orbit of a start
 * inside the space returns to it — and walking keeps the map a bijection of the space. On average a
 * position costs 65536 / 21000 ≈ 3.1 passes. No table, so nothing to build per world.
 *
 * <p><b>The constants are a save-format contract.</b> A world saves only how far it has walked
 * ({@link RecordNumberBook}); change a constant and the walk continues from the same position on another
 * permutation. The ledger still never issues a number it holds as live or closed, so nothing is
 * duplicated — but the sequence stops being the one this seed promised. {@code RecordNumberSequenceTest}
 * pins golden values so such a change fails the build.
 *
 * <p>Package-private and Minecraft-free: only {@link RecordNumberBook} walks it ({@code RecordNumberRules}).
 */
final class RecordNumberSequence {

	private static final int HALF_BITS = 8;
	private static final int HALF_MASK = (1 << HALF_BITS) - 1;
	private static final int ROUNDS = 4;

	/** Mixed into the seed first, so the key never walks in step with the player's record. ASCII {@code ALA-SHF1}. */
	private static final long DOMAIN = 0x414C412D53484631L;

	private final long[] roundKeys = new long[ROUNDS];

	RecordNumberSequence(long worldSeed) {
		long key = mix(worldSeed ^ DOMAIN);
		for (int round = 0; round < ROUNDS; round++) {
			roundKeys[round] = key;
			key = mix(key ^ (round + 1));
		}
	}

	/** The index of the space the {@code position}-th issue of this world lands on. */
	int indexAt(int position) {
		if (position < 0 || position >= ArchiveRecord.SPACE) {
			throw new IllegalArgumentException("sequence position " + position + " is outside 0.."
					+ (ArchiveRecord.SPACE - 1));
		}
		int value = position;
		do {
			value = feistel(value);
		} while (value >= ArchiveRecord.SPACE);
		return value;
	}

	/** One pass of the network: a bijection of 0..65535. Each round maps (L, R) to (R, L xor F(R)). */
	private int feistel(int value) {
		int left = value >>> HALF_BITS;
		int right = value & HALF_MASK;
		for (long key : roundKeys) {
			int mixed = left ^ (int) (mix(key ^ right) & HALF_MASK);
			left = right;
			right = mixed;
		}
		return left << HALF_BITS | right;
	}

	/** SplitMix64's output function (Stafford's variant 13), the same mixer {@code ArchiveRecord} uses. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
