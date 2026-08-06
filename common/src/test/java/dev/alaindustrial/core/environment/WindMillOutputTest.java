package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link WindMillOutput#euFor} — the pure "(height, sea level, sky, weather) → EU/t"
 * mapping that backs every wind mill's {@code produce()}. No Minecraft runtime; deterministic.
 *
 * <p>The canonical numbers are {@code Config.windMillMaxBaseEuPerTick = 4},
 * {@code windMillMaxEuPerTick = 8}, {@code windMillRainFactor = 1.5},
 * {@code windMillThunderFactor = 2.0}, and the MOD-347 altitude profile
 * ({@code windCloudY = 192}, {@code windDeadY = 248}, {@code windRidgeFactor = 0.45},
 * {@code windTraceFactor = 0.06}), but {@code Config} pulls in Fabric/Minecraft classes absent from
 * the L1 classpath, so they are passed as literals — the mapping under test is a pure function and
 * the arithmetic invariants are what matter.
 *
 * @implements wind-mill production mapping (altitude profile × weather, clamped to [0, maxOutput])
 */
class WindMillOutputTest {

	private static final int SEA = 63;
	private static final int MAX_BASE = 4;
	private static final int BLOCKS_PER_BASE = 16;
	private static final int MAX_OUT = 8;
	private static final float RAIN = 1.5f;
	private static final float THUNDER = 2.0f;
	private static final int CLOUD = 192;
	private static final int DEAD = 248;
	private static final float RIDGE_F = 0.45f;
	private static final float TRACE_F = 0.06f;

	private static int eu(int y, boolean sky, boolean rain, boolean thunder) {
		return WindMillOutput.euFor(y, SEA, sky, rain, thunder, MAX_BASE, BLOCKS_PER_BASE, MAX_OUT,
				RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F);
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
	void outputGrowsWithHeightUpToTheClouds() {
		assertTrue(eu(SEA + 16, true, false, false) < eu(SEA + 64, true, false, false),
				"higher inside the climb → more EU/t");
		assertTrue(eu(SEA + 64, true, false, false) <= eu(CLOUD, true, false, false),
				"the shoulder never loses output on the way to the clouds");
		assertEquals(MAX_BASE, eu(CLOUD, true, false, false), "cloud deck, clear → the full base");
	}

	@Test
	void outputCollapsesAboveTheClouds() {
		// The MOD-347 change: very high is now WORSE than the cloud deck, not identical to it.
		assertTrue(eu(220, true, false, false) < eu(CLOUD, true, false, false),
				"above the clouds the mill loses output");
		assertEquals(0, eu(DEAD, true, false, false), "at the dead height the trace rounds to 0 EU/t");
		assertEquals(0, eu(300, true, false, true), "far above, even a thunderstorm powers nothing");
	}

	@Test
	void rainMultipliesBase() {
		int clear = eu(175, true, false, false);
		assertEquals(Math.min(MAX_OUT, Math.round(clear * RAIN)), eu(175, true, true, false),
				"rain scales the height base by 1.5");
	}

	@Test
	void thunderMultipliesBaseAndBeatsRain() {
		assertEquals(eu(175, true, false, true), eu(175, true, true, true),
				"thunder takes precedence over the rain flag");
		assertTrue(eu(175, true, false, true) > eu(175, true, true, false),
				"thunder beats rain in magnitude too");
	}

	@Test
	void finalOutputCapsAtMaxOutput() {
		assertEquals(MAX_OUT, eu(CLOUD, true, false, true), "base 4 × thunder 2.0 → 8 (cap)");
		// With a higher base the product would exceed the cap, so verify the min() clamp explicitly.
		assertEquals(8, WindMillOutput.euFor(CLOUD, SEA, true, false, true, 6, BLOCKS_PER_BASE, 8,
				RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F),
				"base 6 × thunder 2.0 = 12 clamps to maxOutput 8");
	}

	@Test
	void noOpenSkyProducesNothing() {
		assertEquals(0, eu(CLOUD, false, false, false), "roofed → 0 even at the windiest height");
		assertEquals(0, eu(CLOUD, false, true, true), "roofed → 0 even in a thunderstorm");
	}

	@Test
	void stormCannotLiftZeroBase() {
		assertEquals(0, eu(SEA, true, true, true), "base 0 × thunder → 0 (height required)");
	}

	@Test
	void highAltitudeVariantOutProducesT1AtEveryHeight() {
		// T2 high-altitude: maxBase 8 against T1's 4. Its blocksPerBase is 8 against T1's 16, but
		// 8 × 8 and 4 × 16 are both +64, so the two branches share a ridge and therefore the same curve
		// shape — the Sky Mill's advantage is purely that it converts the same wind into twice the base.
		// (That coincidence predates MOD-347; the parameter still separates the Tempest branch.)
		int t2MaxBase = 8;
		int t2Blocks = 8;
		int t2MaxOut = 16;
		int midT1 = eu(150, true, false, false);
		int midT2 = WindMillOutput.euFor(150, SEA, true, false, false, t2MaxBase, t2Blocks, t2MaxOut,
				RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F);
		assertTrue(midT2 > midT1, "T2 out-produces T1 at the same height: " + midT2 + " vs " + midT1);
		assertEquals(t2MaxBase, WindMillOutput.euFor(CLOUD, SEA, true, false, false, t2MaxBase, t2Blocks,
				t2MaxOut, RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F),
				"T2 reaches its full base at the cloud deck");
		assertEquals(t2MaxOut, WindMillOutput.euFor(CLOUD, SEA, true, false, true, t2MaxBase, t2Blocks,
				t2MaxOut, RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F),
				"T2 base 8 × thunder → 16 (cap)");
	}

	/**
	 * {@link WindProfile#ridgeY} guards the climb length with {@code Math.max(1, …)} on both factors.
	 * A boundary mutation there would let a zero-width climb segment through and divide by zero.
	 * Pinning {@code blocksPerBase == 0} (and a negative value) as inputs that still produce a finite,
	 * in-range result kills the flip.
	 */
	@Test
	void zeroOrNegativeBlocksPerBaseStaysFinite_notDivideByZero() {
		int zero = WindMillOutput.euFor(SEA + 16, SEA, true, false, false, MAX_BASE, 0, MAX_OUT,
				RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F);
		int negative = WindMillOutput.euFor(SEA + 16, SEA, true, false, false, MAX_BASE, -4, MAX_OUT,
				RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F);
		assertTrue(zero >= 0 && zero <= MAX_OUT, "blocksPerBase=0 stays in range, got " + zero);
		assertTrue(negative >= 0 && negative <= MAX_OUT, "negative blocksPerBase stays in range, got " + negative);
		assertEquals(0, WindMillOutput.euFor(SEA, SEA, true, false, false, MAX_BASE, 0, MAX_OUT,
				RAIN, THUNDER, CLOUD, DEAD, RIDGE_F, TRACE_F), "blocksPerBase=0 at sea level → 0");
	}
}
