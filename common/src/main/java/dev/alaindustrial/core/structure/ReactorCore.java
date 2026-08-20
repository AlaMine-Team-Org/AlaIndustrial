package dev.alaindustrial.core.structure;

/**
 * The reactor's arithmetic (MOD-468, stage 2) — output, heat and the neighbour bonus, with no
 * Minecraft type in sight so the balance can be exercised by L1 tests rather than by playing.
 *
 * <p><b>Output and heat come from the same number.</b> Every knob that raises power raises heat by the
 * same factor: more rods, tighter packing, deeper insertion. That is the whole design — there is no
 * setting that is simply better, only a choice of how close to the edge to run.
 *
 * <p><b>Depth is a throttle, not a discount.</b> Half depth means half the output, half the heat and
 * half the fuel burnt. Running a reactor gently stretches the uranium instead of wasting it, so the
 * player is never punished for being careful.
 */
public final class ReactorCore {

	private ReactorCore() {
	}

	/** Depth is carried in thousandths so a slider can be precise without floating point. */
	public static final int FULL_DEPTH = 1000;

	/**
	 * EU per tick a reactor produces.
	 *
	 * @param rods            total rods currently racked and burning
	 * @param neighbourPairs  how many adjacent loaded-assembly pairs exist in the room; each pair is
	 *                        counted once, and both racks in it get the bonus
	 * @param euPerRod        base output of one rod at full depth
	 * @param bonusPercent    extra output per adjacency, in percent
	 * @param depthPermille   0…{@link #FULL_DEPTH}
	 */
	public static long output(int rods, int neighbourPairs, int euPerRod, int bonusPercent,
			int depthPermille) {
		if (rods <= 0 || depthPermille <= 0) {
			return 0;
		}
		long base = (long) rods * euPerRod;
		// Each adjacency benefits both of its racks, hence x2 — the reason a tight block of assemblies
		// is worth so much more than the same rods spread out.
		long bonus = base * 2L * neighbourPairs * bonusPercent / 100L;
		return (base + bonus) * clampDepth(depthPermille) / FULL_DEPTH;
	}

	/**
	 * Heat produced this tick, before cooling. Same shape as {@link #output}, but the caller passes a
	 * <em>larger</em> {@code bonusPercent} — see {@code Config.reactorHeatNeighbourBonusPercent}. An
	 * arrangement that doubles the power more than doubles the heat, so density is a trade rather than
	 * a free win: a packed core reaches the tier ceiling on shallower rods, and pays for it in water.
	 */
	public static long heatProduced(int rods, int neighbourPairs, int heatPerRod, int bonusPercent,
			int depthPermille) {
		if (rods <= 0 || depthPermille <= 0) {
			return 0;
		}
		long base = (long) rods * heatPerRod;
		long bonus = base * 2L * neighbourPairs * bonusPercent / 100L;
		return (base + bonus) * clampDepth(depthPermille) / FULL_DEPTH;
	}

	/**
	 * Heat the shell sheds this tick on its own: a flat floor plus a share of the current temperature.
	 *
	 * <p>The proportional term is the whole point. A flat loss gives a reactor exactly two states —
	 * below it the gauge never leaves zero, above it the gauge climbs to the top and stays — and the
	 * first of those is what a player building their first reactor sees. With loss rising alongside
	 * temperature every core settles where production and losses meet, and the gauge becomes a reading
	 * of how hard this particular reactor is working.
	 */
	public static long naturalCooling(long heat, int passiveCooling, int lossPermille) {
		long base = Math.max(0, passiveCooling);
		long proportional = heat <= 0 || lossPermille <= 0 ? 0 : heat * lossPermille / 1000;
		return base + proportional;
	}

	/**
	 * Heat after one tick of production and cooling, clamped to the scale.
	 *
	 * <p>Cooling applies whether or not the reactor is running — a shut-down core still sheds heat,
	 * which is what makes "scram and wait" a real recovery rather than a permanent loss.
	 */
	public static long settleHeat(long heat, long produced, long cooling, int capacity) {
		long next = heat + produced - Math.max(0, cooling);
		if (next < 0) {
			return 0;
		}
		return Math.min(next, Math.max(0, capacity));
	}

	/** Heat as a percentage of the scale — what the gauge and the status line read. */
	public static int heatPercent(long heat, int capacity) {
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.min(100, Math.max(0, heat * 100 / capacity));
	}

	/**
	 * Total energy in one uranium rod. A rod is a quantity of EU, and packing columns together changes
	 * how FAST it comes out, never how much there is.
	 *
	 * <p>That framing replaced a depth-scaled one that did not survive scale. Fuel and heat used to be
	 * driven by an "effective depth" — the fraction of its potential the core was actually being asked
	 * for — carried as an integer per mille. On a core big enough to overshoot the tier ceiling a
	 * hundredfold that fraction truncated to its 1‰ floor, and two absurdities followed from the same
	 * line: a rod lasted about a thousand real days, and heat became {@code fullHeat / 1000}, which
	 * grows without bound as the room grows. A 12-block room came out at roughly 105 000 heat a tick —
	 * more coolant than the shell has surface to admit.
	 */
	public static long rodEnergy(int euPerRod, int burnTicks) {
		return Math.max(0L, (long) euPerRod * Math.max(0, burnTicks));
	}

	/**
	 * Heat for the energy actually produced.
	 *
	 * <p>One division at the end, against the potential at full depth. Because both terms scale with
	 * the same rods and the same adjacencies, the ratio converges as a core grows instead of diverging:
	 * heat per EU approaches {@code heatPerRod x heatBonus / (euPerRod x energyBonus)}, so a room at the
	 * tier ceiling settles near a fixed temperature however many columns are packed into it.
	 */
	public static long heatForOutput(long heatAtFullDepth, long output, long outputAtFullDepth) {
		if (heatAtFullDepth <= 0 || output <= 0 || outputAtFullDepth <= 0) {
			return 0;
		}
		return heatAtFullDepth * output / outputAtFullDepth;
	}

	/**
	 * Water needed to carry {@code heat} away, in mB, rounded UP.
	 *
	 * <p>Rounding up rather than down is what stops a trickle of heat from being cooled for free: the
	 * truncating version returned 0 for anything below the exchange rate, and a reactor idling just
	 * under it would have held its temperature on no water at all.
	 */
	public static long waterForHeat(long heat, int heatPerWater) {
		if (heat <= 0 || heatPerWater <= 0) {
			return 0;
		}
		return (heat + heatPerWater - 1) / heatPerWater;
	}

	/** The inverse: heat that {@code water} mB actually carries away once it boils. */
	public static long heatRemovedByWater(long water, int heatPerWater) {
		return water <= 0 || heatPerWater <= 0 ? 0 : water * heatPerWater;
	}

	private static int clampDepth(int depthPermille) {
		return Math.min(FULL_DEPTH, Math.max(0, depthPermille));
	}
}
