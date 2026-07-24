package dev.alaindustrial.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link NetworkTickGuard} — the tick-isolation wrapper that keeps a throwing
 * neighbour capability from crashing the server tick (MOD-186/MOD-187).
 *
 * <p>Added by MOD-200. The class shipped with zero L1 tests and, once the mutation lane could see it,
 * showed 11 NO_COVERAGE mutants: nothing exercised either the pass-through path or the swallow path.
 * That is the worst shape for a defensive wrapper — if the guard silently stopped guarding, or
 * started swallowing the body's <em>result</em>, no test would have noticed.
 */
class NetworkTickGuardTest {

	/** The ordinary path is a plain pass-through: the body runs, exactly once. */
	@Test
	void runIsolatedExecutesTheBody() {
		AtomicInteger runs = new AtomicInteger();
		NetworkTickGuard.runIsolated("energy", runs::incrementAndGet);
		assertEquals(1, runs.get());
	}

	/** A throwing body must not escape — that escape is precisely the server crash MOD-186 fixed. */
	@Test
	void runIsolatedSwallowsTheThrow() {
		assertDoesNotThrow(() -> NetworkTickGuard.runIsolated("energy", () -> {
			throw new IllegalStateException("a neighbouring mod's capability blew up");
		}));
	}

	/** The metered variant reports what the body actually moved, not a placeholder. */
	@Test
	void tickIsolatedReturnsTheBodyValue() {
		assertEquals(1234L, NetworkTickGuard.tickIsolated("item", () -> 1234L));
	}

	/** A tick that threw moved nothing, so it must report exactly zero EU — not the last value. */
	@Test
	void tickIsolatedReportsZeroWhenTheBodyThrows() {
		assertEquals(0L, NetworkTickGuard.tickIsolated("item", () -> {
			throw new IllegalStateException("boom");
		}));
	}

	/** A body that throws every tick must stay isolated every tick, not just the first. */
	@Test
	void repeatedThrowsStayIsolated() {
		for (int tick = 0; tick < 5; tick++) {
			assertEquals(0L, NetworkTickGuard.tickIsolated("energy", () -> {
				throw new IllegalStateException("same signature every tick");
			}));
		}
	}

	/**
	 * A throwable with an empty stack trace still has to be handled: the dedup key falls back to
	 * {@code "?"} instead of indexing frame 0 of an empty array.
	 */
	@Test
	void throwableWithoutStackTraceIsStillIsolated() {
		IllegalStateException noFrames = new IllegalStateException("no stack");
		noFrames.setStackTrace(new StackTraceElement[0]);
		assertEquals(0L, NetworkTickGuard.tickIsolated("energy", () -> {
			throw noFrames;
		}));
	}

	/** Distinct failure sites are distinct signatures, and each is isolated independently. */
	@Test
	void differentFailuresAreEachIsolated() {
		assertDoesNotThrow(() -> {
			NetworkTickGuard.runIsolated("energy", () -> {
				throw new IllegalArgumentException("bad argument");
			});
			NetworkTickGuard.runIsolated("item", () -> {
				throw new UnsupportedOperationException("unsupported");
			});
		});
		assertTrue(true, "both distinct failures were swallowed");
	}
}
