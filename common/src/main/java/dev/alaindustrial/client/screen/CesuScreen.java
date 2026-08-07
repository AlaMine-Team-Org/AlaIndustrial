package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.CesuMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Reinforced Energy Storage.
 *
 * <p>Reuses the Battery Box GUI atlas on purpose: the frame is a battery gauge and a panel, and both
 * tiers show exactly that. The slot niches are drawn in code (the atlas is opaque where they sit), so
 * a second slot costs no new texture — only a second niche.
 *
 * <p>Rendering order matters and is inherited from the Battery Box screen: the orange fill goes down
 * FIRST, then the static frame is blitted on top, because the battery interior in the atlas is
 * transparent and its borders/dividers overlay the fill to create the segmented look.
 */
public class CesuScreen extends MachineScreen<CesuMenu> {
    private static final Identifier TEXTURE =
            Industrialization.id("textures/gui/container/battery_box.png");

    // Battery geometry — same atlas, so the same constants as BatteryBoxScreen.
    private static final int BATT_INT_X = 51, BATT_INT_Y = 27, BATT_INT_W = 73, BATT_INT_H = 26;
    private static final int BATT_FILL_X = 52, BATT_FILL_Y = 30, BATT_FILL_W = 71, BATT_FILL_H = 21;

    // Slot niches: top-left = the menu's item position minus 1. Vanilla slot palette.
    private static final int CHARGE_NICHE_X = 20, CHARGE_NICHE_Y = 31;
    private static final int DISCHARGE_NICHE_X = 138, DISCHARGE_NICHE_Y = 31;
    private static final int SLOT_DARK = 0xFF373737, SLOT_LIGHT = 0xFFFFFFFF, SLOT_FACE = 0xFF8B8B8B;

    public CesuScreen(CesuMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected Identifier texture() {
        return TEXTURE;
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

        // Step 1a — gray background over the full transparent interior, so an empty battery shows a
        // panel rather than holes.
        graphics.fill(x + BATT_INT_X, y + BATT_INT_Y,
                x + BATT_INT_X + BATT_INT_W, y + BATT_INT_Y + BATT_INT_H,
                GuiStyle.PANEL);

        // Step 1b — orange fill in the cell area only.
        int capacity = this.menu.getCapacity();
        int energy = this.menu.getEnergy();
        int fillW = capacity > 0 ? BATT_FILL_W * energy / capacity : 0;
        if (fillW > 0) {
            graphics.fill(x + BATT_FILL_X, y + BATT_FILL_Y,
                    x + BATT_FILL_X + fillW, y + BATT_FILL_Y + BATT_FILL_H,
                    GuiStyle.ENERGY);
        }

        // Step 2 — static frame on top; its transparent interior reveals the fill laid down above.
        blitStaticFrame(graphics);

        // Step 2b — both slot niches, drawn AFTER the frame because the atlas is opaque there.
        drawSlotNiche(graphics, x + CHARGE_NICHE_X, y + CHARGE_NICHE_Y);
        drawSlotNiche(graphics, x + DISCHARGE_NICHE_X, y + DISCHARGE_NICHE_Y);

        // Step 3 — numeric readout between the battery (ends y=53) and the inventory label (y=72).
        Component charge = Component.translatable("gui.alaindustrial.energy_pct",
                this.menu.getEnergy(), this.menu.getCapacity(), this.menu.getChargePercent());
        Component output = Component.translatable("gui.alaindustrial.output", this.menu.getOutputRate());
        drawCentered(graphics, charge, x, y + 55, GuiStyle.TEXT);
        drawCentered(graphics, output, x, y + 64, GuiStyle.TEXT_DIM);
    }

    /** Classic vanilla three-fill inset: dark top/left, light bottom/right, gray interior. */
    private void drawSlotNiche(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT_DARK);
        graphics.fill(x + 1, y + 1, x + 18, y + 18, SLOT_LIGHT);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FACE);
    }

    /** Draw {@code text} horizontally centered across the GUI face at row {@code y}. */
    private void drawCentered(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        int tx = x + (this.imageWidth - this.font.width(text)) / 2;
        graphics.text(this.font, text, tx, y, color, false);
    }
}
