package dev.alaindustrial.core.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link WindProfile} — the altitude curve shared by all three wind mills and the
 * Wind Gauge (MOD-347). No Minecraft runtime; deterministic.
 *
 * <p>Canonical numbers are the shipped config: sea level 63, cloud deck 192, dead height 248, ridge
 * factor 0.45, trace factor 0.06. {@code Config} pulls in Minecraft classes absent from the L1
 * classpath, so they are passed as literals — same arrangement as {@link WindMillOutputTest}.
 *
 * <p>Two assertions are load-bearing and they pull against each other:
 * {@link #curveIsStrictlyMonotoneOnEverySingleBlock()} (no height reads the same as its neighbour, so
 * climbing always gives feedback) and {@link #fullOutputIsConfinedToANarrowBandOfHeights()} (a mill's
 * cap is reachable only over a short stretch, so picking a height is a real decision). Flattening the
 * curve breaks the first; sharpening it too far breaks the second. Both were player-reported.
 */
class WindProfileTest {

	private static final int SEA = 63;
	private static final int CLOUD = 192;
	private static final int DEAD = 248;
	private static final float RIDGE_F = 0.45f;
	private static final float TRACE_F = 0.06f;
	/** T1 ridge: sea + maxBase(4) × blocksPerBase(16). */
	private static final int RIDGE = SEA + 64;

	private static float f(int y) {
		return WindProfile.factor(y, SEA, RIDGE, CLOUD, DEAD, RIDGE_F, TRACE_F);
	}

	@Test
	void seaLevelAndBelowIsCalm() {
		assertEquals(0.0f, f(SEA), 1e-6f, "sea level → no usable wind");
		assertEquals(0.0f, f(SEA - 30), 1e-6f, "below sea level → no usable wind");
	}

	@Test
	void windPeaksExactlyAtTheCloudDeck() {
		assertEquals(1.0f, f(CLOUD), 1e-6f, "the cloud deck is full strength");
		assertTrue(f(CLOUD - 1) < f(CLOUD), "still climbing one block below the clouds");
		assertTrue(f(CLOUD + 1) < f(CLOUD), "already falling one block above the clouds");
	}

	@Test
	void ridgeFactorIsReachedAtTheRidge() {
		assertEquals(RIDGE_F, f(RIDGE), 1e-6f, "the branch's old cap height is now 45% of full strength");
		assertTrue(f(RIDGE) < f(RIDGE + 1), "the shoulder keeps climbing past the old cap");
	}

	@Test
	void fullOutputIsConfinedToANarrowBandOfHeights() {
		// The second playtest complaint: a T1 mill used to sit at its cap over sixty-seven heights, so
		// picking one was not a decision and the gauge had nothing useful to say. Each branch's
		// full-output band must now be short enough that finding it is real work.
		assertBandWidth(4, RIDGE, 16);   // T1
		assertBandWidth(8, RIDGE, 8);    // Sky Mill
		assertBandWidth(6, SEA + 96, 8); // Tempest Mill (its own, higher ridge)
	}

	/** Fail unless {@code round(maxBase × factor) == maxBase} holds over at most {@code limit} heights. */
	private static void assertBandWidth(int maxBase, int ridge, int limit) {
		int count = 0;
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int y = SEA; y <= DEAD; y++) {
			float v = WindProfile.factor(y, SEA, ridge, CLOUD, DEAD, RIDGE_F, TRACE_F);
			if (Math.round(maxBase * v) == maxBase) {
				count++;
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
			}
		}
		assertTrue(count > 0, "maxBase " + maxBase + " must be reachable at all");
		assertTrue(count <= limit, "maxBase " + maxBase + " is reached over " + count
				+ " heights (Y " + lo + ".." + hi + "), expected at most " + limit);
	}

	@Test
	void aboveTheDeadHeightOnlyATraceRemains() {
		assertEquals(TRACE_F, f(DEAD), 1e-6f, "dead height → trace only");
		assertEquals(TRACE_F, f(DEAD + 64), 1e-6f, "far above → still just the trace, never negative");
		assertTrue(f(DEAD) > 0.0f, "the trace is readable, not a hard zero");
	}

	@Test
	void curveIsStrictlyMonotoneOnEverySingleBlock() {
		// Rising below the clouds, falling above them — and never equal to the block before it.
		// Compared at the gauge's displayed resolution (tenths of km/h at the shipped peak), because
		// that is the resolution the player actually sees.
		for (int y = SEA + 1; y <= CLOUD; y++) {
			assertTrue(tenths(y) > tenths(y - 1),
					"Y " + y + " must read above Y " + (y - 1) + ": " + tenths(y) + " vs " + tenths(y - 1));
		}
		for (int y = CLOUD + 1; y <= DEAD; y++) {
			assertTrue(tenths(y) < tenths(y - 1),
					"Y " + y + " must read below Y " + (y - 1) + ": " + tenths(y) + " vs " + tenths(y - 1));
		}
	}

	private static int tenths(int y) {
		return Math.round(f(y) * 62.5f * 10.0f);
	}

	@Test
	void ridgeYMatchesTheBranchesOldCapHeight() {
		assertEquals(SEA + 64, WindProfile.ridgeY(SEA, 4, 16, CLOUD), "T1: 4 × 16 above sea level");
		assertEquals(SEA + 64, WindProfile.ridgeY(SEA, 8, 8, CLOUD), "Sky Mill: 8 × 8 — coincidentally the same ridge as T1; the branches differ by base, not altitude");
		assertEquals(SEA + 96, WindProfile.ridgeY(SEA, 6, 16, CLOUD), "Tempest Mill: 6 × 16");
	}

	@Test
	void ridgeIsClampedBelowTheCloudDeck() {
		// A hand-edited config with an enormous climb must not swallow the shoulder or invert the curve.
		assertTrue(WindProfile.ridgeY(SEA, 40, 16, CLOUD) < CLOUD, "ridge stays under the clouds");
	}

	@Test
	void outOfOrderHeightsDegradeToARampInsteadOfNaN() {
		// deadY below cloudY is nonsense, but a config file can say it. Must stay finite and in range.
		float v = WindProfile.factor(150, SEA, RIDGE, CLOUD, CLOUD - 10, RIDGE_F, TRACE_F);
		assertTrue(Float.isFinite(v), "must not be NaN");
		assertTrue(v >= 0.0f && v <= 1.0f, "must stay within [0, 1], got " + v);
	}
}
