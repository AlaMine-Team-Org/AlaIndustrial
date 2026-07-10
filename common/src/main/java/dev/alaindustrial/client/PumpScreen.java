package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.menu.PumpMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Pump.
 *
 * <p>Left bar: fluid level (bottom-up). The sprite colour depends on the synced fluid-type id
 * (channel 6): lava → red sprite, water → blue sprite. Rendered from the permille ratio (channels 4/5)
 * so it stays correct regardless of the tank's 10-bucket capacity.
 * Right bar: energy (orange sprite, bottom-up) — stored EU / buffer capacity.
 * Slots: fluid-bucket input at (60,23), empty-bucket output at (98,23).
 */
public class PumpScreen extends AbstractContainerScreen<PumpMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/pump.png");
	private static final int TEX_SIZE = 256;

	// Fluid level fill (LEFT bar). The pump atlas carries two fill sprites in its service area:
	//   lava-red at (190, 0) and water-blue at (201, 0), each 11px wide × 46px tall.
	// The sprite is chosen by the synced fluid-type id (channel 6): lava → red, water → blue, so only one
	// colour is ever drawn — never both at once. inner fill x=16-26 (trough 11px), y=19-65.
	private static final int FLUID_W = 11, FLUID_H = 46;
	private static final int FLUID_X = 16, FLUID_BOTTOM = 65;
	private static final int LAVA_SU = 190, WATER_SU = 201, FLUID_SV = 0;

	// Energy fill (RIGHT bar): orange sprite at (176, 0), 8px wide × 42px tall; inner x=149-158, y=20-64.
	private static final int EFILL_SU = 176, EFILL_SV = 0, EFILL_W = 10, EFILL_H = 44;
	private static final int EFILL_X = 149, EFILL_BOTTOM = 64;

	// Tank capacity in mB for the tooltip (matches PumpBlockEntity.TANK_CAPACITY).
	private static final int TANK_MB = 10_000;

	public PumpScreen(PumpMenu menu, Inventory inventory, Component title) {
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

		// Fluid level fill: grows bottom-up proportional to the tank permille. Only drawn when a fluid is
		// present (id != 0); the sprite colour is selected by the fluid-type id.
		int fluidId = this.menu.getFluidId();
		int denom = this.menu.getFluidPermilleMax();
		int permille = denom > 0 ? this.menu.getFluidPermille() : 0;
		if (fluidId != PumpBlockEntity.FLUID_NONE && permille > 0) {
			int fluidFill = (int) ((long) permille * FLUID_H / denom);
			int su = fluidId == PumpBlockEntity.FLUID_WATER ? WATER_SU : LAVA_SU;
			if (fluidFill > 0) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
						x + FLUID_X, y + FLUID_BOTTOM - fluidFill,
						(float) su, (float) (FLUID_SV + FLUID_H - fluidFill),
						FLUID_W, fluidFill, TEX_SIZE, TEX_SIZE);
			}
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
		// Left bar — fluid level as millibuckets. Pick the tooltip key from the synced fluid-type id.
		int fluidId = this.menu.getFluidId();
		int denom = this.menu.getFluidPermilleMax();
		int permille = denom > 0 ? this.menu.getFluidPermille() : 0;
		if (fluidId != PumpBlockEntity.FLUID_NONE
				&& this.isHovering(FLUID_X, FLUID_BOTTOM - FLUID_H, FLUID_W, FLUID_H, mouseX, mouseY)) {
			int mb = (int) ((long) permille * TANK_MB / denom);
			String key = fluidId == PumpBlockEntity.FLUID_WATER ? "gui.alaindustrial.water" : "gui.alaindustrial.lava";
			graphics.setTooltipForNextFrame(this.font, Component.translatable(key, mb, TANK_MB), mouseX, mouseY);
		}
	}
}
