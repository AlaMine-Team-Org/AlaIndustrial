package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link WindMillOutput#euFor} — the pure "(height, sea level, sky, weather) → EU/t"
 * mapping that backs the wind mill's {@code produce()}. No Minecraft runtime; deterministic.
 *
 * <p>The canonical numbers are {@code Config.windMillMaxBaseEuPerTick = 4}, {@code windMillMaxEuPerTick = 8},
 * {@code windMillRainFactor = 1.5}, {@code windMillThunderFactor = 2.0}, but {@code Config} pulls in
 * Fabric/Minecraft classes absent from the L1 classpath, so they are passed as literals — the mapping under
 * test is a pure function and the arithmetic invariants are what matter.
 *
 * @implements wind-mill production mapping (height base × weather, clamped to [0, maxOutput])
 */
class WindMillOutputTest {

	private static final int SEA = 63;
	private static final int MAX_BASE = 4;
	private static final int MAX_OUT = 8;
	private static final float RAIN = 1.5f;
	private static final float THUNDER = 2.0f;

	private static int eu(int y, boolean sky, boolean rain, boolean thunder) {
		return WindMillOutput.euFor(y, SEA, sky, rain, thunder, MAX_BASE, MAX_OUT, RAIN, THUNDER);
	}

	@Test
	void atSeaLevelProducesNothing() {
		assertEquals(0, eu(SEA, true, false, false), "y = sea level → base 0 → 0 EU/t");
	}

	@Test
	void belowSeaLevelProducesNothing() {
		assertEquals(0, eu(SEA - 20, true, false, false), "below sea level → 0 EU/t");
	}

	@Test
	void baseGrowsPerSixteenBlocks() {
		assertEquals(1, eu(SEA + 16, true, false, false), "+16 blocks → base 1");
		assertEquals(2, eu(SEA + 32, true, false, false), "+32 blocks → base 2");
		assertEquals(3, eu(SEA + 48, true, false, false), "+48 blocks → base 3");
	}

	@Test
	void heightBaseCapsAtMaxBase() {
		assertEquals(MAX_BASE, eu(SEA + 64, true, false, false), "+64 blocks → base 4 (cap)");
		assertEquals(MAX_BASE, eu(SEA + 400, true, false, false), "way up → still capped at 4");
	}

	@Test
	void rainMultipliesBase() {
		// base 2 (y = sea+32) × 1.5 = 3
		assertEquals(3, eu(SEA + 32, true, true, false), "base 2 × rain 1.5 → 3");
	}

	@Test
	void thunderMultipliesBaseAndBeatsRain() {
		// base 2 (y = sea+32) × 2.0 = 4; thunder takes precedence over rain flag
		assertEquals(4, eu(SEA + 32, true, true, true), "base 2 × thunder 2.0 → 4 (thunder wins)");
	}

	@Test
	void finalOutputCapsAtMaxOutput() {
		// base 4 × thunder 2.0 = 8 (exactly the cap); base 4 × 2.0 never exceeds 8 anyway, so push higher base
		assertEquals(MAX_OUT, eu(SEA + 400, true, false, true), "base 4 × thunder 2.0 → 8 (cap)");
		// with a hypothetically higher cap the product would exceed 8, so verify the min() clamp explicitly
		assertEquals(8, WindMillOutput.euFor(SEA + 400, SEA, true, false, true, 6, 8, RAIN, THUNDER),
				"base 6 × thunder 2.0 = 12 clamps to maxOutput 8");
	}

	@Test
	void noOpenSkyProducesNothing() {
		assertEquals(0, eu(SEA + 400, false, false, false), "roofed → 0 even at great height");
		assertEquals(0, eu(SEA + 400, false, true, true), "roofed → 0 even in a thunderstorm");
	}

	@Test
	void stormCannotLiftZeroBase() {
		// At sea level the base is 0; weather multiplies 0 → still 0 (height is required).
		assertEquals(0, eu(SEA, true, true, true), "base 0 × thunder → 0 (height required)");
	}

	@Test
	void highAltitudeVariantGainsBaseTwiceAsFast() {
		// T2 high-altitude: blocksPerBase = 8 (vs T1's 16). So at sea+16 the T1 base is 1 but the T2 base is 2.
		// Canonical T2 numbers: maxBase=8, blocksPerBase=8, maxOutput=16 (same weather factors as T1).
		int t2MaxBase = 8;
		int t2BlocksPerBase = 8;
		int t2MaxOut = 16;
		// y = sea+16: T2 base = 16/8 = 2; clear weather → 2 EU/t
		assertEquals(2, WindMillOutput.euFor(SEA + 16, SEA, true, false, false,
				t2MaxBase, t2BlocksPerBase, t2MaxOut, RAIN, THUNDER), "T2 +16 blocks → base 2 (8/step)");
		// y = sea+16, thunder: base 2 × 2.0 = 4
		assertEquals(4, WindMillOutput.euFor(SEA + 16, SEA, true, false, true,
				t2MaxBase, t2BlocksPerBase, t2MaxOut, RAIN, THUNDER), "T2 base 2 × thunder → 4");
		// y = sea+64: T2 base = 64/8 = 8 (the cap); clear → 8
		assertEquals(8, WindMillOutput.euFor(SEA + 64, SEA, true, false, false,
				t2MaxBase, t2BlocksPerBase, t2MaxOut, RAIN, THUNDER), "T2 +64 blocks → base 8 (cap)");
		// y = sea+64, thunder: base 8 × 2.0 = 16 (the T2 cap)
		assertEquals(16, WindMillOutput.euFor(SEA + 64, SEA, true, false, true,
				t2MaxBase, t2BlocksPerBase, t2MaxOut, RAIN, THUNDER), "T2 base 8 × thunder → 16 (cap)");
	}
}
