package dev.alaindustrial.core.energy;

/**
 * Pure energy-distribution arithmetic used by {@code EnergyNetwork#tick()} — extracted so it can be
 * unit-tested without a Minecraft runtime (L1, see {@code docs/testing/AUTOMATION-STANDARDS.md}).
 *
 * <p>No Minecraft imports, no side effects: every method is a deterministic function of its inputs.
 * The live transport path calls these so the tested code <em>is</em> the production code.
 */
public final class EnergyShare {
	private EnergyShare() {
	}

	/**
	 * EU deliverable this tick: {@code min(supply, demand)}, floored at 0.
	 *
	 * <p>Cable transport is a <b>throughput limit</b> (each consumer is further capped at the tier's
	 * {@code packetCap} in {@link #split}), never an EU-destroying toll. The old {@code − loss} term
	 * was removed in MOD-009: a flat per-tick loss blocked the last small packet, so a buffer could
	 * never top off (BatteryBox stuck at {@code capacity − loss}, e.g. 19998/20000). Distance-based cable
	 * loss (MOD-021) is applied later, per-consumer, via {@link #cableLoss} — proportional to flow, so it
	 * never strands the last top-off packet the way that flat network-level toll did.
	 */
	public static long deliverable(long supply, long demand) {
		return Math.max(0L, Math.min(supply, demand));
	}

	/**
	 * EU destroyed in cable transit for one consumer this tick:
	 * {@code floor(gross × (1 - (1 - lossPerBlock)^distanceBlocks))}. Repeated attenuation keeps loss
	 * monotonic with distance without the hard 100% cutoff of the old linear formula. The result is
	 * clamped to {@code [0, gross - 1]} for positive flow, so even an extremely long line delivers at
	 * least 1 EU and a trickle top-off packet can still fill a buffer exactly (MOD-009).
	 *
	 * <p>The implementation uses {@link Math#log1p(double)} and {@link Math#expm1(double)} instead of
	 * subtracting a rounded {@code pow} result from 1. This preserves precision for small rates and
	 * distances. Pure arithmetic: the caller subtracts the returned loss from what it pulled before
	 * inserting into the consumer, and the lost EU is not returned to any producer.
	 *
	 * @param gross          EU actually pulled from producers toward this consumer (≥ 0)
	 * @param lossPerBlock   fraction of throughput lost per cable block (≥ 0; copper ≈ 0.02)
	 * @param distanceBlocks cable blocks between the consumer and its nearest producer (≥ 0)
	 * @return EU to destroy in transit, in {@code [0, gross]}
	 */
	public static long cableLoss(long gross, double lossPerBlock, int distanceBlocks) {
		if (gross <= 0 || lossPerBlock <= 0 || distanceBlocks <= 0) {
			return 0L;
		}
		double rate = Math.max(0.0, Math.min(lossPerBlock, 1.0));
		double lossFraction = -Math.expm1(distanceBlocks * Math.log1p(-rate));
		long loss = (long) Math.floor(gross * lossFraction);
		return Math.max(0L, Math.min(loss, gross - 1L));
	}

	/**
	 * Split {@code moveTotal} EU across consumers proportionally to each one's free {@code room},
	 * capping every consumer at {@code packetCap} (the tier's per-tick transfer limit). Any rounding
	 * remainder is handed out to consumers that still have headroom, in order.
	 *
	 * @param moveTotal total EU to distribute (≥ 0, already net of loss)
	 * @param room      free capacity per consumer (each ≥ 0)
	 * @param demand    sum of {@code room} (caller-provided to match the live path; must be &gt; 0)
	 * @param packetCap per-consumer per-tick cap (tier voltage)
	 * @return per-consumer EU share, same length/order as {@code room}; sum ≤ {@code moveTotal}
	 */
	public static long[] split(long moveTotal, long[] room, long demand, long packetCap) {
		long[] share = new long[room.length];
		if (moveTotal <= 0 || demand <= 0) {
			return share;
		}
		long assigned = 0;
		for (int i = 0; i < room.length; i++) {
			long s = Math.floorDiv(moveTotal * room[i], demand);
			s = Math.min(s, room[i]);
			s = Math.min(s, packetCap);
			share[i] = s;
			assigned += s;
		}
		// Distribute the rounding remainder to consumers that still have room (capped by packetCap).
		long remainder = moveTotal - assigned;
		for (int i = 0; i < room.length && remainder > 0; i++) {
			long extra = Math.min(remainder, Math.min(room[i] - share[i], packetCap - share[i]));
			if (extra > 0) {
				share[i] += extra;
				remainder -= extra;
			}
		}
		return share;
	}
}
