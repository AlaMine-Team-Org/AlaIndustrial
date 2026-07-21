package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.menu.SolarPanelMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the Solar Panel (LV, neutral T1).
 *
 * Static frame (blit from UV 0,0 → 176×166) provides borders, tick marks, and slot frame.
 * Dynamic elements are blitted from the service area (UV x≥176) so tick marks show through:
 *   - Energy bar  : UV(176, 0..43) orange with tick marks, blitted bottom-up.
 *   - Sun dot     : yellow fill when direct sunlight (MODE_DAY or MODE_PARTIAL).
 *   - Evo bar     : UV(176,48) yellow (day chip) or UV(176,57) blue (night chip).
 */
public class SolarPanelScreen extends MachineScreen<SolarPanelMenu> {
    private static final Identifier TEXTURE =
            Industrialization.id("textures/gui/container/solar_panel.png");

    // ── Sun indicator — service-area 7×7 yellow tile (UV 176,65..71) ────────────
    // Frame: x=153..159, y=17..23 (7×7). Service area: x=176 grey border, x=177..182 yellow.
    // Active: blit UV(176,65) 7×7 → yellow sun design shows. Inactive: texture frame as-is.
    private static final int   SUN_FRAME_X = 153;
    private static final int   SUN_FRAME_Y = 17;
    private static final int   SUN_FRAME_W = 7;
    private static final int   SUN_FRAME_H = 7;
    private static final float SUN_UV_X    = 176.0F;
    private static final float SUN_UV_Y    = 65.0F;

    // ── Evo bar fill zone (x=55..130, y=58..64 = 76×7 including inner borders) ──
    // Height=7: inner top shadow (y=58), 5 fill rows (y=59..63), inner bottom highlight (y=64).
    // Service area YELLOW: UV(176, 48..54) — starts at y=48 (highlight row).
    // Service area BLUE:   UV(176, 57..63) — starts at y=57 (highlight row).
    private static final int   EVO_X          = 55;
    private static final int   EVO_Y          = 58;
    private static final int   EVO_MAX_W      = 75;
    private static final int   EVO_H          = 7;
    private static final float EVO_UV_X       = 176.0F;
    private static final float EVO_UV_Y_DAY   = 48.0F;  // yellow (day chip)
    private static final float EVO_UV_Y_NIGHT = 57.0F;  // blue   (night chip)

    public SolarPanelScreen(SolarPanelMenu menu, Inventory inventory, Component title) {
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

        // Static frame — draws borders, tick-mark lines, slot frames.
        blitStaticFrame(graphics);

        // ── Energy bar fill (bottom-up) — via the shared MachineScreen helper ─────
        int capacity = this.menu.getCapacity();
        int energy   = this.menu.getEnergy();
        renderEnergyBar(graphics, EnergyBarSpec.LEFT);

        // ── Sun indicator — blit service-area tile when active, texture shows when not ──
        int mode = this.menu.getMode();
        boolean directSun = mode == SolarPanelBlockEntity.MODE_DAY
                         || mode == SolarPanelBlockEntity.MODE_PARTIAL;
        if (directSun) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + SUN_FRAME_X, y + SUN_FRAME_Y,
                    SUN_UV_X, SUN_UV_Y,
                    SUN_FRAME_W, SUN_FRAME_H,
                    TEX_SIZE, TEX_SIZE);
        }

        // ── Evo bar fill (left-to-right) — blitted from service area ─────────────
        int evoMax  = this.menu.getEvolveMax();
        int evoProg = this.menu.getEvolveProgress();
        if (evoMax > 0 && evoProg > 0) {
            // Clamp the fill to at least 1px the moment accumulation starts. With a 33 600-tick
            // threshold the proportional fill would otherwise floor to 0px for the first ~448 ticks
            // (~22 s), reading as "not working". evoProg > 0 already means the server counted a
            // qualifying tick (right chip, right time of day, open sky), so a visible pixel is
            // honest feedback that evolution has begun.
            int evoFill = Math.max(1, Math.min(evoProg * EVO_MAX_W / evoMax, EVO_MAX_W));
            boolean nightChip = this.menu.getSlot(0).getItem().is(ModContent.ALIGNMENT_CHIP_NIGHT.get());
            float evoUvY = nightChip ? EVO_UV_Y_NIGHT : EVO_UV_Y_DAY;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + EVO_X, y + EVO_Y,
                    EVO_UV_X, evoUvY,
                    evoFill, EVO_H,
                    TEX_SIZE, TEX_SIZE);
        }

        // ── Text overlay ──────────────────────────────────────────────────────────
        graphics.text(this.font, Component.translatable("gui.alaindustrial.energy", energy, capacity),
                x + 30, y + 22, GuiStyle.TEXT, false);
        graphics.text(this.font, Component.translatable("gui.alaindustrial.output", this.menu.getProductionRate()),
                x + 30, y + 34, GuiStyle.TEXT, false);
        graphics.text(this.font, Component.translatable("gui.alaindustrial.mode", modeLabel(mode)),
                x + 30, y + 46, GuiStyle.TEXT_DIM, false);
    }

    private static Component modeLabel(int mode) {
        String key = switch (mode) {
            case SolarPanelBlockEntity.MODE_DAY     -> "day";
            case SolarPanelBlockEntity.MODE_PARTIAL -> "partial";
            case SolarPanelBlockEntity.MODE_WEATHER -> "weather";
            case SolarPanelBlockEntity.MODE_SNOW    -> "snow";
            default                                  -> "night";
        };
        return Component.translatable("gui.alaindustrial.solar_panel.mode." + key);
    }
}
