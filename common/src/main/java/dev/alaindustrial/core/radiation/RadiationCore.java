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

	/** Hard ceiling on how long a Geiger reading may outlive its sweep — three seconds (MOD-475). */
	public static final long STALE_WINDOW_CEILING_TICKS = 60L;

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
	 * What one container in the world actually leaks (MOD-474): its contents, but never more than
	 * {@code maxItems} items' worth.
	 *
	 * <p><b>The cap is the difference between a hazard and a trap.</b> Leak strength used to be linear
	 * in the count, and a container holds up to 36 stacks: a chest of refined uranium delivered a full
	 * dose scale per sweep at every distance inside the radius, so it killed instantly rather than
	 * warning. Worse, it revived the death loop MOD-470 closed by arithmetic — that reasoning ("a stack
	 * needs about a minute to reach lethal, so there is time to run back and drink milk") only holds
	 * while the source needs a minute.
	 *
	 * <p>It is also the more honest physical model: the box attenuates, and the uranium in the middle
	 * of a pile is shielded by the uranium above it. Doubling what is already a heap does not double
	 * what escapes it.
	 *
	 * @param raw          dose per sweep the contents would radiate uncapped
	 * @param maxItems     items' worth allowed out; {@code 0} keeps containers silent entirely
	 * @param dosePerItem  what one item of the strongest tag is worth
	 */
	public static int containerLeak(int raw, int maxItems, int dosePerItem) {
		if (raw <= 0 || maxItems <= 0 || dosePerItem <= 0) {
			return 0;
		}
		long ceiling = (long) maxItems * dosePerItem;
		return (int) Math.min(raw, ceiling);
	}

	/**
	 * Step of the Geiger counter's click rate for a field, 0 (silent) to 4 (off the scale) — MOD-475.
	 *
	 * <p><b>The counter is deliberately more sensitive than harm.</b> Dose only accumulates while a
	 * field beats the decay rate ({@code radiationTickInterval}); anything weaker bleeds off as fast as
	 * it arrives. A counter that stayed silent below that line would be deaf to exactly the thing a
	 * player wants it for — ore in the rock, which is under the line by design. So step 1 means "there
	 * is something here", not "you are in danger", and silence means "there is nothing at all".
	 *
	 * <p><b>The top step is a ceiling, not a failure.</b> Above {@code offScale} the instrument stops
	 * telling levels apart and just rattles. A cheap tool that saturates is honest — it does not lie
	 * about the reading, it admits it has run out of scale, which is itself the signal to leave and to
	 * build the dosimeter (MOD-567).
	 *
	 * <p>Thresholds are passed in rather than read from {@code Config} so this stays a pure function:
	 * the same reason the rest of this class has no Minecraft types.
	 */
	public static int geigerStep(int field, int faint, int busy, int loud, int offScale) {
		if (field < Math.max(1, faint)) {
			return 0;
		}
		if (field < busy) {
			return 1;
		}
		if (field < loud) {
			return 2;
		}
		return field < offScale ? 3 : 4;
	}



	/**
	 * Ore grade from how far away the nearest uranium ore is — 0 (nothing in range) to 3 (right here).
	 *
	 * <p><b>Distance, not an attenuated field.</b> The field halves within a block and a half, so a
	 * grade read off it collapsed onto its lowest rung about two blocks from a vein: a player standing
	 * on the ore heard one click every two seconds, which is what a player standing on nothing at all
	 * would have heard. Distance splits the radius into three even bands, is monotone as the player
	 * walks — the whole point of hunting by ear — and answers the question a miner is actually asking.
	 *
	 * <p>The bands are fractions of the radius rather than fixed numbers so that the ladder survives
	 * any configured reach: at the shipped 16 blocks they are 0–4, 4–8 and 8–16.
	 *
	 * @param distance blocks to the nearest ore, or negative when there is none in range
	 * @param radius   how far the counter hears ore at all
	 */
	public static int oreStep(double distance, int radius) {
		if (distance < 0.0 || radius <= 0 || distance > radius) {
			return 0;
		}
		if (distance <= radius / 4.0) {
			return 3;
		}
		return distance <= radius / 2.0 ? 2 : 1;
	}

	/**
	 * Has a Geiger reading gone stale — i.e. should the counter fall silent (MOD-475)?
	 *
	 * <p><b>This is a safety net for the sweep's early exits, not housekeeping.</b> The sweep that
	 * produces readings returns early when radiation is switched off in the config and when the player
	 * is in creative or spectator mode, and both exits happen BEFORE a reading could be cleared. Without
	 * an age, a player who flips to creative beside a reactor would hear the rattle forever, and so
	 * would every player in a world where an operator has just turned radiation off.
	 *
	 * <p>Two sweeps of slack, so one missed sweep does not stutter the sound. The window is derived from
	 * the sweep interval rather than fixed: the cadence is tunable, and a hard-coded twenty ticks would
	 * break silently the day somebody slows it down.
	 */
	public static boolean readingWentStale(long now, long takenAt, int sweepInterval) {
		// Capped as well as scaled. A server that slows the sweep to half a minute to save tick time
		// would otherwise get a full minute of phantom rattle after `/gamemode creative` or after
		// radiation is switched off — the window is a safety net, and a safety net measured in minutes
		// is the bug it exists to prevent.
		long window = Math.min(2L * Math.max(1, sweepInterval), STALE_WINDOW_CEILING_TICKS);
		return now - takenAt > window;
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
