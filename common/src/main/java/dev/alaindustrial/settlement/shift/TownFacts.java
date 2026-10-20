package dev.alaindustrial.settlement.shift;

import java.util.List;
import java.util.OptionalInt;
import java.util.TreeSet;

/**
 * What a shift worker's biography may quote about the worker's town (MOD-854): the digits of the house plaques and
 * of the lab under the town hall. A biography line that quotes them names numbers the player can walk to and read on
 * the wall — the same digits, by construction (epic decision R26a, research.md §5.2).
 *
 * <p>Collected from the town's structure on the server ({@code TownBiographyFacts.at}); {@link #UNKNOWN} where the town
 * cannot be read (no structure at the core, a town of an older world), and then no fragment that quotes it is chosen.
 *
 * <p>Minecraft-free on purpose: the L1 lane tests the biography rule with it.
 *
 * @param plaques the three-digit numbers (0 to 999) of the town's whole house plaques, sorted, without repeats
 * @param lab     the three digits (0 to 999) of the lab's plaque, or empty when unknown
 */
public record TownFacts(List<Integer> plaques, OptionalInt lab) {

	/** A town nothing is known about: no fragment with a substitution fits it. */
	public static final TownFacts UNKNOWN = new TownFacts(List.of(), OptionalInt.empty());

	public TownFacts {
		TreeSet<Integer> sorted = new TreeSet<>();
		for (int plaque : plaques) {
			requireDigits(plaque);
			sorted.add(plaque);
		}
		plaques = List.copyOf(sorted);
		lab.ifPresent(TownFacts::requireDigits);
	}

	private static void requireDigits(int number) {
		if (number < 0 || number > 999) {
			throw new IllegalArgumentException("a plaque number has three digits, got " + number);
		}
	}
}
