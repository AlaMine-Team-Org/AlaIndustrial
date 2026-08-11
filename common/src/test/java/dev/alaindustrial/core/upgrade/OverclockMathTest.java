package dev.alaindustrial.core.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1 for the overclocker chip's arithmetic (MOD-392).
 *
 * <p>The shipped tuning is {@code speedFactor = 0.8}, {@code euFactor = 2.0}, ceiling 4, with LV = 32
 * EU/t and MV = 128 EU/t. The numbers pinned below are the ones {@code docs/PERFORMANCE.md} prints.
 */
class OverclockMathTest {

	private static final float SPEED = 0.8f;
	private static final float EU = 2.0f;
	/** Three crafted chips (I, II, III) — the shipped {@code overclockerMaxPerMachine}. */
	private static final int CEILING = 3;
	private static final long LV = 32L;
	private static final long MV = 128L;

	@Test
	@DisplayName("cap: a machine runs only as many steps as its tier can feed")
	void capFollowsTheTier() {
		// 2 EU/t on LV: 4 → 8 → 16, all three tiers fit under the 32 EU/t ceiling.
		assertEquals(3, OverclockMath.cap(2, LV, EU, CEILING));
		// 8 EU/t on LV: 16 → 32 and no further — tier III would demand 64 on a 32 EU/t tier.
		assertEquals(2, OverclockMath.cap(8, LV, EU, CEILING));
		// 12 EU/t on MV: 24 → 48 → 96; all three fit under MV's 128.
		assertEquals(3, OverclockMath.cap(12, MV, EU, CEILING));
	}

	@Test
	@DisplayName("cap: no chip is ever handed out that the tier cannot actually feed")
	void capNeverExceedsTheTier() {
		for (int base : new int[] { 1, 2, 3, 8, 12, 16, 31, 32 }) {
			for (long tier : new long[] { LV, MV, 512L }) {
				int chips = OverclockMath.cap(base, tier, EU, CEILING);
				long draw = OverclockMath.euPerTick(base, 1.0f, EU, chips);
				assertTrue(draw <= tier,
						"base " + base + " on tier " + tier + " → " + chips + " chips draws " + draw);
			}
		}
	}

	@Test
	@DisplayName("cap: degenerate tunings disable the chip instead of dividing by zero")
	void capHandlesDegenerateTunings() {
		assertEquals(0, OverclockMath.cap(2, LV, EU, 0), "a zero ceiling means no steps");
		assertEquals(0, OverclockMath.cap(2, LV, 1.0f, CEILING), "a factor of 1 would be free speed");
		assertEquals(0, OverclockMath.cap(2, LV, 0.5f, CEILING), "a factor below 1 would pay the player");
		assertEquals(0, OverclockMath.cap(0, LV, EU, CEILING), "a machine that draws nothing");
		assertEquals(0, OverclockMath.cap(64, LV, EU, CEILING), "already past its tier: no room at all");
	}

	@Test
	@DisplayName("euPerTick: each tier doubles the draw")
	void drawDoublesPerChip() {
		assertEquals(2, OverclockMath.euPerTick(2, 1.0f, EU, 0));
		assertEquals(4, OverclockMath.euPerTick(2, 1.0f, EU, 1));
		assertEquals(8, OverclockMath.euPerTick(2, 1.0f, EU, 2));
		assertEquals(16, OverclockMath.euPerTick(2, 1.0f, EU, 3));
	}

	@Test
	@DisplayName("duration: each tier cuts the operation to 80 %")
	void durationShrinksPerChip() {
		// The macerator: 150 ticks at 1.0 speed.
		assertEquals(150, OverclockMath.duration(150, SPEED, 0));
		assertEquals(120, OverclockMath.duration(150, SPEED, 1));
		assertEquals(96, OverclockMath.duration(150, SPEED, 2));
		assertEquals(77, OverclockMath.duration(150, SPEED, 3));
	}

	@Test
	@DisplayName("the chip is NOT energy-neutral: E_op rises about 60 % per tier")
	void energyPerOperationRises() {
		// This is the whole point of the feature, and the property that separates it from
		// globalMachineSpeedMultiplier. If a retune ever made E_op flat or falling, the chip would be
		// free speed and this test is the thing that must go red.
		long previous = 0;
		for (int tier = 0; tier <= CEILING; tier++) {
			long eOp = (long) OverclockMath.duration(150, SPEED, tier)
					* OverclockMath.euPerTick(2, 1.0f, EU, tier);
			if (tier > 0) {
				assertTrue(eOp > previous,
						"E_op must grow with the tier: tier " + tier + " costs " + eOp + " vs " + previous);
			}
			previous = eOp;
		}
		// The shipped ladder, exactly as PERFORMANCE.md prints it.
		assertEquals(300, (long) OverclockMath.duration(150, SPEED, 0) * OverclockMath.euPerTick(2, 1.0f, EU, 0));
		assertEquals(480, (long) OverclockMath.duration(150, SPEED, 1) * OverclockMath.euPerTick(2, 1.0f, EU, 1));
		assertEquals(768, (long) OverclockMath.duration(150, SPEED, 2) * OverclockMath.euPerTick(2, 1.0f, EU, 2));
		assertEquals(1232, (long) OverclockMath.duration(150, SPEED, 3) * OverclockMath.euPerTick(2, 1.0f, EU, 3));
	}

	@Test
	@DisplayName("floors: neither a zero-tick operation nor a free tick is reachable")
	void flooredAtOne() {
		assertEquals(1, OverclockMath.duration(1, SPEED, CEILING), "a 1-tick op cannot shrink to 0");
		assertEquals(1, OverclockMath.euPerTick(1, 0.0f, EU, 0), "a 0 multiplier must not make work free");
	}

	@Test
	@DisplayName("a negative tier is treated as none, never as a discount")
	void negativeChipsAreClamped() {
		assertEquals(2, OverclockMath.euPerTick(2, 1.0f, EU, -3));
		assertEquals(150, OverclockMath.duration(150, SPEED, -3));
	}
}
