package dev.alaindustrial.core.energy;

/**
 * A consumer that can tell the network how much it draws per tick while it works (MOD-789, ADR-049). The line
 * plans every machine its per-tick need first — nearer the generators first when the supply is short — then the
 * stores, then the machines' buffers. A consumer that does not implement this — a foreign mod's machine or
 * battery — has an unknown need and is planned as a store: it can neither stop a machine nor lock the stores out.
 * Every consumer of ours that draws a steady rate implements it.
 *
 * <p>Implemented by the block entity, read at the endpoint's host through {@code Level.getBlockEntity}; core code
 * never names a block.
 */
public interface PerTickDemand {
	/** EU per tick this consumer draws while it works; {@code <= 0} means unknown (planned as a store). */
	long perTickDemand();
}
