package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsStore;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge registration of the {@link PlayerModStats} player attachment (MOD-133) and its binding
 * into the common {@link PlayerStatsStore} seam. The attachment registry freezes before mod init, so
 * (like data components) it goes through a {@link DeferredRegister} on the mod bus. Serialized via
 * {@link PlayerModStats#MAP_CODEC} (persists across relog), {@code copyOnDeath} (NeoForge copies it
 * automatically on the death clone), and synced only to its owner — the sync predicate sends the
 * attachment to a player only when that player <em>is</em> the holder.
 */
public final class ModAttachmentsNeoForge {
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
			DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Industrialization.MOD_ID);

	public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerModStats>> PLAYER_STATS =
			ATTACHMENTS.register("player_stats", () -> AttachmentType
					.builder(() -> PlayerModStats.EMPTY)
					.serialize(PlayerModStats.MAP_CODEC)
					.copyOnDeath()
					.sync((holder, player) -> holder == player, PlayerModStats.STREAM_CODEC)
					.build());

	private ModAttachmentsNeoForge() {
	}

	/** Bind the server-side store seam to the deferred attachment holder. Called from the {@code @Mod} ctor. */
	public static void init() {
		PlayerStatsStore.bind(new PlayerStatsStore.Accessor() {
			@Override
			public PlayerModStats get(ServerPlayer player) {
				return player.getData(PLAYER_STATS);
			}

			@Override
			public void set(ServerPlayer player, PlayerModStats stats) {
				player.setData(PLAYER_STATS, stats);
			}
		});
	}
}
