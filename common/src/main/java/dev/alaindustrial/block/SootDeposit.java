package dev.alaindustrial.block;

/**
 * The soot roll of a burnt-out oil fire (MOD-638), kept free of Minecraft types so the one decision
 * in it is testable on the L1 lane. The draw comes in from the caller's {@code RandomSource}; nothing
 * here holds randomness of its own.
 */
public final class SootDeposit {
	private SootDeposit() {
	}

	/**
	 * Does this burnout leave soot? {@code chance} is {@code Config.oilSootChance} (0..1; anything at or
	 * above 1 always deposits, at or below 0 never), {@code draw} a uniform value in {@code [0, 1)}.
	 */
	public static boolean deposits(double chance, double draw) {
		return draw < chance;
	}
}
