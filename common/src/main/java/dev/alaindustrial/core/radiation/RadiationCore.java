package dev.alaindustrial.core.radiation;

/**
 * The whole arithmetic of radiation exposure (MOD-470), deliberately free of any Minecraft type so it
 * can be unit-tested without a game — the same split {@code FuelRodMath} and {@code ReactorCore} use.
 *
 * <p><b>Dose is a number of ticks.</b> It is stored as the remaining duration of the
 * {@code alaindustrial:radiation} effect, which means vanilla persists it with the player, syncs it to
 * the client and shows it in the HUD for free, on both loaders, with no player-data of our own. It
 * also means the decay rate is fixed and honest: one tick of dose bleeds off per tick, so a player who
 * walks away from a source recovers over exactly as long as the scale is deep.
 */
public final class RadiationCore {

	/** Below this share of the scale there is no effect at all — a trace dose is not a symptom. */
	public static final int LEVEL_1_PERCENT = 1;
	public static final int LEVEL_2_PERCENT = 25;
	public static final int LEVEL_3_PERCENT = 60;
	public static final int LEVEL_4_PERCENT = 90;

	private RadiationCore() {
	}

	/**
	 * Severity of a dose, 0 (clean) to 4 (lethal). Derived from the dose itself rather than stored
	 * alongside it, so the two can never disagree.
	 */
	public static int level(int dose, int capacity) {
		if (dose <= 0 || capacity <= 0) {
			return 0;
		}
		int percent = (int) ((long) dose * 100L / capacity);
		if (percent >= LEVEL_4_PERCENT) {
			return 4;
		}
		if (percent >= LEVEL_3_PERCENT) {
			return 3;
		}
		if (percent >= LEVEL_2_PERCENT) {
			return 2;
		}
		return percent >= LEVEL_1_PERCENT ? 1 : 0;
	}

	/** Add exposure to a standing dose, never past the top of the scale. */
	public static int addDose(int current, int added, int capacity) {
		if (added <= 0) {
			return Math.max(0, current);
		}
		long sum = (long) Math.max(0, current) + added;
		return (int) Math.min(sum, Math.max(0, capacity));
	}

	/**
	 * What a dose costs after the suit takes its share.
	 *
	 * <p>Each worn piece cuts the same percentage, and the total is capped: {@code maxPercent} is 100
	 * for ordinary background but deliberately below it for the raw core, so the full suit buys
	 * working time inside a live reactor rather than immunity to it.
	 */
	public static int shielded(int raw, int wornPieces, int perPiecePercent, int maxPercent) {
		if (raw <= 0) {
			return 0;
		}
		int pieces = Math.clamp(wornPieces, 0, 4);
		int cut = Math.clamp(pieces * Math.max(0, perPiecePercent), 0, Math.clamp(maxPercent, 0, 100));
		return (int) Math.max(0L, (long) raw * (100L - cut) / 100L);
	}

	/**
	 * Half-strength distance, in blocks: at this range a source delivers half of what it does in your
	 * face. Small on purpose — radiation should reward backing off by a couple of steps, not by
	 * a couple of chunks.
	 */
	private static final double HALF_STRENGTH_DISTANCE = 1.5;

	/**
	 * Strength of a point source at a distance, dropping off with the square of it and cut to zero at
	 * the radius.
	 *
	 * <p>The first version had no falloff at all: a rod six blocks away hit exactly as hard as one at
	 * your feet, so "stand back" was not a tactic and only a wall could save you. Inverse-square is both
	 * what radiation actually does and the rule that makes a reactor room readable — the danger is at
	 * the core, the corridor is survivable.
	 */
	public static int attenuate(int strength, double distance, int radius) {
		if (strength <= 0 || radius <= 0 || distance >= radius) {
			return 0;
		}
		double d = Math.max(0.0, distance);
		double half = HALF_STRENGTH_DISTANCE * HALF_STRENGTH_DISTANCE;
		return (int) Math.round(strength * half / (half + d * d));
	}

	/**
	 * Sweeps between the durability points a shielding suit spends, given how much dose it stopped in
	 * one sweep. Zero means it is not wearing at all.
	 *
	 * <p><b>Contact costs, and the cost is proportional to the contact.</b> The first version wore the
	 * suit only when a single sweep absorbed more than {@code perPoint}, so a reactor destroyed it and
	 * carrying uranium in the pockets did not touch it — the suit was free exactly where a player uses
	 * it most. Turning the ratio into an INTERVAL keeps both ends sane without any per-player
	 * bookkeeping: a fierce source is one point per sweep, a weak one is one point every few, and the
	 * arithmetic is the same in both cases.
	 *
	 * @param absorbed dose the suit stopped this sweep
	 * @param perPoint dose worth one point of durability
	 * @return sweeps between points, or 0 if nothing was absorbed
	 */
	public static int wearInterval(int absorbed, int perPoint) {
		if (absorbed <= 0) {
			return 0;
		}
		return Math.max(1, Math.max(1, perPoint) / absorbed);
	}

	/**
	 * The ceiling a capped source may push a dose to.
	 *
	 * <p>Raw ore and dust sit under this: they make a player queasy and never worse, because a player
	 * mining uranium has not yet been told radiation exists. Refined uranium has no such cap — it is
	 * handed out after the reactor line, and it kills.
	 */
	public static int cappedCeiling(int capacity, int capPercent) {
		return (int) ((long) Math.max(0, capacity) * Math.clamp(capPercent, 0, 100) / 100L);
	}

	/**
	 * Exposure a capped source may still add, given where the dose already stands. A source under its
	 * ceiling contributes nothing once something else has pushed the player past it — it may not pull
	 * the dose down either, which is why this returns 0 rather than a negative number.
	 */
	public static int cappedContribution(int currentDose, int added, int ceiling) {
		if (added <= 0 || currentDose >= ceiling) {
			return 0;
		}
		return Math.min(added, ceiling - currentDose);
	}
}
