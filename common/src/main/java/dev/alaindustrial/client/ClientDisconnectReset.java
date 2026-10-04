package dev.alaindustrial.client;

import dev.alaindustrial.client.guide.ArchiveRecordClient;
import dev.alaindustrial.client.hud.TeleportFadeHud;
import dev.alaindustrial.client.render.NetworkOverlayRenderer;

/**
 * Client state that belongs to the world being left and must not bleed into the next one — the one list
 * both loaders run from their disconnect hook (Fabric {@code ClientPlayConnectionEvents.DISCONNECT},
 * NeoForge {@code ClientPlayerNetworkEvent.LoggingOut}); {@code loader_parity_check.py} fails if either
 * stops calling it.
 *
 * <p>Add a reset here, never to one loader's handler: that is how the Network Analyzer trace came to
 * survive a world exit on both (MOD-665, D1) — each loader had its own list and neither had the trace.
 */
public final class ClientDisconnectReset {
	private ClientDisconnectReset() {
	}

	public static void run() {
		TeleportFadeHud.reset(); // MOD-106
		ArchiveRecordClient.reset(); // MOD-513
		NetworkOverlayRenderer.clear(); // MOD-665
		ServerBalance.reset(); // MOD-695
	}
}
