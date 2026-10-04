package dev.alaindustrial.core.machine;

/**
 * Why the assembler's planner refused an operation (MOD-714).
 *
 * <p>Minecraft-free on purpose, so the planner can say why without naming the block entity's own
 * {@code AssemblerStatus}: that enum turns a refusal into the status the player sees with one exhaustive
 * {@code switch} ({@code AssemblerStatus.of}), and {@code core} does not import {@code block}.
 */
public enum AssemblyRefusal {
	/** The blueprint records no pattern. */
	NO_BLUEPRINT,
	/** The warehouse (or its absence) cannot supply the ingredients, or a stand-in does not hold. */
	NO_MATERIALS,
	/** The recorded pattern resolves to no recipe, or the recipe makes nothing from the reserved stacks. */
	NO_RECIPE,
	/** The result, or one of its craft remainders, has nowhere to go. */
	OUTPUT_FULL
}
