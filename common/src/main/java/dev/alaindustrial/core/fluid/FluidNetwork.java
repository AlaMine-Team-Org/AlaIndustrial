package dev.alaindustrial.core.fluid;

import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.item.PipeFaceMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * One connected component of fluid pipes and the endpoints touching it (MOD-151).
 *
 * <p><b>Fluid occupies the line, it does not teleport.</b> Each tick a segment hands fluid at most one
 * hop onward, exactly as EU moves along a cable (MOD-070). A consumer is therefore served from the
 * buffers physically touching it, never from the far end of the network — which is what makes a long
 * run visibly fill up rather than deliver instantly.
 *
 * <p>Order within a tick is drain-first: push into sinks, then move one hop toward the consumers
 * (MOD-677; levelling only when none takes anything), then pull from sources. Draining first frees
 * room in the segments that pulling is about to use, so a running line moves fluid every tick
 * instead of stalling behind its own full buffers.
 */
public final class FluidNetwork {

	/** An endpoint outside the pipe graph: the pipe position, the face, and which way it may move. */
	private record Endpoint(BlockPos pipe, Direction side) {
	}

	private final ServerLevel level;
	private final Set<BlockPos> pipes = new LinkedHashSet<>();
	private final List<Endpoint> sources = new ArrayList<>();
	private final List<Endpoint> sinks = new ArrayList<>();
	private boolean endpointsDirty = true;

	private static final Comparator<Endpoint> STABLE_ORDER = Comparator
			.comparingInt((Endpoint e) -> e.pipe().getX())
			.thenComparingInt(e -> e.pipe().getY())
			.thenComparingInt(e -> e.pipe().getZ())
			.thenComparingInt(e -> e.side().ordinal());

	public FluidNetwork(ServerLevel level) {
		this.level = level;
	}

	public ServerLevel level() {
		return level;
	}

	public Set<BlockPos> pipes() {
		return pipes;
	}

	public int size() {
		return pipes.size();
	}

	public boolean isEmpty() {
		return pipes.isEmpty();
	}

	public boolean contains(BlockPos pos) {
		return pipes.contains(pos);
	}

	public void addPipe(BlockPos pos) {
		pipes.add(pos.immutable());
		endpointsDirty = true;
	}

	public void removePipe(BlockPos pos) {
		pipes.remove(pos);
		endpointsDirty = true;
	}

	public void absorb(FluidNetwork other) {
		pipes.addAll(other.pipes);
		endpointsDirty = true;
	}

	public void markDirty() {
		endpointsDirty = true;
	}

	/**
	 * A network is worth ticking when it has any endpoint or any fluid still in flight. A line whose
	 * segments are empty and whose ends are idle costs nothing.
	 */
	public boolean isAwake() {
		if (endpointsDirty) {
			return true;
		}
		if (!sources.isEmpty() || !sinks.isEmpty()) {
			return true;
		}
		for (BlockPos pos : pipes) {
			FluidPipeBlockEntity pipe = pipeAt(pos);
			if (pipe != null && pipe.fluidBuffer.amount > 0) {
				return true;
			}
		}
		return false;
	}

	public void tick() {
		refreshIfDirty();
		serveSinks();
		propagateOneHop();
		fillFromSources();
	}

	private void refreshIfDirty() {
		if (!endpointsDirty) {
			return;
		}
		endpointsDirty = false;
		sources.clear();
		sinks.clear();
		for (BlockPos pos : pipes) {
			FluidPipeBlockEntity pipe = pipeAt(pos);
			if (pipe == null) {
				continue;
			}
			for (Direction dir : Direction.values()) {
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
				if (level.getBlockState(neighbour).getBlock() instanceof FluidPipeBlock
						|| !FluidPipeBlock.shouldConnectTo(level, pos, dir)) {
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
					sources.add(new Endpoint(pos.immutable(), dir));
				} else if (insert && port.supportsInsertion()) {
					sinks.add(new Endpoint(pos.immutable(), dir));
				}
			}
		}
		// Stable order so round-robin cursors do not jump when the set is rebuilt.
		sources.sort(STABLE_ORDER);
		sinks.sort(STABLE_ORDER);
	}

	/** Push each segment's contents into any sink it feeds. */
	private void serveSinks() {
		for (Endpoint sink : sinks) {
			FluidPipeBlockEntity pipe = pipeAt(sink.pipe());
			if (pipe == null || pipe.fluidBuffer.amount <= 0) {
				continue;
			}
			BlockPos target = sink.pipe().relative(sink.side());
			if (!level.isLoaded(target)) {
				continue;
			}
			FluidPort port = FluidLookup.get().find(level, target, sink.side().getOpposite());
			if (port == null || !port.supportsInsertion()) {
				continue;
			}
			moveFluid(pipe.fluidBuffer, port, pipe.fluidBuffer.fluid, pipe.fluidBuffer.amount);
		}
	}

	/**
	 * Move fluid one hop toward the consumers (MOD-677).
	 *
	 * <p><b>Flow, not levelling.</b> Until MOD-677 every hop moved HALF the difference between two
	 * segments. That is diffusion: a line of n segments carried about capacity / n per tick — 16 mB/t
	 * over three basic pipes, 0.5 over thirty — while the tooltip promised 50. Now each segment hands
	 * its whole content to the neighbours one step nearer a consumer, so a line carries its thinnest
	 * segment's worth per tick at any length, which is what a cable does (MOD-070) and what a player
	 * expects of a pipe.
	 *
	 * <p><b>Toward consumers that take something, as the energy network does (MOD-252).</b> The
	 * direction is a BFS from the sink segments whose consumer accepts fluid right now. Seeding from
	 * every sink would let a full tank next to the line wall off a hungry one further on.
	 *
	 * <p><b>Nearest the consumers first.</b> Segments are visited in order of distance, so a segment
	 * that has just emptied into the next one makes room for the one behind it in the same tick — and
	 * each unit still moves exactly one hop, because a segment is visited once. A donor with several
	 * eligible neighbours splits in proportion to their free room.
	 *
	 * <p>With no consumer taking anything the line fills evenly, as it always did ({@link #levelOneHop}).
	 */
	private void propagateOneHop() {
		Map<BlockPos, Integer> distance = distanceFromHungrySinks();
		if (distance.isEmpty()) {
			levelOneHop();
			return;
		}
		List<BlockPos> byDistance = new ArrayList<>(distance.keySet());
		byDistance.sort(Comparator.<BlockPos>comparingInt(distance::get).thenComparingLong(BlockPos::asLong));
		for (BlockPos pos : byDistance) {
			int d = distance.get(pos);
			if (d == 0) {
				continue;   // a sink segment empties into its consumer in serveSinks
			}
			FluidPipeBlockEntity from = pipeAt(pos);
			if (from == null || from.fluidBuffer.amount <= 0) {
				continue;
			}
			FluidHolder fluid = from.fluidBuffer.fluid;
			List<FluidPipeBlockEntity> nearer = new ArrayList<>(6);
			long totalRoom = 0;
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				Integer nd = distance.get(next);
				if (nd == null || nd != d - 1 || !FluidPipeBlock.shouldConnectTo(level, pos, dir)) {
					continue;
				}
				FluidPipeBlockEntity to = pipeAt(next);
				if (to == null || (to.fluidBuffer.amount > 0 && !to.fluidBuffer.fluid.equals(fluid))) {
					continue;
				}
				long room = FluidFlowMath.room(to.fluidBuffer.getCapacity(), to.fluidBuffer.amount);
				if (room > 0) {
					nearer.add(to);
					totalRoom += room;
				}
			}
			long available = Math.min(from.fluidBuffer.amount, totalRoom);
			long left = available;
			for (int k = 0; k < nearer.size() && left > 0; k++) {
				FluidPipeBlockEntity to = nearer.get(k);
				long room = FluidFlowMath.room(to.fluidBuffer.getCapacity(), to.fluidBuffer.amount);
				long share = k == nearer.size() - 1 ? left
						: FluidFlowMath.proportionalShare(available, room, totalRoom);
				long before = from.fluidBuffer.amount;
				moveFluid(from.fluidBuffer, to.fluidBuffer, fluid, Math.min(share, left));
				left -= before - from.fluidBuffer.amount;
			}
		}
	}

	/**
	 * Distance, in hops, from every segment to the nearest sink segment whose consumer accepts the
	 * network's fluid this tick. Empty when nothing is in the line or no consumer takes anything.
	 */
	private Map<BlockPos, Integer> distanceFromHungrySinks() {
		FluidHolder anyFluid = FluidHolder.EMPTY;
		for (BlockPos pos : pipes) {
			FluidPipeBlockEntity pipe = pipeAt(pos);
			if (pipe != null && pipe.fluidBuffer.amount > 0) {
				anyFluid = pipe.fluidBuffer.fluid;
				break;
			}
		}
		Map<BlockPos, Integer> distance = new LinkedHashMap<>();
		if (anyFluid.isEmpty()) {
			return distance;
		}
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (Endpoint sink : sinks) {
			if (!distance.containsKey(sink.pipe()) && consumerAccepts(sink, anyFluid)) {
				distance.put(sink.pipe(), 0);
				queue.add(sink.pipe());
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int next = distance.get(pos) + 1;
			for (Direction dir : Direction.values()) {
				BlockPos n = pos.relative(dir);
				if (pipes.contains(n) && !distance.containsKey(n)
						&& FluidPipeBlock.shouldConnectTo(level, n, dir.getOpposite())) {
					distance.put(n, next);
					queue.add(n);
				}
			}
		}
		return distance;
	}

	/** Whether the consumer behind {@code sink} would take some fluid now — asked, never moved. */
	private boolean consumerAccepts(Endpoint sink, FluidHolder networkFluid) {
		FluidPipeBlockEntity pipe = pipeAt(sink.pipe());
		FluidHolder offered = pipe != null && pipe.fluidBuffer.amount > 0 ? pipe.fluidBuffer.fluid : networkFluid;
		BlockPos target = sink.pipe().relative(sink.side());
		if (!level.isLoaded(target)) {
			return false;
		}
		FluidPort port = FluidLookup.get().find(level, target, sink.side().getOpposite());
		if (port == null || !port.supportsInsertion()) {
			return false;
		}
		return EnergyTransactions.get().simulate(txn -> port.insert(offered, 1, txn)) > 0;
	}

	/**
	 * Even out neighbouring segments by one hop — the pre-MOD-677 rule, kept for a line no consumer is
	 * drinking from, where levelling is exactly right: it fills the line evenly and then sleeps. Moving
	 * only half of the difference keeps two segments from sloshing on consecutive ticks; the arithmetic
	 * (the gap, the {@code <= 1} threshold, the halving) lives in {@link FluidFlowMath}.
	 */
	private void levelOneHop() {
		List<BlockPos> ordered = new ArrayList<>(pipes);
		// asLong, not the translation-invariant PosOrder rule — kept deliberately (MOD-313): equalising
		// halves the difference on every hop, so the pass converges to the same levels whatever order it
		// visits in. All this sort owes is to be total and stable. See ItemNetwork.ENDPOINT_ORDER.
		ordered.sort(Comparator.comparingLong(BlockPos::asLong));
		for (BlockPos pos : ordered) {
			FluidPipeBlockEntity from = pipeAt(pos);
			if (from == null || from.fluidBuffer.amount <= 0) {
				continue;
			}
			for (Direction dir : Direction.values()) {
				BlockPos nextPos = pos.relative(dir);
				if (!pipes.contains(nextPos) || !FluidPipeBlock.shouldConnectTo(level, pos, dir)) {
					continue;
				}
				FluidPipeBlockEntity to = pipeAt(nextPos);
				if (to == null) {
					continue;
				}
				long difference = FluidFlowMath.imbalance(from.fluidBuffer.amount, to.fluidBuffer.amount);
				if (FluidFlowMath.tooSmallToHop(difference)) {
					continue;
				}
				moveFluid(from.fluidBuffer, to.fluidBuffer, from.fluidBuffer.fluid,
						FluidFlowMath.hopAmount(difference));
				if (from.fluidBuffer.amount <= 0) {
					break;
				}
			}
		}
	}

	/** Pull from each source into the segment that touches it. */
	private void fillFromSources() {
		for (Endpoint source : sources) {
			FluidPipeBlockEntity pipe = pipeAt(source.pipe());
			if (pipe == null) {
				continue;
			}
			long room = FluidFlowMath.room(pipe.fluidBuffer.getCapacity(), pipe.fluidBuffer.amount);
			if (FluidFlowMath.noRoomLeft(room)) {
				continue;
			}
			BlockPos donor = source.pipe().relative(source.side());
			if (!level.isLoaded(donor)) {
				continue;
			}
			FluidPort port = FluidLookup.get().find(level, donor, source.side().getOpposite());
			if (port == null || !port.supportsExtraction()) {
				continue;
			}
			// A segment already holding something only accepts more of the same; an empty one takes
			// whatever the donor offers. Normalising flowing→source here keeps a line from stalling
			// forever against a neighbour that stores the flowing variant of the same fluid.
			FluidHolder wanted = pipe.fluidBuffer.amount > 0 ? pipe.fluidBuffer.fluid : normalise(port.fluid());
			if (wanted.isEmpty()) {
				continue;
			}
			moveFluid(port, pipe.fluidBuffer, wanted, room);
		}
	}

	/** Normalise a flowing fluid to its source form so identity comparisons match (MOD-151). */
	private static FluidHolder normalise(FluidHolder holder) {
		if (holder.isEmpty()) {
			return FluidHolder.EMPTY;
		}
		return holder.fluid() instanceof net.minecraft.world.level.material.FlowingFluid flowing
				? FluidHolder.of(flowing.getSource())
				: holder;
	}

	private void moveFluid(FluidPort from, FluidPort to, FluidHolder fluid, long amount) {
		if (amount <= 0 || fluid.isEmpty()) {
			return;
		}
		EnergyTransactions.get().runCommitting(txn -> FluidMover.move(from, to, fluid, amount, txn));
	}

	private FluidPipeBlockEntity pipeAt(BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe ? pipe : null;
	}
}
