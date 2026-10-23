package dev.alaindustrial.client.screen.shiftworker;

/**
 * Where the shift worker's card puts things (MOD-854), relative to the panel's top-left corner. The screen places its
 * buttons and {@link ShiftWorkerCardPages} draws its rows from the same numbers, so a «+» always sits on its stat's
 * row. Minecraft-free.
 *
 * <p>The panel follows the player's profile ({@code DashboardScreen}): a dark header, the worker on the left with its
 * battery and health under it, titled modules on the right.
 */
public final class ShiftWorkerCardLayout {

	public static final int PANEL_W = 384;
	public static final int PANEL_H = 248;
	public static final int HEADER = 22;
	/** Header buttons: the tabs and the close button, right-aligned, this tall. */
	public static final int HEADER_BUTTON_H = 16;
	public static final int HEADER_BUTTON_Y = 3;

	/** The left column: the model's frame, then the name, the battery, the health and the charge hint. */
	public static final int LEFT_X = 12;
	public static final int LEFT_W = 112;
	public static final int MODEL_TOP = HEADER + 8;
	public static final int MODEL_BOTTOM = 134;
	public static final int NAME_Y = 140;
	public static final int NAME_H = 14;
	public static final int BATTERY_LABEL_Y = 160;
	public static final int BATTERY_BAR_Y = 171;
	public static final int BATTERY_BAR_H = 8;
	public static final int BATTERY_TEXT_Y = 182;
	public static final int HEALTH_Y = 198;
	/** Under the health, a living worker's card says how to charge it from a battery. */
	public static final int CHARGE_HINT_Y = 212;
	/** The most lines the charge hint wraps to before it is scaled into one. */
	public static final int CHARGE_HINT_LINES = 2;

	/** The right column, from here to the panel's right edge less {@link #MARGIN}. */
	public static final int RIGHT_X = 136;
	public static final int MARGIN = 12;
	public static final int TOP = HEADER + 8;
	/** Space between two modules. */
	public static final int GAP = 5;
	/** A module's title row and the first row under it. */
	public static final int MODULE_TITLE = 5;
	public static final int MODULE_FIRST_ROW = 17;
	public static final int ROW = 11;

	/** The «rank and level» module: title, bar, experience, drained, free points. */
	public static final int RANK_H = 64;
	public static final int RANK_BAR = 17;
	public static final int RANK_XP = 28;
	public static final int RANK_DRAINED = 40;
	public static final int RANK_FREE = 51;

	/** The stats module: title then one row per stat. */
	public static final int STAT_ROW = 12;
	public static final int STAT_FIRST = 17;
	public static final int STAT_NAME_W = 72;
	public static final int STAT_PIPS = 82;
	public static final int STAT_PIP = 5;
	public static final int STAT_COUNT = 146;
	public static final int STAT_CAPTION = 172;
	public static final int SMALL_BUTTON = 12;
	/** The «Relocate» button on the home row (MOD-862); the arrow picking the house is {@link #SMALL_BUTTON} wide. */
	public static final int RELOCATE_W = 64;

	private ShiftWorkerCardLayout() {
	}

	/** The right column's width. */
	public static int rightWidth() {
		return PANEL_W - MARGIN - RIGHT_X;
	}

	/**
	 * The record module's height: the town, the status, the assignment, and the home of a living worker (MOD-862) or
	 * the death line of a dead one.
	 */
	public static int recordHeight(boolean alive) {
		return MODULE_FIRST_ROW + 4 * ROW + 1;
	}

	/** The top of the record module's home row, where «Relocate» stands (MOD-862). */
	public static int homeRowY() {
		return TOP + MODULE_FIRST_ROW + 3 * ROW;
	}

	/** The top of the «rank and level» module. */
	public static int rankTop(boolean alive) {
		return TOP + recordHeight(alive) + GAP;
	}

	/** The top of the stats module. */
	public static int statsTop(boolean alive) {
		return rankTop(alive) + RANK_H + GAP;
	}

	/** The top of the row of stat {@code index}. */
	public static int statRowY(boolean alive, int index) {
		return statsTop(alive) + STAT_FIRST + index * STAT_ROW;
	}
}
