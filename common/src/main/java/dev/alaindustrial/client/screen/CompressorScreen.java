package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.CompressorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the LV Compressor.
 *
 * <p>Left bar: energy fill (orange, bottom-up).
 * Center: bidirectional compression indicator — left arrow fills from its outer edge
 * (x=81) rightward, right arrow fills from its outer edge (x=105) leftward.
 * Both fill proportional to progress/maxProgress, growing inward toward each other.
 * At 100% both arrows are fully lit; at 0% both are invisible (static gray shows through).
 *
 * <p>Arrow positions verified by pixel scan of compressor.png:
 *   left static arrow  x=81-86, y=34-51 → service sprite u=191-196, v=0-17
 *   right static arrow x=100-105, y=34-51 → service sprite u=210-215, v=0-17
 */
public class CompressorScreen extends MachineScreen<CompressorMenu> {
    private static final Identifier TEXTURE =
            Industrialization.id("textures/gui/container/compressor.png");

    // Compression arrows (pixel-verified against compressor.png):
    //   Left  static arrow: x=81-86, y=34-51
    //   Right static arrow: x=100-105, y=34-51
    //   Both sprites are 6px wide but border pixels at u=195-196 (left) and u=215 (right)
    //   are dark — animation uses only the 4px yellow body so both sides start visibly
    //   at the same progress (fill=1 → bright yellow on each outer edge simultaneously).
    //   Left  body: u=191-194 (bright→dim, grows rightward from x=81)
    //   Right body: u=211-214 (dim→bright, grows leftward  from x=104)
    private static final int ARR_LEFT_X       = 81;   // dest x for left arrow outer edge
    private static final int ARR_RIGHT_LAST_X = 104;  // dest x for right arrow outer edge
    private static final int ARR_Y            = 34;   // dest y (includes border row)
    private static final int ARR_H            = 18;   // arrow height
    private static final int ARR_W            = 4;    // yellow body width per arrow (px)
    private static final int ARR_LEFT_SU      = 191;  // left sprite: u of first yellow pixel
    private static final int ARR_RIGHT_SU_END = 214;  // right sprite: u of last yellow pixel
    private static final int ARR_SV           = 0;    // sprite v start

    public CompressorScreen(CompressorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected Identifier texture() {
        return TEXTURE;
    }

    @Override
    protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;

        // Static frame: full imageWidth × imageHeight visible region.
        blitStaticFrame(graphics);

        // Energy fill: grows bottom-up proportional to stored EU / capacity.
        renderEnergyBar(graphics, EnergyBarSpec.LEFT);

        // Compression arrows: grow from outer edges inward, overlaying static arrow outlines.
        //   Left  fills from x=81 rightward  (outer → inner).
        //   Right fills from x=105 leftward  (outer → inner).
        //   At fill=ARR_W both arrows are fully lit.
        int maxProgress = this.menu.getMaxProgress();
        int progress    = this.menu.getProgress();
        int fill        = maxProgress > 0 ? ARR_W * progress / maxProgress : 0;
        // Show minimum 1px the instant processing starts (progress > 0) so the user
        // gets immediate visual feedback without waiting for fill to round up to 1.
        if (progress > 0 && fill == 0) fill = 1;
        if (fill > 0) {
            // Left arrow: sample leftmost `fill` pixels of sprite, place at outer left edge.
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + ARR_LEFT_X, y + ARR_Y,
                    (float) ARR_LEFT_SU, (float) ARR_SV,
                    fill, ARR_H, TEX_SIZE, TEX_SIZE);

            // Right arrow: sample rightmost `fill` pixels of sprite, place flush to outer right edge.
            // As fill grows, dest_x moves left and src_u moves left by the same amount.
            int rightDestX = ARR_RIGHT_LAST_X - fill + 1;
            int rightSrcU  = ARR_RIGHT_SU_END - fill + 1;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + rightDestX, y + ARR_Y,
                    (float) rightSrcU, (float) ARR_SV,
                    fill, ARR_H, TEX_SIZE, TEX_SIZE);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // Hovering the energy bar shows the exact buffer as "X / max EU" (R-GUI-14).
        renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);
    }
}
