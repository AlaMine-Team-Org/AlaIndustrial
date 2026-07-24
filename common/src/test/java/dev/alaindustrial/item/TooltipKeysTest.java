package dev.alaindustrial.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link TooltipKeys} — the "is Shift held" seam common item code reads without
 * importing a client-only class (MOD-108).
 *
 * <p>Added by MOD-200: the class had no L1 suite at all, so all three of its mutants sat at
 * NO_COVERAGE. The null-guard is the load-bearing part — {@code CLIENT} is deliberately unset on a
 * dedicated server, and dropping the guard would turn every server-side tooltip lookup into an NPE.
 */
class TooltipKeysTest {

	@AfterEach
	void clearHook() {
		// Static mutable seam: leaving a hook installed would leak into the next test.
		TooltipKeys.CLIENT = null;
	}

	/** Dedicated server: no client ever installed a hook, so the answer is a plain false, not a crash. */
	@Test
	void unsetHookReadsFalse() {
		TooltipKeys.CLIENT = null;
		assertFalse(TooltipKeys.shiftDown());
	}

	/** With a hook installed, the answer is the hook's — Shift down. */
	@Test
	void installedHookReportsShiftDown() {
		TooltipKeys.CLIENT = () -> true;
		assertTrue(TooltipKeys.shiftDown());
	}

	/** …and Shift up. Pins that the result is the hook's value, not a constant. */
	@Test
	void installedHookReportsShiftUp() {
		TooltipKeys.CLIENT = () -> false;
		assertFalse(TooltipKeys.shiftDown());
	}
}
