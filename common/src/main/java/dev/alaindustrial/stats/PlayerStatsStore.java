package dev.alaindustrial.stats;

import java.util.function.UnaryOperator;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side seam to a player's {@link PlayerModStats} data attachment (MOD-133). The attachment API
 * is loader-specific (Fabric {@code AttachmentType} vs NeoForge {@code AttachmentType}), so common
 * code goes through this set-once accessor — the same idiom as {@code ModSounds}/{@code ModKeyMappings}.
 * Each loader binds its accessor via {@link #bind} during init; unbound reads/writes are no-ops that fall back to
 * {@link PlayerModStats#EMPTY} rather than NPE (a dedicated server without the attachment wired would
 * simply not track, never crash).
 */
public final class PlayerStatsStore {

	/** Loader-bound bridge to the actual attachment get/set on a {@link ServerPlayer}. */
	public interface Accessor {
		PlayerModStats get(ServerPlayer player);

		void set(ServerPlayer player, PlayerModStats stats);
	}

	private static Accessor accessor;

	private PlayerStatsStore() {
	}

	/** Called once per loader during init, before any stat is read or written. */
	public static void bind(Accessor impl) {
		accessor = impl;
	}

	/** The player's current stats, or {@link PlayerModStats#EMPTY} if the accessor is unbound. */
	public static PlayerModStats get(ServerPlayer player) {
		return accessor == null ? PlayerModStats.EMPTY : accessor.get(player);
	}

	/** Replace the player's stats (triggers attachment persistence + owner sync). No-op if unbound. */
	public static void set(ServerPlayer player, PlayerModStats stats) {
		if (accessor != null) {
			accessor.set(player, stats);
		}
	}

	/** Read-modify-write in one call — the safe way to update immutable stats (get → transform → set). */
	public static void modify(ServerPlayer player, UnaryOperator<PlayerModStats> update) {
		set(player, update.apply(get(player)));
	}
}
