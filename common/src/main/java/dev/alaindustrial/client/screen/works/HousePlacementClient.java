package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.client.render.HousePlacementPreview;
import dev.alaindustrial.network.HousePlacementPayload;
import dev.alaindustrial.network.HousePlacementRequestPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import net.minecraft.client.Minecraft;

/**
 * The client end of the house laying window (MOD-877, R108): a reply updates the house's ghost
 * ({@link HousePlacementPreview}) and the open window, and opens it when the server says so — an inlet put down or
 * right-clicked. The window's presses go back as {@link HousePlacementRequestPayload}s.
 */
public final class HousePlacementClient {

	private HousePlacementClient() {
	}

	/** A reply of the server. */
	public static void receive(HousePlacementPayload payload) {
		HousePlacementPreview.receive(payload);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof HousePlacementScreen open && open.inlet().equals(payload.inlet())) {
			open.update(payload);
		} else if (payload.open()) {
			minecraft.gui.setScreen(new HousePlacementScreen(payload));
		}
	}

	/** Asks the server about the inlet of the open window. */
	static void request(HousePlacementPayload shown, int action, int plan, int rotation) {
		NetworkDispatcher.get().sendToServer(new HousePlacementRequestPayload(shown.inlet(), action, plan, rotation));
	}
}
