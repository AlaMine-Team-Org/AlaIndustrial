package dev.alaindustrial.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link IncubatorStatus} — the reason line of the incubator screen (MOD-234).
 *
 * <p>Added by MOD-244: the enum is Minecraft-free but had no L1 suite, so every one of its mutants
 * would have entered the pitest run as NO_COVERAGE. The ordinals travel over a {@code ContainerData}
 * channel, which is why {@link IncubatorStatus#byOrdinal(int)} has to survive a value the client
 * never sent — a short read as an out-of-range int must land on READY, not throw in the render loop.
 */
class IncubatorStatusTest {

	/** Only the blocking states ship a lang key; the keys themselves are part of en_us.json. */
	@Test
	void translationKeyIsTheLangKeyOfTheState() {
		assertEquals("gui.alaindustrial.incubator.status.no_dome", IncubatorStatus.NO_DOME.translationKey());
		assertEquals("gui.alaindustrial.incubator.status.no_chip", IncubatorStatus.NO_CHIP.translationKey());
		assertEquals("gui.alaindustrial.incubator.status.no_recipe", IncubatorStatus.NO_RECIPE.translationKey());
		assertEquals("gui.alaindustrial.incubator.status.no_fuel", IncubatorStatus.NO_FUEL.translationKey());
		assertEquals("gui.alaindustrial.incubator.status.output_blocked",
				IncubatorStatus.OUTPUT_BLOCKED.translationKey());
	}

	/** READY and NO_INPUT are not faults — the screen prints the mode name for those two instead. */
	@Test
	void readyAndNoInputAreNotBlocking() {
		assertFalse(IncubatorStatus.READY.isBlocking());
		assertFalse(IncubatorStatus.NO_INPUT.isBlocking());
	}

	@Test
	void everyFaultStateIsBlocking() {
		assertTrue(IncubatorStatus.NO_DOME.isBlocking());
		assertTrue(IncubatorStatus.NO_CHIP.isBlocking());
		assertTrue(IncubatorStatus.NO_RECIPE.isBlocking());
		assertTrue(IncubatorStatus.NO_FUEL.isBlocking());
		assertTrue(IncubatorStatus.OUTPUT_BLOCKED.isBlocking());
	}

	/** Round-trip of the wire format: what the server writes into the data slot comes back intact. */
	@Test
	void byOrdinalRoundTripsEveryState() {
		for (IncubatorStatus status : IncubatorStatus.values()) {
			assertEquals(status, IncubatorStatus.byOrdinal(status.ordinal()));
		}
	}

	/**
	 * Out-of-range ordinals fall back to READY instead of throwing. The upper bound is the one that
	 * matters: a ContainerData slot the client has not received yet reads as a stale or default int,
	 * and indexing the values array with it would crash the screen.
	 */
	@Test
	void outOfRangeOrdinalFallsBackToReady() {
		assertEquals(IncubatorStatus.READY, IncubatorStatus.byOrdinal(-1));
		assertEquals(IncubatorStatus.READY, IncubatorStatus.byOrdinal(IncubatorStatus.values().length));
		assertEquals(IncubatorStatus.READY, IncubatorStatus.byOrdinal(Integer.MAX_VALUE));
		assertEquals(IncubatorStatus.READY, IncubatorStatus.byOrdinal(Integer.MIN_VALUE));
	}
}
