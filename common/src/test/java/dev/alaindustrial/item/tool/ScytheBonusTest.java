package dev.alaindustrial.item.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1 suite for the scythe's bonus-seed chance formula (MOD-315).
 *
 * <p>This formula is the whole balance contract of the mechanic: one server-side multiplier scales
 * an eight-tier ladder. What it has to survive is a hand-edited config file, so most cases below are
 * malformed inputs (negative, NaN, infinity, absurdly large) rather than the happy path — an
 * unclamped chance hands out a guaranteed drop, and a NaN disables the mechanic silently. Both read
 * to a server owner as "the config did nothing".
 */
class ScytheBonusTest {

	/** The iron tier's shipped chance — a mid-ladder value, see {@code ScytheTiers.IRON}. */
	private static final float IRON_CHANCE = 0.12f;

	@Test
	@DisplayName("multiplier 1.0 leaves the tier's shipped chance untouched")
	void neutralMultiplierKeepsTierChance() {
		assertEquals(IRON_CHANCE, ScytheBonus.effectiveChance(IRON_CHANCE, 1.0), 1.0e-6);
	}

	@Test
	@DisplayName("multiplier scales the tier chance proportionally")
	void multiplierScalesChance() {
		assertEquals(0.06, ScytheBonus.effectiveChance(IRON_CHANCE, 0.5), 1.0e-6);
		assertEquals(0.24, ScytheBonus.effectiveChance(IRON_CHANCE, 2.0), 1.0e-6);
	}

	@Test
	@DisplayName("multiplier 0 disables the mechanic")
	void zeroMultiplierDisablesBonus() {
		assertEquals(0.0, ScytheBonus.effectiveChance(IRON_CHANCE, 0.0));
	}

	@Test
	@DisplayName("a tier with no bonus (wood) stays at zero however large the multiplier")
	void zeroTierChanceStaysZero() {
		assertEquals(0.0, ScytheBonus.effectiveChance(0.0f, 1000.0));
	}

	@Test
	@DisplayName("the result never exceeds a certainty of 1.0")
	void resultIsClampedToOne() {
		assertEquals(1.0, ScytheBonus.effectiveChance(0.5f, 4.0));
		assertEquals(1.0, ScytheBonus.effectiveChance(IRON_CHANCE, Double.MAX_VALUE));
	}

	@Test
	@DisplayName("exactly reaching 1.0 stays 1.0 rather than tipping over")
	void exactCertaintyIsPreserved() {
		assertEquals(1.0, ScytheBonus.effectiveChance(0.25f, 4.0));
	}

	@Test
	@DisplayName("a negative multiplier floors to zero rather than going negative")
	void negativeMultiplierFloorsToZero() {
		assertEquals(0.0, ScytheBonus.effectiveChance(IRON_CHANCE, -5.0));
	}

	@Test
	@DisplayName("NaN from a hand-edited config normalizes to zero, not to a NaN chance")
	void nanMultiplierFloorsToZero() {
		assertEquals(0.0, ScytheBonus.effectiveChance(IRON_CHANCE, Double.NaN));
	}

	@Test
	@DisplayName("infinity clamps to certainty instead of overflowing")
	void infiniteMultiplierClampsToOne() {
		assertEquals(1.0, ScytheBonus.effectiveChance(IRON_CHANCE, Double.POSITIVE_INFINITY));
		assertEquals(0.0, ScytheBonus.effectiveChance(IRON_CHANCE, Double.NEGATIVE_INFINITY));
	}

	/**
	 * The invariant the roll site depends on: whatever a config file contains, the result is always a
	 * usable probability. {@code RandomSource.nextDouble() >= chance} would misbehave for a negative
	 * or NaN chance, so this is the guarantee that keeps the caller free of its own guards.
	 */
	@Property
	@DisplayName("the result is always a valid probability, for any tier chance and any multiplier")
	void resultIsAlwaysAValidProbability(
			@ForAll @FloatRange(min = 0.0f, max = 1.0f) float tierChance,
			@ForAll double multiplier) {
		double chance = ScytheBonus.effectiveChance(tierChance, multiplier);
		assertTrue(chance >= 0.0 && chance <= 1.0,
				"chance out of range: " + chance + " (tier=" + tierChance + ", mult=" + multiplier + ")");
	}
}
