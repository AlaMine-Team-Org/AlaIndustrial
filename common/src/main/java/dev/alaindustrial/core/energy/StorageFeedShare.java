package dev.alaindustrial.core.energy;

/**
 * How much EU a store may hand to a <b>non-cascade sink</b> in one tick (MOD-353) — the storage→sink
 * feed, so a Teleporter or a Charging Station wired to a Battery Box actually charges.
 *
 * <p>Minecraft-free on purpose, exactly like {@link CascadeShare}: this is the whole decision, it is
 * pure arithmetic, and leaving it inline in {@code EnergyNetwork} would put it where the L1 suite and
 * pitest cannot reach it.
 *
 * <h2>The hole this fills</h2>
 *
 * A store is not a generator, so it contributes nothing to {@code genSupply}. A sink is not a machine,
 * so it contributes nothing to {@code machineDemand}. On a segment whose only source is a store and
 * whose only consumer is a sink, both aggregates are zero, {@code storageBudget} is
 * {@code max(0, 0 − 0) = 0}, and the backup-discharge stage never opens. The cascade does not cover the
 * gap either: it is gated on {@code acceptsCascade()}, and the Teleporter is deliberately outside it.
 * The result was a hard, permanent zero — confirmed on an isolated stand before this class was written.
 *
 * <h2>Why this is not a re-run of the MOD-314 bug</h2>
 *
 * MOD-314 decision R3 removed the Teleporter from the cascade for a concrete reason: the cascade
 * equalises by fill <b>fraction</b>, and a 20 000 EU box against a 500 000 EU fund would be drained to
 * ~2 % of itself with no action from the player. Nothing here weakens that:
 *
 * <ul>
 *   <li>{@link CascadeShare} is untouched and {@code acceptsCascade(teleporter)} stays {@code false} —
 *       the fraction-equalising path still refuses the fund outright.</li>
 *   <li>This channel is <b>absolute</b>, not proportional: a flat EU/tick rate, so a large sink cannot
 *       pull harder than a small one.</li>
 *   <li>The donor keeps a <b>reserve</b>. Below {@code reserveFraction} of its capacity it gives
 *       nothing, so the player's bank can never be emptied into a fund by this path. That reserve is
 *       what replaces "the fund is simply excluded" as the guarantee.</li>
 * </ul>
 *
 * <p>The player-visible contract that follows: <em>through a cable</em> a store feeds a sink slowly and
 * only down to its reserve; <em>flush against it</em> the older direct path still empties the store at
 * full tilt. Two different answers, but each one is something the player chose by how they built.
 */
public final class StorageFeedShare {

	private StorageFeedShare() {
	}

	/**
	 * EU the donor may release to non-cascade sinks this tick.
	 *
	 * <p>The result is the smallest of four independent limits, so every one of them is a hard ceiling
	 * rather than a suggestion:
	 * <ol>
	 *   <li>what the donor holds <b>above its reserve</b> — protects the player's bank;</li>
	 *   <li>the sink's remaining room — never extract what cannot be stored;</li>
	 *   <li>the configured feed rate — the pace written in the spec;</li>
	 *   <li>the tier packet cap — the ceiling every other transfer in this mod also respects.</li>
	 * </ol>
	 *
	 * @param donorAmount     EU currently in the donor store
	 * @param donorCapacity   donor buffer size; a non-positive capacity yields 0 (nothing to reserve
	 *                        against, and dividing by it would be worse than refusing)
	 * @param reserveFraction share of the donor's capacity that must remain untouched, 0..1; values
	 *                        outside that range are clamped, so a broken config cannot open the gate
	 * @param sinkRoom        EU the sink can still accept
	 * @param feedRate        configured EU/tick for this channel; 0 disables the channel entirely,
	 *                        which is the default for every block that does not opt in
	 * @param packetCap       tier packet ceiling for the segment
	 * @return EU to move, never negative
	 */
	public static long feedAllowance(long donorAmount, long donorCapacity, double reserveFraction,
			long sinkRoom, long feedRate, long packetCap) {
		if (donorAmount <= 0 || donorCapacity <= 0 || sinkRoom <= 0 || feedRate <= 0 || packetCap <= 0) {
			return 0L;
		}
		double clamped = Math.max(0.0, Math.min(1.0, reserveFraction));
		// Rounded, not truncated: at a 0.5 reserve on an odd capacity, truncation would let the donor
		// dip one EU below the half it is supposed to keep — small, but it makes the documented promise
		// ("never below half") false, and a test pinned to the promise would fail on odd numbers only.
		long reserve = Math.round(donorCapacity * clamped);
		long spendable = donorAmount - reserve;
		if (spendable <= 0) {
			return 0L;
		}
		return Math.min(Math.min(spendable, sinkRoom), Math.min(feedRate, packetCap));
	}

	/**
	 * The reserve line itself, in EU — what {@link #feedAllowance} refuses to go below.
	 *
	 * <p>Exposed separately because the GUI and the docs quote this number to the player ("the box stops
	 * feeding at half"), and computing it in two places is how the two drift apart.
	 */
	public static long reserveFloor(long donorCapacity, double reserveFraction) {
		if (donorCapacity <= 0) {
			return 0L;
		}
		return Math.round(donorCapacity * Math.max(0.0, Math.min(1.0, reserveFraction)));
	}
}
