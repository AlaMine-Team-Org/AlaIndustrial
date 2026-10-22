package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TownWorksPayload;
import dev.alaindustrial.network.TownWorksRequestPayload;
import dev.alaindustrial.settlement.works.ConstructionRow;
import org.jspecify.annotations.Nullable;

/**
 * Client end of the settlement core's «Construction» tab (MOD-857): keeps the latest rows the server sent and sends
 * the tab's requests and the owner's presses. Client thread only. A request names no core: the server answers for
 * the settlement core menu the player has open.
 */
public final class TownWorksClient {

	private static @Nullable TownWorksPayload latest;

	private TownWorksClient() {
	}

	/** The landing point of the rows: kept as the latest. */
	public static void receive(TownWorksPayload payload) {
		latest = payload;
	}

	/** The latest rows, or null before the first reply since {@link #forget()}. */
	public static @Nullable TownWorksPayload latest() {
		return latest;
	}

	/** Forgets the rows: a core screen that opens calls this, so it never shows the previous core's. */
	public static void forget() {
		latest = null;
	}

	/** Asks for the rows of the open core's tab. */
	public static void request() {
		NetworkDispatcher.get().sendToServer(TownWorksRequestPayload.rows());
	}

	/** Presses {@code action} on the row whose target is {@code target}; the reply brings fresh rows. */
	public static void press(ConstructionRow.Action action, int target) {
		NetworkDispatcher.get().sendToServer(TownWorksRequestPayload.press(action, target));
	}
}
