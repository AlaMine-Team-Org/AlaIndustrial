package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** L1 contract for the status ordinal synchronized by the Vulcanizer menu. */
class VulcanizerStatusTest {

	@Test
	void readyIsTheOnlyNonBlockingState() {
		assertFalse(VulcanizerStatus.READY.isBlocking());
		for (VulcanizerStatus status : VulcanizerStatus.values()) {
			if (status != VulcanizerStatus.READY) {
				assertTrue(status.isBlocking(), status + " must explain a processing stall");
			}
		}
	}

	@Test
	void translationKeysAreStable() {
		assertEquals("gui.alaindustrial.vulcanizer.status.ready", VulcanizerStatus.READY.translationKey());
		assertEquals("gui.alaindustrial.vulcanizer.status.no_heat", VulcanizerStatus.NO_HEAT.translationKey());
		assertEquals("gui.alaindustrial.vulcanizer.status.no_raw_rubber",
				VulcanizerStatus.NO_RAW_RUBBER.translationKey());
		assertEquals("gui.alaindustrial.vulcanizer.status.no_sulfur",
				VulcanizerStatus.NO_SULFUR.translationKey());
		assertEquals("gui.alaindustrial.vulcanizer.status.no_recipe",
				VulcanizerStatus.NO_RECIPE.translationKey());
		assertEquals("gui.alaindustrial.vulcanizer.status.output_blocked",
				VulcanizerStatus.OUTPUT_BLOCKED.translationKey());
	}

	@Test
	void ordinalWireFormatRoundTripsAndRejectsInvalidValues() {
		for (VulcanizerStatus status : VulcanizerStatus.values()) {
			assertEquals(status, VulcanizerStatus.byOrdinal(status.ordinal()));
		}
		assertEquals(VulcanizerStatus.READY, VulcanizerStatus.byOrdinal(-1));
		assertEquals(VulcanizerStatus.READY, VulcanizerStatus.byOrdinal(VulcanizerStatus.values().length));
		assertEquals(VulcanizerStatus.READY, VulcanizerStatus.byOrdinal(Integer.MAX_VALUE));
	}
}
