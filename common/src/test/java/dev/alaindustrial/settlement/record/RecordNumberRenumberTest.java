package dev.alaindustrial.settlement.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.guide.ArchiveRecord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the late coincidence of R41 (MOD-834) in {@link RecordNumberBook}: a player's record, a town lab's
 * digits or a town plaque that turns up after a living worker was given the same number marks that number; the
 * renumbering gives the next number of the issue rule and closes the old one for good; a mark survives a save, and a
 * save from before the mark existed is caught up on.
 *
 * @implements MOD-834-MARK
 */
class RecordNumberRenumberTest {

	private static final long SEED = 834L;

	@Test
	void aPlayerWhoJoinsLaterMarksTheLivingWorker() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber worker = book.issue().orElseThrow();
		RecordNumber other = book.issue().orElseThrow();
		assertFalse(book.isDue(worker));
		book.notePlayerCode(worker);
		assertTrue(book.isDue(worker));
		assertFalse(book.isDue(other));
		assertEquals(List.of(worker), book.due());
		assertEquals(RecordNumber.Status.LIVE, book.statusOf(worker), "the mark alone changes no number");
	}

	@Test
	void aTownLabMarksEveryLivingNumberWithItsDigits() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber worker = book.issue().orElseThrow();
		RecordNumber dead = sameDigitsIssued(book, worker.digits());
		book.close(dead);
		book.noteLabNumber(worker.digits());
		assertTrue(book.isDue(worker));
		assertFalse(book.isDue(dead), "a dead worker keeps its tombstone");
	}

	@Test
	void aTownPlaqueMarksTheLivingWorkerInsteadOfClosingIt() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber worker = book.issue().orElseThrow();
		assertEquals(RecordNumber.Status.LIVE, book.closeFormerResident(worker));
		assertTrue(book.isDue(worker));
	}

	@Test
	void aNumberNobodyHoldsIsNeverMarked() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber code = RecordNumber.parse("K-482").orElseThrow();
		book.notePlayerCode(code);
		book.noteLabNumber(117);
		assertEquals(List.of(), book.due());
	}

	@Test
	void renumberingGivesANewNumberAndClosesTheOldOne() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber old = book.issue().orElseThrow();
		book.notePlayerCode(old);
		RecordNumber fresh = book.renumber(old).orElseThrow();
		assertNotEquals(old, fresh);
		assertEquals(RecordNumber.Status.CLOSED, book.statusOf(old));
		assertEquals(RecordNumber.Status.LIVE, book.statusOf(fresh));
		assertFalse(book.isDue(old));
		assertFalse(book.isDue(fresh));
		assertEquals(Optional.empty(), book.renumber(old), "a second renumbering of the same number");
		for (Optional<RecordNumber> next = book.issue(); next.isPresent(); next = book.issue()) {
			assertNotEquals(old, next.get(), "the old number was handed out again");
			assertNotEquals(fresh, next.get(), "the new number was handed out twice");
		}
	}

	@Test
	void anUnmarkedNumberIsNotRenumbered() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber worker = book.issue().orElseThrow();
		int next = book.next();
		assertEquals(Optional.empty(), book.renumber(worker));
		assertEquals(RecordNumber.Status.LIVE, book.statusOf(worker));
		assertEquals(next, book.next(), "the walk moved for nothing");
	}

	@Test
	void aDeathClearsTheMark() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber worker = book.issue().orElseThrow();
		book.notePlayerCode(worker);
		book.close(worker);
		assertFalse(book.isDue(worker));
		assertEquals(Optional.empty(), book.renumber(worker));
	}

	@Test
	void whenTheSpaceIsSpentTheMarkStays() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber worker = book.issue().orElseThrow();
		while (book.issue().isPresent()) {
			// spend the space
		}
		book.notePlayerCode(worker);
		assertEquals(Optional.empty(), book.renumber(worker));
		assertTrue(book.isDue(worker));
		assertEquals(RecordNumber.Status.LIVE, book.statusOf(worker));
	}

	/** A plaque's mark is saved; a mark on a number no longer living is dropped on the way in. */
	@Test
	void aSavedMarkComesBack() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber marked = book.issue().orElseThrow();
		book.closeFormerResident(marked);
		RecordNumber gone = RecordNumber.parse("W-117").orElseThrow();
		RecordNumberBook back = new RecordNumberBook(book.worldSeed(), book.next(), book.live(), book.closed(),
				book.playerCodes(), book.labNumbers(), List.of(marked, gone));
		assertEquals(List.of(marked), back.due());
	}

	/**
	 * A save written before MOD-834 has no marks, only the clashes a build of then logged and let be: read back, every
	 * living number that is a known player's record or has a known lab's digits is marked.
	 */
	@Test
	void aSaveFromBeforeTheMarksIsCaughtUpOn() {
		RecordNumberBook book = new RecordNumberBook(SEED);
		RecordNumber byPlayer = book.issue().orElseThrow();
		RecordNumber byLab = book.issue().orElseThrow();
		RecordNumber untouched = book.issue().orElseThrow();
		RecordNumberBook old = new RecordNumberBook(book.worldSeed(), book.next(), book.live(), book.closed(),
				List.of(byPlayer), List.of(byLab.digits()));
		assertTrue(old.isDue(byPlayer));
		assertTrue(old.isDue(byLab));
		assertEquals(untouched.digits() == byLab.digits(), old.isDue(untouched));
	}

	/** An issued number with {@code digits}, issuing as many as it takes. */
	private static RecordNumber sameDigitsIssued(RecordNumberBook book, int digits) {
		for (int i = 0; i < ArchiveRecord.SPACE; i++) {
			RecordNumber number = book.issue().orElseThrow();
			if (number.digits() == digits) {
				return number;
			}
		}
		throw new AssertionError("no second number with the digits " + digits);
	}
}
