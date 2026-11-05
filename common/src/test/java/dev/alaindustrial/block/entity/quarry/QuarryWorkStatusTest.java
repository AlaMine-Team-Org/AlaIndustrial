package dev.alaindustrial.block.entity.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The quarry's work states (MOD-951): which hold it up, which are its working phases, and their captions. */
class QuarryWorkStatusTest {

	@Test
	void theFourPhasesWorkAndHoldNothingUp() {
		for (QuarryWorkStatus phase : new QuarryWorkStatus[] {QuarryWorkStatus.TRAVELLING, QuarryWorkStatus.LOWERING,
				QuarryWorkStatus.DRILLING, QuarryWorkStatus.RAISING}) {
			assertTrue(phase.working(), phase.name());
			assertFalse(phase.isBlocking(), phase.name());
		}
	}

	@Test
	void everyStopButDoneHoldsTheQuarryUp() {
		for (QuarryWorkStatus stop : new QuarryWorkStatus[] {QuarryWorkStatus.NOT_FORMED, QuarryWorkStatus.NO_POWER,
				QuarryWorkStatus.OUTPUT_FULL, QuarryWorkStatus.NO_BIT, QuarryWorkStatus.CHUNK_NOT_LOADED,
				QuarryWorkStatus.NO_OWNER}) {
			assertTrue(stop.isBlocking(), stop.name());
			assertFalse(stop.working(), stop.name());
		}
		assertFalse(QuarryWorkStatus.DONE.isBlocking(), "nothing left to dig is not a fault");
		assertFalse(QuarryWorkStatus.DONE.working());
	}

	@Test
	void captionsAreKeyedByName() {
		assertEquals("gui.alaindustrial.quarry.status.output_full", QuarryWorkStatus.OUTPUT_FULL.translationKey());
		assertEquals("gui.alaindustrial.quarry.status.chunk_not_loaded",
				QuarryWorkStatus.CHUNK_NOT_LOADED.translationKey());
	}

	@Test
	void anUnknownOrdinalReadsAsNotFormed() {
		assertEquals(QuarryWorkStatus.DRILLING, QuarryWorkStatus.byOrdinal(QuarryWorkStatus.DRILLING.ordinal()));
		assertEquals(QuarryWorkStatus.NOT_FORMED, QuarryWorkStatus.byOrdinal(-1));
		assertEquals(QuarryWorkStatus.NOT_FORMED, QuarryWorkStatus.byOrdinal(99));
	}
}
