package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.DiamondChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Diamond Chest window (MOD-599) — the six-row scrolling panel over this tier's own atlas,
 * the same geometry the electrum chest and the modular warehouse use.
 */
public class DiamondChestScreen extends AbstractStorageScreen<DiamondChestMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/diamond_chest.png");

	public DiamondChestScreen(DiamondChestMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, DiamondChestMenu.VISIBLE_ROWS, IMAGE_WIDTH_SCROLL, TEXTURE);
	}
}
