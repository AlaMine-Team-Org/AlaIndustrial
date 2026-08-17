package dev.alaindustrial.client;

import dev.alaindustrial.menu.MachineMenu;
import dev.alaindustrial.network.MachineStatsPayload;
import net.minecraft.client.Minecraft;

/**
 * Client landing point for {@link MachineStatsPayload} (MOD-125). Both loaders call this from their own
 * receiver so the "which menu does this belong to" rule exists once.
 *
 * <p>The snapshot is handed to the open menu rather than parked in a static field: the menu is already
 * the object the screen reads from, it dies when the screen closes (so stale statistics cannot outlive
 * the GUI that showed them), and the id check inside {@code acceptStats} rejects a packet that arrives
 * after the player has moved on to a different machine.
 */
public final class MachineStatsClient {

	private MachineStatsClient() {
	}

	public static void receive(MachineStatsPayload payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.containerMenu instanceof MachineMenu menu) {
			menu.acceptStats(payload);
		}
	}
}
