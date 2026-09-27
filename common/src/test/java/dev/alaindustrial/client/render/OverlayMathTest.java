package dev.alaindustrial.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * L1 — the arithmetic of the Network Analyzer overlay (MOD-665): opacity reaches every part of the trace
 * (D12), the trace is coloured along the path from a producer, and the sparks follow game time, so they
 * stop with the game (D14).
 */
class OverlayMathTest {

	/**
	 * @implements MOD-665-D12 — the configured opacity replaces a colour's alpha, the colour stays
	 * @covers MOD-665
	 */
	@Test
	void withAlphaReplacesOnlyAlpha() {
		assertEquals(0x8084CC16, OverlayMath.withAlpha(0xFF84CC16, 0x80));
		assertEquals(0x0084CC16, OverlayMath.withAlpha(0xFF84CC16, -5), "clamped below");
		assertEquals(0xFF84CC16, OverlayMath.withAlpha(0x1284CC16, 999), "clamped above");
	}

	/**
	 * @implements MOD-665 — the trace runs blue at a producer to yellow at the far end: the ramp is
	 *     anchored at both ends, and an unpowered island does not borrow the far colour
	 * @covers MOD-665
	 */
	@Test
	void pathFractionRunsFromProducerToFarEnd() {
		assertEquals(0.0, OverlayMath.pathFraction(0, 8), 0.0);
		assertEquals(0.5, OverlayMath.pathFraction(4, 8), 1e-9);
		assertEquals(1.0, OverlayMath.pathFraction(8, 8), 0.0);
		assertEquals(0.0, OverlayMath.pathFraction(null, 8), 0.0, "unreached position");
		assertEquals(0.0, OverlayMath.pathFraction(0, 0), 0.0, "no producer anywhere");
	}

	/**
	 * @implements MOD-665 — the ramp colour is an honest blend of its two ends, each channel on its own
	 * @covers MOD-665
	 */
	@Test
	void lerpColorBlendsEveryChannel() {
		assertEquals(0xFF11577A, OverlayMath.lerpColor(0xFF11577A, 0xFFFACC15, 0.0));
		assertEquals(0xFFFACC15, OverlayMath.lerpColor(0xFF11577A, 0xFFFACC15, 1.0));
		assertEquals(0x80808080, OverlayMath.lerpColor(0x00000000, 0xFFFFFFFF, 0.5 + 0.5 / 255));
		assertEquals(0xFF0000FF, OverlayMath.lerpColor(0xFF0000FF, 0x00FF0000, -3), "clamped below");
	}

	/**
	 * @implements MOD-665-D14 — the spark phase is a function of game time alone: the same game time
	 *     gives the same picture however much wall-clock time has passed (a paused game)
	 * @covers MOD-665
	 */
	@Test
	void flowPhaseFollowsGameTimeNotTheWallClock() throws InterruptedException {
		double before = OverlayMath.flowPhase(1234.5, 0, 1, 1.0);
		Thread.sleep(60);
		double after = OverlayMath.flowPhase(1234.5, 0, 1, 1.0);
		assertEquals(before, after, 0.0, "paused game: the sparks must not move");
		// One edge per second = 20 ticks per edge: 10 ticks is half an edge further on.
		assertEquals(0.5, OverlayMath.flowPhase(10.0, 0, 1, 1.0), 1e-9);
		assertEquals(0.0, OverlayMath.flowPhase(20.0, 0, 1, 1.0), 1e-9);
		assertEquals(0.75, OverlayMath.flowPhase(5.0, 1, 2, 1.0), 1e-9, "second of two dots starts half an edge on");
	}
}
