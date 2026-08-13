package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.ElectrumChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Electrum Chest window (MOD-409) — the six-row scrolling panel, the same geometry the modular
 * warehouse and the double chest use, over this tier's own atlas.
 *
 * <p>Unlike the iron/silver/gold screens (which extend the plain chest screen and grow one row per
 * tier), this one is built on {@link AbstractStorageScreen}: the container holds nine rows and the
 * panel shows six, so it needs the scrollbar the storage screen draws. Its own atlas file rather
 * than the double chest's, for the reason that file already states — two GUIs that happen to share
 * geometry today should be able to diverge later without repainting each other.
 */
public class ElectrumChestScreen extends AbstractStorageScreen<ElectrumChestMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/electrum_chest.png");

	public ElectrumChestScreen(ElectrumChestMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, ElectrumChestMenu.VISIBLE_ROWS, IMAGE_WIDTH_SCROLL, TEXTURE);
	}
}
