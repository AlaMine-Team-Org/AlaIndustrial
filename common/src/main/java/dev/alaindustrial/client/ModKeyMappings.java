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

	/** Toggle the held-drill charge readout (MOD-079). Default: J — free in vanilla, next to H. Its own
	 * key and its own {@link AlaClientConfig#drillHudEnabled} flag, so the drill readout is bound and
	 * shown independently of the pack readout. */
	public static final KeyMapping TOGGLE_DRILL_HUD = new KeyMapping(
			"key.alaindustrial.toggle_drill_hud",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_J,
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
	}
}
