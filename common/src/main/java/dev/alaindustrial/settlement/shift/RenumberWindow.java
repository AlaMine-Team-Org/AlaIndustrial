package dev.alaindustrial.settlement.shift;

import java.util.Collection;

/**
 * When a shift worker marked for a new number may get it (R41 of MOD-824, MOD-834): only while no player can see
 * it, so nobody watches the number on its back change. Minecraft-free, so the L1 suite pins it; the game side —
 * finding the body and the players — is {@link ShiftWorkerRenumbering}.
 *
 * <p><b>What "seen" means.</b> A player sees an entity the game sends to that player: one within the entity's tracking
 * range, cut to the player's view distance, measured flat (the game ignores height). The window is open while no
 * player of the worker's level is that close, with a margin for the steps taken between two sweeps. A worker with no
 * body in the world — dead and waiting to come back (R39), or in a chunk that is not loaded — is seen by nobody: its
 * number lives in its record only, and a body loaded later shows the new one. A body that has just joined the world
 * and shows no number yet is renumbered before it shows any. The capsule at night (R41) comes with the night schedule
 * (stage 5); until then, out of everyone's sight is the one way.
 */
public final class RenumberWindow {

	/** Blocks added to the game's own sight distance: what a player can walk between two sweeps, and then some. */
	public static final int MARGIN = 16;

	/** Where the worker's body is, as the server knows it. */
	public enum Body {
		/** No body in the world: the worker waits to be brought back, or its chunk is not loaded. */
		NONE,
		/** A body that joined the world and shows no number yet: it will show the record's. */
		JOINING,
		/** A body showing its number to whoever is near. */
		SHOWN
	}

	/** A player in the worker's level, by its flat position. */
	public record Observer(double x, double z) {
	}

	private RenumberWindow() {
	}

	/**
	 * How far, flat, a player can be and still be sent the worker: the entity's tracking range in blocks (already
	 * scaled by the server's setting), cut to the server's view distance in chunks, plus {@link #MARGIN}.
	 */
	public static double sightRadius(int trackingRangeBlocks, int viewDistanceChunks) {
		return Math.min(trackingRangeBlocks, viewDistanceChunks * 16) + MARGIN;
	}

	/**
	 * Whether the worker may get its new number now: it has no body, its body has not shown a number yet, or no
	 * observer is within {@code radius} of ({@code x}, {@code z}), flat.
	 */
	public static boolean isOpen(Body body, double x, double z, Collection<Observer> observers, double radius) {
		if (body != Body.SHOWN) {
			return true;
		}
		double limit = radius * radius;
		for (Observer observer : observers) {
			double dx = observer.x() - x;
			double dz = observer.z() - z;
			if (dx * dx + dz * dz <= limit) {
				return false;
			}
		}
		return true;
	}
}
