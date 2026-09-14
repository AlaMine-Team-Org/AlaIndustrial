package dev.alaindustrial.client;

import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.network.ReactorZonePayload;
import net.minecraft.client.Minecraft;

/**
 * Client landing point for {@link ReactorZonePayload} (MOD-620). Both loaders call this from their own receiver,
 * so the "which menu does this belong to" rule exists once — the same shape as {@link MachineStatsClient}.
 */
public final class ReactorZoneClient {

	private ReactorZoneClient() {
	}

	public static void receive(ReactorZonePayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.containerMenu instanceof ReactorControllerMenu menu) {
			menu.acceptZone(payload);
		}
	}
}
