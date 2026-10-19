package dev.alaindustrial.settlement.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.guide.ArchiveRecord;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the former residents of generated towns (R40 of MOD-824, MOD-830): the number engraved on a
 * town's house plaque follows from the seed and the plaque's position, it is closed in the ledger as a tombstone
 * and never handed out, and it never takes a living worker's number away.
 *
 * @implements MOD-830-PLAQUE
 */
class RecordNumberFormerResidentTest {

	private static final long SEED = 2026L;

	@Test
	void formerResidentIsAFunctionOfSeedAndPosition() {
		RecordNumber number = RecordNumberBook.formerResident(SEED, 120, 64, -340);
		assertEquals(number, RecordNumberBook.formerResident(SEED, 120, 64, -340));
		assertTrue(ArchiveRecord.isValid(number.toString()), number.toString());
		assertNotEquals(number, RecordNumberBook.formerResident(SEED + 1, 120, 64, -340));
		assertNotEquals(number, RecordNumberBook.formerResident(SEED, 121, 64, -340));
		assertNotEquals(number, RecordNumberBook.formerResident(SEED, 120, 65, -340));
		assertNotEquals(number, RecordNumberBook.formerResident(SEED, 120, 64, -339));
	}

	/** Plaques a few blocks apart, as in one town, land all over the space — every letter, nearly no repeats. */
	@Test
	void nearbyPlaquesSpreadOverTheWholeSpace() {
		Set<RecordNumber> numbers = new HashSet<>();
		Set<Character> letters = new HashSet<>();
		for (int x = 0; x < 40; x++) {
			for (int z = 0; z < 25; z++) {
				RecordNumber number = RecordNumberBook.formerResident(SEED, x, 70, z);
				numbers.add(number);
				letters.add(number.letter());
			}
		}
		assertTrue(numbers.size() > 970, "only " + numbers.size() + " distinct numbers out of 1000 plaques");
		assertEquals(ArchiveRecord.ALPHABET.length(), letters.size());
	}

	/** A former resident's number is a tombstone: the walk skips it to the very end of the space. */
	@Test
	void closedFormerResidentIsNeverIssued() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber former = RecordNumberBook.formerResident(SEED, 7, 63, 9);
		assertEquals(RecordNumber.Status.NOT_ISSUED, book.closeFormerResident(former));
		assertEquals(RecordNumber.Status.CLOSED, book.statusOf(former));
		assertEquals(RecordNumber.Status.CLOSED, book.closeFormerResident(former), "second plaque of the same number");
		for (Optional<RecordNumber> next = book.issue(); next.isPresent(); next = book.issue()) {
			assertNotEquals(former, next.get(), "a former resident's number was handed out");
		}
	}

	/**
	 * A town generated after a worker got its number must not close it — the worker would look like a copy. This
	 * is the test that goes red if {@code closeFormerResident} closes unconditionally.
	 */
	@Test
	void livingWorkersNumberStaysLive() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber worker = book.issue().orElseThrow();
		assertEquals(RecordNumber.Status.LIVE, book.closeFormerResident(worker));
		assertEquals(RecordNumber.Status.LIVE, book.statusOf(worker));
		assertFalse(book.closed().contains(worker));
	}

	/** The tombstone survives a save: it is written with the closed numbers. */
	@Test
	void formerResidentTombstoneSurvivesAReadBack() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber former = RecordNumberBook.formerResident(SEED, -5, 80, 44);
		book.closeFormerResident(former);
		RecordNumberBook back = new RecordNumberBook(book.worldSeed(), book.next(), book.live(), book.closed(),
				book.playerCodes(), book.labNumbers());
		assertEquals(RecordNumber.Status.CLOSED, back.statusOf(former));
	}
}
