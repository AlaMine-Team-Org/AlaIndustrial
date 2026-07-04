package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Geothermal Generator.
 *
 * <p>Left bar: lava level (red sprite, bottom-up) — shows remaining lavaTicks / tankCapacity.
 * Right bar: energy (orange sprite, bottom-up) — shows stored EU / buffer capacity.
 * Slots: lava-bucket input at (60,34), empty-bucket output at (98,34).
 */
public class GeothermalGeneratorScreen extends AbstractContainerScreen<GeothermalGeneratorMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/geothermal_generator.png");
	private static final int TEX_SIZE = 256;

	// Lava level fill (LEFT bar): lava-red sprite at atlas service area.
	// Sprite is 11px wide (u=190-200); LAVA_W=11 fills to right inner edge.
	// inner fill x=16-26, y=19-65; LAVA_BOTTOM=65 raises fill 1px vs previous 66.
	private static final int LAVA_SU = 190, LAVA_SV = 0, LAVA_W = 11, LAVA_H = 44;
	private static final int LAVA_X = 16, LAVA_BOTTOM = 65;

	// Energy fill (RIGHT bar): orange sprite at atlas service area.
	// inner fill x=149-159, y=19-64; sprite at u=176, v=0, w=10, h=44.
	private static final int EFILL_SU = 176, EFILL_SV = 0, EFILL_W = 10, EFILL_H = 44;
	private static final int EFILL_X = 149, EFILL_BOTTOM = 64;

	public GeothermalGeneratorScreen(GeothermalGeneratorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;

		// Static frame: 176×166 region at (0,0) of the 256×256 atlas.
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);

		// Lava level fill: grows bottom-up proportional to remaining lava ticks.
		int maxLava = this.menu.getMaxProgress();
		int lavaFill = maxLava > 0 ? (int) ((long) this.menu.getProgress() * LAVA_H / maxLava) : 0;
		if (lavaFill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + LAVA_X, y + LAVA_BOTTOM - lavaFill,
					(float) LAVA_SU, (float) (LAVA_SV + LAVA_H - lavaFill),
					LAVA_W, lavaFill, TEX_SIZE, TEX_SIZE);
		}

		// Energy fill: grows bottom-up proportional to stored EU.
		int capacity = this.menu.getCapacity();
		int eFill = capacity > 0 ? (int) ((long) this.menu.getEnergy() * EFILL_H / capacity) : 0;
		if (eFill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + EFILL_X, y + EFILL_BOTTOM - eFill,
					(float) EFILL_SU, (float) (EFILL_SV + EFILL_H - eFill),
					EFILL_W, eFill, TEX_SIZE, TEX_SIZE);
		}
	}
}
