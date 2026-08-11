package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.DoubleChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The double chest's window (MOD-391) — the 6-row scrolling panel, shared by all three tiers: only
 * the total row count differs between them and it arrives via {@code ContainerData}, so one screen
 * over one menu type serves iron, silver and gold doubles alike. The tier shows in the title, which
 * the block's {@code MenuProvider} sends with the open packet.
 */
public class DoubleChestScreen extends AbstractStorageScreen<DoubleChestMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/double_chest.png");

	public DoubleChestScreen(DoubleChestMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, DoubleChestMenu.VISIBLE_ROWS, IMAGE_WIDTH_SCROLL, TEXTURE);
	}
}
