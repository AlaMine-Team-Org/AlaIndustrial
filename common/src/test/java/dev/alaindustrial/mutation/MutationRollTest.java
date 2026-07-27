package dev.alaindustrial.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.mutation.MutationRoll.Outcome;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link MutationRoll} — the incubator dice (MOD-118). Pure math, no Minecraft on
 * the classpath. Pins the balance thresholds and the exact meaning of each band, so a reordered
 * comparison or a changed constant is caught here rather than in game.
 */
class MutationRollTest {

	/** A DoubleSupplier replaying a fixed script; throws if more values are drawn than scripted. */
	private static DoubleSupplier script(double... values) {
		return new DoubleSupplier() {
			private int i = 0;

			@Override
			public double getAsDouble() {
				if (i >= values.length) {
					throw new AssertionError("the roll drew more random values than the test scripted");
				}
				return values[i++];
			}
		};
	}

	@Test
	void rollBelowSuccessChanceSucceeds() {
		assertEquals(Outcome.SUCCESS, MutationRoll.rollOutcome(0.45, 0.05, script(0.0)));
		assertEquals(Outcome.SUCCESS, MutationRoll.rollOutcome(0.45, 0.05, script(0.4499)));
	}

	/** Slag occupies the band directly above success: [success, success + slag). */
	@Test
	void rollJustAboveSuccessGivesSlag() {
		assertEquals(Outcome.SLAG, MutationRoll.rollOutcome(0.45, 0.05, script(0.45)));
		assertEquals(Outcome.SLAG, MutationRoll.rollOutcome(0.45, 0.05, script(0.4999)));
	}

	@Test
	void rollAboveSuccessPlusSlagFails() {
		assertEquals(Outcome.FAILURE, MutationRoll.rollOutcome(0.45, 0.05, script(0.5)));
		assertEquals(Outcome.FAILURE, MutationRoll.rollOutcome(0.45, 0.05, script(0.99)));
	}

	/** Slag is carved out of the failure share — it never eats into the success chance. */
	@Test
	void slagNeverReducesSuccessShare() {
		assertEquals(Outcome.SUCCESS, MutationRoll.rollOutcome(0.45, 0.9, script(0.4499)));
		assertEquals(Outcome.SLAG, MutationRoll.rollOutcome(0.45, 0.9, script(0.99)));
	}

	/** With a 0.95 success chance the 0.05 slag share exactly fills the rest; no empty failure left. */
	@Test
	void slagShrinksToTheRemainingFailureRoom() {
		assertEquals(Outcome.SLAG, MutationRoll.rollOutcome(0.95, 0.05, script(0.96)));
		assertEquals(Outcome.SUCCESS, MutationRoll.rollOutcome(1.0, 0.05, script(0.999)));
	}

	@Test
	void geneBonusRaisesChanceButNeverPastTheCap() {
		assertEquals(0.45, MutationRoll.effectiveChance(0.45, MutationGrade.COMMON, 0.95), 1e-9);
		assertEquals(0.50, MutationRoll.effectiveChance(0.45, MutationGrade.RARE, 0.95), 1e-9);
		assertEquals(0.55, MutationRoll.effectiveChance(0.45, MutationGrade.EPIC, 0.95), 1e-9);
		assertEquals(0.60, MutationRoll.effectiveChance(0.45, MutationGrade.LEGENDARY, 0.95), 1e-9);
		// wheat seeds at 0.85 plus a legendary gene would reach 1.00 — the cap keeps it uncertain
		assertEquals(0.95, MutationRoll.effectiveChance(0.85, MutationGrade.LEGENDARY, 0.95), 1e-9);
	}

	@Test
	void nullInputGradeCountsAsNoBonus() {
		assertEquals(0.45, MutationRoll.effectiveChance(0.45, null, 0.95), 1e-9);
	}

	/**
	 * Grade bands are read from the rarest down: legendary, then epic, then rare, then common.
	 *
	 * <p>The band edges are compared against accumulated sums, and 0.02 + 0.08 + 0.20 is
	 * 0.30000000000000004 in binary floating point — so the outcome exactly at a nominal edge is
	 * undefined by a few ulps. That is irrelevant in game (the odds of drawing precisely 0.3 are
	 * nil), so the test asserts just inside and just outside each band instead of on the edge.
	 */
	@Test
	void gradeBandsFollowTheConfiguredShares() {
		assertEquals(MutationGrade.LEGENDARY, MutationRoll.rollGrade(0.20, 0.08, 0.02, script(0.0)));
		assertEquals(MutationGrade.LEGENDARY, MutationRoll.rollGrade(0.20, 0.08, 0.02, script(0.0199)));
		assertEquals(MutationGrade.EPIC, MutationRoll.rollGrade(0.20, 0.08, 0.02, script(0.0201)));
		assertEquals(MutationGrade.EPIC, MutationRoll.rollGrade(0.20, 0.08, 0.02, script(0.0999)));
		assertEquals(MutationGrade.RARE, MutationRoll.rollGrade(0.20, 0.08, 0.02, script(0.1001)));
		assertEquals(MutationGrade.RARE, MutationRoll.rollGrade(0.20, 0.08, 0.02, script(0.2999)));
		assertEquals(MutationGrade.COMMON, MutationRoll.rollGrade(0.20, 0.08, 0.02, script(0.3001)));
		assertEquals(MutationGrade.COMMON, MutationRoll.rollGrade(0.20, 0.08, 0.02, script(0.999)));
	}

	/**
	 * A roll landing exactly on a band edge belongs to the LOWER grade — the comparisons are
	 * strict (MOD-244).
	 *
	 * <p>{@link #gradeBandsFollowTheConfiguredShares()} deliberately steps around the edges because
	 * 0.02 + 0.08 + 0.20 is not exactly 0.30 in binary floating point. Quarter shares have no such
	 * problem: 0.25, 0.50 and 0.75 are all exactly representable, so each edge can be asserted
	 * literally and a comparison relaxed to {@code <=} is caught instead of hiding behind the ulps.
	 */
	@Test
	void aRollExactlyOnABandEdgeTakesTheLowerGrade() {
		assertEquals(MutationGrade.EPIC, MutationRoll.rollGrade(0.25, 0.25, 0.25, script(0.25)));
		assertEquals(MutationGrade.RARE, MutationRoll.rollGrade(0.25, 0.25, 0.25, script(0.50)));
		assertEquals(MutationGrade.COMMON, MutationRoll.rollGrade(0.25, 0.25, 0.25, script(0.75)));
	}

	@Test
	void commonGradeLeavesNoTraceAndGrantsNoBonus() {
		assertEquals(0.0, MutationGrade.COMMON.geneBonus(), 1e-9);
		assertEquals(false, MutationGrade.COMMON.isMarked());
		assertEquals(true, MutationGrade.RARE.isMarked());
	}

	/**
	 * Serialized name and lang key of a grade (MOD-244). {@code serializedName()} is what goes into
	 * the {@code alaindustrial:mutation_grade} data component, so a changed spelling silently
	 * orphans every graded item already in a world; the key is what en_us.json ships.
	 */
	@Test
	void gradeNamesAndKeysAreStable() {
		assertEquals("rare", MutationGrade.RARE.serializedName());
		assertEquals("legendary", MutationGrade.LEGENDARY.serializedName());
		assertEquals("tooltip.alaindustrial.mutation_grade.rare", MutationGrade.RARE.translationKey());
		assertEquals("tooltip.alaindustrial.mutation_grade.epic", MutationGrade.EPIC.translationKey());
		assertEquals("tooltip.alaindustrial.mutation_grade.legendary",
				MutationGrade.LEGENDARY.translationKey());
	}

	@Test
	void byOrdinalAndByNameFallBackToCommon() {
		assertEquals(MutationGrade.EPIC, MutationGrade.byOrdinal(2));
		assertEquals(MutationGrade.COMMON, MutationGrade.byOrdinal(-1));
		assertEquals(MutationGrade.COMMON, MutationGrade.byOrdinal(99));
		// The first ordinal past the end: an off-by-one in the upper bound indexes the array and
		// throws instead of falling back (MOD-244).
		assertEquals(MutationGrade.COMMON, MutationGrade.byOrdinal(MutationGrade.values().length));
		assertEquals(MutationGrade.LEGENDARY, MutationGrade.byName("legendary"));
		assertEquals(MutationGrade.COMMON, MutationGrade.byName("nonsense"));
		assertEquals(MutationGrade.COMMON, MutationGrade.byName(null));
	}
}
