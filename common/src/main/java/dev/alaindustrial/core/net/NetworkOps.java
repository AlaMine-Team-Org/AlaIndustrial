package dev.alaindustrial.core.net;

import java.util.Set;

/**
 * Everything {@link GraphNetworkManager} needs to know about one kind of network (MOD-401). The
 * framework owns the bookkeeping — the position index, union on register, BFS re-partition on
 * removal, the round-robin tick and the level sweep; this interface is the only place the three
 * kinds differ.
 *
 * <p><b>Two neighbour queries, not one, and the difference is load-bearing.</b>
 * <ul>
 *   <li>{@link #candidates(Object)} is grid adjacency — the six axis positions, with no world
 *       lookup. It answers "could these two ever be neighbours", which is why the removal path uses
 *       it: by the time a pipe unregisters, its block is already gone from the world and a
 *       connectivity query about it would answer "connected to nothing".</li>
 *   <li>{@link #connected(Object, Object)} is real connectivity. For energy cables it is the same
 *       six positions; for fluid and item pipes a face can be switched off, so it is a
 *       <em>predicate</em> ({@code FluidPipeBlock.shouldConnectTo} / {@code
 *       ItemPipeBlock.shouldConnectTo}) and a face-mode change can both break a link and restore
 *       one — the reason every connectivity-creating entry point re-runs the union (MOD-282).</li>
 * </ul>
 *
 * @param <L> the level key (parameterised so the framework stays Minecraft-free and L1-testable)
 * @param <N> the network object (an {@code EnergyNetwork} / {@code FluidNetwork} / {@code ItemNetwork})
 * @param <P> the node position
 */
public interface NetworkOps<L, N, P> {

	/** A fresh, empty network in {@code level}. */
	N create(L level);

	/**
	 * The network's live node set. The framework reads it, and clears it when re-partitioning a
	 * network into components, so it must be the network's own mutable set rather than a copy.
	 */
	Set<P> nodes(N network);

	/** Add one node. */
	void addNode(N network, P pos);

	/** Remove one node. */
	void removeNode(N network, P pos);

	/** Fold {@code drop}'s nodes into {@code keep}. {@code drop}'s own set is left untouched. */
	void absorb(N keep, N drop);

	/** Invalidate the network's cached endpoints after a topology change. */
	void markDirty(N network);

	/** Whether the network has anything to do this tick. */
	boolean isAwake(N network);

	/**
	 * Tick one network and report what it moved. Energy returns the EU it transferred (the number
	 * behind {@code /ala net}); fluid and item networks have no such counter and return 0. The
	 * framework never interprets the value — it only accumulates it (see
	 * {@link GraphNetworkManager.Telemetry}).
	 */
	long tick(N network);

	/** The positions that could be adjacent to {@code pos} in the grid, ignoring connectivity. */
	Iterable<P> candidates(P pos);

	/** The positions {@code pos} is actually connected to right now. */
	Iterable<P> connected(L level, P pos);
}
