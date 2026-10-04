package dev.alaindustrial.core.net;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A network's nodes and its "the topology changed" flag (MOD-715, batch 11): the bookkeeping every one of
 * the four networks — energy, fluid, item, monitor — used to write out for itself. The nodes keep the
 * order they were added in (ADR-006); the flag starts set, so a new network refreshes its endpoints on its
 * first read.
 *
 * <p>{@link #add} and {@link #remove} set the flag only when the set changed. A network that wants a
 * refresh on every call (the fluid and monitor networks always did) follows them with {@link #markDirty}
 * itself; {@link #absorb} always sets it, as a merge always did.
 *
 * @param <P> the position type of a node
 */
public final class NodeSet<P> {
	private final Set<P> nodes = new LinkedHashSet<>();
	private boolean dirty = true;

	/**
	 * The live set, not a copy: {@link GraphNetworkManager} reads it, and clears it when it re-partitions a
	 * network into components.
	 */
	public Set<P> nodes() {
		return nodes;
	}

	/** Add a node; the flag is set when it was not there yet. Returns whether the set changed. */
	public boolean add(P node) {
		boolean added = nodes.add(node);
		if (added) {
			dirty = true;
		}
		return added;
	}

	/** Remove a node; the flag is set when it was there. Returns whether the set changed. */
	public boolean remove(P node) {
		boolean removed = nodes.remove(node);
		if (removed) {
			dirty = true;
		}
		return removed;
	}

	/** Fold another network's nodes into this one (a merge); always sets the flag. */
	public void absorb(NodeSet<P> other) {
		nodes.addAll(other.nodes);
		dirty = true;
	}

	/** Something around the nodes changed (a neighbour block placed or broken): refresh on the next read. */
	public void markDirty() {
		dirty = true;
	}

	public boolean isDirty() {
		return dirty;
	}

	/** The refresh that the flag asked for has run. */
	public void clearDirty() {
		dirty = false;
	}

	public int size() {
		return nodes.size();
	}

	public boolean contains(P node) {
		return nodes.contains(node);
	}

	public boolean isEmpty() {
		return nodes.isEmpty();
	}
}
