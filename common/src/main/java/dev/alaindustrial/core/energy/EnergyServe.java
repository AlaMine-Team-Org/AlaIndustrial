package dev.alaindustrial.core.energy;
import dev.alaindustrial.core.fluid.FluidMover;

/**
 * Pure long-math extraction from the runtime serve-class loop (MOD-144), the runtime counterpart
 * of the {@link EnergyShare} formulas. {@link EnergyShare#cableLoss} computes how much EU to destroy
 * per consumer per tick (the resistive-loss model introduced in MOD-021); the lines below compute what
 * follows from that loss — the EU actually delivered, the surplus to refund when a consumer accepts
 * less than offered, and the total drawn from the producer pool (delivered + destroyed). They live in
 * the runtime transport path (inside the serve-class loop in the distribution kernel), so a single
 * {@code +→−} or {@code −→+} mutation there is the canonical EU-creation / EU-destruction bug — and
 * because the network kernel is MC-coupled and excluded from pitest (it references
 * {@code net.minecraft.server.level.ServerLevel}), those mutants were previously neither generated
 * nor covered by any L1 test. Extracting the arithmetic here lets the L1 suite (and pitest) cover
 * exactly the runtime loss-application kernel.
 *
 * <p><b>Behavioural identity.</b> The distribution kernel's serve-class loop calls these helpers verbatim. The
 * inline expressions they replace were:
 * <ul>
 *   <li>{@code toDeliver = pulled - loss} (L390) — {@link #deliverAfterLoss};</li>
 *   <li>{@code surplus = toDeliver - inserted} (L392) — {@link #surplus};</li>
 *   <li>{@code consumed += inserted + loss} (L398) — {@link #consumed}.</li>
 * </ul>
 * Pure extract: same results, same exceptions, no new branching. No Minecraft imports, no side effects:
 * every method is a deterministic function of its {@code long} inputs, exactly mirroring the
 * {@code TankMath}/{@link EnergyShare} extraction pattern.
 *
 * <p><b>Why the {@code Math.max(0, …)} clamps are load-bearing.</b> A mutated {@code pulled - loss}
 * that yields negative (e.g. the {@code -} flipped to {@code +}, or a {@code loss > pulled} case from
 * a {@link EnergyShare#cableLoss} regression) would feed a negative {@code toDeliver} to the consumer's
 * {@code insert} — which is out-of-contract (negative insert amounts are rejected with
 * {@link IllegalArgumentException} on most {@link EnergyPort} implementations). The
 * {@link #deliverAfterLoss} clamp keeps the runtime path numerically safe under mutation, but the L1
 * suite asserts the clamp's output regardless, so the mutant still dies in pitest.
 */
public final class EnergyServe {

	private EnergyServe() {
	}

	/**
	 * EU to insert into the consumer this tick: {@code max(0, pulled - loss)}. Clamped to 0 so a loss
	 * that exceeds what was pulled (an out-of-contract case — {@link EnergyShare#cableLoss} clamps loss
	 * to {@code [0, gross]}, but this guard is defensive against future regressions in that clamp) does
	 * not feed a negative amount to {@link EnergyPort#insert}.
	 *
	 * @param pulled EU actually drawn from the producer pool toward this consumer (≥ 0)
	 * @param loss   EU destroyed in cable transit (in {@code [0, pulled]}, per {@link EnergyShare#cableLoss})
	 * @return EU to deliver to the consumer's storage (≥ 0, ≤ {@code pulled})
	 */
	public static long deliverAfterLoss(long pulled, long loss) {
		return Math.max(0L, pulled - loss);
	}

	/**
	 * EU to refund to the producer pool: what was offered ({@code toDeliver}) but not accepted by the
	 * consumer's {@code insert}. Clamped to 0 — {@code insert} never returns more than the offered
	 * amount, so {@code toDeliver - inserted} is always ≥ 0 for well-formed {@link EnergyPort}s, but
	 * the clamp keeps the runtime path safe if that invariant ever regresses. The refunded EU is pushed
	 * back via {@link EnergyNetwork}'s {@code returnRoundRobin} (the same producer pool it was pulled
	 * from), so a partial-acceptance move never destroys EU — mirrors {@code FluidMover}'s refund path.
	 *
	 * @param toDeliver EU offered to the consumer (= {@link #deliverAfterLoss})
	 * @param inserted  EU the consumer actually accepted
	 * @return EU to push back into the producer pool (≥ 0, ≤ {@code toDeliver})
	 */
	public static long surplus(long toDeliver, long inserted) {
		return Math.max(0L, toDeliver - inserted);
	}

	/**
	 * EU to charge against the shared supply pool for this consumer: what ended up in the consumer's
	 * storage ({@code inserted}) plus what was destroyed in cable transit ({@code loss}). The {@code +}
	 * is the load-bearing operand — a {@code +→−} mutant would under-count consumption, leaving
	 * {@code remainingSupply} too high, so the next consumer class (storage sinks) is served from a
	 * budget that pretends the loss was never spent (silent EU creation at the pool level).
	 *
	 * @param inserted EU the consumer actually accepted
	 * @param loss     EU destroyed in cable transit
	 * @return total EU drawn from the pool for this consumer (≥ {@code inserted}, ≥ {@code loss})
	 */
	public static long consumed(long inserted, long loss) {
		return inserted + loss;
	}
}
