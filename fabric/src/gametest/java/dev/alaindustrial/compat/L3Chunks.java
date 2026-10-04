package dev.alaindustrial.compat;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The L3 client lane's wait for chunk meshes (MOD-703, ADR-036). Every Minecraft line has a twin of
 * this class with the same signature; only the body differs. The stands and shot suites call
 * {@link #waitRender} instead of the Fabric client-gametest API, whose route to the wait moved between
 * the API versions the two lines build against — a new stand ported from one line to the other used to
 * break the other line's build on exactly this call (MOD-665).
 *
 * <p><b>This twin: Minecraft 26.3</b> ({@code fabric-client-gametest-api-v1} 6.x): the wait hangs off the
 * singleplayer's server connection.
 */
public final class L3Chunks {

	private L3Chunks() {
	}

	/** Blocks until every chunk in view has been meshed, so a screenshot shows the world, not holes. */
	public static void waitRender(TestSingleplayerContext singleplayer) {
		singleplayer.getConnection().waitForChunksRender();
	}
}
