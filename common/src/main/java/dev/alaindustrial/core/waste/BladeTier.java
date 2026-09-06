package dev.alaindustrial.core.waste;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.ItemStack;

/**
 * The three grades of Recycler blade (MOD-145) — the machine's upkeep, and a choice rather than a tax.
 *
 * <p>Without a blade the Recycler does not run at all, the way a wind mill without a rotor produces
 * nothing. What the grade buys is time and less housekeeping: a better set chews faster, stops choking on
 * scrap metal, and leaves less ash to shovel out. That is the shape recommended by every design review of
 * upkeep mechanics — the player who invests should service the farm LESS often, not merely differently.
 */
public enum BladeTier {
	/** Iron: cheap, slow, and it hates scrap metal. */
	IRON(1.0f, 2.0f, 4),
	/** Tempered iron: the everyday set — no penalty on any fraction. */
	TEMPERED(0.70f, 1.0f, 3),
	/** Diamond-edged: fastest, and grinds fine enough to leave the least ash. */
	DIAMOND(0.50f, 1.0f, 2);

	private final float durationFactor;
	private final float metalPenalty;
	private final int ashPerBatch;

	BladeTier(float durationFactor, float metalPenalty, int ashPerBatch) {
		this.durationFactor = durationFactor;
		this.metalPenalty = metalPenalty;
		this.ashPerBatch = ashPerBatch;
	}

	/** How long one item takes with this blade, relative to the configured base duration. */
	public float durationFactor() {
		return durationFactor;
	}

	/** Extra time this blade needs for the metal fraction — 1.0 means no penalty. */
	public float metalPenalty() {
		return metalPenalty;
	}

	/** Ash left behind by one finished batch. */
	public int ashPerBatch() {
		return ashPerBatch;
	}

	/** Ticks this blade needs for one item of the given fraction. */
	public int durationFor(int baseTicks, WasteFraction fraction) {
		float factor = durationFactor * (fraction == WasteFraction.METAL ? metalPenalty : 1.0f);
		return Math.max(1, Math.round(baseTicks * factor));
	}

	/** The grade of the blade in this stack, or {@code null} if it is not a blade at all. */
	public static BladeTier of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		if (stack.is(ModContent.RECYCLER_BLADES_IRON.get())) {
			return IRON;
		}
		if (stack.is(ModContent.RECYCLER_BLADES_TEMPERED.get())) {
			return TEMPERED;
		}
		if (stack.is(ModContent.RECYCLER_BLADES_DIAMOND.get())) {
			return DIAMOND;
		}
		return null;
	}
}
