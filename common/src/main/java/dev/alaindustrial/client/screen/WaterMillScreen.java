package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.WaterMillMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the LV Water Mill. It mirrors the wind-mill layout: the wheel slot is
 * centered, the left bar shows stored EU, and the status wheel turns blue while water is producing.
 */
public class WaterMillScreen extends MachineScreen<WaterMillMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/water_mill.png");
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 178;

	private static final int STATUS_FRAME_X = 148;
	private static final int STATUS_FRAME_Y = 22;
	private static final int STATUS_FRAME_W = 16;
	private static final int STATUS_FRAME_H = 16;
	private static final float STATUS_UV_X = 176.0F;
	private static final float STATUS_UV_Y = 0.0F;

	public WaterMillScreen(WaterMillMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.inventoryLabelX = 8;
		this.inventoryLabelY = 84;
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);

		// The supplied atlas uses the same left-side energy track as the wind-mill family.
		renderEnergyBar(graphics, EnergyBarSpec.LEFT_WINDMILL);

		// Progress carries the number of horizontal faces touching water (0..4). The slot check keeps
		// the indicator inactive when water is present but the required wheel has not been installed.
		if (this.menu.getSlot(0).hasItem() && this.menu.getProgress() > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					this.leftPos + STATUS_FRAME_X, this.topPos + STATUS_FRAME_Y,
					STATUS_UV_X, STATUS_UV_Y,
					STATUS_FRAME_W, STATUS_FRAME_H,
					TEX_SIZE, TEX_SIZE);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Hovering the energy bar shows the exact buffer as "X / max EU" (R-GUI-14).
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT_WINDMILL);
	}
}
