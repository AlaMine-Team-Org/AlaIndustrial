package dev.alaindustrial.stats;

import java.util.function.Supplier;

/**
 * Client-side seam the dashboard reads its {@link PlayerModStats} from (MOD-133). The server's
 * built-in attachment sync (Fabric {@code syncWith(targetOnly())} / NeoForge {@code sync(...)})
 * delivers the local player's stats into the client player's attachment; this common class exposes
 * that loader-specific read to the common {@code DashboardScreen} without a Fabric/NeoForge import.
 * Each loader binds its reader via {@link #bind} during client init. Unbound (or before the first sync) it yields
 * {@link PlayerModStats#EMPTY}, so the screen opens on zeros rather than crashing.
 */
public final class PlayerStatsClientCache {

	private static Supplier<PlayerModStats> reader = () -> PlayerModStats.EMPTY;

	private PlayerStatsClientCache() {
	}

	/** Called once during client init: supplies the local player's synced stats attachment. */
	public static void bind(Supplier<PlayerModStats> impl) {
		reader = impl;
	}

	/** The local player's current stats snapshot (last synced value), never null. */
	public static PlayerModStats current() {
		PlayerModStats stats = reader.get();
		return stats == null ? PlayerModStats.EMPTY : stats;
	}
}
