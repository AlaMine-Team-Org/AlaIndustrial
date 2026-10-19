package dev.alaindustrial.core.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.settlement.record.RecordNumber;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The shift workers' numbers and the players' records are one space (MOD-827, R3b of MOD-824): every string
 * {@link ArchiveRecord} can write reads back as the {@link RecordNumber} at the same index and prints back
 * unchanged. Lives in {@code core.guide} because {@link ArchiveRecord#format} is package-private and is not
 * changed for this; the number's own formatting is a twin, and this test is what keeps the two together.
 *
 * @implements MOD-827-FORMAT
 */
class RecordNumberFormatParityTest {

	@Test
	void everyArchiveRecordIsTheNumberAtTheSameIndex() {
		for (int index = 0; index < ArchiveRecord.SPACE; index++) {
			String record = ArchiveRecord.format(index);
			RecordNumber number = RecordNumber.parse(record).orElseThrow(() -> new AssertionError(record));
			assertEquals(index, number.index(), record);
			assertEquals(record, number.toString());
		}
	}

	/** A player's record — what the join hook hands the ledger — always reads as a number. */
	@Test
	void playersRecordsReadAsNumbers() {
		Random random = new Random(827);
		for (int i = 0; i < 2000; i++) {
			UUID profile = new UUID(random.nextLong(), random.nextLong());
			String record = ArchiveRecord.of(random.nextLong(), profile);
			assertEquals(record, RecordNumber.parse(record).orElseThrow(() -> new AssertionError(record)).toString());
		}
	}
}
