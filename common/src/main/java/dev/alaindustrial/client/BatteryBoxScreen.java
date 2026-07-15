package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.BatteryBoxMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the BatteryBox (energy storage).
 *
 * <p>Rendering order matters: orange fill is drawn FIRST, then the static frame is blitted on top.
 * The battery interior in battery_box.png has transparent pixels — borders and segment dividers remain
 * opaque and overlay the orange fill, creating the segmented battery look.
 *
 * <p>Battery fill is horizontal (left-to-right) proportional to stored EU / capacity.
 */
public class BatteryBoxScreen extends MachineScreen<BatteryBoxMenu> {
    private static final Identifier TEXTURE =
            Industrialization.id("textures/gui/container/battery_box.png");
    private static final int TEX_SIZE = 256;

    // Battery structure (verified by pixel scan of battery_box.png):
    //   y=26        outer top border (73 dark)
    //   y=27        transparent gap row (0 dark) — gray background only
    //   y=28-29     inner top accent stripe (71 dark each) — drawn by frame
    //   y=30-50     cell area: 21 vertical-divider dark pixels per row — orange fill goes here
    //   y=51        inner bottom accent stripe (71 dark) — drawn by frame
    //   y=52        transparent gap row (0 dark) — gray background only
    //   y=53        outer bottom border (73 dark)
    //   x=50        outer left border; x=51-123 inner fill; x=124 outer right border

    // Full transparent interior — needs gray background to avoid holes in empty state.
    private static final int BATT_INT_X = 51, BATT_INT_Y = 27, BATT_INT_W = 73, BATT_INT_H = 26;

    // Orange fill: x=52-122 (1px inset from transparent edge pixels x=51/x=123 that must stay gray),
    // y=30-50 (between inner accent stripes). Width=71, Height=21.
    private static final int BATT_FILL_X = 52, BATT_FILL_Y = 30, BATT_FILL_W = 71, BATT_FILL_H = 21;

    // Pouch charge-slot niche (MOD-052): 18×18 box left of the battery, centred on its rows;
    // top-left = item position (BatteryBoxMenu.CHARGE_SLOT_X/Y) minus 1. Vanilla slot palette.
    private static final int SLOT_X = 20, SLOT_Y = 31;
    private static final int SLOT_DARK = 0xFF373737, SLOT_LIGHT = 0xFFFFFFFF, SLOT_FACE = 0xFF8B8B8B;

    public BatteryBoxScreen(BatteryBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;

        // Step 1a — gray background covers the full transparent interior (y=27-52).
        // Prevents holes in both gap rows (y=27, y=52) and cell area when battery is empty.
        graphics.fill(x + BATT_INT_X, y + BATT_INT_Y,
                x + BATT_INT_X + BATT_INT_W, y + BATT_INT_Y + BATT_INT_H,
                GuiStyle.PANEL);

        // Step 1b — orange fill in the cell area only (y=30-50, between inner accent stripes).
        // Drawn on top of gray; the inner accent stripes and dividers in the frame overlay both.
        int capacity = this.menu.getCapacity();
        int energy = this.menu.getEnergy();
        int fillW = capacity > 0 ? BATT_FILL_W * energy / capacity : 0;
        if (fillW > 0) {
            graphics.fill(x + BATT_FILL_X, y + BATT_FILL_Y,
                    x + BATT_FILL_X + fillW, y + BATT_FILL_Y + BATT_FILL_H,
                    GuiStyle.ENERGY);
        }

        // Step 2 — static frame on top (battery outline + dividers are opaque, interior transparent).
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);

        // Step 2b — pouch charge-slot niche (MOD-052), drawn AFTER the frame because the texture is
        // opaque there (it predates the slot). Classic vanilla three-fill inset: dark top/left,
        // light bottom/right, gray interior; the slot item renders on top at (21,32).
        graphics.fill(x + SLOT_X, y + SLOT_Y, x + SLOT_X + 18, y + SLOT_Y + 18, SLOT_DARK);
        graphics.fill(x + SLOT_X + 1, y + SLOT_Y + 1, x + SLOT_X + 18, y + SLOT_Y + 18, SLOT_LIGHT);
        graphics.fill(x + SLOT_X + 1, y + SLOT_Y + 1, x + SLOT_X + 17, y + SLOT_Y + 17, SLOT_FACE);

        // Step 3 — numeric readout in the gap between the battery (ends y=53) and the inventory
        // label (y=72): stored / capacity (with %) on one line, the output cap on the next.
        Component charge = Component.translatable("gui.alaindustrial.energy_pct",
                this.menu.getEnergy(), this.menu.getCapacity(), this.menu.getChargePercent());
        Component output = Component.translatable("gui.alaindustrial.output", this.menu.getOutputRate());
        drawCentered(graphics, charge, x, y + 55, GuiStyle.TEXT);
        drawCentered(graphics, output, x, y + 64, GuiStyle.TEXT_DIM);
    }

    /** Draw {@code text} horizontally centered across the GUI face at row {@code y}. */
    private void drawCentered(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        int tx = x + (this.imageWidth - this.font.width(text)) / 2;
        graphics.text(this.font, text, tx, y, color, false);
    }
}
