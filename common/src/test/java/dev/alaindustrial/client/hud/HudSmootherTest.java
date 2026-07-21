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
}
