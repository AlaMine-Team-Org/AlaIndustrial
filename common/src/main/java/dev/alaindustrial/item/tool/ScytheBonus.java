package dev.alaindustrial.item.tool;

/**
 * The scythe's bonus-seed chance formula (MOD-315): the per-tier base scaled by the server's global
 * multiplier.
 *
 * <p><b>Why this is a class of its own.</b> The natural home for the formula was
 * {@link ScytheItem.Profile}, which already holds the tier's balance and is itself free of Minecraft
 * types — but it is nested inside {@code ScytheItem}, and {@code javac} cannot resolve a nested type
 * without reading its enclosing class. {@code ScytheItem extends Item}, so an L1 test referencing
 * {@code ScytheItem.Profile} fails to compile with {@code class file for
 * net.minecraft.world.item.Item not found}: {@code common}'s test source set has no Minecraft on its
 * classpath by design. Lifting the pure function to a top-level class is what makes the balance
 * contract unit-testable, the same shape as {@code TooltipKeys} and {@code TemperedIronToolStats}.
 */
public final class ScytheBonus {

	private ScytheBonus() {
	}

	/**
	 * The effective probability that one harvested crop yields an extra seed: the tier's shipped
	 * chance scaled by {@code multiplier} and clamped into {@code 0..1}.
	 *
	 * <p>The guard is written as {@code !(scaled > 0.0)} rather than {@code scaled <= 0.0} on
	 * purpose — that form also catches {@code NaN}, since every comparison with NaN is false. A
	 * hand-edited config can produce one, and an unnormalized NaN would flow into the roll and
	 * disable the mechanic silently instead of reading as a clean "off".
	 *
	 * @param tierChance the tier's shipped chance (0..1), from {@code ScytheItem.Profile}
	 * @param multiplier the server's global multiplier, {@code Config.scytheBonusSeedMultiplier}
	 * @return a probability in {@code 0..1}, never negative and never NaN
	 */
	public static double effectiveChance(float tierChance, double multiplier) {
		double scaled = tierChance * multiplier;
		if (!(scaled > 0.0)) {
			return 0.0;
		}
		return Math.min(scaled, 1.0);
	}
}
