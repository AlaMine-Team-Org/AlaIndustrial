package dev.alaindustrial.client.compat;

import java.util.List;
import java.util.Locale;

/**
 * Loader-neutral geometry and labels for AlaIndustrial recipe cards.
 *
 * <p>REI and JEI use different widget APIs, but the card must present the same slots in the same
 * order and print the same values. Keeping the coordinates and formatting here prevents the two
 * loader adapters from drifting.
 */
public final class RecipeViewerLayout {
	public static final int WIDTH = 150;
	public static final int HEIGHT = 48;
	public static final int SLOT_Y = 6;
	public static final int ARROW_X = 64;
	public static final int ARROW_Y = 5;
	public static final int LABEL_Y = 34;

	private static final List<Integer> ONE_INPUT = List.of(38);
	private static final List<Integer> TWO_INPUTS = List.of(27, 49);
	private static final List<Integer> ONE_OUTPUT = List.of(95);
	private static final List<Integer> TWO_OUTPUTS = List.of(84, 106);

	private RecipeViewerLayout() {
	}

	/** Ordered x positions for one or two simultaneous input slots. */
	public static List<Integer> inputXs(int count) {
		return positions(count, ONE_INPUT, TWO_INPUTS, "input");
	}

	/** Ordered x positions for one or two simultaneous output slots. */
	public static List<Integer> outputXs(int count) {
		return positions(count, ONE_OUTPUT, TWO_OUTPUTS, "output");
	}

	private static List<Integer> positions(int count, List<Integer> one, List<Integer> two, String role) {
		return switch (count) {
			case 1 -> one;
			case 2 -> two;
			default -> throw new IllegalArgumentException(
					"recipe viewer supports 1 or 2 " + role + " slots, got " + count);
		};
	}

	/** Shared item-processing label used verbatim by REI and JEI. */
	public static String costLabel(int energy, int processingTicks) {
		return energy + " EU · " + formatSeconds(processingTicks);
	}

	/** Shared fluid-processing label; {@code amountMb} is the volume consumed per operation. */
	public static String fluidCostLabel(int energy, int processingTicks, int amountMb) {
		return costLabel(energy, processingTicks) + " · " + amountMb + " mB";
	}

	/** Append the optional success chance used by Incubator recipe families. */
	public static String withChance(String label, double chance) {
		if (chance <= 0.0) {
			return label;
		}
		return label + " · " + Math.round(chance * 100.0) + " %";
	}

	static String formatSeconds(int ticks) {
		String seconds = String.format(Locale.ROOT, "%.1f", ticks / 20.0);
		if (seconds.endsWith(".0")) {
			seconds = seconds.substring(0, seconds.length() - 2);
		}
		return seconds + " s";
	}
}
