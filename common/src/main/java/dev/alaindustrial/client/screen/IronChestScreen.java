package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.IronChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Iron Chest (4 chest rows) — the shared static-chest layout
 * ({@link AbstractStaticChestScreen}) over {@code iron_chest.png}. Slot coordinates are baked into
 * {@link IronChestMenu} and line up with this texture.
 */
public class IronChestScreen extends AbstractStaticChestScreen<IronChestMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/iron_chest.png");

	/** The atlas is taller than the vanilla 3-row chest (184 vs 166): one extra chest row. */
	private static final int IMAGE_HEIGHT = 184;
	/** Player-inventory "Inventory" label Y — 11px above the player grid (IronChestMenu: 4 rows → grid top 103). */
	private static final int PLAYER_INV_LABEL_Y = 92;

	public IronChestScreen(IronChestMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, TEXTURE, IMAGE_HEIGHT, PLAYER_INV_LABEL_Y);
	}
}
