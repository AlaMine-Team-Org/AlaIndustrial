package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.EnergyCondenserMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Energy Condenser (MOD-393).
 *
 * <p><b>The gauge measures the next tier, not the tank.</b> The first version filled the ring against
 * the 4 000 000 capacity, which is arithmetically honest and useless in play: tier I sits at 6 % of
 * the circle, so a player who had just emptied the bank watched a motionless gauge for minutes and
 * concluded the block had stopped working. It had not. The ring now runs 0→100 % between the tier
 * below and the tier above, so every tier is one full sweep, and the three pips underneath say which
 * tiers have already been passed — the one thing a resetting ring cannot show by itself.
 *
 * <p><b>And it prints the actual numbers.</b> A percentage alone still reads as frozen near the
 * bottom of a 250 000 EU climb; "12 480 / 250 000 EU" moves every second and cannot be misread.
 *
 * <p>The arc is scanline-filled and cached per permille value. Drawing it as radial spokes — the
 * obvious approach, and what v1 did — leaves gaps that widen with radius, so the fill looked torn;
 * closing them by brute force costs a spoke every ~1/32 rad, i.e. some 1600 quads a frame. Scanlines
 * give one quad per row instead, and only when the value actually changes.
 */
public class EnergyCondenserScreen extends MachineScreen<EnergyCondenserMenu> {

	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/energy_condenser.png");

	// Ring geometry — must match the atlas, and the bound is asymmetric on purpose.
	//
	// The groove runs 23..31, but its innermost and outermost rings are the dark OUTLINE; the grey body
	// the fill belongs in is everything with {@code 24 <= dist < 31}. Both obvious integer bounds are
	// wrong, and each is wrong visibly: {@code <= 31} covers the body but also paints the four outline
	// pixels that sit at exactly 31 (due north, south, east, west) — one stray pixel poking out of the
	// track on each side. {@code <= 30} avoids them and then leaves 176 body pixels grey, so the band
	// reads too thin. Excluding only the exact radius keeps every body pixel and no outline pixel:
	// verified against the atlas as 0 unpainted, 0 outline touched, 0 outside the groove.
	private static final int CX = 88, CY = 46;
	private static final int FILL_R_OUT = 31, FILL_R_IN = 24;

	// Tier pips, likewise baked at these coordinates — inside the ring, under the slot.
	private static final int PIP_Y = 60;
	private static final int[] PIP_XS = {79, 86, 93};
	private static final int PIP_W = 5, PIP_H = 5;

	/** Amber at the bottom of a tier, near-white at the top — the same heat the clots themselves use. */
	private static final int FILL_DIM = 0xFFC08820;
	private static final int FILL_HOT = 0xFFFFE24E;
	private static final int PIP_LIT = 0xFFFFE24E;

	private static final int ROW_ONE_Y = 82;
	private static final int ROW_TWO_Y = 93;

	/** Horizontal runs of the lit arc, as {y, x0, x1}; rebuilt only when the value moves. */
	private final List<int[]> arcSpans = new ArrayList<>();
	private int cachedPermille = -1;

	public EnergyCondenserScreen(EnergyCondenserMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, EnergyCondenserMenu.IMAGE_HEIGHT);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	public void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
		// The two status rows stand in for the inventory caption: they carry information, it does not.
		this.inventoryLabelY = Integer.MIN_VALUE / 2;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		blitStaticFrame(graphics);

		int tier = this.menu.currentTier();
		int progress = Mth.clamp(this.menu.nextTierPermille(), 0, 1000);
		drawArc(graphics, progress, tier);
		drawPips(graphics, tier);

		long banked = this.menu.bankedEu();
		long target = this.menu.nextThresholdEu();

		Component headline = target > 0
				? (tier > 0
						? Component.translatable("gui.alaindustrial.condenser.tier", roman(tier))
						: Component.translatable("gui.alaindustrial.condenser.tier_none"))
				: Component.translatable("gui.alaindustrial.condenser.max");
		graphics.text(this.font, headline,
				this.leftPos + (this.imageWidth - this.font.width(headline)) / 2,
				this.topPos + ROW_ONE_Y, GuiStyle.TEXT, false);

		Component amounts = target > 0
				? Component.translatable("gui.alaindustrial.condenser.amount", group(banked), group(target))
				: Component.translatable("gui.alaindustrial.condenser.banked", group(banked));
		graphics.text(this.font, amounts,
				this.leftPos + (this.imageWidth - this.font.width(amounts)) / 2,
				this.topPos + ROW_TWO_Y, GuiStyle.TEXT_DIM, false);
	}

	/** Fills the ring clockwise from twelve o'clock, one quad per scanline. */
	private void drawArc(GuiGraphicsExtractor graphics, int permille, int tier) {
		if (permille != cachedPermille) {
			rebuildArc(permille);
			cachedPermille = permille;
		}
		// Colour reads the TIER, not the sweep: the ring restarts every tier, so tying colour to the
		// sweep would make a fresh tier III look exactly like an empty bank.
		int colour = tier > 0 ? FILL_HOT : FILL_DIM;
		for (int[] span : arcSpans) {
			graphics.fill(this.leftPos + span[1], this.topPos + span[0],
					this.leftPos + span[2] + 1, this.topPos + span[0] + 1, colour);
		}
	}

	private void rebuildArc(int permille) {
		arcSpans.clear();
		if (permille <= 0) {
			return;
		}
		double sweep = 2 * Math.PI * permille / 1000.0;
		for (int y = CY - FILL_R_OUT; y <= CY + FILL_R_OUT; y++) {
			int runStart = Integer.MIN_VALUE;
			int prevX = Integer.MIN_VALUE;
			for (int x = CX - FILL_R_OUT; x <= CX + FILL_R_OUT; x++) {
				int dx = x - CX;
				int dy = y - CY;
				double dist = Math.sqrt((double) dx * dx + (double) dy * dy);
				// Sample the pixel's centre, or the innermost and outermost rows round away to nothing.
				boolean lit = dist >= FILL_R_IN && dist < FILL_R_OUT && angleFromTop(dx, dy) <= sweep;
				if (lit) {
					if (runStart == Integer.MIN_VALUE) {
						runStart = x;
					}
					prevX = x;
				} else if (runStart != Integer.MIN_VALUE) {
					arcSpans.add(new int[] {y, runStart, prevX});
					runStart = Integer.MIN_VALUE;
				}
			}
			if (runStart != Integer.MIN_VALUE) {
				arcSpans.add(new int[] {y, runStart, prevX});
			}
		}
	}

	/** Angle in radians, 0 at twelve o'clock, growing clockwise — the direction a gauge fills. */
	private static double angleFromTop(int dx, int dy) {
		double a = Math.atan2(dx, -dy);
		return a < 0 ? a + 2 * Math.PI : a;
	}

	private void drawPips(GuiGraphicsExtractor graphics, int tier) {
		for (int i = 0; i < PIP_XS.length; i++) {
			if (i >= tier) {
				continue;
			}
			int x = this.leftPos + PIP_XS[i] + 1;
			int y = this.topPos + PIP_Y + 1;
			graphics.fill(x, y, x + PIP_W - 2, y + PIP_H - 2, PIP_LIT);
		}
	}

	/** Thin-space grouping, so a seven-digit bank is readable at a glance rather than a wall of digits. */
	private static String group(long value) {
		String digits = Long.toString(value);
		StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3);
		for (int i = 0; i < digits.length(); i++) {
			if (i > 0 && (digits.length() - i) % 3 == 0) {
				out.append(' ');
			}
			out.append(digits.charAt(i));
		}
		return out.toString();
	}

	private static String roman(int tier) {
		return switch (tier) {
			case 1 -> "I";
			case 2 -> "II";
			default -> "III";
		};
	}
}
