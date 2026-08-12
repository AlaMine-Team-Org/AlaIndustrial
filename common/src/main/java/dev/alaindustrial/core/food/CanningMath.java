package dev.alaindustrial.core.food;

/**
 * Arithmetic of the Canning Machine (MOD-383), deliberately free of Minecraft types so the L1 lane
 * can cover it without a game runtime.
 *
 * <p><b>What "food value" is and why it is not just nutrition.</b> The machine melts any food down to
 * a single number and pours it back out as identical rations. If that number were nutrition alone,
 * poor food would come out ahead: five sweet berries carry 10 nutrition but only 2.0 saturation,
 * while the ration they buy carries far more saturation per nutrition — the machine would be an
 * upgrade press for junk. Summing both axes closes that: value = nutrition + saturation.
 *
 * <p><b>What is conserved, and what provably cannot be.</b> Total value strictly drops, because
 * {@link #RATION_VALUE} is below the threshold a ration costs. The <em>split</em> between the two
 * axes is not conserved and cannot be: inputs have wildly different nutrition:saturation ratios and
 * the ration's ratio is fixed, so for some inputs one axis must rise. Measured on vanilla food, a
 * ration at saturation modifier 0.3 let junk food gain 1.80x on saturation; at 0.1 that becomes
 * 0.80x, and the residual gain moves to nutrition for delicacies like the golden carrot (2.27x),
 * which lose almost all their saturation in exchange and are never worth canning. The invariant the
 * tests assert is therefore the total, not either axis.
 *
 * <p>Values are carried in <b>tenths</b> so saturation's decimals survive integer storage and the
 * 16-bit {@code DataSlot} GUI sync.
 */
public final class CanningMath {
	/** Food value is stored in tenths of a point. */
	public static final int VALUE_SCALE = 10;

	/**
	 * Ceiling on one item's contribution. A foreign mod may declare any nutrition it likes; without a
	 * clamp a single absurd item would overflow the buffer and the 16-bit GUI sync behind it.
	 */
	public static final int MAX_ITEM_VALUE = 5_000;

	/** Ceiling on the buffer itself, kept well under {@link Short#MAX_VALUE} for the same reason. */
	public static final int MAX_BUFFER = 30_000;

	/** Hunger the ration restores — the same as a steak. */
	public static final int RATION_NUTRITION = 8;

	/**
	 * Saturation modifier, deliberately the vanilla "poor" grade: the ration gives 1.6 saturation
	 * against a steak's 12.8. It fills the bar as fast and empties again far sooner, which is both the
	 * flavour (industrial food fills you, it does not feed you) and the reason junk food cannot be
	 * laundered into better food here.
	 */
	public static final float RATION_SATURATION_MODIFIER = 0.1f;

	/**
	 * How long a ration takes to eat, against vanilla's 1.6 seconds. Still quicker than real food —
	 * that speed is one of the conveniences the player buys — but no longer instant: at 0.4s the
	 * animation was over before it read as anything, so the act of eating had no visual weight at all.
	 */
	public static final float RATION_CONSUME_SECONDS = 1.0f;

	/** The ration's own value in tenths — derived, so it can never drift from the two above. */
	public static final int RATION_VALUE =
			foodValue(RATION_NUTRITION, RATION_NUTRITION * RATION_SATURATION_MODIFIER * 2.0f);

	private CanningMath() {
	}

	/**
	 * Food value of one item in tenths, or {@code 0} if it carries none worth taking.
	 *
	 * <p>Zero nutrition is rejected rather than accepted-as-zero on purpose: a food that adds nothing
	 * to the buffer would be swallowed forever without ever producing a ration — the machine would
	 * quietly void a hopper feed. Negative saturation is clamped away for the same class of reason.
	 */
	public static int foodValue(int nutrition, float saturation) {
		if (nutrition <= 0) {
			return 0;
		}
		float total = nutrition + Math.max(0.0f, saturation);
		int scaled = Math.round(total * VALUE_SCALE);
		return Math.max(0, Math.min(MAX_ITEM_VALUE, scaled));
	}

	/** Add one item's value to the buffer, saturating at {@link #MAX_BUFFER}. */
	public static int addToBuffer(int buffer, int itemValue) {
		if (itemValue <= 0) {
			return buffer;
		}
		long sum = (long) buffer + itemValue;
		return (int) Math.min(MAX_BUFFER, Math.max(0L, sum));
	}

	/** Whether the machine still needs calories before it can press the next ration. */
	public static boolean wantsMoreFood(int buffer, int valuePerRation) {
		return buffer < valuePerRation;
	}

	/** Whether the buffer holds enough for one ration. */
	public static boolean hasFullRation(int buffer, int valuePerRation) {
		return valuePerRation > 0 && buffer >= valuePerRation;
	}

	/** Buffer left after pressing one ration. */
	public static int consumeRation(int buffer, int valuePerRation) {
		return Math.max(0, buffer - valuePerRation);
	}

	/**
	 * Whether the configured exchange rate actually loses value, which is what makes duplication
	 * impossible. Guards the balance knob rather than trusting it: a threshold at or below
	 * {@link #RATION_VALUE} would turn the machine into a food multiplier, and feeding rations back
	 * into it would be the loop.
	 */
	public static boolean isLossy(int valuePerRation) {
		return valuePerRation > RATION_VALUE;
	}

	/** How many whole rations a given amount of raw food value buys. Used by the recipe viewers. */
	public static int rationsFrom(int foodValue, int valuePerRation) {
		return valuePerRation <= 0 ? 0 : foodValue / valuePerRation;
	}
}
