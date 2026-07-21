package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.MaceratorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Macerator. The static frame (panel, recessed slots, empty energy
 * bar, empty progress arrow) comes from a single 256×256 GUI atlas PNG; the dynamic layer (energy
 * fill, progress fill) is drawn by {@link ProgressMachineScreen}. Item icons are drawn by vanilla at
 * the {@link MaceratorMenu} slot positions, which are aligned to this texture.
 */
public class MaceratorScreen extends ProgressMachineScreen<MaceratorMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/macerator.png");

	// Golden progress-arrow fill sprite in the atlas service area (left-to-right, grows with progress).
	private static final ProgressSpec PROGRESS = new ProgressSpec(
			176, 44, 25, 9,  // sprite u/v/w/h
			82, 38,           // dest x/y in the 176×166 frame
			false);           // no min-1px — the wide arrow already renders fine at fill=1

	public MaceratorScreen(MaceratorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PROGRESS);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}
}
