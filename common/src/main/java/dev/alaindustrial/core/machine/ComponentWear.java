package dev.alaindustrial.core.machine;

/**
 * Pure wear arithmetic for a machine's consumable component (MOD-189): the wind mill's rotor and the
 * water mill's wheel wear out proportionally to the EU they help produce. Kept free of any Minecraft
 * type so it links and runs on the L1 (plain-JUnit) classpath — the caller
 * ({@code AbstractGeneratorBlockEntity#wearComponent}) does the {@code ItemStack}/sound side effects.
 *
 * <p><b>Model.</b> Each active tick (the generator actually produced {@code producedEu > 0}) the caller
 * adds {@code producedEu × weatherFactor} EU to a running accumulator. Every whole {@code euPerDamage}
 * of accumulated EU converts into one durability point of damage; leftover EU stays in the accumulator
 * for the next tick. When the damage reaches {@code maxDamage} the component breaks. So a component's
 * total life is {@code maxDamage × euPerDamage} EU of production — a higher-output generator (a tall
 * high-altitude mill, a storm mill in a thunderstorm, a wide water channel) wears its component out in
 * fewer real ticks, exactly as intended.
 */
public final class ComponentWear {
	private ComponentWear() {
	}

	/**
	 * Outcome of one active wear tick.
	 *
	 * @param accumulatorEu leftover sub-point EU to carry into the next tick (0 when the component broke)
	 * @param newDamage the component's damage value after this tick (clamped to {@code maxDamage} on break)
	 * @param broken whether the component reached {@code maxDamage} and should be removed this tick
	 */
	public record Result(int accumulatorEu, int newDamage, boolean broken) {
	}

	/**
	 * Advance wear by one active tick. The caller must only invoke this when the generator produced EU
	 * this tick ({@code producedEu > 0}).
	 *
	 * @param accumulatorEu carried sub-point EU from previous ticks (>= 0)
	 * @param producedEu EU the generator produced this tick (> 0)
	 * @param weatherFactor extra wear multiplier for adverse weather (>= 1.0; 1.0 = no bonus wear)
	 * @param euPerDamage EU of production per one durability point (> 0)
	 * @param currentDamage the component's current damage value (0 = pristine)
	 * @param maxDamage the component's max durability (> 0)
	 * @return the new accumulator, new damage, and whether the component broke this tick
	 */
	public static Result step(int accumulatorEu, int producedEu, float weatherFactor,
			int euPerDamage, int currentDamage, int maxDamage) {
		// At least 1 EU of wear per active tick so wear never stalls on tiny outputs (e.g. a 1 EU/t
		// water wheel), and the weather multiplier can only add on top of that floor.
		int added = Math.max(1, Math.round(producedEu * weatherFactor));
		int acc = accumulatorEu + added;
		int points = acc / euPerDamage;
		acc -= points * euPerDamage;
		int newDamage = currentDamage + points;
		if (newDamage >= maxDamage) {
			return new Result(0, maxDamage, true);
		}
		return new Result(acc, newDamage, false);
	}
}
