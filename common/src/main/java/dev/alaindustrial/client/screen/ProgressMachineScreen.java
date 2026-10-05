package dev.alaindustrial.client.screen;

import dev.alaindustrial.menu.MachineMenu;
import java.util.Objects;
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
 * <p>Each subclass declares only its {@link MachineMenu} type and its {@link MachineLayout} — atlas, energy
 * bar, {@link ProgressSpec progress sprite} and, if it has one, status row (MOD-716). That replaces three
 * near-identical copies of the same ~50-line {@code drawMachineFrame}/{@code extractTooltip} pair with one
 * shared implementation.
 *
 * <p>Not for screens with non-trivial progress shapes (e.g. Compressor's bidirectional arrows,
 * Generator's flame) — those extend {@link LayoutMachineScreen} with no progress sprite.
 */
public abstract class ProgressMachineScreen<T extends MachineMenu> extends LayoutMachineScreen<T> {
	/** A left-to-right progress sprite in the atlas service area, plus its destination in the GUI frame. */
	public record ProgressSpec(int spriteU, int spriteV, int spriteW, int spriteH,
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

		/** Where the sprite lands in the frame — the click area of a screen whose arrow is exactly the sprite. */
		public GuiRect area() {
			return new GuiRect(destX, destY, spriteW, spriteH);
		}
	}

	private final ProgressSpec progress;

	/** {@code layout} names the atlas, the bar and the progress sprite (MOD-716, CLI-3); the sprite is required. */
	protected ProgressMachineScreen(T menu, Inventory inventory, Component title, MachineLayout layout) {
		super(menu, inventory, title, layout);
		this.progress = Objects.requireNonNull(layout.progress(), "a progress screen's layout names its sprite");
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;
		Identifier texture = texture();

		// Static frame from the atlas: visible imageWidth × imageHeight region at top-left of TEX_SIZE².
		blitStaticFrame(graphics);


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

}
