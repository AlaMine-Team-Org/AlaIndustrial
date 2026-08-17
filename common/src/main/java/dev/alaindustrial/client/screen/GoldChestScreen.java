package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.GoldChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Gold Chest (6 chest rows) — the shared static-chest layout
 * ({@link AbstractStaticChestScreen}) over {@code gold_chest.png}. Slot coordinates are baked into
 * {@link GoldChestMenu} and line up with this texture.
 */
public class GoldChestScreen extends AbstractStaticChestScreen<GoldChestMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/gold_chest.png");

	/** The atlas is taller than the silver chest (220 vs 202): one extra chest row. */
	private static final int IMAGE_HEIGHT = 220;
	/** Player-inventory "Inventory" label Y — 11px above the player grid (GoldChestMenu: 6 rows → grid top 139). */
	private static final int PLAYER_INV_LABEL_Y = 128;

	public GoldChestScreen(GoldChestMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, TEXTURE, IMAGE_HEIGHT, PLAYER_INV_LABEL_Y);
	}
}
