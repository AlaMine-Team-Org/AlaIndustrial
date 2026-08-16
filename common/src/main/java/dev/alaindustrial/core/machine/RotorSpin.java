package dev.alaindustrial.core.machine;

/**
 * Where the Thermal Centrifuge's rotor is pointing this frame, and how fast it is turning (MOD-424).
 *
 * <p>Kept free of any Minecraft type on purpose. The L3 client lane photographs frames and counts
 * pixels; it cannot measure an angular rate, so "the rotor winds up over the spin-up, holds a slower
 * turn while idling and stands dead still when stopped" is only checkable at L1 — which needs the
 * arithmetic to exist somewhere a plain JUnit classpath can reach. The renderer
 * ({@code ThermalCentrifugeBlockEntityRenderer}) keeps the Minecraft side: reading the block entity,
 * baking the model, pushing the pose.
 *
 * <p><b>The angle is derived, never stored.</b> It is a pure function of the game clock, so it survives
 * a chunk reload, a resource-pack swap and a second player watching the same machine from the other
 * side — all three of which would desynchronise a counter ticked on the block entity.
 *
 * <p><b>Why the rate is a step function of the synced spin, and why that is fine.</b> The block entity
 * publishes {@code spinPermille()} only when it crosses a tenth, so the rate changes in ~10 coarse steps
 * over a full spin-up rather than continuously. Because the angle is {@code time × rate}, every one of
 * those steps also jumps the rotor's PHASE by an arbitrary amount. That is the same trade the water mill
 * and the condenser already make, and it is invisible here for a concrete reason: the rotor is a basket
 * of <b>four evenly spaced vanes</b>, so it is symmetric every 90&deg; and a phase jump lands on a pose the
 * eye cannot tell from the one before it. Any evenly spaced count keeps that argument true, but the number
 * written here must be the number {@code ThermalCentrifugeBlockEntityRenderer.VANE_COUNT} actually builds —
 * a rotor with two vanes, or with one vane marked out from the others, would need a continuous rate instead.
 */
public final class RotorSpin {

	/**
	 * Wrap the animation clock by one Minecraft day before it ever reaches a {@code float}.
	 *
	 * <p>A world that has been running for weeks holds a game time in the tens of millions, where a
	 * {@code float} can no longer represent consecutive ticks — the rotor would step in visible jerks and
	 * eventually freeze outright. The condenser documents the same wrap for the same reason.
	 */
	public static final long TIME_WRAP = 24_000L;

	/** Spin permille at which the rotor is fully wound up; the wire format's ceiling. */
	public static final int FULL_SPIN_PERMILLE = 1000;

	/**
	 * The barest crawl, at the very first paid tick of the ramp. Not zero: the instant the lever goes on
	 * the rotor must be seen to move, or the player reads the 20-second spin-up as "nothing happened".
	 */
	public static final float CREEP_RADIANS_PER_TICK = 0.02F;

	/**
	 * Wound up, powered, nothing to process. Deliberately non-zero — a powered machine sitting with a dead
	 * rotor reads as broken, the lesson the garden drone's idle blades already encode — and deliberately
	 * well below {@link #WORKING_RADIANS_PER_TICK}, so "standing by" and "working" are two different
	 * sights rather than the same one.
	 */
	public static final float IDLE_RADIANS_PER_TICK = 0.18F;

	/** Wound up and actually turning out shavings: ~1.7 revolutions a second. */
	public static final float WORKING_RADIANS_PER_TICK = 0.55F;

	private RotorSpin() {
	}

	/**
	 * Angular rate for a rotor at {@code spinPermille}, in radians per tick.
	 *
	 * @param spinPermille {@code ThermalCentrifugeBlockEntity#spinPermille()} — 0 when stopped, 1000 at
	 *                     working speed. Values outside that range are clamped rather than trusted: the
	 *                     number arrives over the network, and a lowered {@code spinupTicks} config can
	 *                     leave a client holding a permille from the old ceiling.
	 * @param processing   whether the machine is actually running a recipe (the {@code LIT} blockstate)
	 * @return 0 exactly when the rotor is stopped, otherwise a strictly positive rate
	 */
	public static float radiansPerTick(int spinPermille, boolean processing) {
		if (spinPermille <= 0) {
			return 0.0F;
		}
		float wound = Math.min(spinPermille, FULL_SPIN_PERMILLE) / (float) FULL_SPIN_PERMILLE;
		float top = processing ? WORKING_RADIANS_PER_TICK : IDLE_RADIANS_PER_TICK;
		return CREEP_RADIANS_PER_TICK + (top - CREEP_RADIANS_PER_TICK) * wound;
	}

	/**
	 * The rotor's angle this frame, in radians.
	 *
	 * <p>Hard zero while stopped, not "whatever the last frame left behind": a stopped rotor parked at an
	 * arbitrary angle is indistinguishable from a running one caught mid-turn, and the block's whole
	 * identity is that the lever starts and stops it.
	 *
	 * @param gameTime     the level's game time in ticks
	 * @param partialTicks fraction of the current tick already elapsed, so the turn is smooth between ticks
	 */
	public static float angle(long gameTime, float partialTicks, int spinPermille, boolean processing) {
		float rate = radiansPerTick(spinPermille, processing);
		if (rate <= 0.0F) {
			return 0.0F;
		}
		// floorMod rather than %: a negative game time (a test clock, a /time command run backwards) would
		// otherwise wrap to a negative tick and spin the rotor the wrong way for a day.
		return (Math.floorMod(gameTime, TIME_WRAP) + partialTicks) * rate;
	}
}
