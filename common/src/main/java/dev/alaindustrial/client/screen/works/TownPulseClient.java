package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TownPulsePayload;
import dev.alaindustrial.network.TownPulseRequestPayload;
import org.jspecify.annotations.Nullable;

/**
 * Client end of the core's town line and «Chronicle» tab (MOD-886): keeps the latest reply and sends the requests.
 * Client thread only. A request names no core: the server answers for the settlement core menu the player has open.
 */
public final class TownPulseClient {

	private static @Nullable TownPulsePayload latest;

	private TownPulseClient() {
	}

	/** The landing point of a reply: kept as the latest. */
	public static void receive(TownPulsePayload payload) {
		latest = payload;
	}

	/** The latest reply, or null before the first one since {@link #forget()}. */
	public static @Nullable TownPulsePayload latest() {
		return latest;
	}

	/** Forgets the reply: a core screen that opens calls this, so it never shows the previous core's. */
	public static void forget() {
		latest = null;
	}

	/** Asks for the needs and the chronicle of the open core's town. */
	public static void request() {
		NetworkDispatcher.get().sendToServer(new TownPulseRequestPayload());
	}
}
