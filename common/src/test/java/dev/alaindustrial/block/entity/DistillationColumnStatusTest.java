package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** L1 contract for the status ordinal synchronized by the Distillation Column menu (MOD-251). */
class DistillationColumnStatusTest {

	@Test
	void translationKeysAreStable() {
		assertEquals("gui.alaindustrial.distillation_column.status.working",
				DistillationColumnStatus.WORKING.translationKey());
		assertEquals("gui.alaindustrial.distillation_column.status.warming",
				DistillationColumnStatus.WARMING.translationKey());
		assertEquals("gui.alaindustrial.distillation_column.status.no_energy",
				DistillationColumnStatus.NO_ENERGY.translationKey());
		assertEquals("gui.alaindustrial.distillation_column.status.no_oil",
				DistillationColumnStatus.NO_OIL.translationKey());
		assertEquals("gui.alaindustrial.distillation_column.status.diesel_full",
				DistillationColumnStatus.DIESEL_FULL.translationKey());
		assertEquals("gui.alaindustrial.distillation_column.status.fuel_oil_full",
				DistillationColumnStatus.FUEL_OIL_FULL.translationKey());
		assertEquals("gui.alaindustrial.distillation_column.status.fouled",
				DistillationColumnStatus.FOULED.translationKey());
	}

	@Test
	void everyStatusHasItsOwnKey() {
		Set<String> keys = new HashSet<>();
		for (DistillationColumnStatus status : DistillationColumnStatus.values()) {
			assertTrue(keys.add(status.translationKey()),
					status + " reuses another status' lang key — the screen would explain the wrong stall");
		}
	}

	@Test
	void ordinalWireFormatRoundTrips() {
		for (DistillationColumnStatus status : DistillationColumnStatus.values()) {
			assertEquals(status, DistillationColumnStatus.byOrdinal(status.ordinal()));
		}
	}

	@Test
	void outOfRangeOrdinalsFallBackToWorking() {
		int last = DistillationColumnStatus.values().length - 1;
		// In-range top of the guard: a mutant that makes the range check always fail would answer
		// WORKING here instead of the real constant.
		assertNotEquals(DistillationColumnStatus.WORKING, DistillationColumnStatus.byOrdinal(last));
		// Upper bound, one past the end. `< VALUES.length` widened to `<=` indexes past the array
		// and throws instead of answering — this is the assertion that kills that mutant.
		assertEquals(DistillationColumnStatus.WORKING, DistillationColumnStatus.byOrdinal(last + 1));
		assertEquals(DistillationColumnStatus.WORKING, DistillationColumnStatus.byOrdinal(-1));
		assertEquals(DistillationColumnStatus.WORKING, DistillationColumnStatus.byOrdinal(Integer.MIN_VALUE));
		assertEquals(DistillationColumnStatus.WORKING, DistillationColumnStatus.byOrdinal(Integer.MAX_VALUE));
		// Note: `ordinal >= 0` narrowed to `> 0` is EQUIVALENT for this enum and cannot be killed —
		// index 0 is WORKING, which is also the out-of-range fallback (same as GalvanicBathStatus).
	}
}
