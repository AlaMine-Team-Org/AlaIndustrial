package dev.alaindustrial.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link HudSmoother} — the charge-HUD readout math shared by the Energy Pack and
 * Electric Drill overlays (MOD-079). Pure client-side math, no Minecraft on the classpath.
 *
 * <p>Pins the boundary fix: exponential easing only <em>approaches</em> its target, so a buffer that
 * has truly hit 0 (or capacity) used to leave the smoothed value a hair off the edge and — with the
 * 1.5-point hysteresis deadband — park the shown number on 1 % (empty) / 99 % (full) forever. A
 * genuine empty/full charge must read exactly 0 % / 100 %.
 */
class HudSmootherTest {

	/** A fully discharged buffer reads exactly 0 %, not the 1 % the hysteresis used to stick on. */
	@Test
	void emptyReadsZero() {
		HudSmoother s = new HudSmoother();
		// Sit at full for a while, then drain to empty — the reported bug path.
		assertEquals(100, s.displayPercent(1.0f, 1.0f));
		assertEquals(0, s.displayPercent(0.0f, 1.0f));
		// Still empty next frame — stays 0, never drifts back up to 1.
		assertEquals(0, s.displayPercent(0.0f, 1.0f));
	}

	/** A full buffer reads exactly 100 %, not 99 %. */
	@Test
	void fullReadsHundred() {
		HudSmoother s = new HudSmoother();
		assertEquals(0, s.displayPercent(0.0f, 1.0f));
		assertEquals(100, s.displayPercent(1.0f, 1.0f));
		assertEquals(100, s.displayPercent(1.0f, 1.0f));
	}

	/**
	 * The stuck-at-1 regression itself: approach empty gradually (many small eased steps toward 0)
	 * and the reading must settle on 0, not park on 1 inside the deadband.
	 */
	@Test
	void gradualDrainToEmptyReachesZero() {
		HudSmoother s = new HudSmoother();
		s.displayPercent(0.5f, 1.0f);          // seed mid-charge
		int last = -1;
		for (int i = 0; i < 200; i++) {
			last = s.displayPercent(0.0f, 1.0f); // hold target at empty, let it ease down
		}
		assertEquals(0, last);
	}

	// --- MOD-200: the mid-range path (easing, snap, hysteresis) --------------------------------
	// Everything above only pins the 0 % / 100 % edges, which the two early returns handle. The
	// whole body between them — the snap test, the exponential step, the deadband — had no L1
	// coverage at all: this class only entered the mutation lane in MOD-200, and its first run
	// left 18 of its mutants SURVIVED. Every expectation below is a literal computed by hand from
	// the documented constants (TAU=10 ticks, SNAP=0.25, DEADBAND=1.5), never by re-running the
	// formula the test is meant to police.

	/** The very first frame has nothing to ease from, so it snaps straight onto the target. */
	@Test
	void firstFrameSnapsOntoTarget() {
		HudSmoother s = new HudSmoother();
		assertEquals(42, s.displayPercent(0.42f, 0.0f));
	}

	/**
	 * A jump larger than the snap threshold is a real event (item swap, big drain) and must show at
	 * once. Easing 0.20 → 0.80 over one tick would read 26 %, not 80 %.
	 */
	@Test
	void largeJumpSnapsInsteadOfEasing() {
		HudSmoother s = new HudSmoother();
		assertEquals(20, s.displayPercent(0.20f, 1.0f));
		assertEquals(80, s.displayPercent(0.80f, 1.0f));
	}

	/**
	 * A jump of exactly the threshold is NOT a snap — the guard is a strict {@code >}. Easing
	 * 0.50 → 0.75 over 10 ticks (one full time constant, k = 1-e⁻¹) lands on 0.65803 → 66 %.
	 * Were the comparison {@code >=}, this would snap and read 75 %.
	 */
	@Test
	void jumpExactlyAtThresholdEasesNotSnaps() {
		HudSmoother s = new HudSmoother();
		assertEquals(50, s.displayPercent(0.50f, 1.0f));
		assertEquals(66, s.displayPercent(0.75f, 10.0f));
	}

	/**
	 * The step is driven by elapsed time, not by frame count: the same 0.50 → 0.62 move reads 55 %
	 * after 5 ticks and 62 % after 40. A HUD at 30 fps and one at 240 fps must agree.
	 */
	@Test
	void easingIsFramerateIndependent() {
		HudSmoother slow = new HudSmoother();
		slow.displayPercent(0.50f, 1.0f);
		HudSmoother fast = new HudSmoother();
		fast.displayPercent(0.50f, 1.0f);

		assertEquals(55, slow.displayPercent(0.62f, 5.0f));
		assertEquals(62, fast.displayPercent(0.62f, 40.0f));
	}

	/** A negative frame delta cannot rewind the smoothing — the step is clamped to zero. */
	@Test
	void negativeDeltaDoesNotMoveTheReading() {
		HudSmoother s = new HudSmoother();
		assertEquals(50, s.displayPercent(0.50f, 1.0f));
		// Unclamped this would ease *away* from the target (k < 0) and read 42 %.
		assertEquals(50, s.displayPercent(0.62f, -5.0f));
	}

	/**
	 * The hysteresis itself: a settled move to exactly one point away is inside the 1.5-point
	 * deadband, so the shown number holds. This is the ripple suppression the class exists for.
	 */
	@Test
	void onePointRippleIsHeldByTheDeadband() {
		HudSmoother s = new HudSmoother();
		assertEquals(50, s.displayPercent(0.50f, 1.0f));
		assertEquals(50, s.displayPercent(0.51f, 100.0f));
	}

	/** …but the deadband only holds; it never sticks. Past 1.5 points the number moves again. */
	@Test
	void movePastTheDeadbandUpdatesTheReading() {
		HudSmoother s = new HudSmoother();
		assertEquals(50, s.displayPercent(0.50f, 1.0f));
		assertEquals(50, s.displayPercent(0.51f, 100.0f));   // held
		assertEquals(53, s.displayPercent(0.53f, 100.0f));   // 3 points away — released
	}
}
