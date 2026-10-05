package dev.alaindustrial.core.energy;

/**
 * How an energy block declares itself to the storage channels (MOD-715, CORE-3; the predicates MOD-691
 * gathered on {@code EnergyBlockEntity}). The network core reads these three answers through this
 * interface rather than through a block-entity class:
 * <ul>
 *   <li>{@link #isEnergyStorageSink()} — served after the working machines (ADR-002);</li>
 *   <li>{@link #acceptsCascade()} — another store may level into it by fill fraction (MOD-314);</li>
 *   <li>{@link #storageFeedRate()} — a store may trickle into it at this flat rate, above its reserve
 *       (MOD-353).</li>
 * </ul>
 * The three channels these feed are ADR-004.
 */
public interface StorageEndpoint {

	/** Served after the working machines: a store, not a consumer (ADR-002). */
	boolean isEnergyStorageSink();

	/** Takes part in the storage→storage cascade as a receiver (MOD-314). */
	boolean acceptsCascade();

	/** The flat rate a store may feed this block at, or 0 when it takes no feed (MOD-353). */
	long storageFeedRate();
}
