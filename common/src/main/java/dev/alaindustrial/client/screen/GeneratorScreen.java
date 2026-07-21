package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.GeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the LV Generator. Static frame from atlas PNG; dynamic layer draws
 * the energy fill (bottom-up orange bar) and burn flame (top-shrinks as fuel is consumed).
 */
public class GeneratorScreen extends MachineScreen<GeneratorMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/generator.png");

	// Burn indicator: flame sprite starts at u=188 (teardrop inner pixels, 14px wide at widest).
	// u=176-185 is the energy-bar sprite — must NOT include it in this blit.
	// Top-anchored: bright yellow-orange tip shows while fuel remains; base shrinks last.
	private static final int FLAME_SU = 188, FLAME_SV = 0, FLAME_W = 14, FLAME_H = 21;
	private static final int FLAME_X = 124, FLAME_Y = 31;

	public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		// Static frame: visible imageWidth × imageHeight region at top-left of the 256×256 atlas.
		blitStaticFrame(graphics);

		// Energy fill: blit the segmented orange sprite (bottom-up) via the shared MachineScreen helper.
		renderEnergyBar(graphics, EnergyBarSpec.LEFT);

		// Burn indicator: bottom-anchored — base stays, tip disappears from top as fuel burns.
		// dest y = FLAME_Y + (FLAME_H - flameFill) shifts the blit up as fill grows.
		int maxBurn = this.menu.getMaxProgress();
		int flameFill = maxBurn > 0 ? this.menu.getProgress() * FLAME_H / maxBurn : 0;
		if (flameFill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + FLAME_X, y + FLAME_Y + FLAME_H - flameFill,
					(float) FLAME_SU, (float) (FLAME_SV + FLAME_H - flameFill),
					FLAME_W, flameFill, TEX_SIZE, TEX_SIZE);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Hovering the energy bar shows the exact buffer as "X / max EU" (R-GUI-14).
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);
	}
}
