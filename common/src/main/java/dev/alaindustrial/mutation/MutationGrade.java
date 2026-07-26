package dev.alaindustrial.mutation;

import java.util.Locale;

/**
 * Rarity grade rolled on every successful incubator mutation (MOD-118).
 *
 * <p>{@link #COMMON} is the absence of a grade: it is never written to an item stack, so ordinary
 * outcomes keep stacking with vanilla items. Only {@link #RARE} and above carry the
 * {@code alaindustrial:mutation_grade} data component.
 *
 * <p>Minecraft-free on purpose so the roll logic stays unit-testable at L1 (see MutationRoll).
 */
public enum MutationGrade {
	COMMON(0.0),
	RARE(0.05),
	EPIC(0.10),
	LEGENDARY(0.15);

	private static final MutationGrade[] VALUES = values();

	/** Bonus in percentage points this grade grants when the item is mutated again. */
	private final double geneBonus;

	MutationGrade(double geneBonus) {
		this.geneBonus = geneBonus;
	}

	public double geneBonus() {
		return geneBonus;
	}

	/** True when the grade leaves a trace on the item (component + vanilla rarity). */
	public boolean isMarked() {
		return this != COMMON;
	}

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "tooltip.alaindustrial.mutation_grade." + serializedName();
	}

	public static MutationGrade byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : COMMON;
	}

	public static MutationGrade byName(String name) {
		if (name != null) {
			for (MutationGrade grade : VALUES) {
				if (grade.serializedName().equals(name)) {
					return grade;
				}
			}
		}
		return COMMON;
	}
}
