package dev.alaindustrial.core.guide;

import dev.alaindustrial.network.ArchiveRecordPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server's half of the archive record (MOD-513): works the record out and sends it to the
 * player's client when they join.
 *
 * <p><b>The server is the only source.</b> A client connected to a dedicated server does not have the
 * world seed, so the record crosses the wire as a finished value and the client only checks its
 * shape. Both inputs are the server's own: the seed from the world-generation options, the profile id
 * from the player's authenticated game profile — nothing the client sent is read.
 *
 * <p><b>Why login is enough.</b> Neither input changes while the player is connected, so one packet
 * per connection covers dimension changes and respawns. The client forgets the value when it leaves
 * the world ({@code ArchiveRecordClient#reset}), so a second world never shows the first one's
 * record.
 *
 * <p>Loader-neutral, like {@link GuideBookGiver}: each loader's join hook calls {@link #sendOnJoin}.
 */
public final class ArchiveRecordSync {

	private ArchiveRecordSync() {
	}

	/**
	 * The record of this player in this world.
	 *
	 * <p>{@code ServerLevel#getSeed} reads {@code server.getWorldGenSettings().options().seed()} in
	 * 26.2 (checked in the bytecode), so every dimension answers with the same world seed; the player's
	 * entity UUID is set from {@code GameProfile#id} in the {@code Player} constructor, and the profile
	 * id is read directly to say which of the two is meant.
	 */
	public static String recordFor(ServerPlayer player) {
		return ArchiveRecord.of(player.level().getSeed(), player.getGameProfile().id());
	}

	/**
	 * Sends the player their record. Called from both loaders' player-join hooks.
	 *
	 * <p>Only to a connection that negotiated the channel: the join event also fires for players whose
	 * connection never did — every gametest mock player — and on NeoForge the send would throw out of
	 * the event. A real client of this mod always has the channel; one without it shows the pending mark.
	 */
	public static void sendOnJoin(ServerPlayer player) {
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (!dispatcher.canSendToPlayer(player, ArchiveRecordPayload.TYPE)) {
			return;
		}
		dispatcher.sendToPlayer(player, new ArchiveRecordPayload(recordFor(player)));
	}
}
