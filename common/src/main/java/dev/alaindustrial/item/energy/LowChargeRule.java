package dev.alaindustrial.item.energy;

/**
 * When a powered item warns its owner that the charge is running out (MOD-652). Pure arithmetic with no game
 * types, so the rules are pinned by a plain JUnit test rather than by flying a jetpack empty.
 *
 * <p>Fed once per debit with the charge before and after it, it answers one of three things:
 * <ul>
 *   <li><b>{@link #LOW} on the crossing.</b> The debit took the charge from "not below the threshold" to
 *   "below it" — the warning every item gets, once, at the moment it matters.</li>
 *   <li><b>{@link #LOW} on a repeat.</b> The item was already below the threshold and is still being spent,
 *   and the last warning is at least {@code repeatTicks} old. This is what makes a tool taken from a chest at
 *   5% warn at all (it never crosses anything) and what makes the jetpack beep every second while it burns its
 *   last few seconds of thrust. A repeat never fires on an empty item: nothing is left to spend.</li>
 *   <li><b>{@link #DEPLETED} when the charge no longer pays for one more action</b> — the debit took it from
 *   "at least {@code depletedBelow}" to "less than that". A tool refuses an action it cannot afford
 *   ({@code ElectricMiningTool#spendIfAffordable}), so its charge never reaches zero: it stops at a remainder
 *   smaller than the price of its main action, and that remainder is exactly where it stops working as an
 *   electric tool. So for a tool {@code depletedBelow} is that price; for the jetpack, which burns to a clean
 *   zero, it is 1. One signal per crossing: once below, nothing spends again until it is charged back above the
 *   price. An item with {@code depletedBelow <= 0} has no such signal.</li>
 * </ul>
 * A credit (charging, a refund) never warns, and neither does a debit that stays above the threshold.
 */
public final class LowChargeRule {

	/** Nothing to say. */
	public static final int NONE = 0;
	/** The charge is low. */
	public static final int LOW = 1;
	/** The charge just ran out. */
	public static final int DEPLETED = 2;

	/**
	 * The "never warned" time: far enough back that {@code now - NEVER} is always past any repeat period, and
	 * far enough from the long bounds that the subtraction cannot overflow.
	 */
	public static final long NEVER = Long.MIN_VALUE / 4;

	private LowChargeRule() {
	}

	/**
	 * Whether {@code charge} is below {@code percent} percent of {@code capacity}, strictly. A percentage over 100
	 * counts as 100; one below 1 counts as 1, so a hand-edited file cannot switch the rule into "always low".
	 */
	public static boolean isLow(long charge, long capacity, int percent) {
		int clamped = Math.max(1, Math.min(percent, 100));
		return charge * 100L < clamped * capacity;
	}

	/** The charge as a whole percent of the capacity, rounded down: 9.99% reads 9, never 10. */
	public static int percent(long charge, long capacity) {
		return capacity <= 0 ? 0 : (int) Math.max(0L, Math.min(100L, charge * 100L / capacity));
	}

	/**
	 * What the debit from {@code before} to {@code after} should tell the owner.
	 *
	 * @param depletedBelow the charge under which the item can no longer do its main action (a tool's price per
	 *        action, 1 for the jetpack); 0 or less means running out is not a signal of its own
	 * @param now the current game time in ticks
	 * @param lastWarn when this owner was last warned about this kind of item, or {@link #NEVER}
	 * @param repeatTicks the shortest gap between repeats; 0 or less turns repeats off
	 */
	public static int decide(long before, long after, long capacity, int percent, long depletedBelow,
			long now, long lastWarn, long repeatTicks) {
		if (capacity <= 0 || after >= before) {
			return NONE;
		}
		if (depletedBelow > 0 && before > 0 && after < depletedBelow
				&& (before >= depletedBelow || after <= 0)) {
			return DEPLETED;
		}
		if (!isLow(after, capacity, percent)) {
			return NONE;
		}
		if (!isLow(before, capacity, percent)) {
			return LOW;
		}
		if (after > 0 && repeatTicks > 0 && now - lastWarn >= repeatTicks) {
			return LOW;
		}
		return NONE;
	}
}
