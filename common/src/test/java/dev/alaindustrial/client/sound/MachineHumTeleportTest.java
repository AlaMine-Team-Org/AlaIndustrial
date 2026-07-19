package dev.alaindustrial.client.sound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link HumTeleportDetector} — the teleport detector that fixes MOD-129 (machine hum
 * silent after a {@code /teleport}). Pure math, no Minecraft on the classpath.
 *
 * <p>Root cause it guards: the hum manager is a process singleton whose loop map used to survive every
 * level change. After a teleport the sound engine leaves each loop in a state the per-position ticker
 * cannot recover — a dimension change / reconnect clears the engine but not the manager's references, and
 * a teleport's loading-screen pause freezes each loop's self-terminating {@code tick()} while still
 * reporting it active — so the {@code instance != null} guard suppressed the restart forever. The fix
 * detects the discontinuity and drops the tracked loops so the ticker rebuilds them. The threshold must
 * ignore all legitimate motion (a few blocks per tick) yet catch any real teleport.
 */
class MachineHumTeleportTest {

	/** A fresh client level (dimension change / reconnect) is a discontinuity regardless of position. */
	@Test
	void levelChangeIsDiscontinuity() {
		// Even a zero jump (same coordinates in the new dimension) must reset the loops.
		assertTrue(HumTeleportDetector.isDiscontinuity(true, true, 0.0));
		assertTrue(HumTeleportDetector.isDiscontinuity(true, false, 0.0));
	}

	/** A long same-dimension teleport (huge single-tick jump) is a discontinuity. */
	@Test
	void largeJumpIsDiscontinuity() {
		double farJumpSqr = 500.0 * 500.0; // teleport to a distant base
		assertTrue(HumTeleportDetector.isDiscontinuity(false, true, farJumpSqr));
		// Just past the threshold still counts.
		assertTrue(HumTeleportDetector.isDiscontinuity(false, true, HumTeleportDetector.TELEPORT_JUMP_SQR + 1.0));
	}

	/** Ordinary movement — even the fastest legitimate motion — never resets the loops. */
	@Test
	void normalMovementIsContinuous() {
		double sprintStepSqr = 1.0 * 1.0;
		double elytraStepSqr = 4.0 * 4.0; // a few blocks per tick, still far below 32
		assertFalse(HumTeleportDetector.isDiscontinuity(false, true, sprintStepSqr));
		assertFalse(HumTeleportDetector.isDiscontinuity(false, true, elytraStepSqr));
		// Exactly at the threshold is not past it — no reset.
		assertFalse(HumTeleportDetector.isDiscontinuity(false, true, HumTeleportDetector.TELEPORT_JUMP_SQR));
	}

	/** With no prior anchor (first tick / just after a reconnect) a jump value is ignored, not trusted. */
	@Test
	void noAnchorIgnoresJump() {
		assertFalse(HumTeleportDetector.isDiscontinuity(false, false, 500.0 * 500.0));
	}
}
