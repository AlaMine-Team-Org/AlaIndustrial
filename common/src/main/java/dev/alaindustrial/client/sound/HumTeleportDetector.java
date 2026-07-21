package dev.alaindustrial.client.sound;

/**
 * Pure teleport detection for the machine-hum manager (MOD-129). Kept free of any {@code net.minecraft}
 * reference so it loads — and is unit-testable — without a client on the classpath (mirrors
 * {@link dev.alaindustrial.client.hud.HudSmoother}). {@link MachineHumClientHook} owns the listener-continuity
 * state and feeds it here each tick.
 *
 * <p>After a teleport the listener stops moving continuously and the sound engine leaves each hum loop in
 * a state the per-position ticker cannot recover from (dimension change / reconnect clears the engine but
 * not the manager's references; a loading-screen pause freezes each loop's self-terminating tick while the
 * engine still reports it active). The manager reacts by dropping its tracked loops so the ticker rebuilds
 * them at the destination — this class decides when.
 */
public final class HumTeleportDetector {

	/**
	 * A single client-tick listener move past this (blocks²) is a teleport, not travel: the fastest
	 * legitimate motion is a few blocks per tick, so no normal movement ever reaches it.
	 */
	public static final double TELEPORT_JUMP_SQR = 32.0 * 32.0;

	private HumTeleportDetector() {}

	/**
	 * A teleport for hum purposes: a new client level (dimension change / reconnect), or — once a previous
	 * anchor exists — a single-tick listener jump past {@link #TELEPORT_JUMP_SQR}.
	 *
	 * @param levelChanged the client level identity differs from the previous tick
	 * @param hasAnchor    a previous listener position was recorded (false on the first tick after a join)
	 * @param jumpSqr      squared distance the listener moved since the previous tick
	 */
	public static boolean isDiscontinuity(boolean levelChanged, boolean hasAnchor, double jumpSqr) {
		return levelChanged || (hasAnchor && jumpSqr > TELEPORT_JUMP_SQR);
	}
}
