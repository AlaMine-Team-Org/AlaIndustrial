package dev.alaindustrial.core.environment;

/**
 * The lightning rod generator's arithmetic (MOD-386) — capacitor size, bleed rate, how much of a
 * strike actually lands, and how often a strike is rolled.
 *
 * <p><b>Deliberately free of Minecraft types</b>, like {@link WindMillOutput} and its siblings, so
 * the L1 unit suite can assert every rule without the game on the classpath — and so PIT mutates it
 * (a class under {@code dev.alaindustrial.core.*} is inside the pitest target glob automatically).
 * Everything here is a pure function of its arguments; the block entity owns the world state.
 *
 * <p><b>The grade multiplier is applied INSIDE these methods, before their clamp</b> — the same
 * contract {@code ComponentTier} establishes for the rotor and the wheel, and for the same reason:
 * applying it to a value that had already been clamped would make "a better tip must not raise the
 * ceiling" a property of today's numbers rather than of the code. Fed in first, the ceiling holds
 * for ANY multiplier an operator can configure.
 */
public final class LightningRodOutput {
	private LightningRodOutput() {
	}

	/**
	 * Capacitor size of a conductor tip: {@code baseCapacity × gradeMultiplier}, clamped to
	 * {@code maxCapacity}. Never negative, and never zero for a positive base — a tip whose grade
	 * multiplier was configured to 0 would otherwise read as "installed but useless", which looks
	 * exactly like a bug from inside the game.
	 */
	public static int capacityFor(int baseCapacity, float gradeMultiplier, int maxCapacity) {
		return scaledAndClamped(baseCapacity, gradeMultiplier, maxCapacity);
	}

	/**
	 * EU/t a conductor tip bleeds from its capacitor into the block's buffer:
	 * {@code baseBleed × gradeMultiplier}, clamped to {@code maxBleed} (the LV tier voltage).
	 */
	public static int bleedFor(int baseBleed, float gradeMultiplier, int maxBleed) {
		return scaledAndClamped(baseBleed, gradeMultiplier, maxBleed);
	}

	private static int scaledAndClamped(int base, float gradeMultiplier, int ceiling) {
		if (base <= 0 || ceiling <= 0) {
			return 0;
		}
		int scaled = Math.max(1, Math.round(base * Math.max(0f, gradeMultiplier)));
		return Math.min(scaled, ceiling);
	}

	/**
	 * How much of a {@code strikeEu} strike a capacitor with {@code freeRoom} left actually banks.
	 * The remainder is <b>lost</b>, not queued — that loss is the whole "discharge before the storm"
	 * game, so it is expressed here as one obvious {@code min} rather than hidden in a caller.
	 */
	public static int accepted(int strikeEu, int freeRoom) {
		if (strikeEu <= 0 || freeRoom <= 0) {
			return 0;
		}
		return Math.min(strikeEu, freeRoom);
	}

	/**
	 * The one-in-N chance divisor to roll this tick, or {@code 0} when no strike is possible.
	 *
	 * <p>Thunder outranks rain because a thunderstorm is also raining — {@code Level#isThundering()}
	 * and {@code isRaining()} are both true then, and testing rain first would silently give a storm
	 * the rain branch's much rarer odds.
	 *
	 * <p>The rain branch has no vanilla counterpart at all: Minecraft only ever spawns lightning
	 * during a thunderstorm. It exists so the rod earns something through ordinary rain instead of
	 * standing dead between storms — see the OKF spec.
	 */
	public static int strikeDivisor(boolean thundering, boolean raining,
			int thunderDivisor, int rainDivisor) {
		if (thundering) {
			return Math.max(0, thunderDivisor);
		}
		if (raining) {
			return Math.max(0, rainDivisor);
		}
		return 0;
	}
}
