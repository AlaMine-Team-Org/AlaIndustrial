package dev.alaindustrial.core.machine;

import dev.alaindustrial.core.upgrade.OverclockMath;

/**
 * The machine tariff formula, written once (MOD-710, CFG-6): how the global speed multiplier turns a
 * machine's base EU/t and base duration into what it really draws and how long it really takes.
 *
 * <p>The multiplier is energy-neutral by design: the draw is multiplied by it and the duration divided
 * by it, so the energy of one operation stays the same up to rounding. Each factor rounds on its own and
 * is never below 1 — a machine that draws nothing would run for free, a zero-length operation would
 * complete endlessly within one tick.
 *
 * <p>Inputs are passed in, never read here: the server passes its live knobs, the client the server's
 * numbers from {@code ServerBalance} (MOD-695), and both get the same answer. The per-machine overclocker
 * chips are NOT part of this formula — they are {@link OverclockMath} on top of it, applied by
 * {@code MachineBlockEntity.effectiveEuPerTick}/{@code effectiveDuration}; with no chips the two agree
 * ({@link #euPerTick} is {@link OverclockMath#euPerTick} with {@code chips = 0}). A block entity calling
 * these from its tick ignores its chips, which the bytecode rule
 * {@code ArchitectureRules.machinesUseOverclockHelpers} forbids outside a constructor.
 *
 * <p>Minecraft-free on purpose: pinned on a grid of multipliers by
 * {@code MachineRateFormulaCharacterizationTest}.
 */
public final class MachineRates {

	private MachineRates() {
	}

	/** The draw per working tick: {@code baseEuPerTick × speedMultiplier}, rounded, at least 1. */
	public static int euPerTick(int baseEuPerTick, float speedMultiplier) {
		return OverclockMath.euPerTick(baseEuPerTick, speedMultiplier, 1.0f, 0);
	}

	/**
	 * The operation length: {@code baseTicks ÷ speedMultiplier}, rounded, at least 1 tick — a faster
	 * server finishes in fewer ticks. The chips' {@link OverclockMath#duration} takes THIS value, never the
	 * base: dividing twice is how a retuned server ends up at the square of its configured speed.
	 */
	public static int duration(int baseTicks, float speedMultiplier) {
		return Math.max(1, Math.round(baseTicks / speedMultiplier));
	}

	/**
	 * The base length of one operation of a processing machine (the macerator family), before the speed
	 * multiplier and the chips: a recipe that states its EU runs {@code recipeEnergy ÷ baseEuPerTick}
	 * ticks, at least 1; one that states none ({@code recipeEnergy <= 0}, the electric furnace's vanilla
	 * fallback) runs the machine's {@code defaultDuration}. {@code AbstractProcessingMachineBlockEntity}
	 * ticks by it; it lives here, Minecraft-free, so L1 can hold the recipe viewers to the machine
	 * ({@code RecipeViewerCostTest}, MOD-743).
	 */
	public static int baseDuration(int recipeEnergy, int baseEuPerTick, int defaultDuration) {
		return recipeEnergy > 0 ? Math.max(1, recipeEnergy / baseEuPerTick) : defaultDuration;
	}

	/**
	 * EU one vanilla smelt costs in the electric furnace: its scaled duration times its scaled draw, each
	 * rounded on its own — exactly what the furnace ticks away, so the recipe-viewer mirrors (MOD-086) quote
	 * the number the machine spends. Multiplying the raw values would agree only at 1.0 (at x3 a 100-tick,
	 * 2 EU/t smelt costs round(100/3) × round(2×3) = 198 EU, not 200).
	 */
	public static int vanillaSmeltEu(int furnaceDuration, int baseEuPerTick, float speedMultiplier) {
		return Math.max(1, duration(furnaceDuration, speedMultiplier) * euPerTick(baseEuPerTick, speedMultiplier));
	}
}
