package dev.alaindustrial.core.structure;

/**
 * How a uranium rod's charge maps onto the durability bar the player sees (MOD-468, stage 4).
 *
 * <p><b>Two scales, on purpose.</b> The reactor spends fuel in EU, because that is the only unit in
 * which a rod is worth the same wherever it stands — see {@link ReactorCore#rodEnergy}. The item shows
 * wear in damage points, because that is what a durability bar is. Converting straight from one to the
 * other every tick would round almost every tick down to nothing: a rod is hundreds of thousands of EU
 * and the bar has a thousand steps, so a tick's draw is a fraction of one step. The remainder therefore
 * has to be carried, and this class is where the carrying is defined and tested.
 *
 * <p>Minecraft-free so the whole fuel economy can be exercised by L1 tests rather than by burning
 * twenty real minutes of uranium in a dev client.
 */
public final class FuelRodMath {

	private FuelRodMath() {
	}

	/**
	 * Steps on the rod's durability bar.
	 *
	 * <p>A thousand rather than the rod's EU value: the bar is about sixteen pixels wide, so anything
	 * past a few hundred steps is invisible anyway, and a round number keeps "damage 250" readable as a
	 * quarter spent. It is also independent of the config — changing {@code reactorEuPerRod} moves how
	 * much energy a rod holds without moving what a full bar means.
	 */
	public static final int ROD_DURABILITY = 1000;

	/**
	 * EU that one point of damage stands for, rounded UP.
	 *
	 * <p>Rounding up rather than down keeps a rod from outliving its own charge: with a truncating
	 * divide the points would each be worth slightly less than they cost, and the last few points would
	 * be free energy.
	 */
	public static long euPerPoint(long rodEnergy) {
		if (rodEnergy <= 0) {
			return 0;
		}
		return (rodEnergy + ROD_DURABILITY - 1) / ROD_DURABILITY;
	}


}
