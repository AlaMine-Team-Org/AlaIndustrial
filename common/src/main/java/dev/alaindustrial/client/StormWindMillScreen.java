package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.StormWindMillMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Storm Wind Mill (T2, LV). Static frame from the wind-mill GUI atlas;
 * dynamic layer draws the energy fill (bottom-up orange bar). No evolution bar — the T2 mill is a
 * leaf tier. A read-only energy display like the T2 solar-panel screens.
 */
public class StormWindMillScreen extends AbstractContainerScreen<StormWindMillMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/generator.png");
	private static final int TEX_SIZE = 256;

	private static final int EFILL_SU = 176, EFILL_SV = 0, EFILL_W = 10, EFILL_H = 44;
	private static final int EFILL_X = 17, EFILL_BOTTOM = 64;

	public StormWindMillScreen(StormWindMillMenu menu, Inventory inventory, Component title) {
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

		// Static frame: visible 176×166 region at top-left of the 256×256 atlas.
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);

		// Energy fill: grows bottom-up, segmented orange look from atlas sprite.
		int capacity = this.menu.getCapacity();
		int eFill = capacity > 0 ? (int) ((long) this.menu.getEnergy() * EFILL_H / capacity) : 0;
		if (eFill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + EFILL_X, y + EFILL_BOTTOM - eFill,
					(float) EFILL_SU, (float) (EFILL_SV + EFILL_H - eFill),
					EFILL_W, eFill, TEX_SIZE, TEX_SIZE);
		}

		// Text overlay: energy + current output.
		graphics.text(this.font,
				Component.translatable("gui.alaindustrial.energy", this.menu.getEnergy(), this.menu.getCapacity()),
				x + 30, y + 22, GuiStyle.TEXT, false);
		graphics.text(this.font,
				Component.translatable("gui.alaindustrial.output", this.menu.getProductionRate()),
				x + 30, y + 34, GuiStyle.TEXT, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (this.isHovering(EFILL_X, EFILL_BOTTOM - EFILL_H, EFILL_W, EFILL_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.energy", this.menu.getEnergy(), this.menu.getCapacity()),
					mouseX, mouseY);
		}
	}
}
