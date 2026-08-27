package dev.alaindustrial.core.crystal;

/**
 * MC-free arithmetic of crystal growth (MOD-505) — how likely a seedbed is to advance on one growth
 * attempt, given what the room around it offers.
 *
 * <p>No Minecraft type is referenced here for the same reason {@link
 * dev.alaindustrial.core.structure.RoomScan} avoids them: that is what makes it an L1 JUnit target
 * ({@code :common:test} runs Minecraft-free, so a class touching {@code net.minecraft..} cannot be
 * exercised there).
 *
 * <p><b>Chance divisors, not timers.</b> Growth is a die roll per attempt, the same idiom the cotton
 * trellis uses ({@code cottonRootingChanceDivisor}): a stage takes "roughly this long" rather than
 * exactly. That is deliberate — it is how vanilla budding amethyst feels, and it means a player who
 * walks past a farm gets the small surprise of finding a crystal further along than they left it,
 * instead of reading a countdown.
 *
 * <p><b>Boosts divide, they do not subtract.</b> Water and power each cut the divisor by their own
 * factor, so they compose multiplicatively and neither can ever be made irrelevant by the other
 * being present. Subtracting would let a large enough single boost floor the divisor and make the
 * second one worthless.
 */
public final class CrystalGrowth {

	private CrystalGrowth() {
	}

	/**
	 * How many growth events a seedbed owes before one crystal is ready to harvest: the bud has to
	 * appear at all, and then climb from its first stage to {@link #RIPE_AGE}. Used to turn a
	 * configured divisor into an expected wall-clock time (and by the tests to check that sum).
	 */
	public static final int EVENTS_PER_CRYSTAL = 4;

	/** Age of a bud that may be harvested; anything below this drops nothing. */
	public static final int RIPE_AGE = 3;

	/**
	 * Shards a ripe cluster hands back to a pickaxe.
	 *
	 * <p>Vanilla's number, not the mod's — read out of {@code loot_table/blocks/amethyst_cluster.json}
	 * in the game jar, where the {@code #minecraft:cluster_max_harvestables} branch sets a count of 4
	 * and applies Fortune on top. It lives here because the farm's whole economy is this figure times
	 * {@code crystalSeedbedChargesPerShard}, and that sum deserves to be checkable rather than
	 * folklore.
	 */
	public static final int SHARDS_PER_RIPE_CRYSTAL = 4;

	/**
	 * The 1-in-N chance of one growth event, after the room's boosts are applied.
	 *
	 * <p>Clamped to at least 1 before it can ever reach {@code RandomSource.nextInt}: a config file
	 * holding {@code 0} would otherwise crash the chunk that ticks the farm — the same shape of bug a
	 * zero divisor caused in MOD-169. The speed-ups are clamped from below for the same reason: a
	 * {@code 0} there would divide by zero outright, and a negative one would silently invert the
	 * boost into a penalty.
	 *
	 * @param baseDivisor  the unboosted 1-in-N chance
	 * @param water        whether the room holds water
	 * @param powered      whether the controller could pay the energy cost this attempt
	 * @param waterSpeedup factor the divisor is cut by when the room holds water
	 * @param powerSpeedup factor the divisor is cut by when the attempt was powered
	 */
	public static int effectiveChanceDivisor(int baseDivisor, boolean water, boolean powered,
			int waterSpeedup, int powerSpeedup) {
		int divisor = Math.max(1, baseDivisor);
		if (water) {
			divisor /= Math.max(1, waterSpeedup);
		}
		if (powered) {
			divisor /= Math.max(1, powerSpeedup);
		}
		return Math.max(1, divisor);
	}

	/**
	 * Expected seconds to grow one crystal end to end, for a given divisor and attempt cadence.
	 *
	 * <p>Exists so the balance target ("an unhelped farm should take an hour or two") is checkable in
	 * a test rather than being a number somebody once did on paper. A 1-in-N roll needs N attempts on
	 * average, and a crystal needs {@link #EVENTS_PER_CRYSTAL} of those.
	 */
	public static double expectedSecondsPerCrystal(int effectiveDivisor, int attemptIntervalTicks) {
		int divisor = Math.max(1, effectiveDivisor);
		int interval = Math.max(1, attemptIntervalTicks);
		return (double) EVENTS_PER_CRYSTAL * divisor * interval / 20.0;
	}
}
