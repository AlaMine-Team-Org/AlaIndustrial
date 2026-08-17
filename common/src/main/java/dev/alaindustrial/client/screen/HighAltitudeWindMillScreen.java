package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.HighAltitudeWindMillMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Texture-backed screen for the high-altitude wind mill (T2, LV) — the shared T2 layout over its own atlas. */
public class HighAltitudeWindMillScreen extends AbstractT2WindMillScreen<HighAltitudeWindMillMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/high_altitude_wind_mill.png");

	public HighAltitudeWindMillScreen(HighAltitudeWindMillMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}
}
