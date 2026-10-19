package dev.alaindustrial.settlement;

/**
 * Whether a settlement core holds its territory, tick by tick, from what its buffer holds (MOD-831, epic decision
 * R5: without energy the core is dark and there is no territory).
 *
 * <p><b>Upkeep is owed, not skipped.</b> A lit core owes its tier's upkeep every tick and pays it from the buffer;
 * what it cannot pay it owes, and it pays the debt back first once energy returns. When the debt reaches
 * {@code graceTicks} ticks of upkeep the core goes dark. So a total blackout darkens it after exactly
 * {@code graceTicks} unpaid ticks, and a supply that covers only part of the upkeep darkens it too, just later —
 * paying whenever it happens to have enough would let three quarters of the price hold the town forever.
 *
 * <p><b>Lighting takes a reserve.</b> A dark core lights only once its buffer holds {@code graceTicks} ticks of
 * upkeep (or as much as the buffer can hold, if that is less). Without it a supply just below the upkeep would
 * light the core on one tick and darken it on the next, and everything that listens to the territory — the
 * shift workers' hibernation (MOD-833) first — would flip with it.
 *
 * <p>Minecraft-free: the block entity feeds it its buffer and drains what it returns, the L1 lane drives it
 * directly ({@code SettlementCoreUpkeepTest}). Both fields are the core's saved state.
 */
public final class SettlementCoreUpkeep {

	private boolean active;
	/** EU owed since the core last paid in full; 0 while dark. */
	private long debt;

	/** A dark core with nothing owed. */
	public SettlementCoreUpkeep() {
	}

	/** A core restored from its save. A dark core owes nothing, whatever the save says. */
	public SettlementCoreUpkeep(boolean active, long debt) {
		this.active = active;
		this.debt = active ? Math.max(0L, debt) : 0L;
	}

	/** Whether the core holds its territory now. */
	public boolean active() {
		return active;
	}

	/** EU owed since the last full payment. */
	public long debt() {
		return debt;
	}

	/**
	 * One tick of the core: decides whether it is lit and returns the EU to drain from its buffer, never more than
	 * {@code stored}.
	 *
	 * @param stored     EU in the buffer now
	 * @param capacity   the buffer's size, which caps the reserve a dark core waits for
	 * @param cost       upkeep per tick of the core's tier; at least 1
	 * @param graceTicks ticks of upkeep a lit core may owe before it goes dark; at least 1
	 */
	public long tick(long stored, long capacity, int cost, int graceTicks) {
		long perTick = Math.max(1, cost);
		long limit = perTick * Math.max(1, graceTicks);
		if (!active) {
			long reserve = Math.max(perTick, Math.min(limit, capacity));
			if (stored < reserve) {
				return 0L;
			}
			active = true;
			debt = 0L;
			return perTick;
		}
		long due = perTick + debt;
		long paid = Math.min(Math.max(0L, stored), due);
		debt = due - paid;
		if (debt >= limit) {
			active = false;
			debt = 0L;
		}
		return paid;
	}
}
