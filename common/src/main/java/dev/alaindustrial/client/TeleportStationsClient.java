package dev.alaindustrial.client;

import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.network.TeleportStationsPayload;
import net.minecraft.client.Minecraft;

/** Client end of the remote's station snapshot (MOD-628): hands it to the remote screen it was sent for. */
public final class TeleportStationsClient {

	private TeleportStationsClient() {
	}

	/** A snapshot for a screen the player has since closed finds no remote menu, and is dropped. */
	public static void receive(TeleportStationsPayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.containerMenu instanceof TeleporterRemoteMenu menu) {
			menu.acceptStations(payload);
		}
	}
}
