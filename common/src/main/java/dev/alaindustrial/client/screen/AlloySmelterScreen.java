package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.AlloySmelterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Alloy Smelter (MOD-064). The static frame (panel, the three recessed
 * component slots, the empty energy bar and progress arrow) comes from the 256×256 GUI atlas; the
 * dynamic layer (energy fill, progress fill) is drawn by {@link ProgressMachineScreen}.
 *
 * <p>The three input slots sit in one vertical column rather than a row: it makes visually plain that
 * they are interchangeable — a column of identical cells feeding one arrow reads as "put the parts
 * here", where a row spaced like the other machines' input→output line would suggest an order.
 */
public class AlloySmelterScreen extends ProgressMachineScreen<AlloySmelterMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/alloy_smelter.png");

	// Golden progress-arrow fill sprite in the atlas service area, centred on the input column.
	private static final ProgressSpec PROGRESS = new ProgressSpec(
			176, 48, 25, 9,  // sprite u/v/w/h
			79, 38,           // dest x/y in the 176×166 frame
			false);           // no min-1px

	public AlloySmelterScreen(AlloySmelterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PROGRESS);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}
}
