package dev.alaindustrial.item.tool;

/**
 * Which extra blocks a drill takes with the one it hits (MOD-821): the effective result of the two bore
 * modules and their two switches. Pure data with no game types, so the cycle order is unit-testable.
 *
 * <p>The key cycles a drill that carries BOTH modules through {@code CROSS, COLUMN, ROW, SINGLE} and back
 * to {@code CROSS}; a drill with one module just flips that module's own switch (see
 * {@code DrillColumnTogglePayload}).
 */
public enum DrillBoreMode {
	/** Centre, above, below, left and right — five blocks. */
	CROSS("cross", true, true),
	/** Centre, above and below — three blocks tall. */
	COLUMN("column", true, false),
	/** Centre, left and right — three blocks wide. */
	ROW("row", false, true),
	/** Only the block aimed at. */
	SINGLE("single", false, false);

	private final String key;
	private final boolean column;
	private final boolean row;

	DrillBoreMode(String key, boolean column, boolean row) {
		this.key = key;
		this.column = column;
		this.row = row;
	}

	/** The suffix of {@code message.alaindustrial.drill_mode.*}. */
	public String key() {
		return key;
	}

	/** Whether this mode takes the blocks above and below. */
	public boolean column() {
		return column;
	}

	/** Whether this mode takes the blocks to the left and right. */
	public boolean row() {
		return row;
	}

	public static DrillBoreMode of(boolean column, boolean row) {
		if (column) {
			return row ? CROSS : COLUMN;
		}
		return row ? ROW : SINGLE;
	}

	/** The next mode in the cycle of a drill that carries both modules. */
	public DrillBoreMode next() {
		return switch (this) {
			case CROSS -> COLUMN;
			case COLUMN -> ROW;
			case ROW -> SINGLE;
			case SINGLE -> CROSS;
		};
	}
}
