package dev.alaindustrial.settlement.shift;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The rule that gives a shift worker its look (MOD-832, ADR-050 point 6): one look out of the candidates, chosen
 * by the worker's record number and the world seed, weighted by each look's {@code weight}. The same number in
 * the same world always gets the same look, whatever order the datapack listed the looks in — candidates are
 * sorted by id first, the registry's order is not something to lean on.
 *
 * <p>The chosen id is written into the worker's record and never recomputed; the rule runs again only when that
 * look has gone from the datapack.
 *
 * <p>Minecraft-free on purpose: the L1 lane tests it ({@code LookPickTest}).
 */
public final class LookPick {

	/** Mixed into the key so a look never walks in step with the number sequence. ASCII {@code ALA-LOOK}. */
	private static final long DOMAIN = 0x414C412D4C4F4F4BL;

	private LookPick() {
	}

	/** One candidate look: its registry id as text and its weight (0 or less — never chosen). */
	public record Candidate(String id, int weight) {
	}

	/**
	 * The id of the look for the worker whose number is {@code numberIndex} (its index in the record space) in the
	 * world with {@code worldSeed}, or empty when no candidate has a positive weight.
	 */
	public static Optional<String> pick(List<Candidate> candidates, long worldSeed, int numberIndex) {
		List<Candidate> sorted = new ArrayList<>(candidates.size());
		long total = 0;
		for (Candidate candidate : candidates) {
			if (candidate.weight() > 0) {
				sorted.add(candidate);
				total += candidate.weight();
			}
		}
		if (sorted.isEmpty()) {
			return Optional.empty();
		}
		sorted.sort(Comparator.comparing(Candidate::id));
		long roll = Math.floorMod(mix(mix(worldSeed ^ DOMAIN) ^ numberIndex), total);
		for (Candidate candidate : sorted) {
			roll -= candidate.weight();
			if (roll < 0) {
				return Optional.of(candidate.id());
			}
		}
		throw new IllegalStateException("the roll ran past the total weight " + total);
	}

	/** SplitMix64's output function (Stafford's variant 13), the mixer {@code RecordNumberSequence} uses too. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
