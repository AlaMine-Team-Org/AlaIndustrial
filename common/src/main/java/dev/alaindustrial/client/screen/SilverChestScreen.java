package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.SilverChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Silver Chest (5 chest rows) — the shared static-chest layout
 * ({@link AbstractStaticChestScreen}) over {@code silver_chest.png}. Slot coordinates are baked into
 * {@link SilverChestMenu} and line up with this texture.
 */
public class SilverChestScreen extends AbstractStaticChestScreen<SilverChestMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/silver_chest.png");

	/** The atlas is taller than the iron chest (202 vs 184): one extra chest row. */
	private static final int IMAGE_HEIGHT = 202;
	/** Player-inventory "Inventory" label Y — 11px above the player grid (SilverChestMenu: 5 rows → grid top 121). */
	private static final int PLAYER_INV_LABEL_Y = 110;

	public SilverChestScreen(SilverChestMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, TEXTURE, IMAGE_HEIGHT, PLAYER_INV_LABEL_Y);
	}
}
