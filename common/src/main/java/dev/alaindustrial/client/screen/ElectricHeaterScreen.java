package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ElectricHeaterStatus;
import dev.alaindustrial.menu.ElectricHeaterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Electric Heater (MOD-418) — energy on the left, temperature on the right, three rows
 * of text between them and one status line under it all. No slots.
 *
 * <p>The heater works without this window ever being opened; it exists so the ramp from x2 to x3 is
 * something the player can watch and plan around instead of infer. The two gauges are deliberately
 * mirrored: the left bar is what the block is <em>given</em>, the right one is what it has <em>become</em>.
 *
 * <p><b>Idle draws a dash, not a zero</b> — the Charging Station's rule, for the same reason: "the
 * machine above is getting x0" would read as a measurement rather than as the absence of one.
 */
public class ElectricHeaterScreen extends MachineScreen<ElectricHeaterMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/electric_heater.png");

	/**
	 * The thermometer's fillable well, measured off the shipped atlas rather than chosen here — the art
	 * is hand-authored, so these constants follow it and not the other way round. A fill that does not
	 * match the drawn well to the pixel bleeds over its own frame.
	 *
	 * <p>Deliberately NOT derived from {@link EnergyBarSpec}: the energy bar's 10x44 is a mod-wide
	 * constant shared with one atlas sprite, while this well is the same width but two pixels taller.
	 * Reusing those constants here would look tidy and quietly misalign the moment either changed.
	 *
	 * <p>Public for the same reason the Charging Station's text metrics are: symmetry with the rest of
	 * the screen family. No text-row stand measures this strip today; should one be added, it must read
	 * the coordinates from here rather than from a copy that would drift the first time the layout moves.
	 */
	public static final int THERMO_X = 146;
	public static final int THERMO_BOTTOM = 65;
	public static final int THERMO_W = 10;
	public static final int THERMO_H = 46;

	// The energy bar is the mod-wide EnergyBarSpec.LEFT and nothing else. Worth stating, because it was
	// briefly replaced by a hand-rolled 8x42 renderer here: the well's SLOT-grey interior really is 8
	// wide, and reading that as "this panel is non-standard" was a misreading of the convention. Every
	// shipped atlas is drawn that way — macerator, mob_repeller and this one are pixel-identical across
	// x=14..30 — because the 10-wide sprite is laid OVER the frame columns, its own dark edge and
	// highlight becoming the frame once the bar is full.

	/** Text column and rows, between the two gauges. */
	public static final int TEXT_X = 34;
	public static final int TEXT_ROW_TOP = 22;
	public static final int TEXT_ROW_STEP = 11;
	/** Status line, in the band below the rows and above the vanilla inventory label (166 - 94 = 72). */
	public static final int STATUS_Y = 58;

	/** Stands in for any value the heater cannot report right now. */
	private static final String DASH = "—";
	/** The multiplication sign the heat rows use mod-wide; escaped so this file stays pure ASCII. */
	private static final String TIMES = "×";

	/**
	 * Thermometer colours: a barely-lit coil, a working one, and the unmistakable "fully hot" cap.
	 *
	 * <p>An incandescence ramp — dark ember to red-orange to bright gold — and deliberately NOT the
	 * cool-to-warm ramp it started as. Blue-to-orange is the obvious choice for a temperature gauge and
	 * the wrong one in RGB: the straight line between them runs through desaturated grey, so a heater at
	 * half warm-up rendered a muddy tan and read as dirty rather than as warming. Every stop here keeps
	 * rising in both red and brightness, so no midpoint can go grey.
	 *
	 * <p>The stops also stay clear of the energy bar's flat {@link GuiStyle#ENERGY} orange beside them:
	 * the left gauge is one colour, this one is always a gradient ending in gold, so the pair never reads
	 * as two of the same meter.
	 */
	private static final int THERMO_COLD = 0xFF6E1F0C;
	private static final int THERMO_WARM = 0xFFE8551A;
	private static final int THERMO_READY = 0xFFFFD24D;

	public ElectricHeaterScreen(ElectricHeaterMenu menu, Inventory inventory, Component title) {
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
		renderThermometer(graphics, x, y);

		ElectricHeaterStatus status = this.menu.getStatus();
		int multiplier = this.menu.getHeatMultiplier();

		// Row 0 — the heater's own temperature, the one value that is always real: it is a property of
		// this block, not a claim about anything it is connected to.
		drawRow(graphics, x, y, 0, Component.translatable("gui.alaindustrial.electric_heater.temperature",
				this.menu.getWarmupPercent()), GuiStyle.TEXT);
		drawRow(graphics, x, y, 1, Component.translatable("gui.alaindustrial.electric_heater.multiplier",
				multiplier > 0 ? TIMES + multiplier : DASH), GuiStyle.TEXT_DIM);
		// The rate is a tariff, not a measurement, so unlike the Charging Station's live rows it stays
		// printed whenever there is a machine above to charge it to — that is exactly when the player is
		// deciding whether they can afford the chips in it. Only "nothing up there to bill" dashes.
		drawRow(graphics, x, y, 2, Component.translatable("gui.alaindustrial.electric_heater.rate",
				status == ElectricHeaterStatus.NO_CONSUMER ? DASH : String.valueOf(this.menu.getRateEuPerTick())),
				GuiStyle.TEXT_DIM);

		Component line = Component.translatable(status.translationKey())
				.withStyle(status.needsAttention() ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GRAY);
		graphics.text(this.font, line, x + TEXT_X, y + STATUS_Y,
				status.needsAttention() ? 0xFF7A2020 : GuiStyle.TEXT_DIM, false);
	}

	/** Fills the thermometer groove from the bottom up, colouring it by how hot the heater is. */
	private void renderThermometer(GuiGraphicsExtractor graphics, int x, int y) {
		int permille = this.menu.getWarmupPermille();
		if (permille <= 0) {
			return;
		}
		int fill = Math.max(1, permille * THERMO_H / 1000);
		graphics.fill(x + THERMO_X, y + THERMO_BOTTOM - fill, x + THERMO_X + THERMO_W, y + THERMO_BOTTOM,
				thermometerColour(permille));
	}

	/**
	 * Cold blue climbing to working orange, then a distinct bright cap at full temperature.
	 *
	 * <p>The cap is a step and not the end of the gradient on purpose: 100 % is the only value that
	 * changes what the machine above receives, so it should be readable at a glance rather than as
	 * "slightly more orange than a moment ago".
	 */
	private static int thermometerColour(int permille) {
		if (permille >= 1000) {
			return THERMO_READY;
		}
		return lerpColour(THERMO_COLD, THERMO_WARM, permille / 1000.0f);
	}

	private static int lerpColour(int from, int to, float t) {
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/** Draw one readout row, left-aligned in the text column. */
	private void drawRow(GuiGraphicsExtractor graphics, int x, int y, int row, Component text, int color) {
		graphics.text(this.font, text, x + TEXT_X, y + TEXT_ROW_TOP + row * TEXT_ROW_STEP, color, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);
		if (isHovering(THERMO_X, THERMO_BOTTOM - THERMO_H, THERMO_W, THERMO_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.electric_heater.tooltip",
							this.menu.getWarmupPercent()),
					mouseX, mouseY);
		}
	}
}
