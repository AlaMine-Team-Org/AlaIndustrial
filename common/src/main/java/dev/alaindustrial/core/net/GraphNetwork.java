package dev.alaindustrial.core.net;

import java.util.Set;

/**
 * A network {@link GraphNetworkManager} can drive through {@link GraphNetworkOps} (MOD-715, batch 11): the
 * operations of {@link NetworkOps} that belong to the network itself. Each network keeps its own answer to
 * what a change means — the energy network also forgets that its line was full, the monitor network
 * restarts its scan clock — so those stay its methods; the bookkeeping under them is a {@link NodeSet}.
 *
 * @param <N> the network type itself
 * @param <P> the position type of a node
 */
public interface GraphNetwork<N extends GraphNetwork<N, P>, P> {

	/** The live node set — see {@link NetworkOps#nodes}. */
	Set<P> nodes();

	/** Add one node. */
	void addNode(P pos);

	/** Remove one node. */
	void removeNode(P pos);

	/** Fold {@code drop}'s nodes into this network. {@code drop}'s own set is left untouched. */
	void absorb(N drop);

	/** Invalidate the cached endpoints after a topology change. */
	void markDirty();

	/** Whether there is anything to do this tick. */
	boolean isAwake();

	/**
	 * Tick once and report what moved for the frame's telemetry — see {@link NetworkOps#tick}: the energy
	 * network's EU, 0 for a network with no throughput counter.
	 */
	long tick();
}
