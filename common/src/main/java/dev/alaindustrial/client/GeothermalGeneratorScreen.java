package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.GeothermalGeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
public class GeothermalGeneratorScreen extends MachineScreen<GeothermalGeneratorMenu> {
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

	// Lava burn buffer expressed in millibuckets for the tooltip. The tank holds 10 buckets
	// (see GeothermalGeneratorBlockEntity.TANK_CAPACITY); 10 buckets × 1000 mB = 10000 mB.
	private static final int LAVA_TANK_MB = 10_000;

	public GeothermalGeneratorScreen(GeothermalGeneratorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
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

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Right bar — stored EU / buffer.
		if (this.isHovering(EFILL_X, EFILL_BOTTOM - EFILL_H, EFILL_W, EFILL_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.energy", this.menu.getEnergy(), this.menu.getCapacity()),
					mouseX, mouseY);
		}
		// Left bar — lava burn buffer as millibuckets. Derive mB from the progress/maxProgress ratio
		// (tank = 10000 mB) so it stays correct even if geothermalBurnTicks changes in config.
		int maxProgress = this.menu.getMaxProgress();
		if (maxProgress > 0 && this.isHovering(LAVA_X, LAVA_BOTTOM - LAVA_H, LAVA_W, LAVA_H, mouseX, mouseY)) {
			int lavaMb = (int) ((long) this.menu.getProgress() * LAVA_TANK_MB / maxProgress);
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.lava", lavaMb, LAVA_TANK_MB),
					mouseX, mouseY);
		}
	}
}
