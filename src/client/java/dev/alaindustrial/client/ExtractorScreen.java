package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.ExtractorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the LV Extractor. The static frame (panel, recessed input/output slots,
 * empty energy bar, empty progress chevrons) is a single 256×256 GUI atlas PNG blitted as the
 * background; only the dynamic layers are drawn on top:
 *
 * <ul>
 *   <li>Energy fill — orange bar, grows bottom-up proportional to stored EU / capacity.
 *   <li>Progress chevrons — cyan {@code >>>} sprite, grows left-to-right proportional to
 *       progress / maxProgress, overlaying the dark static chevrons between the two slots.
 * </ul>
 *
 * <p>Both dynamic sprites live in the atlas service area (u ≥ 176) and are pixel-aligned to the
 * frame. Item icons are drawn by vanilla at the {@link ExtractorMenu} slot positions, which match
 * this texture. No readout text — energy is the bar, progress is the chevrons.
 */
public class ExtractorScreen extends AbstractContainerScreen<ExtractorMenu> {
    private static final Identifier TEXTURE =
            Industrialization.id("textures/gui/container/extractor.png");
    private static final int TEX_SIZE = 256;

    // Orange energy-fill sprite in the atlas service area, and where it draws in the GUI.
    // Sprite: u=176-185 (10px), v=0-46 (solid orange). Bar interior runs x=17-26, full fill spans
    // y=19 (top) to y=63 (bottom) — 45px tall, edge-to-edge inside the bar border. Only the bottom
    // EFILL_H rows of the sprite are sampled at full charge.
    private static final int EFILL_SU = 176, EFILL_SV = 0, EFILL_W = 10, EFILL_H = 45;
    private static final int EFILL_X = 17, EFILL_BOTTOM = 64;

    // Cyan progress-chevron sprite in the atlas service area (left-to-right, grows with progress).
    // Sprite: u=188-216 (29px), v=2-7 (6px). Draws over the dark static chevrons at x=80, y=39.
    private static final int PROG_SU = 188, PROG_SV = 2, PROG_SW = 29, PROG_SH = 6;
    private static final int PROG_X = 80, PROG_Y = 39;

    public ExtractorScreen(ExtractorMenu menu, Inventory inventory, Component title) {
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

        // Static frame: visible 176×166 region at the top-left of the 256×256 atlas.
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);

        // Energy fill: orange bar, bottom-up. Sample only the bottom `eFill` rows of the sprite.
        int capacity = this.menu.getCapacity();
        int eFill = capacity > 0 ? (int) ((long) this.menu.getEnergy() * EFILL_H / capacity) : 0;
        if (eFill > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + EFILL_X, y + EFILL_BOTTOM - eFill,
                    (float) EFILL_SU, (float) (EFILL_SV + EFILL_H - eFill),
                    EFILL_W, eFill, TEX_SIZE, TEX_SIZE);
        }

        // Progress chevrons (left-to-right): blit the cyan sprite, growing right with progress.
        int max = this.menu.getMaxProgress();
        int progFilled = max > 0 ? this.menu.getProgress() * PROG_SW / max : 0;
        // Show a minimum 1px the instant processing starts, for immediate visual feedback.
        if (this.menu.getProgress() > 0 && progFilled == 0) {
            progFilled = 1;
        }
        if (progFilled > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + PROG_X, y + PROG_Y,
                    (float) PROG_SU, (float) PROG_SV,
                    progFilled, PROG_SH, TEX_SIZE, TEX_SIZE);
        }
    }
}
