package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** L1 contract for the status ordinal synchronized by the Galvanic Bath menu (MOD-324). */
class GalvanicBathStatusTest {

	@Test
	void translationKeysAreStable() {
		assertEquals("gui.alaindustrial.galvanic_bath.status.ready",
				GalvanicBathStatus.READY.translationKey());
		assertEquals("gui.alaindustrial.galvanic_bath.status.no_fiber",
				GalvanicBathStatus.NO_FIBER.translationKey());
		assertEquals("gui.alaindustrial.galvanic_bath.status.no_silver",
				GalvanicBathStatus.NO_SILVER.translationKey());
		assertEquals("gui.alaindustrial.galvanic_bath.status.no_water",
				GalvanicBathStatus.NO_WATER.translationKey());
		assertEquals("gui.alaindustrial.galvanic_bath.status.no_recipe",
				GalvanicBathStatus.NO_RECIPE.translationKey());
		assertEquals("gui.alaindustrial.galvanic_bath.status.output_blocked",
				GalvanicBathStatus.OUTPUT_BLOCKED.translationKey());
	}

	@Test
	void everyStatusHasItsOwnKey() {
		Set<String> keys = new HashSet<>();
		for (GalvanicBathStatus status : GalvanicBathStatus.values()) {
			assertTrue(keys.add(status.translationKey()),
					status + " reuses another status' lang key — the screen would explain the wrong stall");
		}
	}

	@Test
	void ordinalWireFormatRoundTrips() {
		for (GalvanicBathStatus status : GalvanicBathStatus.values()) {
			assertEquals(status, GalvanicBathStatus.byOrdinal(status.ordinal()));
		}
	}

	@Test
	void outOfRangeOrdinalsFallBackToReady() {
		int last = GalvanicBathStatus.values().length - 1;
		// In-range top of the guard: a mutant that makes the range check always fail would answer
		// READY here instead of the real constant.
		assertNotEquals(GalvanicBathStatus.READY, GalvanicBathStatus.byOrdinal(last));
		// Upper bound, one past the end. `< VALUES.length` widened to `<=` indexes past the array and
		// throws instead of answering — this is the assertion that kills that mutant.
		assertEquals(GalvanicBathStatus.READY, GalvanicBathStatus.byOrdinal(last + 1));
		assertEquals(GalvanicBathStatus.READY, GalvanicBathStatus.byOrdinal(-1));
		assertEquals(GalvanicBathStatus.READY, GalvanicBathStatus.byOrdinal(Integer.MIN_VALUE));
		assertEquals(GalvanicBathStatus.READY, GalvanicBathStatus.byOrdinal(Integer.MAX_VALUE));
		// Note for whoever reads a surviving mutant here: `ordinal >= 0` narrowed to `> 0` is
		// EQUIVALENT for this enum and cannot be killed. Index 0 is READY, which is also the
		// out-of-range fallback, so both the guarded and the fallback path return the same constant.
		// The same holds for GardenDroneStatus (IDLE) and VulcanizerStatus (READY).
	}
}
