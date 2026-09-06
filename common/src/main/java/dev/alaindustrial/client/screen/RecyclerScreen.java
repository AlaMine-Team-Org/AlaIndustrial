package dev.alaindustrial.client.screen;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.waste.SlagGrade;
import dev.alaindustrial.core.waste.WasteFraction;
import dev.alaindustrial.block.entity.RecyclerBlockEntity;
import dev.alaindustrial.menu.RecyclerMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Recycler (MOD-145).
 *
 * <p>The three fraction rows ARE the tutorial. The machine's rule — a varied batch casts a better
 * briquette — is invisible in the item that comes out, so the screen has to show the mix while it is
 * being built, in words and in numbers.
 *
 * <p><b>Why the frame is 176x212.</b> The first draft used the standard 166 and everything collided:
 * the window title ran into the batch label, the Russian fraction names ran into their own bars and the
 * status line sat on top of the last row. Four slots, a batch gauge, three labelled rows and a status
 * line do not fit a one-machine window, and height is the cheapest thing in a GUI. Every coordinate here
 * mirrors {@code tools/gen_recycler_assets.py}, which draws the troughs these bars fill.
 */
public class RecyclerScreen extends MachineScreen<RecyclerMenu> {
	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/recycler.png");

	private static final int FRAME_W = 176;
	private static final int FRAME_H = 222;

	/**
	 * Batch gauge trough, measured off the shipped artwork rather than assumed: x 69..126, y 41..51.
	 * The owner moved this bar when he reworked the frame, and a hand-guessed coordinate would have
	 * left the fill hanging outside its groove.
	 */
	private static final int BATCH_X = 69;
	private static final int BATCH_Y = 41;
	private static final int BATCH_W = 58;
	/** Interior height of the drawn trough — 35..45 inclusive. One less left a dead pixel row. */
	private static final int BATCH_H = 11;

	/** Fraction rows: label at the left margin, bar in the trough on the right. */
	private static final int ROW_LABEL_X = 10;
	private static final int ROW_BAR_X = 77;
	private static final int ROW_BAR_W = 86;
	/** Interior height of a fraction trough — 85..90 inclusive, same fix as the batch bar. */
	private static final int ROW_BAR_H = 6;
	private static final int ROW_FIRST_Y = 85;
	private static final int ROW_STEP = 11;
	/** Text sits one pixel above the bar so a 7-pixel glyph is centred on a 5-pixel bar. */
	private static final int ROW_TEXT_OFFSET = -1;

	/**
	 * Progress groove, directly under the batch gauge: x 70..125, y 58..66 (measured off the artwork).
	 * Every other machine in the mod shows the operation running — an arrow, a hammer, a saw — and
	 * without it this one looked idle even while grinding, and the overclocker chips and the player's
	 * speed skills had nothing to visibly act on.
	 */
	private static final int PROGRESS_X = 70;
	private static final int PROGRESS_Y = 58;
	private static final int PROGRESS_W = 56;
	private static final int PROGRESS_H = 9;
	private static final int COLOUR_PROGRESS = 0xFFE89A3E;

	/** Free band under the slot rows (they end at y=69) and above the fraction panel (starts at 84). */
	private static final int OUTCOME_Y = 72;
	/** Between the last fraction row (ends at 116) and the inventory caption (128). */
	private static final int STATUS_Y = 118;

	private static final int COLOUR_BATCH = 0xFFC8B48A;
	private static final int COLOUR_LABEL = 0xFF404040;
	/** White with a shadow: the reading sits on the bar and must stay legible full or empty. */
	private static final int COLOUR_READOUT = 0xFFFFFFFF;
	private static final int COLOUR_STATUS = 0xFFB03030;
	/** Sandy mineral, cold metal, warm burnable — three hues a colour-blind player can still tell apart
	 * by their labels, which is why the labels are not optional. */
	private static final int[] FRACTION_COLOURS = {0xFFAD9A78, 0xFF8FA6C0, 0xFF9BB06A};

	public RecyclerScreen(RecyclerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, FRAME_W, FRAME_H);
		// The frame is taller than the vanilla default, so the two built-in labels have to follow the
		// artwork: without this the inventory caption floats in the middle of the fraction rows.
		this.titleLabelY = 5;
		this.inventoryLabelY = RecyclerMenu.PLAYER_INV_Y - 12;
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		blitStaticFrame(graphics);
		renderEnergyBar(graphics, EnergyBarSpec.LEFT);

		int x = this.leftPos;
		int y = this.topPos;
		int threshold = Math.max(1, Config.recyclerBatchMass);
		int mass = this.menu.batchMass();

		// Batch gauge with its own caption and a mass readout, so the player can see the target.
		int filled = Math.min(BATCH_W, mass * BATCH_W / threshold);
		if (filled > 0) {
			graphics.fill(x + BATCH_X, y + BATCH_Y, x + BATCH_X + filled, y + BATCH_Y + BATCH_H,
					COLOUR_BATCH);
		}
		// Caption ABOVE the gauge, reading INSIDE it. Both were tried beside the bar first: on the left
		// the caption ran into the energy gauge, on the right it collided with the reading at three
		// digits. Above the bar nothing else is drawn.
		Component caption = Component.translatable("gui.alaindustrial.recycler.batch");
		graphics.text(this.font, caption, x + BATCH_X, y + BATCH_Y - 11, COLOUR_LABEL, false);
		Component readout = Component.literal(mass + " / " + threshold);
		graphics.text(this.font, readout,
				x + BATCH_X + (BATCH_W - this.font.width(readout)) / 2, y + BATCH_Y + 2,
				COLOUR_READOUT, true);

		// Operation progress: how far the current item is through the grinder.
		int maxProgress = Math.max(1, this.menu.getMaxProgress());
		int done = Math.min(PROGRESS_W, this.menu.getProgress() * PROGRESS_W / maxProgress);
		if (done > 0) {
			graphics.fill(x + PROGRESS_X, y + PROGRESS_Y, x + PROGRESS_X + done,
					y + PROGRESS_Y + PROGRESS_H, COLOUR_PROGRESS);
		}

		// Fraction rows: label, bar, share of the batch.
		for (int i = 0; i < WasteFraction.GRADED_COUNT; i++) {
			WasteFraction fraction = WasteFraction.values()[i];
			int rowY = y + ROW_FIRST_Y + i * ROW_STEP;
			int value = this.menu.fractionMass(fraction);
			graphics.text(this.font, Component.translatable(labelKey(fraction)),
					x + ROW_LABEL_X, rowY + ROW_TEXT_OFFSET, COLOUR_LABEL, false);
			int width = Math.min(ROW_BAR_W, value * ROW_BAR_W / threshold);
			if (width > 0) {
				graphics.fill(x + ROW_BAR_X, rowY, x + ROW_BAR_X + width, rowY + ROW_BAR_H,
						FRACTION_COLOURS[i]);
			}
		}

		// What this batch would cast if it finished now. Without this line the mixing rule is invisible
		// until a briquette pops out, and the player has no way to connect cause to effect.
		SlagGrade grade = SlagGrade.of(this.menu.fractionMass(WasteFraction.MINERAL),
				this.menu.fractionMass(WasteFraction.METAL),
				this.menu.fractionMass(WasteFraction.COMBUSTIBLE));
		Component outcome = Component.translatable("gui.alaindustrial.recycler.outcome",
				Component.translatable(gradeKey(grade)));
		// Drawn on the free band BELOW the slot rows, centred and shrunk to fit. Under the gauge it
		// collided with the output slots: the Russian line is ~19 glyphs wide and the slots start at
		// x=134, so any left-anchored line there runs straight into them.
		drawFittedStatus(graphics, outcome, OUTCOME_Y, 8, FRAME_W - 8, COLOUR_LABEL);

		if (!this.menu.getStatus().isSilent()) {
			drawFittedStatus(graphics, Component.translatable(this.menu.getStatus().key()),
					STATUS_Y, 8, FRAME_W - 8, COLOUR_STATUS);
		}
	}

	/**
	 * The blade slot shows a ghost of what belongs in it. Without a hint the machine looks broken to a
	 * player who has never seen it: the status says "no blades", and nothing says where they go.
	 */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		ghostHint(graphics, RecyclerBlockEntity.BLADE_SLOT, cyclingHint(List.of(
				ModContent.RECYCLER_BLADES_IRON.get(),
				ModContent.RECYCLER_BLADES_TEMPERED.get(),
				ModContent.RECYCLER_BLADES_DIAMOND.get())));
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		renderEnergyTooltip(graphics, mouseX, mouseY, EnergyBarSpec.LEFT);

		int threshold = Math.max(1, Config.recyclerBatchMass);
		if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY)) {
			int maxProgress = Math.max(1, this.menu.getMaxProgress());
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.recycler.progress.tip",
							this.menu.getProgress() * 100 / maxProgress),
					mouseX, mouseY);
		}
		// The batch gauge explains the machine's whole rule, so its tooltip says the rule out loud.
		if (isHovering(BATCH_X, BATCH_Y, BATCH_W, BATCH_H, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.recycler.batch.tip",
							this.menu.batchMass(), threshold),
					mouseX, mouseY);
		}
		// Each fraction row reports its own mass and its share, because the share is what picks the grade.
		for (int i = 0; i < WasteFraction.GRADED_COUNT; i++) {
			int rowY = ROW_FIRST_Y + i * ROW_STEP;
			if (!isHovering(ROW_LABEL_X, rowY - 1, ROW_BAR_X + ROW_BAR_W - ROW_LABEL_X, ROW_BAR_H + 2,
					mouseX, mouseY)) {
				continue;
			}
			WasteFraction fraction = WasteFraction.values()[i];
			int graded = this.menu.fractionMass(WasteFraction.MINERAL)
					+ this.menu.fractionMass(WasteFraction.METAL)
					+ this.menu.fractionMass(WasteFraction.COMBUSTIBLE);
			int value = this.menu.fractionMass(fraction);
			int percent = graded > 0 ? value * 100 / graded : 0;
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.recycler.fraction.tip",
							Component.translatable(labelKey(fraction)), value, percent),
					mouseX, mouseY);
		}
	}

	private static String gradeKey(SlagGrade grade) {
		return switch (grade) {
			case POOR -> "item.alaindustrial.slag_poor";
			case COMMON -> "item.alaindustrial.slag";
			case RICH -> "item.alaindustrial.slag_rich";
		};
	}

	private static String labelKey(WasteFraction fraction) {
		return switch (fraction) {
			case MINERAL -> "gui.alaindustrial.recycler.mineral";
			case METAL -> "gui.alaindustrial.recycler.metal";
			case COMBUSTIBLE -> "gui.alaindustrial.recycler.combustible";
			case OTHER -> "gui.alaindustrial.recycler.other";
		};
	}
}
