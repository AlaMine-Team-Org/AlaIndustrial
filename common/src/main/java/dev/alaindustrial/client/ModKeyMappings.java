package dev.alaindustrial.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import net.minecraft.client.KeyMapping;
import dev.alaindustrial.network.FluxweaveStepAssistPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's key mappings (MOD-065) — currently one: toggle the Energy Pack charge readout. Declared
 * here, in common, so both loaders bind the same {@link KeyMapping} instance; each registers it its
 * own way (Fabric {@code KeyMappingHelper}, NeoForge {@code RegisterKeyMappingsEvent}) and polls
 * {@link #handleInput()} from its client tick.
 *
 * <p>The binding shows up in vanilla Controls under a mod-owned category, so players can rebind it
 * like any other key.
 */
public final class ModKeyMappings {

	/** Mod-owned category in the Controls screen. */
	// MOD-498 — KeyMapping.Category#register(Identifier) is deprecated by NeoForge only; vanilla leaves it
	// plain. NeoForge points at RegisterKeyMappingsEvent#registerCategory(Category), which is its own event
	// API — unavailable here, because this field is declared in common/ precisely so both loaders share one
	// category instance and each binds the mappings its own way.
	@SuppressWarnings("deprecation")
	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Industrialization.id("main"));

	/** Toggle the worn-pack charge readout. Default: H — free in vanilla. */
	public static final KeyMapping TOGGLE_ENERGY_HUD = new KeyMapping(
			"key.alaindustrial.toggle_energy_hud",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_H,
			CATEGORY);

	/** Toggle the held-drill charge readout (MOD-079). Default: J — free in vanilla, next to H. Its own
	 * key and its own {@link AlaClientConfig#drillHudEnabled} flag, so the drill readout is bound and
	 * shown independently of the pack readout. */
	public static final KeyMapping TOGGLE_DRILL_HUD = new KeyMapping(
			"key.alaindustrial.toggle_drill_hud",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_J,
			CATEGORY);

	/** Open the player profile / dashboard (MOD-133). Default: K — free in vanilla, next to H/J. NOT
	 * P: vanilla binds P to Social Interactions (the multiplayer/LAN player list), so P would clash. */
	public static final KeyMapping OPEN_PROFILE = new KeyMapping(
			"key.alaindustrial.open_profile",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_K,
			CATEGORY);

	/** Toggle the Fluxweave leggings' step assist (MOD-127). Default: G — free in vanilla, next to H/J/K. */
	public static final KeyMapping TOGGLE_STEP_ASSIST = new KeyMapping(
			"key.alaindustrial.toggle_step_assist",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_G,
			CATEGORY);

	private ModKeyMappings() {
	}

	/**
	 * Consume any pending press of either toggle key: flip that overlay, persist the choice, and say so
	 * in the action bar (an overlay may be off-screen-empty when the matching gear isn't held/worn, so
	 * without the message a press would look like it did nothing).
	 */
	public static void handleInput() {
		Player player = Minecraft.getInstance().player;
		while (TOGGLE_ENERGY_HUD.consumeClick()) {
			// apply() writes the field AND persists the file — the same path the config screen uses.
			AlaClientConfig.apply(AlaClientConfig.snapshot()
					.withEnergyHudEnabled(!AlaClientConfig.energyHudEnabled));
			if (player != null) {
				player.sendOverlayMessage(Component.translatable(AlaClientConfig.energyHudEnabled
						? "message.alaindustrial.energy_hud.on"
						: "message.alaindustrial.energy_hud.off"));
			}
		}
		while (TOGGLE_DRILL_HUD.consumeClick()) {
			AlaClientConfig.apply(AlaClientConfig.snapshot()
					.withDrillHudEnabled(!AlaClientConfig.drillHudEnabled));
			if (player != null) {
				player.sendOverlayMessage(Component.translatable(AlaClientConfig.drillHudEnabled
						? "message.alaindustrial.drill_hud.on"
						: "message.alaindustrial.drill_hud.off"));
			}
		}
		while (TOGGLE_STEP_ASSIST.consumeClick()) {
			// Unlike the other three mappings this one cannot be handled client-side: the assist is an
			// attribute modifier on the trousers, and attributes are applied from the stack's components
			// on the server. A custom KeyMapping is not part of vanilla's input sync either, so the
			// server has to be told explicitly. The reply (message + click) comes back from the handler.
			if (player != null) {
				NetworkDispatcher.get().sendToServer(new FluxweaveStepAssistPayload());
			}
		}
		while (OPEN_PROFILE.consumeClick()) {
			Minecraft mc = Minecraft.getInstance();
			// Only open from the in-world state (no screen up) — pressing P inside a menu shouldn't stack.
			// 26.2: the current/target screen lives on Gui, not Minecraft (mc.gui.screen()/setScreen()).
			if (mc.player != null && mc.gui.screen() == null) {
				mc.gui.setScreen(new dev.alaindustrial.client.dashboard.DashboardScreen());
			}
		}
		// MOD-133: re-anchor the inventory profile button to leftPos/topPos (shifts when the recipe
		// book opens without re-firing screen init). Both loaders drive handleInput() each client tick.
		dev.alaindustrial.client.dashboard.InventoryProfileButton.tick();
	}
}
