package dev.alaindustrial.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * The rows of the core's «Chronicle» tab, each as tall as its sentence (MOD-890, owner's playtest of 0.1.220): a
 * sentence too long for two lines at full size is scaled down and may take a third, and its row grows with it, so the
 * hover wash covers the whole entry — the day and every line — and the next row starts under it, never over it.
 *
 * <p>The tab scrolls by whole rows: {@code scroll} is the index of the top row. Minecraft-free on purpose: the L1 test
 * {@code ChronicleRowsTest} checks the layout; the screen measures each row's lines with {@link TextFit}.
 */
public final class ChronicleRows {

	private ChronicleRows() {
	}

	/** One row on screen: its index into the chronicle, its top from the view's top, its height. */
	public record Row(int index, int top, int height) {

		/** Whether {@code y}, from the view's top, falls on this row. */
		public boolean contains(int y) {
			return y >= top && y < top + height;
		}
	}

	/**
	 * A row's height: {@code lines} lines of {@code scale}, at least one full line, and the gap under it.
	 */
	public static int height(int lines, float scale) {
		int text = Math.max(SettlementCoreLayout.LINE_STEP, Math.max(1, lines) * lineStep(scale));
		return text + SettlementCoreLayout.CHRONICLE_GAP;
	}

	/** From one line of a row to the next at {@code scale}: a scaled-down sentence sits closer. */
	public static int lineStep(float scale) {
		return scale >= 1.0f ? SettlementCoreLayout.LINE_STEP
				: Math.max(1, Math.round(SettlementCoreLayout.LINE_STEP * scale));
	}

	/**
	 * The rows shown from row {@code scroll} in a view {@code view} high, a chronicle of {@code size} rows whose
	 * heights {@code heightOf} gives: every row that starts inside the view and ends inside it; the first is shown even
	 * when it alone is taller.
	 */
	public static List<Row> visible(int size, int scroll, int view, IntUnaryOperator heightOf) {
		List<Row> rows = new ArrayList<>();
		int top = 0;
		for (int i = Math.max(0, scroll); i < size; i++) {
			int height = heightOf.applyAsInt(i);
			if (!rows.isEmpty() && top + height > view) {
				break;
			}
			rows.add(new Row(i, top, height));
			top += height;
		}
		return rows;
	}

	/** The row at {@code y} from the view's top, or -1. */
	public static int rowAt(List<Row> rows, int y) {
		for (Row row : rows) {
			if (row.contains(y)) {
				return row.index();
			}
		}
		return -1;
	}

	/**
	 * The furthest the tab may scroll: the first row from which the rest of the chronicle fits in the view, so the last
	 * row can be reached and the view never scrolls past it.
	 */
	public static int maxScroll(int size, int view, IntUnaryOperator heightOf) {
		int used = 0;
		int first = size;
		for (int i = size - 1; i >= 0; i--) {
			used += heightOf.applyAsInt(i);
			if (used > view) {
				break;
			}
			first = i;
		}
		return Math.min(Math.max(0, size - 1), first);
	}
}
