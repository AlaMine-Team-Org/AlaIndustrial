package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.MoonlitSolarPanelBlockEntity;
import dev.alaindustrial.menu.MoonlitSolarPanelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
public class MoonlitSolarPanelScreen extends MachineScreen<MoonlitSolarPanelMenu> {

    private static final Identifier TEXTURE =
            Industrialization.id("textures/gui/container/moonlit_solar_panel.png");

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
    protected Identifier texture() {
        return TEXTURE;
    }

    @Override
    protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;

        // Static frame — borders and tick marks
        blitStaticFrame(graphics);

        // Energy bar fill (bottom-up) via the shared MachineScreen helper
        int capacity = this.menu.getCapacity();
        int energy   = this.menu.getEnergy();
        renderEnergyBar(graphics, EnergyBarSpec.LEFT);

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
