package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link WaterMillOutput#euFor(int, int)} — the pure "water faces → EU/t" mapping
 * that backs the water mill's {@code produce()}. No Minecraft runtime; deterministic.
 *
 * <p>The canonical per-side rate is {@code Config.waterMillEuPerTick = 1}, but {@code Config} pulls in
 * Fabric/Minecraft classes absent from the L1 classpath, so the rate is passed as a literal — the
 * mapping under test is a pure integer function and the arithmetic invariant is what matters.
 *
 * @implements water-mill production mapping (waterSides × perSide, clamped 0..4)
 */
class WaterMillOutputTest {

	/** The documented default per-side rate ({@code Config.waterMillEuPerTick}). */
	private static final int DEFAULT_PER_SIDE = 1;

	@Test
	void zeroWaterFacesProducesNothing() {
		assertEquals(0, WaterMillOutput.euFor(0, DEFAULT_PER_SIDE), "no water → 0 EU/t");
	}

	@Test
	void oneFaceProducesPerSide() {
		assertEquals(DEFAULT_PER_SIDE, WaterMillOutput.euFor(1, DEFAULT_PER_SIDE), "1 face → 1× perSide");
	}

	@Test
	void fourFacesProducesFullOutput() {
		// Fully surrounded: perSide × 4 (the documented cap of 4 EU/t at the default perSide = 1).
		assertEquals(DEFAULT_PER_SIDE * 4, WaterMillOutput.euFor(4, DEFAULT_PER_SIDE), "4 faces → cap");
	}

	@Test
	void scalesLinearlyWithFaces() {
		for (int n = 0; n <= 4; n++) {
			assertEquals(DEFAULT_PER_SIDE * n, WaterMillOutput.euFor(n, DEFAULT_PER_SIDE), n + " faces");
		}
	}

	@Test
	void clampsAboveFourAndBelowZero() {
		assertEquals(DEFAULT_PER_SIDE * 4, WaterMillOutput.euFor(9, DEFAULT_PER_SIDE), "count > 4 clamps to cap");
		assertEquals(0, WaterMillOutput.euFor(-3, DEFAULT_PER_SIDE), "negative count clamps to 0");
	}

	@Test
	void respectsConfiguredPerSideRate() {
		// The mapping is proportional to the per-side rate, not hard-coded to 1.
		assertEquals(6, WaterMillOutput.euFor(2, 3), "perSide=3, 2 faces → 6");
	}
}
