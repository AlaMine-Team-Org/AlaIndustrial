package dev.alaindustrial.core.fluid;

import dev.alaindustrial.core.item.PipeFaceMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * The segments of one {@link FluidNetwork}, how they join and the endpoints they touch, read from the world
 * once per refresh rather than on every hop of every tick (MOD-734) — the fluid side of what
 * {@code EnergyTopologyCache} is to a cable line.
 *
 * <p><b>Why caching the joins is as safe as the network itself.</b> Whether two member segments join
 * ({@link FluidPipeNode#connects}) changes only with a face switched off or a segment's family — both go
 * through {@link FluidNetworkManager#topologyChanged}, which re-partitions the network and marks it dirty —
 * and with a segment placed, broken or unloaded, which registers or unregisters it. The partition into
 * networks already trusts exactly these events, so a join this cache holds is wrong only when the network
 * itself is wrong.
 *
 * <p><b>The segment references are checked, not trusted.</b> A cached block entity that has been removed
 * reads as missing and marks the network dirty; a member whose block entity was not there at refresh is
 * looked up again on every read, as every member was before. Positions keep the network's node order
 * (ADR-006), so a walk over the indices is the walk over the node set it replaces.
 *
 * <p>Package-private — part of the {@code FluidNetwork} implementation.
 */
final class FluidLineTopology {

	/** Cached once — {@link Direction#values()} clones its array on every call. */
	static final Direction[] DIRECTIONS = Direction.values();

	/** A neighbour slot with no member segment behind it. */
	static final int NONE = -1;

	/**
	 * Everything one refresh reads, held together so a refresh replaces it in one assignment (MOD-734
	 * review). Reading a join asks the neighbour's fluid port, which is another mod's capability and may
	 * throw; a refresh that died half-way must leave the previous layout whole, not new positions beside
	 * old neighbour slots.
	 *
	 * @param positions the members, in the network's node order
	 * @param segments the block entity of each member at refresh, or {@code null}
	 * @param neighbours six slots per member, in {@link Direction} order: the member there, or {@link #NONE}
	 * @param joins one bit per {@link Direction} ordinal: whether the member's own face there joins
	 * @param asLongOrder every index in {@link BlockPos#asLong} order — the levelling pass's order
	 * @param indexOf each member's index
	 */
	private record Layout(BlockPos[] positions, FluidPipeNode[] segments, int[] neighbours, int[] joins,
			int[] asLongOrder, Map<BlockPos, Integer> indexOf) {
		static final Layout EMPTY = new Layout(new BlockPos[0], new FluidPipeNode[0], new int[0], new int[0],
				new int[0], new LinkedHashMap<>());
	}

	private final ServerLevel level;
	private Layout layout = Layout.EMPTY;

	FluidLineTopology(ServerLevel level) {
		this.level = level;
	}

	/**
	 * An endpoint outside the pipe graph: the pipe position, the face, the segment's index here and the
	 * position across that face, both resolved at refresh (MOD-734).
	 */
	record Endpoint(BlockPos pipe, Direction side, int segment, BlockPos neighbour) {
	}

	private static final Comparator<Endpoint> STABLE_ORDER = Comparator
			.comparingInt((Endpoint e) -> e.pipe().getX())
			.thenComparingInt(e -> e.pipe().getY())
			.thenComparingInt(e -> e.pipe().getZ())
			.thenComparingInt(e -> e.side().ordinal());

	/**
	 * Read the segments in {@code pipes}, in its order, how each face joins, and the endpoints the line
	 * pulls from ({@code sources}) and pushes into ({@code sinks}). All or nothing: everything is read
	 * first, and only then do this topology and the two lists take the new values, so a read that throws
	 * leaves all three as they were.
	 */
	void rebuild(Set<BlockPos> pipes, List<Endpoint> sources, List<Endpoint> sinks) {
		Layout next = readSegments(pipes);
		List<Endpoint> nextSources = new ArrayList<>();
		List<Endpoint> nextSinks = new ArrayList<>();
		findEndpoints(next, pipes, nextSources, nextSinks);
		layout = next;
		sources.clear();
		sources.addAll(nextSources);
		sinks.clear();
		sinks.addAll(nextSinks);
	}

	private Layout readSegments(Set<BlockPos> pipes) {
		int n = pipes.size();
		BlockPos[] positions = pipes.toArray(new BlockPos[0]);
		FluidPipeNode[] segments = new FluidPipeNode[n];
		int[] neighbours = new int[n * DIRECTIONS.length];
		int[] joins = new int[n];
		Map<BlockPos, Integer> indexOf = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			indexOf.put(positions[i], i);
		}
		for (int i = 0; i < n; i++) {
			segments[i] = lookUp(positions[i]);
			for (Direction dir : DIRECTIONS) {
				Integer next = indexOf.get(positions[i].relative(dir));
				neighbours[i * DIRECTIONS.length + dir.ordinal()] = next == null ? NONE : next;
				if (segments[i] != null && segments[i].connects(dir)) {
					joins[i] |= 1 << dir.ordinal();
				}
			}
		}
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
		}
		Arrays.sort(order, Comparator.comparingLong(i -> positions[i].asLong()));
		int[] asLongOrder = new int[n];
		for (int i = 0; i < n; i++) {
			asLongOrder[i] = order[i];
		}
		return new Layout(positions, segments, neighbours, joins, asLongOrder, indexOf);
	}

	/** The faces of the segments that touch a fluid port outside the network, with their roles. */
	private void findEndpoints(Layout read, Set<BlockPos> pipes, List<Endpoint> sources, List<Endpoint> sinks) {
		for (int i = 0; i < read.positions().length; i++) {
			BlockPos pos = read.positions()[i];
			FluidPipeNode pipe = read.segments()[i];
			if (pipe == null) {
				continue;
			}
			for (Direction dir : DIRECTIONS) {
				BlockPos neighbour = pos.relative(dir);
				if (pipes.contains(neighbour) || !level.isLoaded(neighbour)) {
					continue;
				}
				PipeFaceMode mode = pipe.faceMode(dir);
				if (mode == PipeFaceMode.DISABLED) {
					continue;
				}
				// MOD-662: a pipe outside this network is never an endpoint — a steam line laid against a
				// water line would otherwise find its neighbour's buffer through the fluid lookup and
				// trade with it. Nor is a port that serves only the other family.
				if (level.getBlockEntity(neighbour) instanceof FluidPipeNode || !pipe.connects(dir)) {
					continue;
				}
				FluidPort port = FluidLookup.get().find(level, neighbour, dir.getOpposite());
				if (port == null) {
					continue;
				}
				// NEUTRAL asks the port which way it goes, and takes the answer only when there is no
				// question: a one-way port has a role whether or not anybody wrenched it. Ordinary tanks
				// answer "both" and still wait for the wrench, which is right — there the direction is a
				// decision, and guessing it would move a player's fluid the wrong way. But the reactor's
				// parts are one-way by construction (a column drinks water and gives steam, an exhaust
				// only takes), and making the player configure a fitting that has no second option is a
				// step that can only be got wrong.
				boolean extract = mode == PipeFaceMode.EXTRACT
						|| (mode == PipeFaceMode.NEUTRAL && port.supportsExtraction()
								&& !port.supportsInsertion());
				boolean insert = mode == PipeFaceMode.INSERT
						|| (mode == PipeFaceMode.NEUTRAL && port.supportsInsertion()
								&& !port.supportsExtraction());
				if (extract && port.supportsExtraction()) {
					sources.add(new Endpoint(pos.immutable(), dir, i, neighbour.immutable()));
				} else if (insert && port.supportsInsertion()) {
					sinks.add(new Endpoint(pos.immutable(), dir, i, neighbour.immutable()));
				}
			}
		}
		// Stable order so round-robin cursors do not jump when the set is rebuilt.
		sources.sort(STABLE_ORDER);
		sinks.sort(STABLE_ORDER);
	}

	int size() {
		return layout.positions().length;
	}

	BlockPos pos(int index) {
		return layout.positions()[index];
	}

	/** The index of the member segment at {@code pos}, or {@link #NONE}. */
	int indexOf(BlockPos pos) {
		Integer index = layout.indexOf().get(pos);
		return index == null ? NONE : index;
	}

	/** The member segment one step from {@code index} in {@code dir}, or {@link #NONE}. */
	int neighbour(int index, Direction dir) {
		return layout.neighbours()[index * DIRECTIONS.length + dir.ordinal()];
	}

	/** Whether the face of {@code index} pointing in {@code dir} joined it to its neighbour at refresh. */
	boolean joins(int index, Direction dir) {
		return (layout.joins()[index] & (1 << dir.ordinal())) != 0;
	}

	/** Every index, {@link BlockPos#asLong} order. Read-only by convention. */
	int[] asLongOrder() {
		return layout.asLongOrder();
	}

	/**
	 * The live segment at {@code index}, or {@code null} when its chunk is not loaded or it has none.
	 * {@code stale} runs when the cached one turned out removed, and when a member missing at refresh has
	 * appeared — either way the joins read at refresh no longer describe it.
	 */
	FluidPipeNode segment(int index, Runnable stale) {
		BlockPos pos = layout.positions()[index];
		if (!level.isLoaded(pos)) {
			return null;
		}
		FluidPipeNode cached = layout.segments()[index];
		if (cached != null && !cached.isRemoved()) {
			return cached;
		}
		FluidPipeNode live = lookUp(pos);
		if (live != cached) {
			layout.segments()[index] = live;
			stale.run();
		}
		return live;
	}

	private FluidPipeNode lookUp(BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof FluidPipeNode pipe ? pipe : null;
	}
}
