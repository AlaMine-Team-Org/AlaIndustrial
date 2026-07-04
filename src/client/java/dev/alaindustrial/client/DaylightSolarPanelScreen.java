package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.menu.DaylightSolarPanelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Daylight Solar Panel GUI — post-evolution, day-only panel.
 *
 * <p>Same service-area blit model as {@link SolarPanelScreen}: the static background blit
 * provides borders and tick marks; dynamic fills come from UV x≥176 so tick marks show through.
 * No chip slot and no evolution bar (panel is already evolved).
 */
public class DaylightSolarPanelScreen extends AbstractContainerScreen<DaylightSolarPanelMenu> {

    private static final Identifier TEXTURE =
            Industrialization.id("textures/gui/container/daylight_solar_panel.png");
    private static final int TEX_SIZE = 256;

    // Energy bar — same layout as SolarPanelScreen (x=17..26, y=20..63 = 10×44)
    private static final int   EBAR_X      = 17;
    private static final int   EBAR_BOTTOM = 64;
    private static final int   EBAR_W      = 10;
    private static final int   EBAR_H      = 44;
    private static final float EBAR_UV_X   = 176.0F;
    private static final float EBAR_UV_TOP = 0.0F;

    // Sun indicator — same position and service-area UV as SolarPanelScreen (7×7 yellow tile)
    private static final int   SUN_FRAME_X = 153;
    private static final int   SUN_FRAME_Y = 17;
    private static final int   SUN_FRAME_W = 7;
    private static final int   SUN_FRAME_H = 7;
    private static final float SUN_UV_X    = 176.0F;
    private static final float SUN_UV_Y    = 65.0F;

    public DaylightSolarPanelScreen(DaylightSolarPanelMenu menu, Inventory inventory, Component title) {
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

        // Sun indicator — blit yellow 7×7 tile when daylight is available
        int mode = this.menu.getMode();
        boolean sunActive = mode == DaylightSolarPanelBlockEntity.MODE_DAY
                         || mode == DaylightSolarPanelBlockEntity.MODE_DAY_PARTIAL;
        if (sunActive) {
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
            case DaylightSolarPanelBlockEntity.MODE_DAY         -> "day";
            case DaylightSolarPanelBlockEntity.MODE_DAY_WEATHER -> "weather";
            case DaylightSolarPanelBlockEntity.MODE_DAY_PARTIAL -> "partial";
            case DaylightSolarPanelBlockEntity.MODE_DAY_SNOW    -> "snow";
            default                                              -> "night";
        };
        return Component.translatable("gui.alaindustrial.solar_panel.mode." + key);
    }
}
