package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.ChargePadMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Charging Station (MOD-416) — four rows of text next to the buffer bar, no slots.
 *
 * <p>The station works without this window ever being opened; it exists for the player who wants the
 * numbers behind the indicator light. Right-click opens it, including while standing on the plate —
 * stepping on deliberately does not, or the window would pop up on every step.
 *
 * <p><b>Idle draws a dash, not a zero.</b> With nobody on the plate the station delivers nothing, and
 * three rows of zeroes would read as measured values ("it is charging 0 items at 0 EU/t") rather than
 * as the absence of a measurement. The dash is also what the L3 frame captures, since the shot stand
 * opens the screen from beside the plate rather than on it.
 */
public class ChargePadScreen extends MachineScreen<ChargePadMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/charge_pad.png");

	/**
	 * Text column and rows, left of nothing and right of the bar (which occupies x 17..27, y 20..63).
	 *
	 * <p>Public for symmetry with the rest of the screen family; the charging station has no text-row
	 * stand of its own, so no gate reads these numbers today. Should one be added, it must measure the
	 * strip from here rather than from a copy that would drift the first time the layout moves — a
	 * drifted copy keeps passing while measuring a blank strip.
	 */
	public static final int TEXT_X = 34;
	public static final int TEXT_ROW_TOP = 22;
	public static final int TEXT_ROW_STEP = 11;

	/** Stands in for any value the station cannot report right now. */
	private static final String DASH = "—";

	public ChargePadScreen(ChargePadMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		blitStaticFrame(graphics);
		renderEnergyBar(graphics, EnergyBarSpec.LEFT);

		boolean serving = this.menu.isServing();
		int eta = this.menu.getEtaSeconds();

		// Row 0 — the station's own reserve, the one value that is always real.
		drawRow(graphics, x, y, 0, Component.translatable("gui.alaindustrial.energy_pct",
				this.menu.getEnergy(), this.menu.getCapacity(), this.menu.getChargePercent()), GuiStyle.TEXT);
		drawRow(graphics, x, y, 1, Component.translatable("gui.alaindustrial.charge_pad.rate",
				serving ? String.valueOf(this.menu.getRateEuPerTick()) : DASH), GuiStyle.TEXT_DIM);
		drawRow(graphics, x, y, 2, Component.translatable("gui.alaindustrial.charge_pad.items",
				serving ? String.valueOf(this.menu.getItemsCharging()) : DASH), GuiStyle.TEXT_DIM);
		// The estimate needs a rate to divide by, so it goes dark the moment delivery stops — and also
		// when the gear it is measuring is already full, where "0 s" would be a countdown that never runs.
		drawRow(graphics, x, y, 3, Component.translatable("gui.alaindustrial.charge_pad.eta",
				serving && eta > 0 ? String.valueOf(eta) : DASH), GuiStyle.TEXT_DIM);
	}

	/** Draw one readout row, left-aligned in the text column. */
	private void drawRow(GuiGraphicsExtractor graphics, int x, int y, int row, Component text, int color) {
		graphics.text(this.font, text, x + TEXT_X, y + TEXT_ROW_TOP + row * TEXT_ROW_STEP, color, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);
	}
}
