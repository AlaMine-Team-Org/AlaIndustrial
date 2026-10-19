package dev.alaindustrial.settlement.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.alaindustrial.core.guide.ArchiveRecord;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The two checks every record-number test makes (MOD-827): no number twice, and every number in the archive's
 * shape. One helper, so {@code RecordNumberBookTest#aPlantedDuplicateFailsTheUniquenessCheck} proves the check
 * the other tests rely on can fail.
 */
final class RecordNumberChecks {

	private RecordNumberChecks() {
	}

	/** Fails naming the first number that appears twice, or one that is not {@code <letter>-<NNN>}. */
	static void assertDistinctAndWellFormed(Collection<RecordNumber> numbers) {
		Map<RecordNumber, Integer> firstSeen = new HashMap<>();
		int position = 0;
		for (RecordNumber number : numbers) {
			Integer earlier = firstSeen.putIfAbsent(number, position);
			if (earlier != null) {
				fail("number " + number + " issued twice: at " + earlier + " and at " + position);
			}
			String text = number.toString();
			assertTrue(ArchiveRecord.isValid(text), "not in the record's shape: " + text);
			assertTrue(ArchiveRecord.ALPHABET.indexOf(text.charAt(0)) >= 0, "letter outside the alphabet: " + text);
			assertEquals(number.index(), ArchiveRecord.ALPHABET.indexOf(number.letter()) * 1000 + number.digits(),
					"letter and digits do not add up to the index of " + text);
			position++;
		}
	}
}
