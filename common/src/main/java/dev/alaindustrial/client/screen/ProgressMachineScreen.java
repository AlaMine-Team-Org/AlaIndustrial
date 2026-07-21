package dev.alaindustrial.client.screen;

import dev.alaindustrial.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the family of "one static frame + one left-to-right progress sprite"
 * processing machines (Macerator, Electric Furnace, Extractor). The static frame (panel, slots,
 * empty energy bar, empty progress arrow) is blitted from a 256×256 GUI atlas PNG; on top of it,
 * the dynamic layer fills the energy bar ({@link #renderEnergyBar}) and the progress sprite (this
 * class's {@link ProgressSpec}).
 *
 * <p>Each subclass declares only its {@link Identifier texture}, its {@link MachineMenu} type, and
 * its {@link ProgressSpec progress sprite}. That replaces three near-identical copies of the same
 * ~50-line {@code drawMachineFrame}/{@code extractTooltip} pair with one shared implementation.
 *
 * <p>Not for screens with non-trivial progress shapes (e.g. Compressor's bidirectional arrows,
 * Generator's flame) — those keep their own subclasses.
 */
public abstract class ProgressMachineScreen<T extends MachineMenu> extends MachineScreen<T> {
	/** A left-to-right progress sprite in the atlas service area, plus its destination in the GUI frame. */
	protected record ProgressSpec(int spriteU, int spriteV, int spriteW, int spriteH,
			int destX, int destY, boolean minOnePixel) {
		/**
		 * @param spriteU    atlas u of the sprite's left edge
		 * @param spriteV    atlas v of the sprite's top edge
		 * @param spriteW    full sprite width — the progress fill blits {@code (progress/max)*spriteW}
		 *                   pixels of it, left-to-right
		 * @param spriteH    sprite height
		 * @param destX      destination x inside the 176×166 frame
		 * @param destY      destination y inside the 176×166 frame
		 * @param minOnePixel if true, render at least 1 px the instant {@code progress > 0} so the user
		 *                   gets immediate feedback without waiting for the integer-rounded fill to reach 1
		 */
	}

	private final ProgressSpec progress;

	protected ProgressMachineScreen(T menu, Inventory inventory, Component title, ProgressSpec progress) {
		super(menu, inventory, title);
		this.progress = progress;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;
		Identifier texture = texture();

		// Static frame from the atlas: visible imageWidth × imageHeight region at top-left of TEX_SIZE².
		blitStaticFrame(graphics);

		// Energy fill: blit the segmented orange sprite (bottom-up) via the shared MachineScreen helper.
		renderEnergyBar(graphics, EnergyBarSpec.LEFT);

		// Progress fill (left-to-right): blit the sprite, growing right with progress.
		int max = this.menu.getMaxProgress();
		int progFilled = max > 0 ? this.menu.getProgress() * progress.spriteW() / max : 0;
		if (progress.minOnePixel() && this.menu.getProgress() > 0 && progFilled == 0) {
			progFilled = 1;
		}
		if (progFilled > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
					x + progress.destX(), y + progress.destY(),
					(float) progress.spriteU(), (float) progress.spriteV(),
					progFilled, progress.spriteH(), TEX_SIZE, TEX_SIZE);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// Hovering the energy bar shows the exact buffer as "X / max EU" (R-GUI-14).
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);
	}
}
