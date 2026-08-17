package dev.alaindustrial.client;

import java.util.Locale;

/**
 * Number and duration formatting shared by the mod's readouts (MOD-125).
 *
 * <p>Exists because the same two jobs had already been solved three times in three private copies
 * (the dashboard's {@code compact}, the stock frame's {@code formatCount}, the recipe viewer's
 * {@code formatSeconds}), each with its own rounding and none of them able to express days. A fourth
 * copy for the statistics panel would have made "how does this mod write a big number" a question with
 * four answers.
 *
 * <p>{@link Locale#ROOT} throughout: a decimal comma in a German client turns "1.2k EU" into a string
 * whose separator disagrees with every other number on the same screen.
 */
public final class ReadoutFormat {

	private ReadoutFormat() {
	}

	/**
	 * Abbreviate a magnitude: exact below 1000, then one decimal with a k/M/B suffix.
	 *
	 * <p>Deliberately lossy — a career counter reaching eight digits is unreadable in a 159px panel, and
	 * the exact figure stays available in the row's tooltip.
	 */
	public static String compact(long value) {
		if (value < 1000L) {
			return Long.toString(value);
		}
		if (value < 1_000_000L) {
			return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
		}
		if (value < 1_000_000_000L) {
			return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
		}
		return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
	}

	/** The exact figure with thousands separators, for the tooltip behind a {@link #compact} reading. */
	public static String exact(long value) {
		return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
	}

	/**
	 * A tick span as {@code d}, {@code hh}, {@code mm} — the pieces, not a rendered string.
	 *
	 * <p>The unit letters live in the language files, not here: "d/h/m" is English, and a helper that
	 * baked them in would ship an untranslatable readout to nineteen other locales. The caller pairs
	 * these numbers with a translatable pattern.
	 *
	 * @return {@code [days, hours, minutes]}
	 */
	public static long[] durationParts(long ticks) {
		long totalSeconds = Math.max(0L, ticks) / 20L;
		return new long[] {
				totalSeconds / 86_400L,
				(totalSeconds % 86_400L) / 3600L,
				(totalSeconds % 3600L) / 60L };
	}

	/** Zero-padded {@code hh:mm} for the duration pattern's time half. */
	public static String clock(long hours, long minutes) {
		return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
	}
}
