package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * L1 unit tests for {@link WaterMillOutput#euFor(int, int)} — the pure "(water faces × perSide) → EU/t"
 * mapping that backs the water mill's {@code produce()}. No Minecraft runtime; deterministic. Mirrors the
 * {@link WindMillOutputTest} pattern — the production mapping is a pure function and the arithmetic
 * invariants (the {@code 0..4} clamp and the {@code perSide × sides} product) are what matter.
 *
 * @implements water-mill production mapping (sides clamped to 0..4, multiplied by perSide)
 */
class WaterMillOutputTest {

	// --- clamp: sides = max(0, min(4, waterSides)) ---

	@Test
	void zeroSidesProducesNothing() {
		assertEquals(0, WaterMillOutput.euFor(0, 1), "no water faces → 0 EU/t");
	}

	@Test
	void fourSidesIsTheCap() {
		assertEquals(4, WaterMillOutput.euFor(4, 1), "fully surrounded → 4 EU/t at perSide=1");
	}

	/**
	 * The {@code Math.min(4, waterSides)} upper clamp: values above 4 must collapse to 4, never leak
	 * through (a boundary/return-value mutant returning the raw input would diverge on 5, 8, 100).
	 */
	@ParameterizedTest
	@CsvSource({ "5, 1, 4", "6, 1, 4", "8, 1, 4", "100, 1, 4", "1_000_000, 1, 4" })
	void aboveFourSidesClampsToFour(int waterSides, int perSide, int expected) {
		assertEquals(expected, WaterMillOutput.euFor(waterSides, perSide),
				"waterSides > 4 clamps to 4 — fully-surrounded is the ceiling");
	}

	/**
	 * The {@code Math.max(0, waterSides)} lower clamp: negative inputs must collapse to 0, never produce
	 * a negative product (a mutant letting negatives through would return {@code perSide × negative}).
	 */
	@ParameterizedTest
	@CsvSource({ "-1, 1, 0", "-5, 1, 0", "-100, 3, 0" })
	void negativeSidesClampsToZero(int waterSides, int perSide, int expected) {
		assertEquals(expected, WaterMillOutput.euFor(waterSides, perSide),
				"negative waterSides clamps to 0 — no negative production");
	}

	// --- product: perSide × sides ---

	/**
	 * The {@code perSide * sides} multiplication. A {@code *}→{@code /} mutation (the {@code MATH} mutator)
	 * would turn {@code 3 × 2 = 6} into {@code 3 / 2 = 1}; these distinct values kill the flip.
	 */
	@Test
	void multipliesPerSideByClampedSides() {
		assertEquals(6, WaterMillOutput.euFor(2, 3), "2 water faces × 3 perSide → 6");
		assertEquals(9, WaterMillOutput.euFor(3, 3), "3 water faces × 3 perSide → 9");
		assertEquals(12, WaterMillOutput.euFor(4, 3), "4 water faces × 3 perSide → 12 (capped sides)");
		assertEquals(8, WaterMillOutput.euFor(2, 4), "2 water faces × 4 perSide → 8");
	}

	@Test
	void zeroPerSideProducesNothingEvenWhenSurrounded() {
		assertEquals(0, WaterMillOutput.euFor(4, 0), "perSide=0 → 0 regardless of sides");
	}

	/**
	 * The canonical balance point: {@code Config.waterMillEuPerTick = 1} per side, fully surrounded
	 * (4 faces) → 4 EU/t. Pins the realistic production number the live {@code produce()} reaches.
	 */
	@Test
	void canonicalOneEuPerSideFullySurrounded() {
		assertEquals(4, WaterMillOutput.euFor(4, 1), "Config.waterMillEuPerTick=1 × 4 sides → 4 EU/t");
	}
}
