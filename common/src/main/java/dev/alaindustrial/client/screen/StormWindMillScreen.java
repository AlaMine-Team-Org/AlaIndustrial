package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.StormWindMillMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Texture-backed screen for the storm wind mill (T2, LV) — the shared T2 layout over its own atlas. */
public class StormWindMillScreen extends AbstractT2WindMillScreen<StormWindMillMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/storm_wind_mill.png");

	public StormWindMillScreen(StormWindMillMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}
}
