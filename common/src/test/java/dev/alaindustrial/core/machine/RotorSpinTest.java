package dev.alaindustrial.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1 unit tests for {@link RotorSpin} — the Thermal Centrifuge rotor's rate and angle arithmetic
 * (MOD-424).
 *
 * <p><b>Why this suite exists at L1 at all.</b> The L3 client lane can prove the rotor's geometry lands
 * in a captured frame and that it moves between two frames; it cannot measure how fast. Everything this
 * file pins — stopped means exactly zero, the ramp is monotone, idle-at-speed is slower than working but
 * not dead, the clock wraps — is invisible to a pixel gate and would otherwise be checked by nothing.
 *
 * <p>Constants are compared against each other rather than against copied literals only where the point
 * IS the relation (idle &lt; working). Where a number is the claim, the number is written out.
 */
class RotorSpinTest {

	private static final float EPS = 1.0E-6F;

	// ── stopped: the one state that must be exact ───────────────────────────────────────────────

	@Test
	@DisplayName("a stopped rotor has rate 0 and angle 0, whatever the clock says")
	void stoppedIsHardZero() {
		assertEquals(0.0F, RotorSpin.radiansPerTick(0, false), 0.0F);
		assertEquals(0.0F, RotorSpin.radiansPerTick(0, true), 0.0F,
				"the LIT flag must not resurrect a stopped rotor — no signal means no motion");
		for (long time : new long[] {0L, 1L, 12_345L, 999_999L}) {
			assertEquals(0.0F, RotorSpin.angle(time, 0.0F, 0, false), 0.0F,
					"a stopped rotor parked at an arbitrary angle is indistinguishable from a running "
							+ "one caught mid-turn, at game time " + time);
			assertEquals(0.0F, RotorSpin.angle(time, 0.75F, 0, true), 0.0F);
		}
	}

	@Test
	@DisplayName("a negative permille is treated as stopped, not as reverse")
	void negativePermilleIsStopped() {
		assertEquals(0.0F, RotorSpin.radiansPerTick(-1, false), 0.0F);
		assertEquals(0.0F, RotorSpin.angle(100L, 0.0F, -500, true), 0.0F);
	}

	// ── the ramp ───────────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("the first paid tick already moves the rotor, and slowly")
	void theRampStartsAtACrawl() {
		float first = RotorSpin.radiansPerTick(1, false);
		assertTrue(first > 0.0F, "1 permille must not read as stopped — a 20-second spin-up that starts "
				+ "with a dead rotor reads as 'nothing happened'");
		assertTrue(first < RotorSpin.IDLE_RADIANS_PER_TICK / 4.0F,
				"the start of the ramp must be visibly slower than the idle turn it climbs to, was " + first);
	}

	@Test
	@DisplayName("the rate rises monotonically across the whole spin-up")
	void rampIsMonotone() {
		float previous = -1.0F;
		for (int permille = 0; permille <= RotorSpin.FULL_SPIN_PERMILLE; permille += 50) {
			float rate = RotorSpin.radiansPerTick(permille, false);
			assertTrue(rate > previous,
					"rate must never fall as the rotor winds up: " + permille + " permille gave " + rate
							+ " after " + previous);
			previous = rate;
		}
	}

	@Test
	@DisplayName("half wound is genuinely between the crawl and the top, not snapped to either")
	void halfWoundSitsBetween() {
		float half = RotorSpin.radiansPerTick(500, false);
		float expected = RotorSpin.CREEP_RADIANS_PER_TICK
				+ (RotorSpin.IDLE_RADIANS_PER_TICK - RotorSpin.CREEP_RADIANS_PER_TICK) * 0.5F;
		assertEquals(expected, half, EPS);
		assertTrue(half > RotorSpin.CREEP_RADIANS_PER_TICK && half < RotorSpin.IDLE_RADIANS_PER_TICK,
				"a step function instead of a ramp would park here on one of the two ends, was " + half);
	}

	// ── the two wound-up states ────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("wound up and idle: still turning, and slower than working")
	void idleAtSpeedTurnsButSlower() {
		float idle = RotorSpin.radiansPerTick(RotorSpin.FULL_SPIN_PERMILLE, false);
		float working = RotorSpin.radiansPerTick(RotorSpin.FULL_SPIN_PERMILLE, true);
		assertEquals(RotorSpin.IDLE_RADIANS_PER_TICK, idle, EPS);
		assertEquals(RotorSpin.WORKING_RADIANS_PER_TICK, working, EPS);
		assertTrue(idle > 0.0F, "a spun-up, powered machine with a dead rotor reads as broken");
		assertTrue(idle < working,
				"standing by and working must be two different sights; idle=" + idle + " working=" + working);
	}

	@Test
	@DisplayName("nothing turns faster than the working rate, however large the permille arrives")
	void permilleAboveFullClamps() {
		assertEquals(RotorSpin.WORKING_RADIANS_PER_TICK, RotorSpin.radiansPerTick(5_000, true), EPS,
				"a permille from an older, larger spinupTicks ceiling must clamp rather than overspeed");
		assertEquals(RotorSpin.IDLE_RADIANS_PER_TICK, RotorSpin.radiansPerTick(Integer.MAX_VALUE, false), EPS);
	}

	// ── the clock ──────────────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("the angle is the wrapped clock times the rate")
	void angleIsClockTimesRate() {
		float rate = RotorSpin.radiansPerTick(RotorSpin.FULL_SPIN_PERMILLE, true);
		assertEquals(100.0F * rate, RotorSpin.angle(100L, 0.0F, RotorSpin.FULL_SPIN_PERMILLE, true), EPS);
		assertEquals(100.5F * rate, RotorSpin.angle(100L, 0.5F, RotorSpin.FULL_SPIN_PERMILLE, true), EPS,
				"partial ticks must move the rotor, or it steps 20 times a second instead of turning");
	}

	@Test
	@DisplayName("the clock wraps by a Minecraft day, so a long-running world keeps float precision")
	void clockWrapsByOneDay() {
		float atStart = RotorSpin.angle(7L, 0.25F, 800, true);
		assertEquals(atStart, RotorSpin.angle(7L + RotorSpin.TIME_WRAP, 0.25F, 800, true), EPS);
		assertEquals(atStart, RotorSpin.angle(7L + 5_000L * RotorSpin.TIME_WRAP, 0.25F, 800, true), EPS,
				"a world up for weeks must not lose the fractional tick to float rounding");
		// Negative control: within one day the angle really does change, so the test above is pinning the
		// wrap rather than a function that ignores its clock.
		assertTrue(Math.abs(atStart - RotorSpin.angle(7L + 11_000L, 0.25F, 800, true)) > 1.0F,
				"if the angle did not depend on the clock at all, the wrap assertions would be vacuous");
	}

	@Test
	@DisplayName("a clock running backwards still turns the rotor forwards")
	void negativeClockDoesNotReverseTheRotor() {
		assertTrue(RotorSpin.angle(-1L, 0.0F, RotorSpin.FULL_SPIN_PERMILLE, true) > 0.0F,
				"a plain % would wrap a negative tick to a negative angle and spin the rotor backwards");
	}
}
