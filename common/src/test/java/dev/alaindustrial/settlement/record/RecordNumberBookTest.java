package dev.alaindustrial.settlement.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.guide.ArchiveRecord;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link RecordNumberBook} (MOD-827): the issue rule of R3a, R3b, R10a and R35 of MOD-824 —
 * unique numbers from the seed, a closed number never again, no known player's record, no known lab's digits —
 * and what happens when the space runs out.
 *
 * <p>The property that spends the whole space issues 21 000 numbers per try, about 10 ms under the JaCoCo agent
 * of the L1 run: 50 tries, half a second.
 *
 * @implements MOD-827-ISSUE
 */
class RecordNumberBookTest {

	private static List<RecordNumber> issue(RecordNumberBook book, int count) {
		List<RecordNumber> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			out.add(book.issue().orElseThrow(() -> new AssertionError("the space ran out early")));
		}
		return out;
	}

	private static List<RecordNumber> issueAll(RecordNumberBook book) {
		List<RecordNumber> out = new ArrayList<>();
		for (Optional<RecordNumber> next = book.issue(); next.isPresent(); next = book.issue()) {
			out.add(next.get());
		}
		return out;
	}

	/** The book as its saved fields would read it back — what the ledger's codec hands to the constructor. */
	private static RecordNumberBook readBack(RecordNumberBook book) {
		return new RecordNumberBook(book.worldSeed(), book.next(), book.live(), book.closed(), book.playerCodes(),
				book.labNumbers());
	}

	/** Acceptance criterion of MOD-827: a thousand issued numbers, all different, all {@code <letter>-<NNN>}. */
	@Test
	void thousandIssuedNumbersAreDistinctAndWellFormed() {
		RecordNumberBook book = new RecordNumberBook(2026L);
		List<RecordNumber> numbers = issue(book, 1000);
		RecordNumberChecks.assertDistinctAndWellFormed(numbers);
		numbers.forEach(number -> assertEquals(RecordNumber.Status.LIVE, book.statusOf(number), number.toString()));
	}

	/**
	 * The uniqueness check every test here relies on can fail: a thousand issued numbers with one planted twice
	 * are refused, naming it.
	 */
	@Test
	void aPlantedDuplicateFailsTheUniquenessCheck() {
		List<RecordNumber> numbers = issue(new RecordNumberBook(2026L), 1000);
		RecordNumber planted = numbers.get(499);
		numbers.add(planted);
		AssertionError error = assertThrows(AssertionError.class,
				() -> RecordNumberChecks.assertDistinctAndWellFormed(numbers));
		assertTrue(error.getMessage().contains(planted.toString()), error.getMessage());
	}

	/** The same seed and the same order of issue give the same numbers — here with deaths in between. */
	@Test
	void sameSeedAndSameOrderGiveTheSameNumbers() {
		RecordNumberBook first = new RecordNumberBook(-42L);
		RecordNumberBook second = new RecordNumberBook(-42L);
		List<RecordNumber> a = issue(first, 30);
		List<RecordNumber> b = issue(second, 30);
		assertEquals(a, b);
		first.close(a.get(7));
		second.close(b.get(7));
		assertEquals(issue(first, 30), issue(second, 30));
	}

	@Test
	void differentSeedsGiveDifferentNumbers() {
		assertNotEquals(issue(new RecordNumberBook(1L), 20), issue(new RecordNumberBook(2L), 20));
	}

	/** A dead worker's number stays a tombstone through the whole rest of the space (R10a). */
	@Test
	void closedNumberIsNeverIssuedAgain() {
		RecordNumberBook book = new RecordNumberBook(7L);
		List<RecordNumber> issued = issue(book, 10);
		List<RecordNumber> dead = List.of(issued.get(0), issued.get(4), issued.get(9));
		dead.forEach(number -> assertEquals(RecordNumber.Status.LIVE, book.close(number)));
		dead.forEach(number -> assertEquals(RecordNumber.Status.CLOSED, book.close(number), "second death"));
		List<RecordNumber> rest = issueAll(book);
		for (RecordNumber number : dead) {
			assertFalse(rest.contains(number), number + " was issued again after its holder died");
			assertEquals(RecordNumber.Status.CLOSED, book.statusOf(number));
		}
	}

	/**
	 * A number closed before the walk reaches it — a worker that outlived a lost save — is skipped when its
	 * position comes. Without the tombstone check the walk would hand it out: this is the test that goes red.
	 */
	@Test
	void numberClosedBeforeTheWalkReachesItIsSkipped() {
		RecordNumber upcoming = new RecordNumberBook(11L).issue().orElseThrow();
		RecordNumberBook book = new RecordNumberBook(11L);
		assertEquals(RecordNumber.Status.NOT_ISSUED, book.close(upcoming));
		assertFalse(issueAll(book).contains(upcoming), upcoming + " was issued although it is closed");
	}

	@Provide
	Arbitrary<Set<Integer>> playerCodes() {
		return Arbitraries.integers().between(0, ArchiveRecord.SPACE - 1).set().ofMaxSize(60);
	}

	@Provide
	Arbitrary<Set<Integer>> labNumbers() {
		return Arbitraries.integers().between(0, RecordNumberBook.LAB_NUMBERS - 1).set().ofMaxSize(30);
	}

	/**
	 * The whole space, spent: every issued number is distinct and well-formed, none is a known player's record,
	 * none has a known lab's digits (R35), exactly the numbers that are not blocked get issued — and after the
	 * last one the book refuses for good.
	 */
	@Property(tries = 50)
	void spendingTheSpaceSkipsPlayersAndLabsAndThenStops(@ForAll long seed,
			@ForAll("playerCodes") Set<Integer> players, @ForAll("labNumbers") Set<Integer> labs) {
		RecordNumberBook book = new RecordNumberBook(seed);
		players.forEach(index -> assertTrue(book.notePlayerCode(RecordNumber.ofIndex(index))));
		labs.forEach(lab -> assertTrue(book.noteLabNumber(lab)));

		List<RecordNumber> issued = issueAll(book);

		RecordNumberChecks.assertDistinctAndWellFormed(issued);
		BitSet blocked = new BitSet(ArchiveRecord.SPACE);
		players.forEach(blocked::set);
		for (int index = 0; index < ArchiveRecord.SPACE; index++) {
			if (labs.contains(index % RecordNumberBook.LAB_NUMBERS)) {
				blocked.set(index);
			}
		}
		for (RecordNumber number : issued) {
			assertFalse(players.contains(number.index()), number + " is a known player's record");
			assertFalse(labs.contains(number.digits()), number + " has the digits of a known lab");
		}
		assertEquals(ArchiveRecord.SPACE - blocked.cardinality(), issued.size());
		assertEquals(Optional.empty(), book.issue());
		assertEquals(Optional.empty(), book.issue());
	}

	/** The issue rule R35 by example: with lab 304 known, no {@code <letter>-304} is ever handed out. */
	@Test
	void labThreeHundredFourBlocksEveryLetterOfIt() {
		RecordNumberBook book = new RecordNumberBook(304L);
		book.noteLabNumber(304);
		List<RecordNumber> issued = issueAll(book);
		assertTrue(issued.stream().noneMatch(number -> number.digits() == 304));
		assertEquals(ArchiveRecord.SPACE - ArchiveRecord.ALPHABET.length(), issued.size());
	}

	@Provide
	Arbitrary<List<Long>> scripts() {
		return Arbitraries.longs().list().ofMinSize(1).ofMaxSize(200);
	}

	/**
	 * Any order of issues, deaths, players and labs, played on two books of the same seed, gives the same
	 * answers — and a book read back from its saved fields halfway through goes on exactly as the one that was
	 * never saved. Every number handed out along the way was free at that moment.
	 */
	@Property(tries = 300)
	void sameScriptGivesTheSameAnswersAcrossASave(@ForAll long seed, @ForAll("scripts") List<Long> script,
			@ForAll long cutSeed) {
		int cut = Math.floorMod(cutSeed, script.size() + 1);
		RecordNumberBook kept = new RecordNumberBook(seed);
		RecordNumberBook twin = new RecordNumberBook(seed);
		List<String> keptTrace = new ArrayList<>();
		List<String> twinTrace = new ArrayList<>();
		List<RecordNumber> keptIssued = new ArrayList<>();
		List<RecordNumber> twinIssued = new ArrayList<>();
		play(kept, script.subList(0, cut), keptTrace, keptIssued);
		play(twin, script.subList(0, cut), twinTrace, twinIssued);
		RecordNumberBook reloaded = readBack(twin);
		play(kept, script.subList(cut, script.size()), keptTrace, keptIssued);
		play(reloaded, script.subList(cut, script.size()), twinTrace, twinIssued);
		assertEquals(keptTrace, twinTrace);
		RecordNumberChecks.assertDistinctAndWellFormed(keptIssued);
	}

	/** Plays one step per entry of {@code script}, writing what the book answered to {@code trace}. */
	private static void play(RecordNumberBook book, List<Long> script, List<String> trace, List<RecordNumber> issued) {
		for (long step : script) {
			int arg = (int) ((step >>> 3) & Integer.MAX_VALUE);
			switch ((int) Math.floorMod(step, 5L)) {
				case 0 -> {
					Optional<RecordNumber> number = book.issue();
					number.ifPresent(n -> {
						assertFalse(book.isKnownPlayerCode(n), n + " issued over a known player's record");
						assertFalse(book.isKnownLabNumber(n.digits()), n + " issued with a known lab's digits");
						issued.add(n);
					});
					trace.add("issue " + number);
				}
				case 1 -> {
					if (!issued.isEmpty()) {
						RecordNumber dead = issued.get(arg % issued.size());
						trace.add("close " + dead + " " + book.close(dead));
					}
				}
				case 2 -> trace.add("player " + book.notePlayerCode(RecordNumber.ofIndex(arg % ArchiveRecord.SPACE)));
				case 3 -> trace.add("lab " + book.noteLabNumber(arg % RecordNumberBook.LAB_NUMBERS));
				default -> trace.add("close-unissued " + book.close(RecordNumber.ofIndex(arg % ArchiveRecord.SPACE)));
			}
			trace.add("next " + book.next());
		}
	}

	/** A save that lists a number both as live and as closed reads back closed: a tombstone is never undone. */
	@Test
	void savedTombstoneWinsOverLive() {
		RecordNumber number = RecordNumber.parse("W-117").orElseThrow();
		RecordNumberBook book = new RecordNumberBook(1L, 5, List.of(number), List.of(number), List.of(), List.of());
		assertEquals(RecordNumber.Status.CLOSED, book.statusOf(number));
		assertEquals(List.of(), book.live());
	}

	@Test
	void savedFieldsOutOfRangeAreClampedOrDropped() {
		RecordNumberBook ahead = new RecordNumberBook(1L, ArchiveRecord.SPACE + 50, List.of(), List.of(), List.of(),
				List.of(-1, 1000, 304));
		assertEquals(ArchiveRecord.SPACE, ahead.next());
		assertEquals(List.of(304), ahead.labNumbers());
		assertEquals(Optional.empty(), ahead.issue());
		assertEquals(0, new RecordNumberBook(1L, -3, List.of(), List.of(), List.of(), List.of()).next());
	}

	@Test
	void notingIsIdempotentAndReportsTheFirstTimeOnly() {
		RecordNumberBook book = new RecordNumberBook(3L);
		RecordNumber code = RecordNumber.parse("K-482").orElseThrow();
		assertTrue(book.notePlayerCode(code));
		assertFalse(book.notePlayerCode(code));
		assertTrue(book.isKnownPlayerCode(code));
		assertTrue(book.noteLabNumber(482));
		assertFalse(book.noteLabNumber(482));
		assertTrue(book.isKnownLabNumber(482));
		assertThrows(IllegalArgumentException.class, () -> book.noteLabNumber(1000));
		assertThrows(IllegalArgumentException.class, () -> book.isKnownLabNumber(-1));
	}

	/** {@code issuedWithDigits} finds the live and the dead numbers that a newly found lab would clash with. */
	@Test
	void issuedWithDigitsFindsLiveAndClosed() {
		RecordNumberBook book = new RecordNumberBook(9L);
		List<RecordNumber> issued = issue(book, 200);
		RecordNumber alive = issued.get(3);
		RecordNumber dead = issued.get(5);
		book.close(dead);
		assertTrue(book.issuedWithDigits(alive.digits()).contains(alive));
		assertTrue(book.issuedWithDigits(dead.digits()).contains(dead));
		Set<Integer> usedDigits = new HashSet<>();
		issued.forEach(number -> usedDigits.add(number.digits()));
		int unused = 0;
		while (usedDigits.contains(unused)) {
			unused++;
		}
		assertEquals(List.of(), book.issuedWithDigits(unused));
	}
}
