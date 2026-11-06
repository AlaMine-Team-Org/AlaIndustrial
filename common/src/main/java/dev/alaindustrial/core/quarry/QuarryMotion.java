package dev.alaindustrial.core.quarry;

/**
 * Where the quarry's moving parts stand during one operation (MOD-952, stage C of MOD-637): the bridge along the
 * paths, the cart along the bridge and the tip of the drill, as the operation's phases run.
 *
 * <p>An operation goes from where the drill was to the next cell: the bridge and the cart travel there at the
 * drill's old height, the drill is lowered onto the block, bites into it while it drills, and is raised back to the
 * block's top, where it waits for the next operation. Between operations the drill rests where the last one left it;
 * before the first one it is pulled up out of the pit.
 *
 * <p>The server runs the phases ({@code QuarryDigger}); the client is told each phase as it starts and draws the
 * pose from the phase's progress, so a phase that is held — a drilling waiting for a bit — simply stays at its end.
 * Minecraft-free: everything is in world blocks, along and across the paths.
 */
public final class QuarryMotion {

	/** How deep the drill head bites into the block it drills, in blocks. */
	public static final float BITE = 0.4F;

	/** The phases of one operation; {@link #IDLE} between operations. */
	public enum Phase {
		IDLE, TRAVEL, LOWER, DRILL, RAISE
	}

	/**
	 * One operation's way, in world blocks.
	 *
	 * @param fromAlong  where the bridge starts, along the paths
	 * @param fromAcross where the cart starts, along the bridge
	 * @param fromTip    the height of the drill's tip as it starts
	 * @param toAlong    the bridge over the cell
	 * @param toAcross   the cart over the cell
	 * @param contactTip the height the tip touches the cell at: the block's top
	 */
	public record Leg(float fromAlong, float fromAcross, float fromTip, float toAlong, float toAcross,
			float contactTip) {

		/** Resting at one place: no way to go. */
		public static Leg resting(float along, float across, float tip) {
			return new Leg(along, across, tip, along, across, tip);
		}
	}

	/**
	 * Where the parts are.
	 *
	 * @param along    the bridge, along the paths
	 * @param across   the cart, along the bridge
	 * @param tip      the height of the drill's tip
	 * @param spinning whether the drill turns: only while it drills
	 */
	public record Pose(float along, float across, float tip, boolean spinning) {
	}

	private QuarryMotion() {
	}

	/** The pose {@code progress} (0 at the phase's start, 1 at its end; clamped) into {@code phase} of {@code leg}. */
	public static Pose pose(Phase phase, float progress, Leg leg) {
		float p = Math.max(0.0F, Math.min(1.0F, progress));
		float bottom = leg.contactTip() - BITE;
		return switch (phase) {
			case IDLE -> new Pose(leg.toAlong(), leg.toAcross(), leg.contactTip(), false);
			case TRAVEL -> new Pose(lerp(leg.fromAlong(), leg.toAlong(), p), lerp(leg.fromAcross(), leg.toAcross(), p),
					leg.fromTip(), false);
			case LOWER -> new Pose(leg.toAlong(), leg.toAcross(), lerp(leg.fromTip(), leg.contactTip(), p), false);
			case DRILL -> new Pose(leg.toAlong(), leg.toAcross(), lerp(leg.contactTip(), bottom, p), p < 1.0F);
			case RAISE -> new Pose(leg.toAlong(), leg.toAcross(), lerp(bottom, leg.contactTip(), p), false);
		};
	}

	/**
	 * How far into a phase that started at game time {@code start} and lasts {@code ticks} the time {@code now} is,
	 * 0 to 1. A phase of no ticks is over at once.
	 */
	public static float progress(long start, int ticks, double now) {
		if (ticks <= 0) {
			return 1.0F;
		}
		return (float) Math.max(0.0D, Math.min(1.0D, (now - start) / ticks));
	}

	/**
	 * How far the drill hangs below its resting length, in blocks: the pipe grows by this much. A tip above the
	 * resting tip — the drill pulled up out of the pit — hangs at its resting length.
	 */
	public static float drop(float restingTip, float tip) {
		return Math.max(0.0F, restingTip - tip);
	}

	private static float lerp(float from, float to, float p) {
		return from + (to - from) * p;
	}
}
