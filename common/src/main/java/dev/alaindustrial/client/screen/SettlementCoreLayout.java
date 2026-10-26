package dev.alaindustrial.client.screen;

import java.util.List;

/**
 * Where the settlement core's screen puts its text, and which translated lines it shows (MOD-831, MOD-842).
 *
 * <p>Minecraft-free on purpose: {@link SettlementCoreScreen} draws from these numbers and keys, and the L1 test
 * {@code SettlementCoreLayoutTest} fits every key of every language of the mod into the same boxes with the same
 * {@link TextFit} — a translation that would cross the frame fails the build instead of reaching a playtest.
 *
 * <p><b>The column.</b> Rows start right of the energy bar (x 15..28) and end at the frame's inner border. The
 * status row may wrap onto a second line, every other row holds one line (scaled down when it must), and the hint
 * of a dark unclaimed core runs under the bar at the full width, on up to three lines. The show-borders button
 * (MOD-843) sits under the energy bar, left of the rows and above the hint.
 */
public final class SettlementCoreLayout {

	/**
	 * Panel size; must match {@code tools/gen_settlement_core_gui.py}. Owner's round 2 of 0.1.219 (R2-2): wider and
	 * taller, so a «Construction» row's controls fit beside its words and the long translations get room; still inside
	 * 426 x 240, the screen of 1280 x 720 at interface scale 3 (MOD-873).
	 */
	public static final int IMAGE_WIDTH = 256;
	public static final int IMAGE_HEIGHT = 204;
	/**
	 * Where the energy fill sprite sits in the atlas: under the panel, at the left, because a panel 256 wide leaves no
	 * service area to its right.
	 */
	public static final int ENERGY_SPRITE_U = 0;
	public static final int ENERGY_SPRITE_V = 210;
	/** The frame's border: three pixels on every side ({@code tools/gen_reactor_controller_gui.py}). */
	public static final int BORDER = 3;

	public static final int TITLE_Y = 6;
	/** The text column: right of the energy bar to the frame's inner border, less a margin. */
	public static final int TEXT_LEFT = 34;
	public static final int TEXT_RIGHT = IMAGE_WIDTH - 8;
	public static final int ROW_TOP = 20;
	/** From one row to the next. */
	public static final int ROW_STEP = 11;
	/** From one line of a wrapped row to its next line: a row's lines sit closer than two rows. */
	public static final int LINE_STEP = 10;
	public static final int STATUS_MAX_LINES = 2;
	public static final int ROW_MAX_LINES = 1;

	/** The hint under the readout, below the energy bar and the button, so it may use the whole width. */
	public static final int HINT_LEFT = 8;
	public static final int HINT_TOP = 98;
	public static final int HINT_MAX_LINES = 3;

	/** The show-borders button (MOD-843): under the energy bar, left of the text column. */
	public static final int BUTTON_X = 10;
	public static final int BUTTON_Y = 70;

	/** The first row: lit, dark, a second core of its town (R43), or a core of no town (R53). */
	public static final String STATUS_ACTIVE = "settlement.alaindustrial.core.status.active";
	public static final String STATUS_DARK = "settlement.alaindustrial.core.status.dark";
	public static final String STATUS_MISPLACED = "settlement.alaindustrial.core.status.misplaced";
	public static final String STATUS_UNBOUND = "settlement.alaindustrial.core.status.unbound";
	public static final List<String> STATUS_KEYS =
			List.of(STATUS_ACTIVE, STATUS_DARK, STATUS_MISPLACED, STATUS_UNBOUND);

	/**
	 * The one-line rows under the status: the territory's width and depth (two numbers, MOD-848 — a structure's town
	 * is a rectangle over its buildings, so a radius would not describe it), then one number each.
	 */
	public static final String TERRITORY = "settlement.alaindustrial.core.territory";
	public static final String HOUSES = "settlement.alaindustrial.core.houses";
	public static final String WORKERS = "settlement.alaindustrial.core.workers";
	public static final String UPKEEP = "settlement.alaindustrial.core.upkeep";
	public static final List<String> NUMBER_ROWS = List.of(TERRITORY, HOUSES, WORKERS, UPKEEP);

	/** The owner row: nobody, you, somebody whose name the screen does not know, or the owner by name. */
	public static final String OWNER_NONE = "settlement.alaindustrial.core.owner.none";
	public static final String OWNER_YOU = "settlement.alaindustrial.core.owner.you";
	public static final String OWNER_OTHER = "settlement.alaindustrial.core.owner.other";
	public static final String OWNER_NAMED = "settlement.alaindustrial.core.owner.named";
	public static final List<String> OWNER_KEYS = List.of(OWNER_NONE, OWNER_YOU, OWNER_OTHER, OWNER_NAMED);

	public static final String HINT = "settlement.alaindustrial.core.owner.hint";

	/**
	 * The roster tabs (MOD-854): «Shift workers» (the living and the dead waiting to come back) and «Closed» (the
	 * dead for good), down the panel's left edge after the core's own tab. One row per worker — number, name, level,
	 * status — under the heading; the wheel scrolls a longer list, a click opens the worker's card.
	 */
	public static final String TAB_CORE = "settlement.alaindustrial.core.tab.core";
	public static final String TAB_WORKERS = "settlement.alaindustrial.core.tab.workers";
	public static final String TAB_CLOSED = "settlement.alaindustrial.core.tab.closed";
	/** The «Construction» tab (MOD-857): the town's buildings and its work queue, rows the server words. */
	public static final String TAB_WORKS = "settlement.alaindustrial.core.tab.works";
	/**
	 * The «Chronicle» tab (MOD-886): the town hall's log, newest line first — the day, the worker, the sentence — one
	 * row of up to {@link #CHRONICLE_TEXT_LINES} lines per line of the log, the wheel scrolling a longer one.
	 */
	public static final String TAB_CHRONICLE = "settlement.alaindustrial.core.tab.chronicle";
	/** Where a chronicle row's sentence starts, right of its day. */
	public static final int CHRONICLE_TEXT = 48;
	/** How many lines a chronicle row's sentence wraps onto at full size; past that it is scaled down. */
	public static final int CHRONICLE_TEXT_LINES = 2;
	/**
	 * The most lines a chronicle row shows (MOD-890): a sentence too long for two lines even scaled down takes a third,
	 * cut with an ellipsis; the tooltip shows it whole. A row is as tall as its lines ({@link ChronicleRows}).
	 */
	public static final int CHRONICLE_MAX_LINES = 3;
	/** The gap under a chronicle row. */
	public static final int CHRONICLE_GAP = 2;
	/** The height the chronicle's rows share: from the roster's top to the frame's bottom border. */
	public static final int CHRONICLE_VIEW = IMAGE_HEIGHT - BORDER - 1 - SettlementCoreLayout.ROSTER_TOP;
	/**
	 * The widest a tooltip of the core's screen runs (MOD-890): on a wide screen a long line is wrapped like vanilla's,
	 * not stretched across the screen.
	 */
	public static final int TOOLTIP_MAX_WIDTH = 200;
	/**
	 * The town's needs on the core's tab (MOD-886, the town's pulse): one line «The town needs: …» on up to
	 * {@link #NEEDS_TOP_MAX_LINES} lines under the rows, then the short list of up to {@link #NEEDS_LIST_ROWS} needs,
	 * at the hint's width.
	 */
	public static final int NEEDS_TOP_MAX_LINES = 2;
	public static final int NEEDS_LIST_ROWS = 6;
	/** The gap between the rows and the needs. */
	public static final int NEEDS_GAP = 2;
	/** A need's row in the list starts this far right of the needs' left edge, after its picture. */
	public static final int NEEDS_INDENT = 11;
	/**
	 * The pictures of the needs (MOD-890): the top need's beside its line, an item's size; each need of the list a
	 * half-size one at the row's start.
	 */
	public static final int NEEDS_ICON = 16;
	public static final int NEEDS_SMALL_ICON = 8;

	/** The line under the rows for a viewer who is not the town's owner (R95). */
	public static final String WORKS_READ_ONLY = "settlement.alaindustrial.works.read_only";
	public static final String ROSTER_EMPTY = "settlement.alaindustrial.core.roster.empty";
	public static final String ROSTER_CLOSED_EMPTY = "settlement.alaindustrial.core.roster.closed_empty";
	public static final String ROSTER_LOADING = "settlement.alaindustrial.core.roster.loading";
	public static final String ROSTER_OPEN = "settlement.alaindustrial.core.roster.open";
	/** The «Find» button's tooltip at a living worker's row (R4-13). */
	public static final String ROSTER_LOCATE = "settlement.alaindustrial.core.roster.locate";
	/** A row's status word, by {@code ShiftWorkerRecord.Status} serialized name. */
	public static final String ROSTER_STATUS = "settlement.alaindustrial.core.roster.status.";

	public static final int ROSTER_LEFT = 7;
	public static final int ROSTER_RIGHT = IMAGE_WIDTH - 7;
	public static final int ROSTER_TOP = 19;
	public static final int ROSTER_ROW = 11;
	/** As many rows as the panel holds above its bottom border. */
	public static final int ROSTER_ROWS = (IMAGE_HEIGHT - BORDER - 1 - ROSTER_TOP) / ROSTER_ROW;
	/** The worker's face at a row's start: a square left of the number, one pixel above the text line. */
	public static final int COL_FACE = 1;
	public static final int FACE_SIZE = 10;
	/** Column starts inside a row: number, name, level, status. */
	public static final int COL_NUMBER = 13;
	public static final int COL_NAME = 46;
	public static final int COL_LEVEL = 138;
	public static final int COL_STATUS = 170;
	/**
	 * The «Find» button at the right end of a living worker's row (R4-13), square; the status column ends left of
	 * it, so a long status word in any language still has about fifty pixels.
	 */
	public static final int LOCATE_BUTTON = 10;
	/**
	 * The picture in a small button: an item at half size. A button whose side minus this is even has it centred on
	 * a whole pixel (MOD-890: an 11-pixel button left one pixel above and left of it and two below and right).
	 */
	public static final int SMALL_ICON = 8;
	/**
	 * The names a «Construction» card prints in its name line; the L1 test fits each into
	 * {@link #worksNameWidth()} in every language (the server words the rows, {@code ConstructionRows}).
	 */
	public static final List<String> WORKS_NAMES = List.of(
			"settlement.alaindustrial.building.town_hall", "settlement.alaindustrial.building.capsule_house",
			"settlement.alaindustrial.building.workshop", "settlement.alaindustrial.building.pump_station",
			"settlement.alaindustrial.building.warehouse", "settlement.alaindustrial.building.guard_post",
			"settlement.alaindustrial.building.unnamed", "settlement.alaindustrial.building.capsule_house.type_1",
			"settlement.alaindustrial.building.capsule_house.type_2",
			"settlement.alaindustrial.building.capsule_house.type_3",
			"settlement.alaindustrial.building.capsule_house.type_4");

	/*
	 * The «Construction» tab as cards (owner's round 3 of 0.1.219, R3-3, R3-6): the hint bar at the top, then a pane
	 * that scrolls by pixels — section headings, building cards, the road network, the plans. Offsets from the image's
	 * top-left; a card's parts from the card's top-left.
	 */
	/** The hint bar: what to do next, two lines at most. */
	public static final int WORKS_HINT_TOP = 19;
	public static final int WORKS_HINT_HEIGHT = 22;
	/** Where the hint's words start after the badge, and how wide they may run. */
	public static final int WORKS_HINT_TEXT = 21;
	public static final int WORKS_HINT_MAX_LINES = 2;
	/** The scrolling pane under the hint bar. */
	public static final int WORKS_VIEW_TOP = WORKS_HINT_TOP + WORKS_HINT_HEIGHT + 3;
	public static final int WORKS_VIEW_BOTTOM = IMAGE_HEIGHT - BORDER - 1;
	/** The scroll bar at the pane's right, and the cards left of it. */
	public static final int WORKS_SCROLLBAR = 3;
	public static final int WORKS_CONTENT_LEFT = ROSTER_LEFT + 1;
	public static final int WORKS_CONTENT_RIGHT = ROSTER_RIGHT - WORKS_SCROLLBAR - 3;
	/** A section heading's height and the gap above it. */
	public static final int WORKS_HEADING = 14;
	/** A closed card: the picture in its box at the left, the name, the chip and the share, one button at the right. */
	public static final int WORKS_CARD = 30;
	public static final int WORKS_CARD_GAP = 3;
	public static final int WORKS_ICON_BOX = 26;
	public static final int WORKS_ICON = 24;
	public static final int WORKS_CARD_TEXT = 32;
	public static final int WORKS_BUTTON = 20;
	/** The widest a chip may be; its word is scaled down to fit. */
	public static final int WORKS_CHIP_MAX = 84;
	/** The open part of a card: its lines indented under the picture. */
	public static final int WORKS_OPEN_INDENT = 6;
	public static final int WORKS_OPEN_MAX_LINES = 3;
	/**
	 * A small button inside the open part: a job's cancel and restart, «show it» at the block in the way (MOD-890), the
	 * schematic switch.
	 */
	public static final int WORKS_SMALL_BUTTON = 10;
	/** One row of the plans: its label's width, then slots of {@link #WORKS_SLOT} pixels. */
	public static final int WORKS_PLAN_ROW = 22;
	public static final int WORKS_PLAN_LABEL = 62;
	public static final int WORKS_SLOT = 18;

	/** The section headings, chip words and hint lines of the tab; the L1 test fits each into its box. */
	public static final List<String> WORKS_SECTIONS = List.of("settlement.alaindustrial.works.section.hall",
			"settlement.alaindustrial.works.section.house", "settlement.alaindustrial.works.section.other",
			"settlement.alaindustrial.works.section.roads", "settlement.alaindustrial.works.section.plans");
	public static final List<String> WORKS_CHIPS = List.of("settlement.alaindustrial.works.chip.ruin",
			"settlement.alaindustrial.works.chip.damaged", "settlement.alaindustrial.works.chip.no_plan",
			"settlement.alaindustrial.works.chip.locked", "settlement.alaindustrial.works.chip.working",
			"settlement.alaindustrial.works.chip.waiting", "settlement.alaindustrial.works.chip.done");
	/** «Stage %1$s of %2$s: %3$s» inside an open card, and «Every stage stands». */
	public static final String WORKS_CARD_STAGE = "settlement.alaindustrial.works.card.stage";
	public static final String WORKS_CARD_STAGES_DONE = "settlement.alaindustrial.works.card.stages_done";
	/** The tooltip of a card's top: opens and closes it. */
	public static final String WORKS_CARD_OPEN = "settlement.alaindustrial.works.card.open";
	public static final String WORKS_CARD_CLOSE = "settlement.alaindustrial.works.card.close";
	/** «%1$s of %2$s» — plan sheets found. */
	public static final String WORKS_SHEETS_COUNT = "settlement.alaindustrial.works.plans.sheets_count";

	/** The width the hint's words may take. */
	public static int worksHintWidth() {
		return ROSTER_RIGHT - ROSTER_LEFT - WORKS_HINT_TEXT - 3;
	}

	/** The width of a card. */
	public static int worksCardWidth() {
		return WORKS_CONTENT_RIGHT - WORKS_CONTENT_LEFT;
	}

	/** The width of a closed card's name: from its text column to its button. */
	public static int worksNameWidth() {
		return worksCardWidth() - WORKS_CARD_TEXT - WORKS_BUTTON - 6;
	}

	/** The width of an open card's lines. */
	public static int worksOpenWidth() {
		return worksCardWidth() - 2 * WORKS_OPEN_INDENT;
	}

	private SettlementCoreLayout() {
	}

	/** How many rows a roster of {@code size} can scroll by. */
	public static int maxRosterScroll(int size) {
		return Math.max(0, size - ROSTER_ROWS);
	}

	public static int columnWidth() {
		return TEXT_RIGHT - TEXT_LEFT;
	}

	public static int hintWidth() {
		return TEXT_RIGHT - HINT_LEFT;
	}

	/** The top of the row after a status of {@code statusLines} lines. */
	public static int firstNumberRowY(int statusLines) {
		return ROW_TOP + (statusLines - 1) * LINE_STEP + ROW_STEP;
	}

	/** The top of the town's needs: under the last row, where a dark unclaimed core's hint would stand. */
	public static int needsTop(int afterRowsY) {
		return hintTop(afterRowsY) + NEEDS_GAP;
	}

	/** Where a small button of side {@code button} draws its {@link #SMALL_ICON} picture, from its left or top edge. */
	public static int smallIconInset(int button) {
		return (button - SMALL_ICON) / 2;
	}

	/** The width the top need's words may take, right of its picture. */
	public static int needsTopWidth() {
		return hintWidth() - NEEDS_ICON - 2;
	}

	/** The width a tooltip wraps at on a screen {@code screenWidth} wide: {@link #TOOLTIP_MAX_WIDTH}, or less. */
	public static int tooltipWidth(int screenWidth) {
		return Math.max(80, Math.min(TOOLTIP_MAX_WIDTH, screenWidth / 2 - 12));
	}

	/** The hint's top: under the last row, but never over the button. */
	public static int hintTop(int afterRowsY) {
		return Math.max(afterRowsY, HINT_TOP);
	}
}
