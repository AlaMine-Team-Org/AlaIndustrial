package dev.alaindustrial.core.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link ArchiveRecord} (MOD-513): the shape of a record, the letters it may use, and
 * the fact that it is a function of the world seed and the profile id and of nothing else.
 *
 * @implements MOD-513-REC
 */
class ArchiveRecordTest {

	private static final UUID ZERO = new UUID(0L, 0L);
	private static final UUID ONES = new UUID(-1L, -1L);
	private static final UUID SAMPLE_A = UUID.fromString("12345678-9abc-4def-8123-456789abcdef");
	private static final UUID SAMPLE_B = UUID.fromString("d3b07384-d9a0-4c9a-8b1e-0f5e2c6a7b11");

	/** The owner's list, exactly: A to X without I, O, V, Y and Z. */
	@Test
	void alphabetIsTheApprovedTwentyOneLetters() {
		assertEquals("ABCDEFGHJKLMNPQRSTUWX", ArchiveRecord.ALPHABET);
		for (char banned : "IOVYZ".toCharArray()) {
			assertEquals(-1, ArchiveRecord.ALPHABET.indexOf(banned), "banned letter " + banned);
		}
		Set<Character> distinct = new HashSet<>();
		for (char c : ArchiveRecord.ALPHABET.toCharArray()) {
			assertTrue(c >= 'A' && c <= 'Z', "not an uppercase English letter: " + c);
			assertTrue(distinct.add(c), "duplicate letter " + c);
		}
		assertEquals(26 - 5, distinct.size());
		assertEquals(21_000, ArchiveRecord.SPACE);
	}

	@Test
	void formatWritesLetterHyphenAndThreeDigits() {
		assertEquals("A-000", ArchiveRecord.format(0));
		assertEquals("A-007", ArchiveRecord.format(7));
		assertEquals("A-070", ArchiveRecord.format(70));
		assertEquals("B-000", ArchiveRecord.format(1000));
		assertEquals("N-345", ArchiveRecord.format(12_345));
		assertEquals("X-999", ArchiveRecord.format(ArchiveRecord.SPACE - 1));
		assertThrows(IllegalArgumentException.class, () -> ArchiveRecord.format(-1));
		assertThrows(IllegalArgumentException.class, () -> ArchiveRecord.format(ArchiveRecord.SPACE));
	}

	/** Every index maps to a different valid record, so the whole space is reachable and nothing else is. */
	@Test
	void formatIsABijectionOntoValidRecords() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < ArchiveRecord.SPACE; i++) {
			String record = ArchiveRecord.format(i);
			assertTrue(ArchiveRecord.isValid(record), record);
			assertTrue(seen.add(record), "index " + i + " repeats " + record);
		}
		assertEquals(ArchiveRecord.SPACE, seen.size());
	}

	@Test
	void isValidAcceptsOnlyTheExactShape() {
		assertTrue(ArchiveRecord.isValid("A-000"));
		assertTrue(ArchiveRecord.isValid("K-482"));
		assertTrue(ArchiveRecord.isValid("X-999"));
		for (String bad : new String[] {
				null, "", "K-48", "K-4820", "KK-482", "K482", "K_482", "k-482", " K-482", "K-482 ",
				"I-100", "O-100", "V-100", "Y-100", "Z-100", "K-48a", "K--48",
				"K-٤٨٢", // Arabic-Indic digits
				"K-४८٢", // Devanagari digits
				"Κ-482", // Greek capital kappa, looks like K
		}) {
			assertFalse(ArchiveRecord.isValid(bad), "accepted " + bad);
		}
	}

	/**
	 * Golden values. They pin the mixing steps: if one of these changes, every player in every existing
	 * world has just been given another record, which is a decision for the owner, not a refactor.
	 */
	@Test
	void goldenRecordsDoNotMove() {
		assertEquals("B-824", ArchiveRecord.of(0L, ZERO));
		assertEquals("P-226", ArchiveRecord.of(1L, SAMPLE_A));
		assertEquals("L-733", ArchiveRecord.of(-1L, ONES));
		assertEquals("F-070", ArchiveRecord.of(8_675_309L, SAMPLE_A));
		assertEquals("F-015", ArchiveRecord.of(Long.MIN_VALUE, ONES));
		assertEquals("N-990", ArchiveRecord.of(4_213_787_135_487_651_912L, SAMPLE_B));
		assertEquals("N-077", ArchiveRecord.of(4_213_787_135_487_651_912L, ONES));
	}

	/**
	 * A lab's plaque number is pinned the same way: change the mixing and every lab in every existing
	 * world would read a different number on its next visit if it were ever regenerated.
	 */
	@Test
	void goldenLabNumbersDoNotMove() {
		assertEquals(LAB_GOLDEN[0], ArchiveRecord.labNumber(0L, 0, 0, 0));
		assertEquals(LAB_GOLDEN[1], ArchiveRecord.labNumber(1L, 100, 64, -200));
		assertEquals(LAB_GOLDEN[2], ArchiveRecord.labNumber(-1L, -30_000_000, -64, 30_000_000));
		assertEquals(LAB_GOLDEN[3], ArchiveRecord.labNumber(4_213_787_135_487_651_912L, 8_032, 91, 8_021));
	}

	/** Each coordinate and the seed matter on their own, and the number always fits three digits. */
	@Test
	void labNumberUsesEveryInputAndStaysInRange() {
		Random random = new Random(2_513L);
		int changed = 0;
		for (int i = 0; i < 1_000; i++) {
			long seed = random.nextLong();
			int x = random.nextInt(60_000_000) - 30_000_000;
			int y = random.nextInt(384) - 64;
			int z = random.nextInt(60_000_000) - 30_000_000;
			int base = ArchiveRecord.labNumber(seed, x, y, z);
			assertTrue(base >= 0 && base <= 999, "out of range: " + base);
			changed += base != ArchiveRecord.labNumber(seed + 1, x, y, z) ? 1 : 0;
			changed += base != ArchiveRecord.labNumber(seed, x + 1, y, z) ? 1 : 0;
			changed += base != ArchiveRecord.labNumber(seed, x, y + 1, z) ? 1 : 0;
			changed += base != ArchiveRecord.labNumber(seed, x, y, z + 1) ? 1 : 0;
		}
		// One in a thousand collides by chance; far more than that would mean an input is ignored.
		assertTrue(changed > 3_950, "inputs changed the number only " + changed + " times of 4000");
	}

	/** Computed independently (a Python port of the mixing, checked against the record goldens above). */
	private static final int[] LAB_GOLDEN = {314, 351, 340, 372};

	/** Asking in any order, any number of times, gives the same answers: nothing is remembered between calls. */
	@Test
	void recordDoesNotDependOnCallOrder() {
		Random random = new Random(513L);
		List<long[]> inputs = new ArrayList<>();
		for (int i = 0; i < 2_000; i++) {
			inputs.add(new long[] {random.nextLong(), random.nextLong(), random.nextLong()});
		}
		List<String> forward = new ArrayList<>();
		for (long[] in : inputs) {
			forward.add(ArchiveRecord.of(in[0], new UUID(in[1], in[2])));
		}
		for (int i = inputs.size() - 1; i >= 0; i--) {
			long[] in = inputs.get(i);
			assertEquals(forward.get(i), ArchiveRecord.of(in[0], new UUID(in[1], in[2])));
		}
	}

	/**
	 * Each input matters on its own: change only the seed, only the high half of the id or only the low
	 * half, and the record changes — except by collision, which 21 000 values make rare but not impossible.
	 */
	@Test
	void seedAndBothHalvesOfTheProfileIdAllMatter() {
		Random random = new Random(1_513L);
		int trials = 2_000;
		int seedChanged = 0;
		int highChanged = 0;
		int lowChanged = 0;
		for (int i = 0; i < trials; i++) {
			long seed = random.nextLong();
			UUID id = new UUID(random.nextLong(), random.nextLong());
			String base = ArchiveRecord.of(seed, id);
			if (!base.equals(ArchiveRecord.of(seed + 1, id))) {
				seedChanged++;
			}
			if (!base.equals(ArchiveRecord.of(seed, new UUID(id.getMostSignificantBits() ^ 1L,
					id.getLeastSignificantBits())))) {
				highChanged++;
			}
			if (!base.equals(ArchiveRecord.of(seed, new UUID(id.getMostSignificantBits(),
					id.getLeastSignificantBits() ^ 1L)))) {
				lowChanged++;
			}
		}
		// Expected collisions per 2 000 pairs: about 0.1. Allowing ten keeps the test honest, not flaky.
		assertTrue(seedChanged >= trials - 10, "seed changed the record only " + seedChanged + " times");
		assertTrue(highChanged >= trials - 10, "high id bits changed it only " + highChanged + " times");
		assertTrue(lowChanged >= trials - 10, "low id bits changed it only " + lowChanged + " times");
	}

	/** Every letter and every number turns up, and no letter is favoured: a check on the mixing, not a proof. */
	@Test
	void recordsSpreadOverTheWholeSpace() {
		Random random = new Random(2_513L);
		int samples = 210_000;
		int[] letters = new int[ArchiveRecord.ALPHABET.length()];
		boolean[] numbers = new boolean[1000];
		for (int i = 0; i < samples; i++) {
			String record = ArchiveRecord.of(random.nextLong(), new UUID(random.nextLong(), random.nextLong()));
			letters[ArchiveRecord.ALPHABET.indexOf(record.charAt(0))]++;
			numbers[Integer.parseInt(record.substring(2))] = true;
		}
		int expected = samples / letters.length;
		for (int l = 0; l < letters.length; l++) {
			// 5 % of 10 000 is about five standard deviations of a fair letter.
			assertTrue(Math.abs(letters[l] - expected) < expected / 20,
					"letter " + ArchiveRecord.ALPHABET.charAt(l) + " came up " + letters[l] + " times, expected ~" + expected);
		}
		for (int n = 0; n < numbers.length; n++) {
			assertTrue(numbers[n], "number " + n + " never came up");
		}
	}

	/**
	 * The record is the same whatever the default locale. {@code String.format} would have written the
	 * digits in the locale's own script under these three.
	 */
	@Test
	void recordIgnoresTheDefaultLocale() {
		String expected = ArchiveRecord.of(4_213_787_135_487_651_912L, ONES);
		Locale saved = Locale.getDefault();
		try {
			for (String tag : new String[] {"ar-SA-u-nu-arab", "hi-IN-u-nu-deva", "th-TH-u-nu-thai", "tr-TR"}) {
				Locale.setDefault(Locale.forLanguageTag(tag));
				String record = ArchiveRecord.of(4_213_787_135_487_651_912L, ONES);
				assertEquals(expected, record, tag);
				assertTrue(ArchiveRecord.isValid(record), tag + " produced " + record);
			}
		} finally {
			Locale.setDefault(saved);
		}
	}
}
