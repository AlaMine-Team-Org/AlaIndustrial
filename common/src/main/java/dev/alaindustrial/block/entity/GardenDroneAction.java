package dev.alaindustrial.block.entity;

/**
 * The four jobs the Garden Drone can do to one tile (MOD-277).
 *
 * <p>{@link #PRIORITY_ORDER} is the contract, not an implementation detail: the drone always clears
 * ripe crops before it plants, plants before it fertilizes, and tills last. Harvesting first keeps a
 * finished farm from stalling on a full-but-unripe field; tilling last means the drone only widens
 * the farm once the existing one is fully tended.
 */
public enum GardenDroneAction {
	/** Take a ripe crop into the station's output slots. */
	HARVEST,
	/** Put one seed from the seed slot onto bare farmland. */
	PLANT,
	/** Spend one bone meal on an immature crop. */
	FERTILIZE,
	/** Turn bare ground into farmland, widening the plot. */
	TILL;

	/** Evaluation order for one working tick; see the class note for why it is this way round. */
	public static final GardenDroneAction[] PRIORITY_ORDER = { HARVEST, PLANT, FERTILIZE, TILL };
}
