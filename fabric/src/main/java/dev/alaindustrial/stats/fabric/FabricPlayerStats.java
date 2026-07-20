package dev.alaindustrial.stats.fabric;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsStore;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric registration of the {@link PlayerModStats} player attachment (MOD-133) and its binding into
 * the common {@link PlayerStatsStore} seam. Persistent (survives relog), {@code copyOnDeath} (survives
 * death — on Fabric this is an explicit opt-in, unlike NeoForge) and synced only to its owner via
 * {@link AttachmentSyncPredicate#targetOnly()}, so a player's career never leaks to others on a server.
 */
public final class FabricPlayerStats {

	/** The player attachment holding one {@link PlayerModStats} per player. */
	public static final AttachmentType<PlayerModStats> TYPE = AttachmentRegistry.create(
			Industrialization.id("player_stats"),
			builder -> builder
					.initializer(() -> PlayerModStats.EMPTY)
					.persistent(PlayerModStats.CODEC)
					.copyOnDeath()
					.syncWith(PlayerModStats.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private FabricPlayerStats() {
	}

	/** Register the attachment and bind the server-side store seam. Called once from Fabric init. */
	public static void init() {
		PlayerStatsStore.bind(new PlayerStatsStore.Accessor() {
			@Override
			public PlayerModStats get(ServerPlayer player) {
				return player.getAttachedOrCreate(TYPE);
			}

			@Override
			public void set(ServerPlayer player, PlayerModStats stats) {
				player.setAttached(TYPE, stats);
			}
		});
	}
}
