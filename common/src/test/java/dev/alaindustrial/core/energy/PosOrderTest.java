package dev.alaindustrial.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * The rule that decides who gets energy first at equal distance — held in place by tests, at last.
 *
 * <p><b>Why this class exists.</b> {@link PosOrder} was written wrong on the first attempt (it sorted
 * by the packed {@code BlockPos.asLong()}), that version was committed, and it was caught by a human
 * reading the code — not by any gate. Nothing in the build could have caught it: the architecture rule
 * only inspects collection TYPES, and the world gametests never detected the non-determinism in the
 * first place, which is precisely why the task existed. So the regression that matters most here —
 * "someone simplifies the comparator back to something that is not translation-invariant" — had no
 * test at all. It has one now, and the property below fails on exactly that mutation.
 */
class PosOrderTest {

	/**
	 * Coordinates are kept well inside {@code int} range on purpose: the properties translate positions
	 * by a delta, and the point is to test the ORDERING rule, not what happens when x + dx overflows.
	 * ±30 000 000 is the vanilla world border, so this is the whole reachable world and then some.
	 */
	@Provide
	Arbitrary<Integer> coordinate() {
		return Arbitraries.integers().between(-30_000_000, 30_000_000);
	}

	/** Translation deltas small enough that coordinate + delta cannot overflow the range above. */
	@Provide
	Arbitrary<Integer> delta() {
		return Arbitraries.integers().between(-1_000_000, 1_000_000);
	}

	// ── the property the wrong implementation violated ───────────────────────────────────────────

	/**
	 * <b>Translation invariance.</b> Moving both positions by the same vector never changes their
	 * relative order. This is the whole point of the rule: the same layout must behave the same
	 * wherever it is built.
	 *
	 * <p>This is the property {@code comparingLong(BlockPos::asLong)} fails — verified counterexample:
	 * {@code (0,64,-1)} vs {@code (0,64,0)} flips when both shift by +1 on z, because the packed form
	 * compares each coordinate inside a bit field where a negative value looks large and positive.
	 */
	@Property
	void orderSurvivesTranslation(
			@ForAll("coordinate") int x1, @ForAll("coordinate") int y1, @ForAll("coordinate") int z1,
			@ForAll("coordinate") int x2, @ForAll("coordinate") int y2, @ForAll("coordinate") int z2,
			@ForAll("delta") int dx, @ForAll("delta") int dy, @ForAll("delta") int dz) {
		int before = Integer.signum(PosOrder.compare(x1, y1, z1, x2, y2, z2));
		int after = Integer.signum(
				PosOrder.compare(x1 + dx, y1 + dy, z1 + dz, x2 + dx, y2 + dy, z2 + dz));
		assertEquals(before, after,
				"translating both positions by (" + dx + "," + dy + "," + dz + ") changed their order");
	}

	/**
	 * The documented counterexample, pinned as a point test so the class also fails loudly if someone
	 * "simplifies" the comparator back to the packed form — a property can in principle shrink to a
	 * confusing case, this one names it.
	 */
	@Test
	void packedFormWouldFlipAcrossZeroButThisRuleDoesNot() {
		// (0,64,-1) vs (0,64,0), then both shifted by +1 on z.
		int before = Integer.signum(PosOrder.compare(0, 64, -1, 0, 64, 0));
		int after = Integer.signum(PosOrder.compare(0, 64, 0, 0, 64, 1));
		assertEquals(-1, before, "(0,64,-1) must sort before (0,64,0)");
		assertEquals(before, after, "a plain +1 translation on z must not flip the order");
	}

	// ── total-order axioms ───────────────────────────────────────────────────────────────────────

	/** Antisymmetry: swapping the arguments flips the sign, and equal positions compare equal. */
	@Property
	void isAntisymmetric(
			@ForAll("coordinate") int x1, @ForAll("coordinate") int y1, @ForAll("coordinate") int z1,
			@ForAll("coordinate") int x2, @ForAll("coordinate") int y2, @ForAll("coordinate") int z2) {
		int forward = Integer.signum(PosOrder.compare(x1, y1, z1, x2, y2, z2));
		int backward = Integer.signum(PosOrder.compare(x2, y2, z2, x1, y1, z1));
		assertEquals(-forward, backward, "compare is not antisymmetric");
	}

	/** Consistency with equality: compare == 0 exactly when all three coordinates match. */
	@Property
	void zeroExactlyWhenEqual(
			@ForAll("coordinate") int x1, @ForAll("coordinate") int y1, @ForAll("coordinate") int z1,
			@ForAll("coordinate") int x2, @ForAll("coordinate") int y2, @ForAll("coordinate") int z2) {
		boolean same = x1 == x2 && y1 == y2 && z1 == z2;
		assertEquals(same, PosOrder.compare(x1, y1, z1, x2, y2, z2) == 0,
				"compare == 0 must mean the same position and nothing else");
	}

	/** Transitivity over a random triple — a comparator that breaks it corrupts every sort. */
	@Property
	void isTransitive(
			@ForAll("coordinate") int ax, @ForAll("coordinate") int ay, @ForAll("coordinate") int az,
			@ForAll("coordinate") int bx, @ForAll("coordinate") int by, @ForAll("coordinate") int bz,
			@ForAll("coordinate") int cx, @ForAll("coordinate") int cy, @ForAll("coordinate") int cz) {
		int ab = PosOrder.compare(ax, ay, az, bx, by, bz);
		int bc = PosOrder.compare(bx, by, bz, cx, cy, cz);
		if (ab <= 0 && bc <= 0) {
			assertTrue(PosOrder.compare(ax, ay, az, cx, cy, cz) <= 0, "a≤b and b≤c but a>c");
		}
		if (ab >= 0 && bc >= 0) {
			assertTrue(PosOrder.compare(ax, ay, az, cx, cy, cz) >= 0, "a≥b and b≥c but a<c");
		}
	}

	/**
	 * No overflow. Subtraction-based comparators ({@code x1 - x2}) look right and invert silently once
	 * the operands are far enough apart; this pins the two extremes so that shortcut cannot creep in.
	 */
	@Test
	void extremeCoordinatesDoNotOverflow() {
		assertTrue(PosOrder.compare(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0) < 0,
				"MIN_VALUE must sort before MAX_VALUE on x");
		assertTrue(PosOrder.compare(0, Integer.MAX_VALUE, 0, 0, Integer.MIN_VALUE, 0) > 0,
				"MAX_VALUE must sort after MIN_VALUE on y");
		assertTrue(PosOrder.compare(0, 0, Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE) < 0,
				"MIN_VALUE must sort before MAX_VALUE on z");
	}

	/** Axis precedence: x dominates y, y dominates z. A reordered chain would break tie-breaking. */
	@Test
	void axisPrecedenceIsXThenYThenZ() {
		assertTrue(PosOrder.compare(0, 100, 100, 1, 0, 0) < 0, "x must dominate y and z");
		assertTrue(PosOrder.compare(0, 0, 100, 0, 1, 0) < 0, "y must dominate z");
	}
}
