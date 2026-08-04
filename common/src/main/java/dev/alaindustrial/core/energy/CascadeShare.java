package dev.alaindustrial.core.energy;

/**
 * How much EU a fuller store may hand to an emptier one in one tick (MOD-314) — the storage→storage
 * cascade, so "put down a second Battery Box to extend the bank" actually works.
 *
 * <p>Minecraft-free on purpose: this is the whole decision, it is pure arithmetic, and the alternative
 * (leaving it inline in {@code EnergyNetwork}) would put it in a class the L1 suite cannot reach — the
 * exact shape MOD-324 spent a task cleaning up.
 *
 * <h2>Why not simply "transfer while the donor is fuller"</h2>
 *
 * That rule does not converge here, and the failure is not subtle: it drains the player's batteries.
 * Three separate reasons stack up.
 *
 * <ul>
 *   <li><b>Transfer is quantised.</b> EU moves through cable segment buffers ({@code Config.cableBuffer},
 *       12 by default), not in infinitesimal amounts. With a strict "donor fraction &gt; sink fraction"
 *       test and a fixed packet, a pair one EU apart still moves a whole packet and lands on the other
 *       side of equality. The step does not shrink as the gap shrinks, so the pair settles into a stable
 *       two-cycle rather than a fixed point.</li>
 *   <li><b>Energy in flight is invisible.</b> The line advances one hop per tick, so up to
 *       {@code cableBuffer × cables} EU is already travelling toward the sink but still absent from its
 *       stored amount. The donor keeps donating for as many ticks as the line is long — overshoot scales
 *       with distance, not with packet size.</li>
 *   <li><b>Every hand-off is charged resistive loss</b> (MOD-021, applied per delivery in
 *       {@code serveConsumersFromLine}). So each half-cycle of an oscillation burns EU permanently. Two
 *       boxes on a ring would not merely jitter — they would convert their whole charge to heat and then
 *       keep spinning, which is strictly worse than the bug this fixes.</li>
 * </ul>
 *
 * <h2>The remedy, and where it comes from</h2>
 *
 * This project already solved the identical problem one subsystem over. {@code FluidNetwork.propagateOneHop}
 * pairs a <b>deadband</b> ({@code if (difference <= 1) continue;}) with a <b>half step</b>
 * ({@code moveFluid(..., difference / 2)}), commented as keeping a line from "sloshing back and forth
 * between two segments on consecutive ticks". Same two ingredients here:
 *
 * <ul>
 *   <li><b>Half step.</b> {@link #equalisingTransfer} is the exact amount that would leave both stores at
 *       the same fill fraction; the cascade offers half of it. Halving cannot overshoot, and it makes the
 *       remaining gap shrink geometrically, so flow stops on its own — no "stop exactly at equality" rule
 *       to get wrong.</li>
 *   <li><b>Deadband.</b> Below {@code deadband} EU nothing moves at all. The caller passes
 *       {@code Config.cableBuffer}: the deadband has to be measured in the units transfer actually
 *       arrives in, and per MOD-070 that is the segment buffer — <em>not</em> the tier packet cap, which
 *       is a different and larger ceiling.</li>
 * </ul>
 *
 * <p>Known bound, stated rather than hidden: on a long line the in-flight term above can still exceed one
 * deadband, so a pair can cross equality slightly before settling. The half step makes that excursion
 * shrink every tick instead of repeating, which is what turns a permanent oscillation into a brief one.
 */
public final class CascadeShare {

	private CascadeShare() {
	}

	/**
	 * Compare two stores by fill FRACTION, exactly.
	 *
	 * <p>Cross-multiplication rather than {@code (double) amount / capacity}: the comparison decides
	 * whether energy moves at all, and a rounding tie at the boundary is precisely the input that would
	 * restart an oscillation. Falls back to floating point only on overflow, which needs two capacities
	 * near {@link Integer#MAX_VALUE} — reachable only by hand-editing the config, but the alternative is
	 * a silently flipped sign, so it is handled instead of assumed away.
	 *
	 * @return negative if {@code a} is proportionally emptier, positive if fuller, 0 if equal
	 */
	public static int compareFill(long aAmount, long aCapacity, long bAmount, long bCapacity) {
		if (aCapacity <= 0 || bCapacity <= 0) {
			return 0;
		}
		try {
			return Long.compare(Math.multiplyExact(aAmount, bCapacity), Math.multiplyExact(bAmount, aCapacity));
		} catch (ArithmeticException overflow) {
			return Double.compare((double) aAmount / aCapacity, (double) bAmount / bCapacity);
		}
	}

	/**
	 * EU the donor would have to hand over for both stores to end at the same fill fraction.
	 *
	 * <p>Solving {@code (dAmount − x) / dCapacity == (sAmount + x) / sCapacity} gives
	 * {@code x = (dAmount·sCapacity − sAmount·dCapacity) / (dCapacity + sCapacity)}. Returns 0 when the
	 * donor is not proportionally fuller, so callers need no separate sign check.
	 */
	public static long equalisingTransfer(long donorAmount, long donorCapacity, long sinkAmount, long sinkCapacity) {
		if (donorCapacity <= 0 || sinkCapacity <= 0) {
			return 0L;
		}
		try {
			long numerator = Math.subtractExact(
					Math.multiplyExact(donorAmount, sinkCapacity),
					Math.multiplyExact(sinkAmount, donorCapacity));
			if (numerator <= 0) {
				return 0L;
			}
			return numerator / Math.addExact(donorCapacity, sinkCapacity);
		} catch (ArithmeticException overflow) {
			double x = ((double) donorAmount * sinkCapacity - (double) sinkAmount * donorCapacity)
					/ ((double) donorCapacity + sinkCapacity);
			return x <= 0 ? 0L : (long) Math.min(x, Long.MAX_VALUE);
		}
	}

	/**
	 * EU this donor may push toward this sink in one tick: half the equalising transfer, refused entirely
	 * below {@code deadband}, and never more than {@code packetCap}.
	 *
	 * <p>The deadband is checked against the FULL equalising transfer, not against the half step. Checking
	 * the half would let a gap of {@code 2 × deadband − 1} open the cascade for a sub-deadband move, which
	 * is the jitter the deadband exists to refuse.
	 *
	 * @param deadband minimum meaningful gap, in EU — pass {@code Config.cableBuffer} (MOD-070: the
	 *     segment buffer is the real per-tick throughput, the tier packet cap is a separate ceiling)
	 * @param packetCap the network's tier throughput ceiling for one source in one tick
	 * @return EU to allow, or 0 to refuse
	 */
	public static long allowance(long donorAmount, long donorCapacity, long sinkAmount, long sinkCapacity,
			long deadband, long packetCap) {
		if (packetCap <= 0) {
			return 0L;
		}
		long gap = equalisingTransfer(donorAmount, donorCapacity, sinkAmount, sinkCapacity);
		if (gap < deadband) {
			return 0L;
		}
		long half = gap / 2;
		if (half <= 0) {
			return 0L;
		}
		return Math.min(half, packetCap);
	}
}
