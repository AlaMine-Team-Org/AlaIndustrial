package dev.alaindustrial.client;

import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.network.ReactorLogPayload;
import net.minecraft.client.Minecraft;

/** Client end of the reactor log (MOD-622): hands a snapshot to the controller screen it was sent for. */
public final class ReactorLogClient {

	private ReactorLogClient() {
	}

	/** A snapshot for a screen the player has since closed finds no controller menu, and is dropped. */
	public static void receive(ReactorLogPayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.containerMenu instanceof ReactorControllerMenu menu) {
			menu.acceptLog(payload);
		}
	}
}
