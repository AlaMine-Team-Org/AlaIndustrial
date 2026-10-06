package dev.alaindustrial.core.environment;

/**
 * When the occupant of a mob wheel stops running to fight (MOD-763, decision D9). The wheel never switches a
 * mob's goals off, so a hostile runner keeps choosing targets; while it has a live player target close by, it is
 * <b>distracted</b>: the wheel only holds it in place, does not turn it along the run, makes no EU and spends
 * none of its stamina. Minecraft-free so the boundary is tested on the L1 lane; the drive asks the live mob.
 */
public final class MobWheelDistraction {
	private MobWheelDistraction() {
	}

	/** How close a player target must be, in blocks (eye-independent: entity positions). */
	public static final double RANGE_BLOCKS = 6.0;

	/**
	 * Whether an occupant is distracted.
	 *
	 * @param livePlayerTarget whether its current target is a player that is alive (and not a spectator)
	 * @param distanceSqr      squared distance between the occupant and that target, blocks²
	 */
	public static boolean isDistracted(boolean livePlayerTarget, double distanceSqr) {
		return livePlayerTarget && distanceSqr <= RANGE_BLOCKS * RANGE_BLOCKS;
	}
}
