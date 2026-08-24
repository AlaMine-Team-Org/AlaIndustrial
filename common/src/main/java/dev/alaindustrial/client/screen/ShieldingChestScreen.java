package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.ShieldingChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Shielding Chest (MOD-474) — the shared static-chest layout
 * ({@link AbstractStaticChestScreen}) over four chest rows.
 *
 * <p><b>It shares the iron chest's atlas on purpose.</b> The chest GUI atlases in this mod carry no
 * tier colour at all — they are the plain vanilla grey panel and differ from each other in exactly one
 * thing, the number of 18 px rows (iron 4, silver 5, gold 6). This chest has four rows, so a
 * {@code shielding_chest.png} of its own would be a byte-for-byte copy of {@code iron_chest.png}: a
 * second file that can only ever drift from the first, never differ from it usefully. The block's
 * identity is carried where a player actually sees it — the 3D model in the world
 * ({@code entity/chest/shielding.png}: lead plate, hazard banding, trefoil on the lid) — not on a slot
 * grid. If this chest ever needs its own panel art, this is the one line to change.
 */
public class ShieldingChestScreen extends AbstractStaticChestScreen<ShieldingChestMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/iron_chest.png");

	/** The atlas is taller than the vanilla 3-row chest (184 vs 166): one extra chest row. */
	private static final int IMAGE_HEIGHT = 184;
	/** Player-inventory "Inventory" label Y — 11px above the player grid (4 rows → grid top 103). */
	private static final int PLAYER_INV_LABEL_Y = 92;

	public ShieldingChestScreen(ShieldingChestMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, TEXTURE, IMAGE_HEIGHT, PLAYER_INV_LABEL_Y);
	}
}
