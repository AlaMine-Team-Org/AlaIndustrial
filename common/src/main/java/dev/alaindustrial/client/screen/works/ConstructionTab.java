package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.client.render.HallSchematicOverlay;
import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.SettlementCoreLayout;
import dev.alaindustrial.client.screen.TextFit;
import dev.alaindustrial.client.screen.works.WorksModel.Card;
import dev.alaindustrial.client.screen.works.WorksModel.Control;
import dev.alaindustrial.client.screen.works.WorksModel.Line;
import dev.alaindustrial.client.screen.works.WorksModel.Model;
import dev.alaindustrial.client.screen.works.WorksModel.Section;
import dev.alaindustrial.network.TownWorksPayload;
import dev.alaindustrial.settlement.works.BuildingTile;
import dev.alaindustrial.settlement.works.ConstructionRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The «Construction» tab of the settlement core's screen (MOD-857; owner's round 3 of 0.1.219, R3-3 and R3-6): at the
 * top a dark bar that says what to do next, under it a pane that scrolls — the town hall, the houses, the other
 * buildings, each a card with its picture, name, status chip, share restored and the one button of its next step; the
 * road network; the plans the town has found. A click on a card opens it: the stage the building is on, what the stage
 * still lacks, why the building waits, who lives in it, the open jobs of its order and the schematic switch.
 *
 * <p>The server words every line ({@link TownWorksPayload}): the rows of {@code ConstructionRows}, the card data of
 * {@link BuildingTile} and the hint. This tab only lays them out: a stage row and the job rows find their card by
 * their target. The town's owner gets the buttons (R95); anybody gets the schematic switch, which is visual and
 * personal ({@link HallSchematicOverlay}).
 *
 * <p>Asked for when the tab opens and every two seconds while it stays open, like the shift workers' roster.
 */
public final class ConstructionTab {

	private static final int REFRESH_TICKS = 40;
	private static final int SCROLL_STEP = 15;
	private static final int LINE = 10;

	/** One laid-out block of the pane, at {@code y} from the pane's content top. */
	private record Block(Kind kind, int y, int height, @Nullable Card card, @Nullable Component text,
			List<Line> lines) {
		enum Kind { HEADING, CARD, NETWORK, SHEETS, HOUSE_PLANS, NOTE }
	}

	/** A control on screen and what it does. */
	private record Hit(ConstructionRow.Action action, int target) {
	}

	private int scroll;
	private int ticks;
	/** The building whose card is open, or -1; {@link #chosen} once the player clicked one. */
	private int open = -1;
	private boolean chosen;

	/** The tab opened: asks for its rows and starts at the top. */
	public void opened() {
		scroll = 0;
		ticks = 0;
		TownWorksClient.request();
	}

	/** One client tick while the tab is open. */
	public void tick() {
		if (++ticks % REFRESH_TICKS == 0) {
			TownWorksClient.request();
		}
	}

	// --- layout ----------------------------------------------------------------------------------------------------

	private List<Block> layout(Model model, boolean manage) {
		if (!chosen) {
			open = WorksModel.autoOpen(model);
		}
		List<Block> blocks = new ArrayList<>();
		int y = 0;
		if (model.empty() != null) {
			blocks.add(new Block(Block.Kind.NOTE, y, LINE + 2, null, model.empty(), List.of()));
			y += LINE + 2;
		}
		for (Section section : model.sections()) {
			blocks.add(new Block(Block.Kind.HEADING, y, SettlementCoreLayout.WORKS_HEADING, null,
					Component.translatable(section.key()), List.of()));
			y += SettlementCoreLayout.WORKS_HEADING;
			for (Card card : section.cards()) {
				List<Line> lines = card.row().target() == open ? WorksModel.openLines(card) : List.of();
				int height = SettlementCoreLayout.WORKS_CARD + (lines.isEmpty() ? 0 : lines.size() * LINE + 6)
						+ (lines.stream().anyMatch(line -> line.barPercent() >= 0) ? 3 : 0);
				blocks.add(new Block(Block.Kind.CARD, y, height, card, null, lines));
				y += height + SettlementCoreLayout.WORKS_CARD_GAP;
			}
		}
		if (model.network() != null) {
			blocks.add(new Block(Block.Kind.HEADING, y, SettlementCoreLayout.WORKS_HEADING, null,
					Component.translatable(BuildingTile.SECTION_ROADS), List.of()));
			y += SettlementCoreLayout.WORKS_HEADING;
			blocks.add(new Block(Block.Kind.NETWORK, y, 24, null, component(model.network().text()), List.of()));
			y += 24 + SettlementCoreLayout.WORKS_CARD_GAP;
		}
		if (!model.sheets().isEmpty() || !model.housePlans().isEmpty()) {
			blocks.add(new Block(Block.Kind.HEADING, y, SettlementCoreLayout.WORKS_HEADING, null,
					Component.translatable(BuildingTile.SECTION_PLANS), List.of()));
			y += SettlementCoreLayout.WORKS_HEADING;
			if (!model.sheets().isEmpty()) {
				blocks.add(new Block(Block.Kind.SHEETS, y, SettlementCoreLayout.WORKS_PLAN_ROW, null, null,
						List.of()));
				y += SettlementCoreLayout.WORKS_PLAN_ROW;
			}
			if (!model.housePlans().isEmpty()) {
				blocks.add(new Block(Block.Kind.HOUSE_PLANS, y, SettlementCoreLayout.WORKS_PLAN_ROW, null, null,
						List.of()));
				y += SettlementCoreLayout.WORKS_PLAN_ROW;
			}
		}
		if (!manage && !model.sections().isEmpty()) {
			blocks.add(new Block(Block.Kind.NOTE, y + 2, LINE * 2 + 2, null,
					Component.translatable(SettlementCoreLayout.WORKS_READ_ONLY), List.of()));
		}
		return blocks;
	}

	// --- drawing ---------------------------------------------------------------------------------------------------

	/**
	 * Draws the tab into the image whose top-left is ({@code x0}, {@code y0}); {@code unbound} — a core of no town.
	 */
	public void draw(GuiGraphicsExtractor graphics, Font font, int x0, int y0, boolean unbound, int mouseX,
			int mouseY) {
		TownWorksPayload payload = TownWorksClient.latest();
		Model model = WorksModel.model();
		if (payload == null || model == null) {
			Component waiting = Component.translatable(unbound ? SettlementCoreLayout.STATUS_UNBOUND
					: SettlementCoreLayout.ROSTER_LOADING);
			WorksPaint.cell(graphics, font, waiting, x0 + SettlementCoreLayout.ROSTER_LEFT + 2,
					y0 + SettlementCoreLayout.ROSTER_TOP + 2, 200, GuiStyle.TEXT_DIM);
			return;
		}
		WorksPaint.drawHint(graphics, font, payload.hint(), x0, y0);
		List<Block> blocks = layout(model, payload.manage());
		int viewTop = y0 + SettlementCoreLayout.WORKS_VIEW_TOP;
		int viewBottom = y0 + SettlementCoreLayout.WORKS_VIEW_BOTTOM;
		int total = contentHeight(blocks);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - (viewBottom - viewTop))));
		graphics.enableScissor(x0 + SettlementCoreLayout.ROSTER_LEFT, viewTop, x0 + SettlementCoreLayout.ROSTER_RIGHT,
				viewBottom);
		int left = x0 + SettlementCoreLayout.WORKS_CONTENT_LEFT;
		int right = x0 + SettlementCoreLayout.WORKS_CONTENT_RIGHT;
		for (Block block : blocks) {
			int y = viewTop + block.y() - scroll;
			if (y > viewBottom || y + block.height() < viewTop) {
				continue;
			}
			boolean inside = mouseY >= viewTop && mouseY < viewBottom;
			drawBlock(graphics, font, model, block, left, right, y, inside ? mouseX : -1, inside ? mouseY : -1);
		}
		graphics.disableScissor();
		drawScrollBar(graphics, x0, viewTop, viewBottom, total);
	}

	private static int contentHeight(List<Block> blocks) {
		int bottom = 0;
		for (Block block : blocks) {
			bottom = Math.max(bottom, block.y() + block.height());
		}
		return bottom + 2;
	}

	private void drawScrollBar(GuiGraphicsExtractor graphics, int x0, int top, int bottom, int total) {
		int view = bottom - top;
		if (total <= view) {
			return;
		}
		int x = x0 + SettlementCoreLayout.ROSTER_RIGHT - SettlementCoreLayout.WORKS_SCROLLBAR - 1;
		graphics.fill(x, top, x + SettlementCoreLayout.WORKS_SCROLLBAR, bottom, GuiStyle.LEVEL_TRACK);
		int thumb = Math.max(12, view * view / total);
		int at = top + (view - thumb) * scroll / Math.max(1, total - view);
		graphics.fill(x, at, x + SettlementCoreLayout.WORKS_SCROLLBAR, at + thumb, GuiStyle.COPPER);
	}

	private void drawBlock(GuiGraphicsExtractor graphics, Font font, Model model, Block block, int left, int right,
			int y, int mouseX, int mouseY) {
		switch (block.kind()) {
			case HEADING -> {
				WorksPaint.cell(graphics, font, block.text(), left + 1, y + 3, right - left - 2, GuiStyle.LABEL);
				graphics.fill(left, y + 12, right, y + 13, GuiStyle.COPPER);
			}
			case NOTE -> {
				TextFit.Fit fit = TextFit.fit(block.text().getString(), right - left - 4, 2, font::width);
				for (int i = 0; i < fit.lines().size(); i++) {
					WorksPaint.scaled(graphics, font, fit.lines().get(i), left + 2, y + i * LINE, fit.scale(),
							GuiStyle.TEXT_DIM);
				}
			}
			case CARD -> drawCard(graphics, font, block, left, right, y, mouseX, mouseY);
			case NETWORK -> WorksPaint.drawNetwork(graphics, font, model.network(), block.text(), left, right, y,
					mouseX, mouseY);
			case SHEETS -> WorksPaint.drawSheets(graphics, font, model.sheets(), left, y);
			case HOUSE_PLANS -> WorksPaint.drawHousePlans(graphics, font, model.housePlans(), left, y);
		}
	}

	private void drawCard(GuiGraphicsExtractor graphics, Font font, Block block, int left, int right, int y,
			int mouseX, int mouseY) {
		Card card = block.card();
		ConstructionRow row = card.row();
		boolean isOpen = row.target() == open;
		boolean top = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + SettlementCoreLayout.WORKS_CARD;
		Optional<ConstructionRow.Action> main = WorksModel.mainAction(row);
		int buttonX = right - SettlementCoreLayout.WORKS_BUTTON - 3;
		int buttonY = y + (SettlementCoreLayout.WORKS_CARD - SettlementCoreLayout.WORKS_BUTTON) / 2;
		int size = SettlementCoreLayout.WORKS_BUTTON;
		boolean onButton = main.isPresent() && WorksPaint.inside(mouseX, mouseY, buttonX, buttonY, size, size);
		WorksPaint.cardFace(graphics, left, right, y, block.height(), isOpen, top && !onButton);
		// The picture on a dark backdrop, as the shift worker's card shows its model.
		int box = SettlementCoreLayout.WORKS_ICON_BOX;
		graphics.fill(left + 2, y + 2, left + 2 + box, y + 2 + box, GuiStyle.MODEL_BACK_TOP);
		graphics.fill(left + 2, y + 2 + box / 2, left + 2 + box, y + 2 + box, GuiStyle.MODEL_BACK_BOTTOM);
		BuildingTile tile = card.tile();
		if (tile != null && TownBuildingIcons.has(tile.icon())) {
			TownBuildingIcons.draw(graphics, tile.icon(), left + 3, y + 3, SettlementCoreLayout.WORKS_ICON);
		} else {
			graphics.item(new ItemStack(Items.BRICKS), left + 7, y + 7);
		}
		int text = left + SettlementCoreLayout.WORKS_CARD_TEXT;
		int textRight = main.isPresent() ? buttonX - 3 : right - 4;
		WorksPaint.cell(graphics, font, component(row.text()), text, y + 3, textRight - text, GuiStyle.TEXT);
		BuildingTile.Chip chip = tile == null ? BuildingTile.Chip.RUIN : tile.chip();
		int chipRight = WorksPaint.drawChip(graphics, font, chip, text, y + 13);
		String percent = row.percent() >= 0 ? row.percent() + "%" : "";
		int percentX = textRight - font.width(percent);
		if (!percent.isEmpty()) {
			graphics.text(font, percent, percentX, y + 14, GuiStyle.TEXT, false);
		}
		if (tile != null && tile.resident().isPresent() && percentX - chipRight > 20) {
			WorksPaint.cell(graphics, font, component(tile.resident().get()), chipRight + 4, y + 14,
					percentX - chipRight - 8, GuiStyle.TEXT_DIM);
		}
		WorksPaint.bar(graphics, text, y + 25, textRight, Math.max(0, row.percent()), WorksPaint.barColour(chip));
		if (main.isPresent()) {
			WorksPaint.button(graphics, WorksPaint.icon(main.get()), buttonX, buttonY, size, onButton, false);
		}
		WorksPaint.drawOpenLines(graphics, font, block.lines(), left, right, y + SettlementCoreLayout.WORKS_CARD + 1,
				mouseX, mouseY);
	}

	// --- input -----------------------------------------------------------------------------------------------------

	/** What is under the mouse: a control, a card's top, a plan slot — with the words its tooltip shows. */
	private record Target(@Nullable Hit hit, int toggle, @Nullable Component tooltip) {
	}

	private Optional<Target> target(int x0, int y0, double mouseX, double mouseY) {
		TownWorksPayload payload = TownWorksClient.latest();
		Model model = WorksModel.model();
		int viewTop = y0 + SettlementCoreLayout.WORKS_VIEW_TOP;
		int viewBottom = y0 + SettlementCoreLayout.WORKS_VIEW_BOTTOM;
		if (payload == null || model == null) {
			return Optional.empty();
		}
		int hintTop = y0 + SettlementCoreLayout.WORKS_HINT_TOP;
		if (mouseY >= hintTop && mouseY < hintTop + SettlementCoreLayout.WORKS_HINT_HEIGHT
				&& payload.hint().isPresent() && hintCut(payload.hint().get())) {
			return Optional.of(new Target(null, -1, component(payload.hint().get())));
		}
		if (mouseY < viewTop || mouseY >= viewBottom) {
			return Optional.empty();
		}
		int left = x0 + SettlementCoreLayout.WORKS_CONTENT_LEFT;
		int right = x0 + SettlementCoreLayout.WORKS_CONTENT_RIGHT;
		Font font = Minecraft.getInstance().font;
		for (Block block : layout(model, payload.manage())) {
			int y = viewTop + block.y() - scroll;
			if (mouseY < y || mouseY >= y + block.height() || mouseX < left || mouseX >= right) {
				continue;
			}
			switch (block.kind()) {
				case CARD -> {
					return cardTarget(font, block, left, right, y, mouseX, mouseY);
				}
				case NETWORK -> {
					int bx = right - 18 - 3;
					if (model.network().allows(ConstructionRow.Action.SCHEMATIC)
							&& WorksPaint.inside(mouseX, mouseY, bx, y + 3, 18, 18)) {
						return Optional.of(control(new Hit(ConstructionRow.Action.SCHEMATIC,
								model.network().target())));
					}
				}
				case SHEETS -> {
					int index = slotAt(left, mouseX);
					if (index >= 0 && index < model.sheets().size()) {
						ConstructionRow sheet = model.sheets().get(index);
						return Optional.of(new Target(null, -1, joined(component(sheet.text()),
								sheet.status().map(ConstructionTab::component).orElse(Component.empty()))));
					}
				}
				case HOUSE_PLANS -> {
					int index = slotAt(left, mouseX);
					if (index >= 0 && index < model.housePlans().size()) {
						ConstructionRow plan = model.housePlans().get(index);
						return Optional.of(new Target(null, -1, joined(component(plan.text()),
								plan.status().map(ConstructionTab::component).orElse(Component.empty()))));
					}
				}
				default -> {
				}
			}
			return Optional.empty();
		}
		return Optional.empty();
	}

	private static int slotAt(int left, double mouseX) {
		double at = mouseX - (left + SettlementCoreLayout.WORKS_PLAN_LABEL);
		if (at < 0) {
			return -1;
		}
		int step = SettlementCoreLayout.WORKS_SLOT + 2;
		return at % step < SettlementCoreLayout.WORKS_SLOT ? (int) (at / step) : -1;
	}

	private Optional<Target> cardTarget(Font font, Block block, int left, int right, int y, double mouseX,
			double mouseY) {
		Card card = block.card();
		ConstructionRow row = card.row();
		if (mouseY < y + SettlementCoreLayout.WORKS_CARD) {
			Optional<ConstructionRow.Action> main = WorksModel.mainAction(row);
			int buttonX = right - SettlementCoreLayout.WORKS_BUTTON - 3;
			int buttonY = y + (SettlementCoreLayout.WORKS_CARD - SettlementCoreLayout.WORKS_BUTTON) / 2;
			int size = SettlementCoreLayout.WORKS_BUTTON;
			if (main.isPresent() && WorksPaint.inside(mouseX, mouseY, buttonX, buttonY, size, size)) {
				return Optional.of(control(new Hit(main.get(), row.target())));
			}
			Component name = component(row.text());
			Component tip = Component.translatable(row.target() == open ? SettlementCoreLayout.WORKS_CARD_CLOSE
					: SettlementCoreLayout.WORKS_CARD_OPEN);
			int textRight = main.isPresent() ? buttonX - 3 : right - 4;
			boolean cut = !WorksPaint.fits(font, name, textRight - left - SettlementCoreLayout.WORKS_CARD_TEXT);
			return Optional.of(new Target(null, row.target(), cut ? joined(name, tip) : tip));
		}
		int x = left + SettlementCoreLayout.WORKS_OPEN_INDENT;
		int width = right - left - 2 * SettlementCoreLayout.WORKS_OPEN_INDENT;
		int at = y + SettlementCoreLayout.WORKS_CARD + 1 + 3;
		int small = SettlementCoreLayout.WORKS_SMALL_BUTTON;
		for (Line line : block.lines()) {
			Control control = line.control();
			if (control != null) {
				if (control.action() == ConstructionRow.Action.SCHEMATIC) {
					boolean shown = schematicShown(control.target());
					Component words = Component.translatable(shown ? HallSchematicOverlay.HIDE_KEY
							: ConstructionRow.Action.SCHEMATIC.key());
					int w = Math.min(width, font.width(words) + SettlementCoreLayout.WORKS_SMALL_BUTTON + 6);
					if (WorksPaint.inside(mouseX, mouseY, x, at - 1, w, SettlementCoreLayout.WORKS_SMALL_BUTTON)) {
						return Optional.of(new Target(new Hit(control.action(), control.target()), -1, null));
					}
				} else if (WorksPaint.inside(mouseX, mouseY, x + width - small, at - 1, small, small)) {
					return Optional.of(control(new Hit(control.action(), control.target())));
				}
			}
			int height = LINE + (line.barPercent() >= 0 ? 3 : 0);
			if (mouseY >= at - 1 && mouseY < at - 1 + height && control == null
					&& !WorksPaint.fits(font, line.text(), width)) {
				return Optional.of(new Target(null, -1, line.text()));
			}
			at += height;
		}
		return Optional.empty();
	}

	private static Target control(Hit hit) {
		Component words = Component.translatable(hit.action() == ConstructionRow.Action.SCHEMATIC
				&& schematicShown(hit.target()) ? HallSchematicOverlay.HIDE_KEY : hit.action().key());
		return new Target(hit, -1, words);
	}

	private static Component joined(Component first, Component second) {
		return Component.empty().append(first).append(": ").append(second);
	}

	/** Whether the hint bar had to cut the hint. */
	private static boolean hintCut(ConstructionRow.Text hint) {
		Font font = Minecraft.getInstance().font;
		return !TextFit.fit(component(hint).getString(), SettlementCoreLayout.worksHintWidth(),
				SettlementCoreLayout.WORKS_HINT_MAX_LINES, font::width).fits();
	}

	/** The tooltip of what is under the mouse, if any. */
	public Optional<Component> tooltip(int x0, int y0, double mouseX, double mouseY) {
		return target(x0, y0, mouseX, mouseY).map(Target::tooltip);
	}

	/** A click: presses a control or opens and closes a card. Whether it hit anything. */
	public boolean clicked(int x0, int y0, double mouseX, double mouseY) {
		Optional<Target> target = target(x0, y0, mouseX, mouseY);
		if (target.isEmpty()) {
			return false;
		}
		Hit hit = target.get().hit();
		if (hit != null) {
			TownWorksPayload payload = TownWorksClient.latest();
			if (hit.action() == ConstructionRow.Action.SCHEMATIC) {
				if (payload != null) {
					// Visual and personal: asked for on its own.
					HallSchematicOverlay.toggle(payload.core(), hit.target());
				}
			} else {
				TownWorksClient.press(hit.action(), hit.target());
			}
			return true;
		}
		if (target.get().toggle() >= 0) {
			int card = target.get().toggle();
			open = open == card ? -1 : card;
			chosen = true;
			return true;
		}
		return false;
	}

	/** Whether the schematic of building {@code target} of the town whose tab this is is shown (Q6: one at a time). */
	static boolean schematicShown(int target) {
		TownWorksPayload payload = TownWorksClient.latest();
		return payload != null && HallSchematicOverlay.isShown(payload.core(), target);
	}

	/** The wheel scrolls the pane. Whether it did. */
	public boolean scrolled(double scrollY) {
		if (TownWorksClient.latest() == null || scrollY == 0.0) {
			return false;
		}
		scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * SCROLL_STEP);
		return true;
	}

	// --- words -----------------------------------------------------------------------------------------------------

	/**
	 * A row's sentence: every argument shown as a translation, a joined list in the key's one placeholder. Public so
	 * another screen that shows the same server-worded lines (the dispatcher's, MOD-858) reads them the same way.
	 */
	public static Component component(ConstructionRow.Text text) {
		if (text.joined()) {
			MutableComponent list = Component.empty();
			for (int i = 0; i < text.args().size(); i++) {
				if (i > 0) {
					list.append(", ");
				}
				list.append(argument(text.args().get(i)));
			}
			return Component.translatable(text.key(), list);
		}
		Object[] args = new Object[text.args().size()];
		for (int i = 0; i < args.length; i++) {
			args[i] = argument(text.args().get(i));
		}
		return Component.translatable(text.key(), args);
	}

	/** One argument: «Glass ×6» for a counted one (MOD-860), else the translation of the argument itself. */
	private static Component argument(String argument) {
		return ConstructionRow.Text.counted(argument)
				.<Component>map(counted -> Component.translatable(ConstructionRow.Text.COUNTED,
						String.valueOf(counted.count()), Component.translatable(counted.key())))
				.orElseGet(() -> Component.translatable(argument));
	}

}
