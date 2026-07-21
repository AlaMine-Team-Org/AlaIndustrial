package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.ExtractorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the LV Extractor. The static frame (panel, recessed input/output slots,
 * empty energy bar, empty progress chevrons) is a single 256×256 GUI atlas PNG; the dynamic layer
 * (energy fill, progress chevrons) is drawn by {@link ProgressMachineScreen}. Item icons are drawn
 * by vanilla at the {@link ExtractorMenu} slot positions, which match this texture.
 *
 * <p>The chevron sprite is a cyan {@code >>>} overlay that grows left-to-right over the dark static
 * chevrons between the two slots, proportional to progress/maxProgress.
 */
public class ExtractorScreen extends ProgressMachineScreen<ExtractorMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/extractor.png");

	// Cyan progress-chevron sprite in the atlas service area (left-to-right, grows with progress).
	// Sprite: u=188-216 (29px), v=2-7 (6px). Draws over the dark static chevrons at x=80, y=39.
	private static final ProgressSpec PROGRESS = new ProgressSpec(
			188, 2, 29, 6,  // sprite u/v/w/h
			80, 39,          // dest x/y in the 176×166 frame
			true);           // min-1px — the narrow chevrons benefit from immediate feedback at progress=1

	public ExtractorScreen(ExtractorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PROGRESS);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}
}
