package dev.alaindustrial.client.screen.works;

import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.screen.SettlementCoreLayout;
import dev.alaindustrial.client.screen.TextFit;
import dev.alaindustrial.network.TownWorksPayload;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildingTile;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.ConstructionRows;
import dev.alaindustrial.settlement.works.PlanSheetRows;
import dev.alaindustrial.settlement.works.StageRows;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * What the core's «Construction» tab shows, taken out of the latest reply (owner's round 3 of 0.1.219, R3-3): the
 * buildings' rows grouped into sections by their {@link BuildingTile}, each card with its stage row and its job rows,
 * the road network's row and the plans; and the lines of an open card. Drawing is {@link ConstructionTab}'s.
 */
final class WorksModel {

	private WorksModel() {
	}

	/** The main button of a card: the one of the four «order» actions or the cancel its row offers. */
	static Optional<ConstructionRow.Action> mainAction(ConstructionRow row) {
		for (ConstructionRow.Action action : ConstructionRow.Action.values()) {
			if ((action.places() || action == ConstructionRow.Action.ORDER_CANCEL) && row.allows(action)) {
				return Optional.of(action);
			}
		}
		return Optional.empty();
	}

	/** One card: a building's row, its tile, its stage row and its job rows. */
	record Card(ConstructionRow row, @Nullable BuildingTile tile, @Nullable ConstructionRow stage,
			List<ConstructionRow> jobs) {
	}

	/** What the pane shows, top to bottom, as the payload says it. */
	record Model(List<Section> sections, @Nullable ConstructionRow network, List<ConstructionRow> sheets,
			List<ConstructionRow> housePlans, @Nullable Component empty) {
	}

	record Section(String key, List<Card> cards) {
	}

	/**
	 * One line of an open card: words, an optional bar under them, an optional small control at the right, and the
	 * cell of a block in the way whose «show it» button sits at the right instead (MOD-890).
	 */
	record Line(Component text, int colour, int barPercent, @Nullable Control control, @Nullable Spot spot) {

		Line(Component text, int colour, int barPercent, @Nullable Control control) {
			this(text, colour, barPercent, control, null);
		}
	}

	/** The cell an «In the way: X at x y z» line names: its «show it» button outlines it in the world (MOD-890). */
	record Spot(int x, int y, int z) {
	}

	/** A control: an action on a target; for the schematic switch, its word sits beside the icon. */
	record Control(ConstructionRow.Action action, int target) {
	}

	@Nullable
	static Model model() {
		TownWorksPayload payload = TownWorksClient.latest();
		if (payload == null) {
			return null;
		}
		Map<Integer, BuildingTile> tiles = new HashMap<>();
		for (BuildingTile tile : payload.tiles()) {
			tiles.put(tile.index(), tile);
		}
		Map<Integer, ConstructionRow> stages = new HashMap<>();
		Map<Integer, ConstructionRow> jobs = new HashMap<>();
		List<ConstructionRow> buildings = new ArrayList<>();
		List<ConstructionRow> sheets = new ArrayList<>();
		List<ConstructionRow> housePlans = new ArrayList<>();
		ConstructionRow network = null;
		Component empty = null;
		for (ConstructionRow row : payload.rows()) {
			String section = row.section();
			if (section.equals(StageRows.SECTION)) {
				stages.put(row.target(), row);
			} else if (section.equals(ConstructionRows.SECTION_QUEUE)) {
				jobs.putIfAbsent(row.target(), row);
			} else if (section.equals(PlanSheetRows.SECTION)) {
				sheets.add(row);
			} else if (section.equals(ConstructionRows.SECTION_HOUSE_PLANS)) {
				housePlans.add(row);
			} else if (section.equals(ConstructionRows.SECTION_BUILDINGS)) {
				if (row.text().key().equals(ConstructionRows.ROAD_NETWORK)) {
					network = row;
				} else if (row.text().key().equals(ConstructionRows.NO_BUILDINGS)) {
					empty = ConstructionTab.component(row.text());
				} else {
					buildings.add(row);
				}
			}
		}
		List<Section> sections = sections(buildings, tiles, stages, jobs);
		return new Model(sections, network, sheets, housePlans, empty);
	}

	/** The buildings' rows as cards, grouped by their tiles' sections in the order of {@link BuildingTile.Group}. */
	private static List<Section> sections(List<ConstructionRow> buildings, Map<Integer, BuildingTile> tiles,
			Map<Integer, ConstructionRow> stages, Map<Integer, ConstructionRow> jobs) {
		List<Section> sections = new ArrayList<>();
		for (BuildingTile.Group group : BuildingTile.Group.values()) {
			List<Card> cards = new ArrayList<>();
			for (ConstructionRow row : buildings) {
				BuildingTile tile = tiles.get(row.target());
				BuildingTile.Group of = tile == null ? BuildingTile.Group.OTHER : tile.group();
				if (of != group) {
					continue;
				}
				List<ConstructionRow> own = new ArrayList<>();
				if (tile != null) {
					for (int job : tile.jobs()) {
						ConstructionRow jobRow = jobs.get(job);
						if (jobRow != null) {
							own.add(jobRow);
						}
					}
				}
				cards.add(new Card(row, tile, stages.get(row.target()), own));
			}
			if (!cards.isEmpty()) {
				sections.add(new Section(group.key(), cards));
			}
		}
		return sections;
	}

	/** The card open by default: the first whose order is under way, else none. */
	static int autoOpen(Model model) {
		for (Section section : model.sections()) {
			for (Card card : section.cards()) {
				if (card.tile() != null && (card.tile().chip() == BuildingTile.Chip.WORKING
						|| card.tile().chip() == BuildingTile.Chip.WAITING)) {
					return card.row().target();
				}
			}
		}
		return -1;
	}

	/** The lines of an open card. */
	static List<Line> openLines(Card card) {
		Font font = Minecraft.getInstance().font;
		int width = SettlementCoreLayout.worksOpenWidth();
		List<Line> lines = new ArrayList<>();
		ConstructionRow stage = card.stage();
		if (stage != null) {
			List<String> args = stage.text().args();
			if (stage.text().key().equals(StageRows.LINE) && args.size() >= 4) {
				Component words = Component.translatable(SettlementCoreLayout.WORKS_CARD_STAGE, args.get(1),
						args.get(2), Component.translatable(args.get(3)));
				MutableComponent full = words.copy();
				stage.status().ifPresent(count -> full.append("  ").append(ConstructionTab.component(count)));
				lines.add(new Line(full, GuiStyle.TEXT, stage.percent(), null));
			} else if (stage.text().key().equals(StageRows.LINE_DONE)) {
				lines.add(new Line(Component.translatable(SettlementCoreLayout.WORKS_CARD_STAGES_DONE),
						GuiStyle.STATUS_GOOD, -1, null));
			} else {
				stage.status().ifPresent(status -> lines.add(new Line(ConstructionTab.component(status),
						GuiStyle.TEXT_DIM, -1, null)));
			}
			stage.detail().ifPresent(detail -> wrap(font, detail, width, lines,
					stage.tone() == ConstructionRow.Tone.BAD ? GuiStyle.STATUS_BLOCKING
							: stage.tone() == ConstructionRow.Tone.GOOD ? GuiStyle.STATUS_GOOD : GuiStyle.TEXT_DIM));
		}
		card.row().detail().ifPresent(detail -> wrap(font, detail, width, lines,
				blocking(detail) ? GuiStyle.STATUS_BLOCKING : GuiStyle.TEXT_DIM));
		for (ConstructionRow job : card.jobs()) {
			MutableComponent words = Component.empty();
			List<String> args = job.text().args();
			words.append(args.isEmpty() ? ConstructionTab.component(job.text()) : Component.translatable(args.get(0)));
			job.status().ifPresent(status -> words.append(": ").append(ConstructionTab.component(status)));
			Control control = job.allows(ConstructionRow.Action.START)
					? new Control(ConstructionRow.Action.START, job.target())
					: job.allows(ConstructionRow.Action.CANCEL)
							? new Control(ConstructionRow.Action.CANCEL, job.target()) : null;
			lines.add(new Line(words, job.tone() == ConstructionRow.Tone.BAD ? GuiStyle.STATUS_BLOCKING
					: GuiStyle.TEXT, -1, control));
			job.detail().ifPresent(reason -> wrap(font, reason, width, lines,
					GuiStyle.STATUS_BLOCKING));
		}
		if (card.tile() != null) {
			card.tile().resident().ifPresent(resident -> lines.add(new Line(ConstructionTab.component(resident),
					GuiStyle.TEXT_DIM, -1, null)));
		}
		if (card.row().allows(ConstructionRow.Action.SCHEMATIC)) {
			lines.add(new Line(Component.empty(), GuiStyle.TEXT, -1,
					new Control(ConstructionRow.Action.SCHEMATIC, card.row().target())));
		}
		return lines;
	}

	/** Whether a building's second line says why it may not go on — red — rather than what it misses. */
	static boolean blocking(ConstructionRow.Text detail) {
		return !detail.key().equals(ConstructionRows.MISSING_KEYS);
	}

	/**
	 * A server-worded line broken into lines of {@code width}; an «In the way» reason leaves room at the right for its
	 * «show it» button, which its first line carries (MOD-890).
	 */
	static void wrap(Font font, ConstructionRow.Text text, int width, List<Line> out, int colour) {
		Optional<int[]> cell = BlockReason.obstructedCell(text.key(), text.args());
		if (cell.isEmpty()) {
			wrap(font, ConstructionTab.component(text), width, out, colour);
			return;
		}
		int first = out.size();
		wrap(font, ConstructionTab.component(text), width - SettlementCoreLayout.WORKS_SMALL_BUTTON - 3, out, colour);
		Line line = out.get(first);
		out.set(first, new Line(line.text(), line.colour(), line.barPercent(), null,
				new Spot(cell.get()[0], cell.get()[1], cell.get()[2])));
	}

	/** {@code text} broken into lines of {@code width}, at most {@link SettlementCoreLayout#WORKS_OPEN_MAX_LINES}. */
	static void wrap(Font font, Component text, int width, List<Line> out, int colour) {
		TextFit.Fit fit = TextFit.fit(text.getString(), width, SettlementCoreLayout.WORKS_OPEN_MAX_LINES, font::width);
		for (String part : fit.lines()) {
			out.add(new Line(Component.literal(part), colour, -1, null));
		}
	}
}
