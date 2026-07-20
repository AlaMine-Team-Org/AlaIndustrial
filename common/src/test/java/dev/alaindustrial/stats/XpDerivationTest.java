package dev.alaindustrial.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * L1 coverage for {@link LevelMath#xpOf} — the MOD-133 mod-XP derivation. Loader- and Config-free
 * (both rates are passed in), so it pins the two-term formula itself: hands-on machine work plus the
 * deliberately weaker generator trickle.
 *
 * <p>This is worth its own suite because XP is <em>derived</em>, never stored. Each term must divide
 * its own running career total; dividing per-event deltas instead would round every contribution to
 * zero (one machine operation is worth ~0.2–0.3 XP at the shipped rates, a generator tick far less).
 * {@link #eachTermDividesTheCareerTotalNotTheDeltas()} is the regression guard for exactly that.
 *
 * @implements mod-XP derivation from career EU totals, generator trickle weighting, truncation safety
 */
class XpDerivationTest {

	private static final int EU_PER_XP = 1000;
	private static final int EU_PER_XP_GENERATED = 20_000;

	@Test
	void machineWorkIsTheDominantTerm() {
		assertEquals(5L, LevelMath.xpOf(5_000L, 0L, EU_PER_XP, EU_PER_XP_GENERATED),
				"5000 useful EU at 1000 EU/XP = 5 XP");
	}

	@Test
	void generatorProductionContributesButFarLess() {
		// The same raw EU is worth 20x less when it merely came out of a generator.
		long eu = 100_000L;
		long fromMachines = LevelMath.xpOf(eu, 0L, EU_PER_XP, EU_PER_XP_GENERATED);
		long fromGenerators = LevelMath.xpOf(0L, eu, EU_PER_XP, EU_PER_XP_GENERATED);
		assertEquals(100L, fromMachines, "100k useful EU = 100 XP");
		assertEquals(5L, fromGenerators, "100k produced EU = 5 XP (20x weaker on purpose)");
		assertTrue(fromGenerators < fromMachines, "unattended production must never out-earn real work");
	}

	@Test
	void bothTermsAddUp() {
		assertEquals(105L, LevelMath.xpOf(100_000L, 100_000L, EU_PER_XP, EU_PER_XP_GENERATED),
				"the two career totals contribute independently");
	}

	@Test
	void eachTermDividesTheCareerTotalNotTheDeltas() {
		// 10_000 macerator operations of 300 EU each. Credited incrementally (300 / 1000 = 0 every
		// time) this would total 0 XP; dividing the accumulated career total yields the true 3000.
		long opEu = 300L;
		long ops = 10_000L;
		// (opEu / EU_PER_XP == 0 — a single op alone truncates to zero; that is the trap being guarded.
		// Not asserted: it divides two local literals and could never fail whatever LevelMath does.)
		assertEquals(3_000L, LevelMath.xpOf(opEu * ops, 0L, EU_PER_XP, EU_PER_XP_GENERATED),
				"career total must not lose the sub-XP remainder of every operation");
	}

	@Test
	void setCommandBacksOutTheGeneratorTerm() {
		// `/ala profile set 100` for a player who produced 400k EU: generators already grant 20 points,
		// so only 80 may come from machines — 80_000 EU. Without the back-out the player would land on
		// 120, and `set` followed by `show` would disagree.
		long consumed = LevelMath.consumedForTargetXp(100L, 400_000L, EU_PER_XP, EU_PER_XP_GENERATED);
		assertEquals(80_000L, consumed, "machine EU must make up only the remainder of the target");
		assertEquals(100L, LevelMath.xpOf(consumed, 400_000L, EU_PER_XP, EU_PER_XP_GENERATED),
				"solving then re-deriving must land exactly on the requested target");
	}

	@Test
	void setCommandRefusesAnUnreachableTarget() {
		// Production alone grants 20 points here, so 5 is unreachable without destroying career EU.
		// -1 is the refusal signal; the command surfaces the floor instead of silently missing.
		assertEquals(-1L, LevelMath.consumedForTargetXp(5L, 400_000L, EU_PER_XP, EU_PER_XP_GENERATED),
				"a target below the generator floor must be refused, not silently clamped");
		// Exactly at the floor is reachable, with a zero machine term — the boundary, not an error.
		assertEquals(0L, LevelMath.consumedForTargetXp(20L, 400_000L, EU_PER_XP, EU_PER_XP_GENERATED),
				"landing exactly on the floor is reachable with no machine EU at all");
	}

	@Test
	void degenerateRatesAreSafe() {
		assertEquals(0L, LevelMath.xpOf(0L, 0L, EU_PER_XP, EU_PER_XP_GENERATED), "a fresh player has 0 XP");
		// Config clamps both rates to >= 1, but the pure function must not divide by zero regardless.
		assertEquals(150L, LevelMath.xpOf(100L, 50L, 0, 0), "non-positive rates clamp to 1, never throw");
	}
}
