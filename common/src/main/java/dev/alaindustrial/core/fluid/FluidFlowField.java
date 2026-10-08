package dev.alaindustrial.core.fluid;

import dev.alaindustrial.core.net.DistanceField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The direction a {@link FluidNetwork} pushes fluid in: hops from every segment to the nearest sink segment
 * whose consumer takes fluid (MOD-677), as the tick reads it (MOD-734).
 *
 * <p><b>Flooded only when its inputs change.</b> The field is a function of the seeds — the hungry sink
 * segments, in seed order — and of the joins in {@link FluidLineTopology}. On a running line the same
 * consumers stay hungry tick after tick, so the flood runs again only when the seeds differ from the last
 * flood or the topology was rebuilt ({@link #invalidate}) — the energy network's rule for its own fields
 * (MOD-715). Which consumers are hungry is still asked every tick, by the network.
 *
 * <p><b>The walk is the one it replaces.</b> The flood is the shared {@link DistanceField} over the cached
 * joins, entering a neighbour only through ITS face pointing back; equal distances are swept in
 * {@link BlockPos#asLong} order, as they always were (MOD-715). Only the reading of the result moved into
 * two arrays.
 *
 * <p>Package-private — part of the {@code FluidNetwork} implementation.
 */
final class FluidFlowField {

	private final FluidLineTopology topology;
	private final DistanceField<BlockPos> distance;
	/** The seeds {@link #hops} and {@link #sweep} were flooded from; meaningful only while {@link #current}. */
	private final List<BlockPos> seeds = new ArrayList<>();
	private boolean current;
	/** Hops from each segment (by topology index) to the nearest seed, or -1 when unreached. */
	private int[] hops = new int[0];
	/** The reached segments that are not seeds, nearest first, ties in {@code asLong} order. */
	private int[] sweep = new int[0];

	FluidFlowField(FluidLineTopology topology) {
		this.topology = topology;
		this.distance = new DistanceField<>(this::unreachedJoinedSegments, Comparator.comparingLong(BlockPos::asLong));
	}

	/** The topology was rebuilt: the next {@link #update} floods whatever its seeds. */
	void invalidate() {
		current = false;
	}

	/** Make the field the one flooded from {@code hungry} (non-empty, in seed order), flooding only if it is not. */
	void update(List<BlockPos> hungry) {
		if (current && hungry.equals(seeds)) {
			return;
		}
		distance.clear();
		for (BlockPos seed : hungry) {
			distance.seed(seed, 0);
		}
		distance.flood();
		hops = new int[topology.size()];
		Arrays.fill(hops, -1);
		List<BlockPos> ordered = distance.sortByDistance(distance.distances().keySet(), false);
		int[] order = new int[ordered.size()];
		int count = 0;
		for (BlockPos pos : ordered) {
			int index = topology.indexOf(pos);
			int d = distance.distanceOrNull(pos);
			hops[index] = d;
			if (d > 0) {
				order[count++] = index;
			}
		}
		sweep = Arrays.copyOf(order, count);
		seeds.clear();
		seeds.addAll(hungry);
		current = true;
	}

	/** Hops from the segment at {@code index} to the nearest seed, or -1 when the flood never reached it. */
	int hops(int index) {
		return hops[index];
	}

	/** The reached segments that are not seeds, nearest first. Read-only by convention. */
	int[] sweep() {
		return sweep;
	}

	/**
	 * The member segments one hop from {@code pos} that the flood has not reached yet and whose facing side
	 * joins back, in {@link Direction} order — the graph the field floods.
	 */
	private List<BlockPos> unreachedJoinedSegments(BlockPos pos) {
		List<BlockPos> out = new ArrayList<>(6);
		int index = topology.indexOf(pos);
		for (Direction dir : FluidLineTopology.DIRECTIONS) {
			int n = topology.neighbour(index, dir);
			if (n != FluidLineTopology.NONE && !distance.contains(topology.pos(n))
					&& topology.joins(n, dir.getOpposite())) {
				out.add(topology.pos(n));
			}
		}
		return out;
	}
}
