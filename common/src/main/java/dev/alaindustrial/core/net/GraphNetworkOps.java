package dev.alaindustrial.core.net;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The {@link NetworkOps} of any {@link GraphNetwork} (MOD-715, batch 11). Every operation on a network is a
 * call to the network itself; what a manager still supplies is what only it knows — how to make an empty
 * network in a level, which positions could touch a node, and which actually do. Before this, each of the
 * four managers kept an anonymous {@code NetworkOps} of some sixty lines that only forwarded these calls.
 *
 * @param <L> the level type
 * @param <N> the network type
 * @param <P> the position type of a node
 */
public final class GraphNetworkOps<L, N extends GraphNetwork<N, P>, P> implements NetworkOps<L, N, P> {
	private final Function<L, N> create;
	private final Function<P, ? extends Iterable<P>> candidates;
	private final BiFunction<L, P, ? extends Iterable<P>> connected;

	/**
	 * @param create a fresh, empty network in a level
	 * @param candidates the positions that could be adjacent to a node, ignoring connectivity
	 * @param connected the positions a node is actually connected to right now
	 */
	public GraphNetworkOps(Function<L, N> create, Function<P, ? extends Iterable<P>> candidates,
			BiFunction<L, P, ? extends Iterable<P>> connected) {
		this.create = create;
		this.candidates = candidates;
		this.connected = connected;
	}

	@Override
	public N create(L level) {
		return create.apply(level);
	}

	@Override
	public Set<P> nodes(N network) {
		return network.nodes();
	}

	@Override
	public void addNode(N network, P pos) {
		network.addNode(pos);
	}

	@Override
	public void removeNode(N network, P pos) {
		network.removeNode(pos);
	}

	@Override
	public void absorb(N keep, N drop) {
		keep.absorb(drop);
	}

	@Override
	public void markDirty(N network) {
		network.markDirty();
	}

	@Override
	public boolean isAwake(N network) {
		return network.isAwake();
	}

	@Override
	public long tick(N network) {
		return network.tick();
	}

	@Override
	public Iterable<P> candidates(P pos) {
		return candidates.apply(pos);
	}

	@Override
	public Iterable<P> connected(L level, P pos) {
		return connected.apply(level, pos);
	}
}
