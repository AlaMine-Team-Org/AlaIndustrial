package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.ElectricFurnaceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Electric Furnace. The static frame (panel, recessed slots, empty
 * energy bar, empty progress arrow) comes from a single 256×256 GUI atlas PNG blitted as the
 * background; only the dynamic layer (energy fill, progress fill) is drawn on top. Item icons are
 * drawn by vanilla at the {@link ElectricFurnaceMenu} slot positions, which are aligned to this
 * texture.
 */
public class ElectricFurnaceScreen extends MachineScreen<ElectricFurnaceMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/electric_furnace.png");
	private static final int TEX_SIZE = 256;

	// Segmented orange energy-fill sprite in the atlas service area, and where it draws in the GUI.
	private static final int EFILL_SU = 176, EFILL_SV = 0, EFILL_W = 10, EFILL_H = 44;
	private static final int EFILL_X = 17, EFILL_BOTTOM = 64;

	// Golden progress-arrow fill sprite in the atlas service area (left-to-right, grows with progress).
	private static final int PROG_SU = 176, PROG_SV = 44, PROG_SW = 25, PROG_SH = 9;
	private static final int PROG_X = 82, PROG_Y = 38;

	public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inventory, Component title) {
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

		// Static frame from the atlas: visible 176×166 region at the top-left of the 256×256 texture.
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);

		// Energy fill: blit the segmented orange sprite (bottom-up).
		int capacity = this.menu.getCapacity();
		int eFill = capacity > 0 ? (int) ((long) this.menu.getEnergy() * EFILL_H / capacity) : 0;
		if (eFill > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + EFILL_X, y + EFILL_BOTTOM - eFill,
					(float) EFILL_SU, (float) (EFILL_SV + EFILL_H - eFill),
					EFILL_W, eFill, TEX_SIZE, TEX_SIZE);
		}

		// Progress fill (left-to-right): blit golden arrow sprite from atlas, growing right with progress.
		int max = this.menu.getMaxProgress();
		int progFilled = max > 0 ? this.menu.getProgress() * PROG_SW / max : 0;
		if (progFilled > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					x + PROG_X, y + PROG_Y,
					(float) PROG_SU, (float) PROG_SV,
					progFilled, PROG_SH, TEX_SIZE, TEX_SIZE);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Hovering the energy bar shows the exact buffer as "X / max EU" (R-GUI-14). The EFILL_*
		// constants describe the bar interior; isHovering subtracts leftPos/topPos and adds a 1px
		// margin, so the visible bar border is covered too. Reuses gui.alaindustrial.energy.
		if (this.isHovering(EFILL_X, EFILL_BOTTOM - EFILL_H, EFILL_W, EFILL_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.energy", this.menu.getEnergy(), this.menu.getCapacity()),
					mouseX, mouseY);
		}
	}
}
