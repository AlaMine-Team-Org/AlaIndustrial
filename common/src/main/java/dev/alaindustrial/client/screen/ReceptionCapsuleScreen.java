package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ReceptionCapsuleBlockEntity;
import dev.alaindustrial.client.render.entity.ShiftWorkerModel;
import dev.alaindustrial.client.render.entity.ShiftWorkerRenderer;
import dev.alaindustrial.menu.ReceptionCapsuleMenu;
import dev.alaindustrial.settlement.ReceptionCapsuleRules;
import dev.alaindustrial.settlement.ReceptionCapsuleRules.Material;
import dev.alaindustrial.settlement.ReceptionCapsuleRules.Verdict;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Screen of the reception capsule (MOD-833, re-laid out in MOD-844): a header with the worker's head beside the
 * town's workers against its limit and the EU still missing, the choice button, a progress bar across the window,
 * then one row per material of the price (its slot, its name and "in the slot / needed") with the energy bar
 * beside the rows. The status row says why the capsule stands still (no core, the core dark, outside the
 * territory, the town full or unclaimed, a material or EU short, the archive silent) or how far the restoration
 * has got (the capsule spec: a refusal is said, never silent).
 *
 * <p>Every number comes from the server's channels ({@link ReceptionCapsuleMenu}); the counts are the synced slot
 * contents. A material's name and the status wrap onto a second line rather than shrink; a row that still does not
 * fit is scaled down, never cut. The widths of every shipped translation against these bands were measured with
 * the game's font metrics (MOD-844 research.md). Not a {@code LayoutMachineScreen}: the head, the rows and the
 * button are logic of its own.
 */
public class ReceptionCapsuleScreen extends MachineScreen<ReceptionCapsuleMenu> {

	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/reception_capsule.png");

	/** The left and right inner edges every full-width row keeps to. */
	public static final int INNER_LEFT = 8;
	public static final int INNER_RIGHT = 168;
	/** The worker's head: an 18-pixel picture at the header's left, the two header rows right of it. */
	public static final int HEAD_X = 8;
	public static final int HEAD_Y = 17;
	public static final int HEAD_SIZE = 18;
	/** GUI pixels per model unit of the head: the helmet (0.56 units across) turned 30 degrees fills ~15 pixels. */
	private static final float HEAD_SCALE = 19.0F;
	private static final float HEAD_TILT = -8.0F;
	private static final float HEAD_TURN = 30.0F;
	/** How far the skin renderer lifts a model above its picture's bottom edge, in model units (javap 26.3). */
	private static final float SKIN_LIFT = 1.601F;
	/** The head's centre below its pivot, in model units: the 8-pixel head hangs above the neck. */
	private static final float HEAD_HALF = 0.25F;
	/** The header rows, right of the head. */
	public static final int READOUT_LEFT = 30;
	public static final int WORKERS_Y = 18;
	public static final int EU_Y = 28;
	/** The choice button (R39): new worker or one of the dead who wait; drawn only while somebody waits. */
	public static final int CHOICE_Y = 37;
	public static final int CHOICE_H = 12;
	/** The restoration's progress, across the window. */
	public static final int PROGRESS_Y = 51;
	public static final int PROGRESS_H = 8;
	/** A material row: the name right of the slot, the count right-aligned to the inner edge. */
	public static final int NAME_LEFT = ReceptionCapsuleMenu.SLOTS_X + 22;
	private static final int COUNT_GAP = 4;
	/** The energy bar beside the material rows; the atlas generator draws its well to match. */
	public static final EnergyBarSpec ENERGY_BAR = new EnergyBarSpec(17, 112, 176, 0);
	/** The status: one line at {@link #STATUS_Y}, or two from {@link #STATUS_Y2} when it does not fit one. */
	public static final int STATUS_Y = 125;
	public static final int STATUS_Y2 = 121;
	private static final int LINE_H = 9;

	/** The head model, baked when the screen opens; null only before {@link #init}. */
	private Model.@Nullable Simple workerHead;

	public ReceptionCapsuleScreen(ReceptionCapsuleMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, ReceptionCapsuleMenu.IMAGE_HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		if (this.minecraft != null) {
			this.workerHead = ShiftWorkerModel.guiHead(this.minecraft.getEntityModels());
		}
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	/** The energy bar and its tooltip, drawn by the base (MOD-716), beside the material rows. */
	@Override
	protected EnergyBarSpec energyBar() {
		return ENERGY_BAR;
	}

	/** No statistics tab: the capsule has no upgrade panel, so the chip that feeds it can never be fitted. */
	@Override
	protected boolean hasStatsTab() {
		return false;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		drawWorkerHead(graphics);
		row(graphics, WORKERS_Y, Component.translatable("settlement.alaindustrial.capsule.workers",
				this.menu.getLiving(), this.menu.getLimit()), GuiStyle.TEXT);
		long missing = this.menu.getEuMissing();
		row(graphics, EU_Y, missing > 0
						? Component.translatable("settlement.alaindustrial.capsule.eu_missing", missing)
						: Component.translatable("settlement.alaindustrial.capsule.eu_ready"),
				missing > 0 ? GuiStyle.TEXT_DIM : GuiStyle.TEXT);
		if (choiceShown()) {
			int x0 = this.leftPos + INNER_LEFT;
			int y0 = this.topPos + CHOICE_Y;
			graphics.fill(x0, y0, x0 + INNER_RIGHT - INNER_LEFT, y0 + CHOICE_H,
					this.menu.getChoice().isPresent() ? GuiStyle.BUTTON_ACTIVE : GuiStyle.BUTTON);
			if (overChoice(mouseX, mouseY)) {
				graphics.fill(x0, y0, x0 + INNER_RIGHT - INNER_LEFT, y0 + CHOICE_H, GuiStyle.HOVER_WASH);
			}
			fittedCentred(graphics, choiceLabel(), INNER_LEFT + 2, INNER_RIGHT - 2, CHOICE_Y + 2, GuiStyle.PANEL_HI);
		}
		drawProgress(graphics);
		for (Material material : Material.values()) {
			drawMaterialRow(graphics, material);
		}
	}

	/**
	 * The worker's head, turned like a head in a slot: the base look (an open helmet, the first classic face). The
	 * next worker's own look is chosen by the server when it steps out, from its number and the biome, so the
	 * screen cannot know it in advance. The skin renderer puts the model's origin at the picture's bottom centre and
	 * lifts it {@link #SKIN_LIFT} units; the holder part moves the head's centre to the middle of the picture, and the
	 * tilt pivots on that middle too.
	 */
	private void drawWorkerHead(GuiGraphicsExtractor graphics) {
		if (this.workerHead == null) {
			return;
		}
		float half = HEAD_SIZE / (2.0F * HEAD_SCALE);
		this.workerHead.root().y = 16.0F * (SKIN_LIFT + HEAD_HALF - half);
		int x0 = this.leftPos + HEAD_X;
		int y0 = this.topPos + HEAD_Y;
		graphics.skin(this.workerHead, ShiftWorkerRenderer.FALLBACK_CLASSIC, HEAD_SCALE, HEAD_TILT, HEAD_TURN, -half,
				x0, y0, x0 + HEAD_SIZE, y0 + HEAD_SIZE);
	}

	/** A recessed bar across the window, filled in the energy amber as the restoration runs. */
	private void drawProgress(GuiGraphicsExtractor graphics) {
		int x0 = this.leftPos + INNER_LEFT;
		int y0 = this.topPos + PROGRESS_Y;
		int x1 = this.leftPos + INNER_RIGHT;
		graphics.fill(x0, y0, x1, y0 + PROGRESS_H, GuiStyle.SLOT_EDGE);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + PROGRESS_H - 1, GuiStyle.SLOT);
		int max = this.menu.getMaxProgress();
		int inner = x1 - x0 - 2;
		int filled = max <= 0 ? 0 : (int) Math.min(inner, (long) inner * this.menu.getProgress() / max);
		if (filled > 0) {
			graphics.fill(x0 + 1, y0 + 1, x0 + 1 + filled, y0 + PROGRESS_H - 1, GuiStyle.ENERGY);
			graphics.fill(x0 + 1, y0 + 1, x0 + 1 + filled, y0 + 2, GuiStyle.ENERGY_HI);
		}
	}

	/** One material row: the name (on two lines when it needs them) and "in slot / needed", red while short. */
	private void drawMaterialRow(GuiGraphicsExtractor graphics, Material material) {
		int need = this.menu.getNeed(material);
		int have = this.menu.getHave(material);
		int rowY = ReceptionCapsuleMenu.SLOTS_Y + ReceptionCapsuleBlockEntity.slotOf(material)
				* ReceptionCapsuleMenu.SLOT_STEP;
		Component count = Component.literal(have + "/" + need);
		int countWidth = this.font.width(count);
		graphics.text(this.font, count, this.leftPos + INNER_RIGHT - 1 - countWidth, this.topPos + rowY + 4,
				ReceptionCapsuleRules.missing(have, need) > 0 ? GuiStyle.STATUS_BLOCKING : GuiStyle.TEXT, false);
		Component name = new ItemStack(ReceptionCapsuleBlockEntity.itemOf(material)).getHoverName();
		int band = INNER_RIGHT - 1 - countWidth - COUNT_GAP - NAME_LEFT;
		List<FormattedCharSequence> lines = this.font.split(name, band);
		if (lines.size() == 2) {
			graphics.text(this.font, lines.get(0), this.leftPos + NAME_LEFT, this.topPos + rowY, GuiStyle.LABEL, false);
			graphics.text(this.font, lines.get(1), this.leftPos + NAME_LEFT, this.topPos + rowY + LINE_H,
					GuiStyle.LABEL, false);
		} else {
			fitted(graphics, name, this.leftPos + NAME_LEFT, this.topPos + rowY + 4, band, GuiStyle.LABEL);
		}
	}

	/** Whether the choice button is there: some of the town's dead wait, or one is chosen (R39). */
	private boolean choiceShown() {
		return this.menu.getAwaiting() > 0 || this.menu.getChoice().isPresent();
	}

	/** "New worker", or "Bring back W-117 (×2)" for the chosen dead one. */
	private Component choiceLabel() {
		return this.menu.getChoice()
				.map(number -> Component.translatable("settlement.alaindustrial.capsule.choice.return", number))
				.orElseGet(() -> Component.translatable("settlement.alaindustrial.capsule.choice.new"));
	}

	private boolean overChoice(double mouseX, double mouseY) {
		return choiceShown() && isHovering(INNER_LEFT, CHOICE_Y, INNER_RIGHT - INNER_LEFT, CHOICE_H, mouseX,
				mouseY);
	}

	/** A click on the choice button asks the server for the next choice (the vanilla menu-button channel). */
	@Override
	protected boolean controlClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !overChoice(event.x(), event.y())) {
			return false;
		}
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
					ReceptionCapsuleMenu.BUTTON_NEXT_CHOICE);
			AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
		}
		return true;
	}

	/** Over the choice button: what it does and what bringing a worker back costs. */
	@Override
	protected void controlTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (overChoice(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("settlement.alaindustrial.capsule.choice.hint"), mouseX, mouseY);
		}
	}

	/** The status row, drawn after the energy bar so a long translation is never covered (MachineScreen). */
	@Override
	protected void drawFrameText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Verdict verdict = this.menu.getVerdict();
		Component status = verdict.blocking()
				? Component.translatable(statusKey(verdict))
				: Component.translatable("settlement.alaindustrial.capsule.restoring", percent());
		int colour = verdict.blocking() ? GuiStyle.STATUS_BLOCKING : GuiStyle.TEXT;
		int band = INNER_RIGHT - INNER_LEFT;
		List<FormattedCharSequence> lines = this.font.split(status, band);
		if (lines.size() == 2) {
			for (int i = 0; i < 2; i++) {
				int width = this.font.width(lines.get(i));
				graphics.text(this.font, lines.get(i), this.leftPos + INNER_LEFT + (band - width) / 2,
						this.topPos + STATUS_Y2 + i * LINE_H, colour, false);
			}
			return;
		}
		drawFittedStatus(graphics, status, STATUS_Y, INNER_LEFT, INNER_RIGHT, colour);
	}

	/** The restoration's progress in whole percent. */
	private String percent() {
		int max = this.menu.getMaxProgress();
		int value = max <= 0 ? 0 : (int) (100L * this.menu.getProgress() / max);
		return Integer.toString(value);
	}

	/** Each empty material slot shows, faded, what goes there. */
	@Override
	protected void drawGhostHints(GuiGraphicsExtractor graphics) {
		for (Material material : Material.values()) {
			ghostHint(graphics, ReceptionCapsuleBlockEntity.slotOf(material),
					new ItemStack(ReceptionCapsuleBlockEntity.itemOf(material)));
		}
	}

	/** The translation key of a refusal. */
	static String statusKey(Verdict verdict) {
		return "settlement.alaindustrial.capsule.status." + verdict.name().toLowerCase(Locale.ROOT);
	}

	/** One header row, right of the head to the inner edge, scaled down when a translation runs longer. */
	private void row(GuiGraphicsExtractor graphics, int y, Component text, int colour) {
		fitted(graphics, text, this.leftPos + READOUT_LEFT, this.topPos + y, INNER_RIGHT - READOUT_LEFT, colour);
	}

	/** {@code text} centred between {@code left} and {@code right}, scaled down when it does not fit. */
	private void fittedCentred(GuiGraphicsExtractor graphics, Component text, int left, int right, int y,
			int colour) {
		int band = right - left;
		int width = this.font.width(text);
		int x = width <= band ? left + (band - width) / 2 : left;
		fitted(graphics, text, this.leftPos + x, this.topPos + y, band, colour);
	}

	/** {@code text} at ({@code x}, {@code y}), scaled down to {@code maxWidth} when a translation runs longer. */
	private void fitted(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int colour) {
		int width = this.font.width(text);
		if (width <= maxWidth) {
			graphics.text(this.font, text, x, y, colour, false);
			return;
		}
		float scale = Math.max(MIN_STATUS_SCALE, (float) maxWidth / width);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, text, 0, 0, colour, false);
		graphics.pose().popMatrix();
	}
}
