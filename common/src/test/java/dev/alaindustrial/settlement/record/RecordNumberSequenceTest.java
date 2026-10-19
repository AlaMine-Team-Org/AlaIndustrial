package dev.alaindustrial.settlement.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.alaindustrial.core.guide.ArchiveRecord;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link RecordNumberSequence} (MOD-827): the order of issue is a permutation of the whole
 * space, keyed by the world seed and nothing else, and it does not drift between builds.
 *
 * <p>The whole-space properties walk all 21 000 positions per try (about 1.5 ms); 100 tries keep the class
 * well under a second while still meeting a hundred unrelated seeds.
 *
 * @implements MOD-827-SEQ
 */
class RecordNumberSequenceTest {

	private static List<RecordNumber> walk(RecordNumberSequence sequence, int count) {
		List<RecordNumber> out = new ArrayList<>(count);
		for (int position = 0; position < count; position++) {
			out.add(RecordNumber.ofIndex(sequence.indexAt(position)));
		}
		return out;
	}

	/** Every seed's walk meets every number of the space exactly once, each in the archive's shape. */
	@Property(tries = 100)
	void walkIsAPermutationOfTheWholeSpace(@ForAll long seed) {
		List<RecordNumber> all = walk(new RecordNumberSequence(seed), ArchiveRecord.SPACE);
		RecordNumberChecks.assertDistinctAndWellFormed(all);
		assertEquals(ArchiveRecord.SPACE, all.size());
	}

	/** The same seed gives the same walk, built twice. */
	@Property(tries = 100)
	void walkFollowsFromTheSeed(@ForAll long seed) {
		assertEquals(walk(new RecordNumberSequence(seed), 64), walk(new RecordNumberSequence(seed), 64));
	}

	/** Two different seeds do not share their first twenty numbers. */
	@Property
	void differentSeedsWalkDifferently(@ForAll long seed, @ForAll long other) {
		if (seed == other) {
			return;
		}
		assertNotEquals(walk(new RecordNumberSequence(seed), 20), walk(new RecordNumberSequence(other), 20));
	}

	/**
	 * Golden values: the walk is a save-format contract (see the class comment of {@link RecordNumberSequence}).
	 * A failure here means the mixing changed — every existing world would continue on another order.
	 */
	@Test
	void walkIsPinned() {
		assertEquals("[N-019, C-622, K-166, U-151, W-893, K-192]", walk(new RecordNumberSequence(0L), 6).toString());
		assertEquals("[E-629, E-773, W-301, N-481, T-446, T-069]",
				walk(new RecordNumberSequence(12345L), 6).toString());
		assertEquals("H-722", RecordNumber.ofIndex(new RecordNumberSequence(0L).indexAt(20_999)).toString());
	}

	@Test
	void positionOutsideTheSpaceIsRefused() {
		RecordNumberSequence sequence = new RecordNumberSequence(1L);
		assertThrows(IllegalArgumentException.class, () -> sequence.indexAt(-1));
		assertThrows(IllegalArgumentException.class, () -> sequence.indexAt(ArchiveRecord.SPACE));
	}
}
