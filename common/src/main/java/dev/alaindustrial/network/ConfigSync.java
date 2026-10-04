package dev.alaindustrial.network;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.KnobSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The server's half of config synchronisation (MOD-695): sends each client the server's client-visible
 * knobs when the player joins and again, to everyone online, after every re-read of the config file.
 *
 * <p><b>Why both moments.</b> A join covers a fresh connection; a reload ({@code /ala config reload},
 * datapack {@code /reload}) changes the numbers under players already connected, and a client that kept
 * the login snapshot would show the old balance until it relogged.
 *
 * <p>Loader-neutral like {@code ArchiveRecordSync}: each loader's join hook calls {@link #sendOnJoin},
 * and each reload path goes through {@link #reloadAndBroadcast}, so "re-read, then tell the clients" is
 * written once instead of once per reload site.
 */
public final class ConfigSync {

	private ConfigSync() {
	}

	/** The payload carrying the server's current client-visible balance. */
	public static ConfigSyncPayload payload() {
		return new ConfigSyncPayload(KnobSnapshot.capture().encode());
	}

	/**
	 * Sends the player the server's balance. Called from both loaders' player-join hooks.
	 *
	 * <p>Only to a connection that negotiated the channel — the same guard as {@code ArchiveRecordSync}:
	 * a gametest mock player never did, and on NeoForge the send would throw out of the join event. A
	 * client without the channel keeps its local numbers, which is exactly the pre-MOD-695 behaviour.
	 */
	public static void sendOnJoin(ServerPlayer player) {
		send(player, payload());
	}

	/** Sends the current balance to every player online. A {@code null} server (none running) sends nothing. */
	public static void broadcast(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		ConfigSyncPayload payload = payload();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player, payload);
		}
	}

	/**
	 * Re-reads {@code config/alaindustrial.json} and tells every connected client the result. The one
	 * entry point for a reload that happens while a server is running: {@code /ala config reload}, Fabric's
	 * {@code END_DATA_PACK_RELOAD} and NeoForge's all-players {@code OnDatapackSyncEvent}.
	 *
	 * <p>Broadcast whatever the outcome: after {@code ERROR} the live balance is unchanged and the
	 * snapshot repeats it, after {@code SCHEMA_TOO_NEW} it is the built-in defaults and the clients must
	 * learn that.
	 */
	public static Config.LoadResult reloadAndBroadcast(@Nullable MinecraftServer server) {
		Config.LoadResult result = Config.reload();
		broadcast(server);
		return result;
	}

	private static void send(ServerPlayer player, ConfigSyncPayload payload) {
		if (payload.data().length > PayloadBudget.MAX_CONFIG_SNAPSHOT_BYTES) {
			// Cannot happen with today's knobs (KnobSnapshotTest pins the whole registry under the ceiling);
			// if it ever does, the client keeps its local numbers rather than the codec throwing mid-send.
			Industrialization.LOGGER.error("[config-sync] snapshot of {} bytes exceeds the {}-byte budget; not sent",
					payload.data().length, PayloadBudget.MAX_CONFIG_SNAPSHOT_BYTES);
			return;
		}
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (!dispatcher.canSendToPlayer(player, ConfigSyncPayload.TYPE)) {
			return;
		}
		dispatcher.sendToPlayer(player, payload);
	}
}
