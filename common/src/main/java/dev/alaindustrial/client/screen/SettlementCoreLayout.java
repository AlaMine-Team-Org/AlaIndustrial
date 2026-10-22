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

	/** Panel size; must match {@code tools/gen_settlement_core_gui.py}. */
	public static final int IMAGE_WIDTH = 176;
	public static final int IMAGE_HEIGHT = 134;
	/** The frame's border: three pixels on every side ({@code tools/gen_reactor_controller_gui.py}). */
	public static final int BORDER = 3;

	public static final int TITLE_Y = 6;
	/** The text column: right of the energy bar to the frame's inner border, less a margin. */
	public static final int TEXT_LEFT = 34;
	public static final int TEXT_RIGHT = 168;
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
	/** The line under the rows for a viewer who is not the town's owner (R95). */
	public static final String WORKS_READ_ONLY = "settlement.alaindustrial.works.read_only";
	public static final String ROSTER_EMPTY = "settlement.alaindustrial.core.roster.empty";
	public static final String ROSTER_CLOSED_EMPTY = "settlement.alaindustrial.core.roster.closed_empty";
	public static final String ROSTER_LOADING = "settlement.alaindustrial.core.roster.loading";
	public static final String ROSTER_OPEN = "settlement.alaindustrial.core.roster.open";
	/** A row's status word, by {@code ShiftWorkerRecord.Status} serialized name. */
	public static final String ROSTER_STATUS = "settlement.alaindustrial.core.roster.status.";

	public static final int ROSTER_LEFT = 7;
	public static final int ROSTER_RIGHT = 169;
	public static final int ROSTER_TOP = 19;
	public static final int ROSTER_ROW = 11;
	public static final int ROSTER_ROWS = 10;
	/** The worker's face at a row's start: a square left of the number, one pixel above the text line. */
	public static final int COL_FACE = 1;
	public static final int FACE_SIZE = 10;
	/** Column starts inside a row: number, name, level, status. */
	public static final int COL_NUMBER = 13;
	public static final int COL_NAME = 46;
	public static final int COL_LEVEL = 94;
	public static final int COL_STATUS = 122;
	/** Column starts inside a «Construction» row: status, percent, the owner's controls; and a second line's indent. */
	public static final int WORKS_COL_STATUS = 76;
	public static final int WORKS_COL_PERCENT = 124;
	public static final int WORKS_COL_ACTIONS = 143;
	public static final int WORKS_ACTION_SIZE = 9;
	public static final int WORKS_DETAIL_INDENT = 8;
	/**
	 * The words a «Construction» row puts in its status column — the buildings' states and the jobs' statuses — and
	 * the names in its main column; the L1 test fits each into its column in every language (the server words the rows,
	 * {@code ConstructionRows}).
	 */
	public static final List<String> WORKS_STATUS_WORDS = List.of(
			"settlement.alaindustrial.works.state.ruin", "settlement.alaindustrial.works.state.clearing",
			"settlement.alaindustrial.works.state.repair", "settlement.alaindustrial.works.state.intact",
			"settlement.alaindustrial.works.state.upgraded", "settlement.alaindustrial.works.status.queued",
			"settlement.alaindustrial.works.status.taken", "settlement.alaindustrial.works.status.blocked",
			"settlement.alaindustrial.works.status.done", "settlement.alaindustrial.works.status.cancelled");
	public static final List<String> WORKS_NAMES = List.of(
			"settlement.alaindustrial.building.town_hall", "settlement.alaindustrial.building.capsule_house",
			"settlement.alaindustrial.building.workshop", "settlement.alaindustrial.building.pump_station",
			"settlement.alaindustrial.building.warehouse", "settlement.alaindustrial.building.guard_post",
			"settlement.alaindustrial.building.unnamed");

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

	/** The hint's top: under the last row, but never over the button. */
	public static int hintTop(int afterRowsY) {
		return Math.max(afterRowsY, HINT_TOP);
	}
}
