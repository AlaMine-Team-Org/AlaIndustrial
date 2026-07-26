package dev.alaindustrial.mutation;

import java.util.function.DoubleSupplier;

/**
 * Pure dice logic of the incubator (MOD-118): the outcome roll and the rarity roll.
 *
 * <p>Deliberately free of any Minecraft type so it links and runs at L1 (see MOD-130's
 * TemperedGearRoll for the same pattern). The randomness is injected as a {@link DoubleSupplier},
 * which lets game tests force a specific outcome instead of gambling.
 *
 * <p>All probabilities are fractions in {@code [0, 1]}.
 */
public final class MutationRoll {

	private MutationRoll() {
	}

	/** What a single attempt produced. */
	public enum Outcome {
		/** The recipe result is produced and a rarity grade is rolled for it. */
		SUCCESS,
		/** The input is consumed and turns into irradiated slag. */
		SLAG,
		/** The input is consumed and nothing is produced. */
		FAILURE
	}

	/**
	 * Success chance of one attempt: the recipe base plus the gene bonus of the input item,
	 * clamped to {@code cap} so a mutation is never guaranteed.
	 */
	public static double effectiveChance(double baseChance, MutationGrade inputGrade, double cap) {
		double bonus = inputGrade == null ? 0.0 : inputGrade.geneBonus();
		double combined = clamp01(baseChance) + bonus;
		return Math.min(combined, clamp01(cap));
	}

	/**
	 * Rolls the outcome of one attempt.
	 *
	 * <p>Slag is carved out of the failure share, never out of the success share: the success
	 * chance stays exactly {@code successChance}, and slag only replaces part of what would
	 * otherwise be an empty miss. When {@code successChance + slagChance} exceeds 1.0 the slag
	 * share shrinks to whatever is left.
	 */
	public static Outcome rollOutcome(double successChance, double slagChance, DoubleSupplier rng) {
		double success = clamp01(successChance);
		double roll = rng.getAsDouble();
		if (roll < success) {
			return Outcome.SUCCESS;
		}
		double slagRoom = Math.max(0.0, 1.0 - success);
		double slag = Math.min(clamp01(slagChance), slagRoom);
		return roll < success + slag ? Outcome.SLAG : Outcome.FAILURE;
	}

	/**
	 * Rolls the rarity grade of a successful outcome. The shares are read from the top down
	 * (legendary, epic, rare); whatever is left over is {@link MutationGrade#COMMON}.
	 */
	public static MutationGrade rollGrade(double rareChance, double epicChance,
			double legendaryChance, DoubleSupplier rng) {
		double legendary = clamp01(legendaryChance);
		double epic = clamp01(epicChance);
		double rare = clamp01(rareChance);
		double roll = rng.getAsDouble();
		if (roll < legendary) {
			return MutationGrade.LEGENDARY;
		}
		if (roll < legendary + epic) {
			return MutationGrade.EPIC;
		}
		if (roll < legendary + epic + rare) {
			return MutationGrade.RARE;
		}
		return MutationGrade.COMMON;
	}

	private static double clamp01(double value) {
		if (Double.isNaN(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}
}
