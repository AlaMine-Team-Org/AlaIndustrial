package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.network.BuildingCardPayload;
import dev.alaindustrial.network.BuildingCardRequestPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import net.minecraft.client.Minecraft;

/**
 * The client end of a town building's card (round 2 of 0.1.219, Q2): a reply updates the open card, and opens it when
 * the server says so — a right click on the house's inlet or the building's plaque. Its presses go back as
 * {@link BuildingCardRequestPayload}s.
 */
public final class BuildingCardClient {

	private BuildingCardClient() {
	}

	/** A reply of the server. */
	public static void receive(BuildingCardPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof BuildingCardScreen open && open.anchor().equals(payload.anchor())) {
			open.update(payload);
		} else if (payload.open()) {
			minecraft.gui.setScreen(new BuildingCardScreen(payload));
		}
	}

	/** Presses action {@code action} (an ordinal, -1 — only a refresh) on the card of {@code shown}. */
	static void press(BuildingCardPayload shown, int action) {
		NetworkDispatcher.get().sendToServer(new BuildingCardRequestPayload(shown.anchor(), action));
	}
}
