package dev.alaindustrial.core.fluid;
import dev.alaindustrial.core.energy.EnergyBuffer;

/**
 * Pure long-math extraction from {@link FluidTank} (MOD-113), the fluid counterpart of the inline
 * math {@link EnergyBuffer} keeps private. {@link FluidTank}'s API takes a {@link FluidHolder}
 * (which wraps {@code net.minecraft.world.level.material.Fluid}, a type absent from {@code :common}'s
 * L1 test runtime — verified by smoke test, see MOD-113 task.md), so {@link FluidTank} itself cannot
 * be exercised by an L1 JUnit suite. Extracting the transfer arithmetic here — no MC types, no side
 * effects, every method a deterministic function of its {@code long} inputs — lets the L1 suite (and
 * pitest) cover exactly the math that was a 42-mutant NO_COVERAGE hole in the MOD-110 baseline.
 *
 * <p><b>Behavioural identity.</b> {@link FluidTank#insert}/{@link FluidTank#extract} call these
 * helpers verbatim — the inline expressions they replace were
 * {@code Math.min(maxAmount, capacity - amount)} / {@code Math.min(maxAmount, amount)} and the
 * {@code amount == 0} fluid-clear tests in {@link FluidTank#readSnapshot}/{@link FluidTank#onFinalCommit}.
 * This is a pure extract: same results, same exceptions, no new branching. The math mirrors
 * {@link EnergyBuffer}'s transfer kernel (both reimplement the single-variant-storage semantics the
 * loaders share) minus the per-op {@code maxInsert}/{@code maxExtract} rate caps — a fluid tank has
 * no rate limit, only the capacity ceiling.
 */
final class TankMath {

	private TankMath() {
	}

	/**
	 * @throws IllegalArgumentException if {@code capacity < 0} — a tank with negative capacity is invalid.
	 * 		(Boundary: {@code 0} is legal — a zero-capacity tank is the "decorative / sealed" degenerate case.)
	 */
	static long checkCapacity(long capacity) {
		if (capacity < 0) {
			throw new IllegalArgumentException("Fluid tank capacity must be non-negative");
		}
		return capacity;
	}

	/**
	 * Amount that fits into a tank holding {@code amount} mB against a {@code capacity} ceiling, capped
	 * by the caller's {@code maxAmount}. Mirrors {@code SingleVariantStorage}'s
	 * {@code Math.min(maxAmount, capacity - amount)}: negative {@code room} (overfill, can't happen on a
	 * well-formed tank) floors at 0 via the {@code Math.min} against the non-negative {@code maxAmount}.
	 */
	static long toInsert(long amount, long capacity, long maxAmount) {
		return Math.min(maxAmount, capacity - amount);
	}

	/**
	 * Amount that can be taken out of a tank holding {@code amount} mB, capped by the caller's
	 * {@code maxAmount}. Mirrors {@code Math.min(maxAmount, amount)}: never extracts more than is stored.
	 */
	static long toExtract(long amount, long maxAmount) {
		return Math.min(maxAmount, amount);
	}

	/**
	 * Whether the stored amount has hit the floor — the trigger for the fluid-clear invariant at both
	 * transaction terminals ({@link FluidTank#readSnapshot} rollback-to-zero,
	 * {@link FluidTank#onFinalCommit} commit-to-zero). Kept here so the {@code == 0} boundary is
	 * unit-testable without a live {@link FluidHolder}.
	 */
	static boolean shouldClearFluid(long amount) {
		return amount == 0;
	}

	/**
	 * Whether a tank of this {@code capacity} exposes the capability at all. {@link FluidTank} reports
	 * {@code capacity > 0} for BOTH insert and extract: a zero-capacity tank is sealed (its disc in the
	 * GUI shows nothing, and no neighbour can push into or pull from it). The {@code > 0} boundary
	 * (vs {@code >= 0}) is the pitest-killed mutant — a flip to {@code >= 0} would report capability on
	 * a tank that can hold nothing.
	 */
	static boolean supportsOp(long capacity) {
		return capacity > 0;
	}
}
