package dev.alaindustrial.client.screen.teleporter;

import dev.alaindustrial.client.ServerBalance;

/**
 * How many teleport points a remote may hold and how many are still free — the «Stations» tab's
 * "bound N / max" header and its "free" rows — against the SERVER's limit ({@link ServerBalance}), not the
 * local file's (MOD-695). Minecraft-free, so L1 covers it.
 */
public final class PointAllowance {

	private PointAllowance() {
	}

	/** The most points one remote holds. */
	public static int max() {
		return ServerBalance.teleporterMaxPoints();
	}

	/** Points still free on a remote that has {@code bound} of them, never negative. */
	public static int free(int bound) {
		return Math.max(0, max() - bound);
	}
}
