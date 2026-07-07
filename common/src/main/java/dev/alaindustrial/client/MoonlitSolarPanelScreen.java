package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Moonlit Solar Panel GUI — post-evolution, night-only panel.
 *
 * <p>Mirrors {@link DaylightSolarPanelScreen} but inverts the active-sky check:
 * the sun indicator lights up when the moon is visible (MODE_NIGHT / MODE_NIGHT_PARTIAL).
 * No chip slot and no evolution bar (panel is already evolved).
 */
public class MoonlitSolarPanelScreen extends AbstractContainerScreen<MoonlitSolarPanelMenu> {

    private static final Identifier TEXTURE =
            Industrialization.id("textures/gui/container/moonlit_solar_panel.png");
    private static final int TEX_SIZE = 256;

    // Energy bar — same layout as SolarPanelScreen (x=17..26, y=20..63 = 10×44)
    private static final int   EBAR_X      = 17;
    private static final int   EBAR_BOTTOM = 64;
    private static final int   EBAR_W      = 10;
    private static final int   EBAR_H      = 44;
    private static final float EBAR_UV_X   = 176.0F;
    private static final float EBAR_UV_TOP = 0.0F;

    // Moon indicator — reuses the same 7×7 service-area tile as the solar sun indicator
    private static final int   SUN_FRAME_X = 153;
    private static final int   SUN_FRAME_Y = 17;
    private static final int   SUN_FRAME_W = 7;
    private static final int   SUN_FRAME_H = 7;
    private static final float SUN_UV_X    = 176.0F;
    private static final float SUN_UV_Y    = 65.0F;

    public MoonlitSolarPanelScreen(MoonlitSolarPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        // Static frame — borders and tick marks
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);

        // Energy bar fill (bottom-up)
        int capacity = this.menu.getCapacity();
        int energy   = this.menu.getEnergy();
        int eFill    = capacity > 0 ? (int) ((long) energy * EBAR_H / capacity) : 0;
        if (eFill > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + EBAR_X,                y + EBAR_BOTTOM - eFill,
                    EBAR_UV_X, EBAR_UV_TOP + (EBAR_H - eFill),
                    EBAR_W,                    eFill,
                    TEX_SIZE, TEX_SIZE);
        }

        // Moon indicator — blit yellow 7×7 tile when moonlight is available
        int mode = this.menu.getMode();
        boolean moonActive = mode == MoonlitSolarPanelBlockEntity.MODE_NIGHT
                          || mode == MoonlitSolarPanelBlockEntity.MODE_NIGHT_PARTIAL;
        if (moonActive) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + SUN_FRAME_X, y + SUN_FRAME_Y,
                    SUN_UV_X, SUN_UV_Y,
                    SUN_FRAME_W, SUN_FRAME_H,
                    TEX_SIZE, TEX_SIZE);
        }

        // Text overlay
        graphics.text(this.font, Component.translatable("gui.alaindustrial.energy", energy, capacity),
                x + 30, y + 22, GuiStyle.TEXT, false);
        graphics.text(this.font, Component.translatable("gui.alaindustrial.output", this.menu.getProductionRate()),
                x + 30, y + 34, GuiStyle.TEXT, false);
        graphics.text(this.font, Component.translatable("gui.alaindustrial.mode", modeLabel(mode)),
                x + 30, y + 46, GuiStyle.TEXT_DIM, false);
    }

    private static Component modeLabel(int mode) {
        String key = switch (mode) {
            case MoonlitSolarPanelBlockEntity.MODE_NIGHT         -> "night";
            case MoonlitSolarPanelBlockEntity.MODE_NIGHT_WEATHER -> "night_weather";
            case MoonlitSolarPanelBlockEntity.MODE_NIGHT_PARTIAL -> "night_partial";
            case MoonlitSolarPanelBlockEntity.MODE_NIGHT_SNOW    -> "night_snow";
            default                                               -> "idle_day";
        };
        return Component.translatable("gui.alaindustrial.solar_panel.mode." + key);
    }
}
