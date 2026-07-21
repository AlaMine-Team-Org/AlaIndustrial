package dev.alaindustrial.core.fluid;

/**
 * Pure long-math extraction from {@link FluidMover#move} (MOD-113), mirroring the rationale for
 * {@link TankMath}: {@link FluidMover}'s API takes {@link FluidPort} + {@link FluidHolder} (both
 * coupled to {@code net.minecraft.world.level.material.Fluid}, absent from {@code :common}'s L1 test
 * runtime — verified by smoke test, see MOD-113 task.md), so {@link FluidMover} itself cannot be
 * exercised by an L1 JUnit suite. Its 12-mutant NO_COVERAGE hole in the MOD-110 baseline is the
 * refund-arithmetric — extracted here so the L1 suite + pitest can cover it.
 *
 * <p><b>Behavioural identity.</b> {@link FluidMover#move} calls these helpers verbatim. The inline
 * expressions they replace were:
 * <ul>
 *   <li>{@code extracted <= 0} short-circuit (L33) — see {@link #nothingExtracted};</li>
 *   <li>{@code inserted < extracted} refund-needed test (L37) — see {@link #shortfallNeeded};</li>
 *   <li>{@code extracted - inserted} shortfall (L42) — see {@link #shortfall};</li>
 *   <li>{@code inserted + refunded} total-moved return (L44) — see {@link #movedWithRefund}.</li>
 * </ul>
 * Pure extract: same results, no new branching, no MC types.
 */
final class FluidMoverMath {

	private FluidMoverMath() {
	}

	/**
	 * True when the source yielded nothing — the short-circuit that returns 0 from {@link FluidMover#move}
	 * before attempting an insert. Boundary: {@code extracted == 0} counts as "nothing" (a probe or an
	 * empty source). The {@code <=} is intentional: a negative {@code extract} return is out-of-contract
	 * for a well-formed {@link FluidPort} but is treated as "nothing" defensively.
	 */
	static boolean nothingExtracted(long extracted) {
		return extracted <= 0;
	}

	/**
	 * True when the target accepted less than was extracted — the refund path is needed. Boundary:
	 * {@code inserted == extracted} does NOT trigger a refund (the common full-acceptance case). The
	 * strict {@code <} (vs {@code <=}) is what keeps a full-acceptance move off the refund path.
	 */
	static boolean shortfallNeeded(long inserted, long extracted) {
		return inserted < extracted;
	}

	/**
	 * The amount to put back into the source: exactly what the target refused. The MATH mutant flips
	 * {@code extracted - inserted} to {@code extracted + inserted} — which would refund the SUM,
	 * duplicating the moved fluid and destroying conservation. This extraction puts that mutant in
	 * pitest's reach.
	 */
	static long shortfall(long extracted, long inserted) {
		return extracted - inserted;
	}

	/**
	 * The total amount that ended up in the target: what it accepted directly ({@code inserted}) plus
	 * what was refunded back into the source ({@code refunded}). The MATH mutant flips the {@code +} to
	 * {@code -}, so a partial-acceptance move would report {@code inserted - refunded} — under-reporting
	 * the moved amount by twice the refund. A PrimitiveReturns mutant zeroing the return would report 0
	 * on every partial-acceptance move.
	 */
	static long movedWithRefund(long inserted, long refunded) {
		return inserted + refunded;
	}
}
