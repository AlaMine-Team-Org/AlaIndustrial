package dev.alaindustrial.settlement.works;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One row of the settlement core's «Construction» tab (MOD-857, epic decision R88), as the server words it and the
 * client draws it: a section heading, a main line, a short status at its right, an optional percent, an optional
 * second line, a colour, and the controls the viewer may press on it. The client knows no building, job or plan — it
 * draws rows — so a later task adds its own rows (the plan sheets of MOD-859, the material stages of MOD-860) with a
 * {@link ConstructionRows.Source} and no change to the screen.
 *
 * <p>Minecraft-free on purpose: the rows are data.
 *
 * @param section the translation key of the section the row belongs to; the screen prints a heading wherever the
 *                section changes
 * @param text    the main line
 * @param status  the short word at the right, or empty
 * @param percent a share in whole percent shown at the right, or -1 for none
 * @param tone    the colour of the main line and the status
 * @param detail  a second line under the main one — a job's block reason, a building's missing key points
 * @param target  what the row's controls act on — a building index or a job number, by the control
 * @param actions the controls the viewer may press, one bit per {@link Action}; 0 for a viewer who is
 *                not the town's owner (R95)
 */
public record ConstructionRow(String section, Text text, Optional<Text> status, int percent, Tone tone,
		Optional<Text> detail, int target, int actions) {

	/** The colour of a row. */
	public enum Tone {
		/** Ordinary text. */
		NORMAL,
		/** Grey: history, nothing to do. */
		DIM,
		/** Green: done, intact. */
		GOOD,
		/** Red: blocked, missing. */
		BAD
	}

	/**
	 * A translated sentence: a key and its arguments, each argument itself shown as a translation (a block's
	 * description id reads as its name, a plain number as itself). {@code joined} puts all the arguments,
	 * comma-separated, into the key's one placeholder — a list of unknown length.
	 *
	 * @param key    the translation key
	 * @param args   its arguments
	 * @param joined whether the arguments go, joined, into one placeholder
	 */
	public record Text(String key, List<String> args, boolean joined) {

		public Text {
			Objects.requireNonNull(key, "key");
			args = List.copyOf(args);
		}

		/** A key with its arguments one by one. */
		public static Text of(String key, String... args) {
			return new Text(key, List.of(args), false);
		}

		/** A block reason as a line ({@link BlockReason} has the same shape). */
		public static Text of(BlockReason reason) {
			return new Text(reason.key(), reason.args(), reason.joined());
		}

		/** The translation key a counted argument reads through: «%2$s ×%1$s» — the name, then the count. */
		public static final String COUNTED = "settlement.alaindustrial.works.counted";

		/**
		 * An argument that reads as «Glass ×6» (MOD-860): {@code count} of the thing whose translation key is
		 * {@code key}. Written as {@code 6*block.minecraft.glass} — no translation key holds a {@code *}, so a plain
		 * key or number never parses as one ({@link #counted(String)}).
		 */
		public static String counted(int count, String key) {
			return count + "*" + key;
		}

		/** The count and key of a counted argument, or empty for any other argument. */
		public static Optional<Counted> counted(String argument) {
			int star = argument.indexOf('*');
			if (star <= 0 || star == argument.length() - 1) {
				return Optional.empty();
			}
			for (int i = 0; i < star; i++) {
				if (!Character.isDigit(argument.charAt(i))) {
					return Optional.empty();
				}
			}
			try {
				return Optional.of(new Counted(Integer.parseInt(argument.substring(0, star)),
						argument.substring(star + 1)));
			} catch (NumberFormatException tooLong) {
				return Optional.empty();
			}
		}

		/**
		 * A counted argument taken apart.
		 *
		 * @param count how many
		 * @param key   the translation key of what
		 */
		public record Counted(int count, String key) {
		}
	}

	public ConstructionRow {
		Objects.requireNonNull(section, "section");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(tone, "tone");
		percent = Math.max(-1, Math.min(100, percent));
	}

	/** Whether the viewer may press {@code action} on this row. */
	public boolean allows(Action action) {
		return (actions & action.bit()) != 0;
	}

	/** The controls of the tab. */
	public enum Action {
		/**
		 * Order the row's building restored (round 2 of 0.1.219, Q1): its clearing, its road and its building, as it
		 * needs them ({@link TownOrders}). The four «order» actions do the same; each words the one button the row
		 * shows by the building's state.
		 */
		ORDER_RESTORE,
		/** Order a house a player laid built. */
		ORDER_BUILD,
		/** Order a building whose order was called off to go on from where it stopped. */
		ORDER_RESUME,
		/** Order a building restored once and damaged since mended. */
		ORDER_REPAIR,
		/** Call off the row's building's order: every open job on it. */
		ORDER_CANCEL,
		/** Call off the row's job. */
		CANCEL,
		/** Put the row's blocked job back in the queue. */
		START,
		/**
		 * Show or hide the town hall's schematic: its cells still to build as ghost blocks in the world (owner's round
		 * 2, point 7). Visual only and personal, so every viewer gets it; the client handles the press itself
		 * ({@link HallSchematic}), the tab's request never carries it.
		 */
		SCHEMATIC;

		/** Its bit in {@link ConstructionRow#actions()}. */
		public int bit() {
			return 1 << ordinal();
		}

		/** The translation key of its tooltip. */
		public String key() {
			return "settlement.alaindustrial.works.action." + name().toLowerCase(Locale.ROOT);
		}

		/** Whether it places an order (round 2 of 0.1.219, Q1). */
		public boolean places() {
			return this == ORDER_RESTORE || this == ORDER_BUILD || this == ORDER_RESUME || this == ORDER_REPAIR;
		}

		/** The action that words {@code button}. */
		public static Action of(OrderButton button) {
			return switch (button) {
				case RESTORE -> ORDER_RESTORE;
				case BUILD -> ORDER_BUILD;
				case RESUME -> ORDER_RESUME;
				case REPAIR -> ORDER_REPAIR;
				case CANCEL -> ORDER_CANCEL;
			};
		}

		/** The action with this ordinal, if any — what the request packet carries. */
		public static Optional<Action> byOrdinal(int ordinal) {
			Action[] all = values();
			return ordinal >= 0 && ordinal < all.length ? Optional.of(all[ordinal]) : Optional.empty();
		}
	}
}
