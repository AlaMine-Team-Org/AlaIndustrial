package dev.alaindustrial.settlement.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.guide.ArchiveRecord;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link RecordNumber} (MOD-827): the value's text form, its parts, and that reading back
 * accepts exactly the archive's shape.
 *
 * @implements MOD-827-NUM
 */
class RecordNumberTest {

	@Test
	void textFormIsLetterHyphenAndThreeDigits() {
		assertEquals("A-000", RecordNumber.ofIndex(0).toString());
		assertEquals("A-007", RecordNumber.ofIndex(7).toString());
		assertEquals("K-482", RecordNumber.ofIndex(ArchiveRecord.ALPHABET.indexOf('K') * 1000 + 482).toString());
		assertEquals("X-999", RecordNumber.ofIndex(ArchiveRecord.SPACE - 1).toString());
		assertThrows(IllegalArgumentException.class, () -> RecordNumber.ofIndex(-1));
		assertThrows(IllegalArgumentException.class, () -> RecordNumber.ofIndex(ArchiveRecord.SPACE));
	}

	@Test
	void partsAreTheLetterAndTheLabDigits() {
		RecordNumber number = RecordNumber.parse("W-117").orElseThrow();
		assertEquals('W', number.letter());
		assertEquals(117, number.digits());
		assertEquals(ArchiveRecord.ALPHABET.indexOf('W') * 1000 + 117, number.index());
	}

	/** Every index reads back to itself through its text: the whole space, both ways. */
	@Test
	void everyNumberReadsBackToItself() {
		for (int index = 0; index < ArchiveRecord.SPACE; index++) {
			RecordNumber number = RecordNumber.ofIndex(index);
			assertEquals(Optional.of(number), RecordNumber.parse(number.toString()), number.toString());
		}
	}

	@Test
	void parseRejectsAnythingButTheExactShape() {
		for (String bad : new String[] {null, "", "K-48", "K-4820", "k-482", "I-100", "Z-100", "K_482", " K-482"}) {
			assertEquals(Optional.empty(), RecordNumber.parse(bad), String.valueOf(bad));
		}
	}

	/** The text does not follow the default locale's digits (the trap {@code String.format} would set). */
	@Test
	void textFormIgnoresTheDefaultLocale() {
		Locale saved = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"));
			assertEquals("N-345", RecordNumber.ofIndex(12_345).toString());
		} finally {
			Locale.setDefault(saved);
		}
	}

	@Test
	void equalityAndOrderFollowTheIndex() {
		RecordNumber a = RecordNumber.ofIndex(5);
		assertEquals(a, RecordNumber.parse("A-005").orElseThrow());
		assertEquals(a.hashCode(), RecordNumber.parse("A-005").orElseThrow().hashCode());
		assertNotEquals(a, RecordNumber.ofIndex(1005));
		assertTrue(a.compareTo(RecordNumber.ofIndex(1005)) < 0);
	}
}
