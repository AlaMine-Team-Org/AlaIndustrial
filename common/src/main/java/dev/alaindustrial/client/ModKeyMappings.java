package dev.alaindustrial.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import net.minecraft.client.KeyMapping;
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
	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Industrialization.id("main"));

	/** Toggle the worn-pack charge readout. Default: H — free in vanilla. */
	public static final KeyMapping TOGGLE_ENERGY_HUD = new KeyMapping(
			"key.alaindustrial.toggle_energy_hud",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_H,
			CATEGORY);

	private ModKeyMappings() {
	}

	/**
	 * Consume any pending press of the toggle key: flip the overlay, persist the choice, and say so
	 * in the action bar (the overlay may be off-screen-empty when no pack is worn, so without the
	 * message a press would look like it did nothing).
	 */
	public static void handleInput() {
		while (TOGGLE_ENERGY_HUD.consumeClick()) {
			// apply() writes the field AND persists the file — the same path the config screen uses.
			AlaClientConfig.apply(AlaClientConfig.snapshot()
					.withEnergyHudEnabled(!AlaClientConfig.energyHudEnabled));
			Player player = Minecraft.getInstance().player;
			if (player != null) {
				player.sendOverlayMessage(Component.translatable(AlaClientConfig.energyHudEnabled
						? "message.alaindustrial.energy_hud.on"
						: "message.alaindustrial.energy_hud.off"));
			}
		}
	}
}
