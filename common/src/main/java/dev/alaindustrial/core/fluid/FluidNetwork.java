package dev.alaindustrial.core.fluid;

import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidLineTopology.Endpoint;
import dev.alaindustrial.core.net.GraphNetwork;
import dev.alaindustrial.core.net.NodeSet;
import java.util.ArrayList;
import java.util.List;
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
public final class FluidNetwork implements GraphNetwork<FluidNetwork, BlockPos> {

	private final ServerLevel level;
	/** The pipes and the "refresh the endpoints" flag (MOD-715, batch 11). */
	private final NodeSet<BlockPos> nodes = new NodeSet<>();
	private final Set<BlockPos> pipes = nodes.nodes();
	private final List<Endpoint> sources = new ArrayList<>();
	private final List<Endpoint> sinks = new ArrayList<>();
	/** The segments and their joins, read at refresh instead of on every hop (MOD-734). */
	private final FluidLineTopology topology;
	private final Runnable markStale = this::markDirty;

	/** Which way fluid flows, flooded only when the hungry sinks or the topology change (MOD-734). */
	private final FluidFlowField field;
	/** The hungry sinks' segments this tick, in seed order. */
	private final List<BlockPos> hungrySeeds = new ArrayList<>();
	/** Scratch list of a donor's eligible neighbours, reused across donors and ticks. */
	private final List<FluidPipeNode> nearer = new ArrayList<>(6);

	/*
	 * Sleep (MOD-734, ADR-047). The network sleeps once a tick moved nothing; while it sleeps, isAwake asks
	 * the endpoints the tick's own questions under a simulated transaction, and a write into a segment from
	 * outside a tick wakes it at once. There is deliberately no periodic real tick: an error in the probe
	 * must show up in FluidNetworkSleepScenarios, not be papered over every N ticks.
	 */
	/** The last tick moved nothing, and nothing has written into a segment since. */
	private boolean idle;
	/** Net mB moved by the tick in progress. */
	private long movedThisTick;
	/** The line's fluid as the last tick's field saw it ({@link FluidHolder#EMPTY} for an empty line). */
	private FluidHolder lineFluid = FluidHolder.EMPTY;
	/** The hungry sink segments of the tick that fell asleep — what the probe compares against. */
	private final List<BlockPos> idleHungry = new ArrayList<>();
	/** Scratch for the probe's own count of hungry sinks. */
	private final List<BlockPos> probeHungry = new ArrayList<>();

	public FluidNetwork(ServerLevel level) {
		this.level = level;
		this.topology = new FluidLineTopology(level);
		this.field = new FluidFlowField(topology);
	}

	public ServerLevel level() {
		return level;
	}

	public Set<BlockPos> pipes() {
		return pipes;
	}

	@Override
	public Set<BlockPos> nodes() {
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

	/** Add a pipe; the endpoints are re-read even when it was already there, as they always were. */
	@Override
	public void addNode(BlockPos pos) {
		nodes.add(pos.immutable());
		nodes.markDirty();
	}

	@Override
	public void removeNode(BlockPos pos) {
		nodes.remove(pos);
		nodes.markDirty();
	}

	@Override
	public void absorb(FluidNetwork other) {
		nodes.absorb(other.nodes);
	}

	@Override
	public void markDirty() {
		nodes.markDirty();
	}

	/**
	 * Whether this tick could move anything (MOD-734, ADR-047). A network whose last tick moved nothing
	 * sleeps; asleep, it asks every sink and source the question the tick would — simulated — and the
	 * consumers which of them are hungry. Any answer that differs from the tick that fell asleep wakes it.
	 * O(endpoints), never O(segments).
	 *
	 * <p>A foreign port that throws while being asked counts as "awake": the guarded tick meets it next
	 * ({@code NetworkTickGuard}, MOD-186), where the failure is isolated and logged.
	 */
	@Override
	public boolean isAwake() {
		if (nodes.isDirty() || !idle) {
			return true;
		}
		try {
			return endpointsWouldMove();
		} catch (RuntimeException failure) {
			return true;
		}
	}

	/** A segment of this network was written from outside its tick (MOD-734): tick again. */
	public void wake() {
		idle = false;
	}

	/** The asleep network's probe: what the tick would do at its ends, asked and never moved. */
	private boolean endpointsWouldMove() {
		for (Endpoint sink : sinks) {
			if (serve(sink, true) > 0) {
				return true;
			}
		}
		if (!lineFluid.isEmpty()) {
			collectHungrySinks(lineFluid, probeHungry);
			if (!probeHungry.equals(idleHungry)) {
				return true;
			}
		}
		for (Endpoint source : sources) {
			if (fill(source, true) > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * How many fluid-network ticks are on THIS thread's stack right now (MOD-734). A segment whose buffer
	 * commits while it is above zero leaves its visible state to the {@link #settleSegments() settle} at the
	 * end of the tick; outside a tick it settles at once. Per thread, not one shared counter: the server
	 * ticks its networks on one thread, and a tick on any other — a second server in the same JVM, a
	 * benchmark — must neither defer nor un-defer this one's segments.
	 */
	private static final ThreadLocal<int[]> TICKS_IN_PROGRESS = ThreadLocal.withInitial(() -> new int[1]);

	/** Whether a fluid-network tick is running on the calling thread — see {@link #TICKS_IN_PROGRESS}. */
	public static boolean isTicking() {
		return TICKS_IN_PROGRESS.get()[0] > 0;
	}

	/** One tick. A fluid network has no throughput counter, so the frame's telemetry slot stays at zero. */
	@Override
	public long tick() {
		int[] ticking = TICKS_IN_PROGRESS.get();
		ticking[0]++;
		movedThisTick = 0;
		try {
			refreshIfDirty();
			serveSinks();
			propagateOneHop();
			fillFromSources();
			idle = movedThisTick == 0;
			if (idle) {
				idleHungry.clear();
				idleHungry.addAll(hungrySeeds);
			}
		} finally {
			ticking[0]--;
			settleSegments();
		}
		return 0L;
	}

	/** Let every segment apply what its commits during this tick put off ({@link FluidPipeNode#settle}). */
	private void settleSegments() {
		for (int i = 0; i < topology.size(); i++) {
			FluidPipeNode pipe = segment(i);
			if (pipe != null) {
				pipe.settle();
			}
		}
	}

	private void refreshIfDirty() {
		if (!nodes.isDirty()) {
			return;
		}
		// The flag is cleared first so that a mark made while the world is read is kept, and set again when
		// the read throws (a foreign port's capability may, MOD-186): the topology then keeps the previous
		// layout whole, and the next tick reads again (MOD-734 review).
		nodes.clearDirty();
		try {
			topology.rebuild(pipes, sources, sinks);
		} catch (RuntimeException failure) {
			nodes.markDirty();
			throw failure;
		}
		field.invalidate();
	}

	/** Push each segment's contents into any sink it feeds. */
	private void serveSinks() {
		for (Endpoint sink : sinks) {
			serve(sink, false);
		}
	}

	/** Push the sink segment's contents into its consumer — or, {@code simulate}d, ask how much would go. */
	private long serve(Endpoint sink, boolean simulate) {
		FluidPipeNode pipe = segment(sink.segment());
		if (pipe == null || pipe.lineBuffer().amount <= 0) {
			return 0;
		}
		BlockPos target = sink.neighbour();
		if (!level.isLoaded(target)) {
			return 0;
		}
		FluidPort port = FluidLookup.get().find(level, target, sink.side().getOpposite());
		if (port == null || !port.supportsInsertion()) {
			return 0;
		}
		return move(pipe.lineBuffer(), port, pipe.lineBuffer().fluid, pipe.lineBuffer().amount, simulate);
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
		if (!distanceFromHungrySinks()) {
			levelOneHop();
			return;
		}
		// The sweep holds no sink segment: those empty into their consumers in serveSinks.
		for (int index : field.sweep()) {
			int d = field.hops(index);
			FluidPipeNode from = segment(index);
			if (from == null || from.lineBuffer().amount <= 0) {
				continue;
			}
			FluidHolder fluid = from.lineBuffer().fluid;
			nearer.clear();
			long totalRoom = 0;
			for (Direction dir : FluidLineTopology.DIRECTIONS) {
				int next = topology.neighbour(index, dir);
				if (next == FluidLineTopology.NONE || field.hops(next) != d - 1 || !topology.joins(index, dir)) {
					continue;
				}
				FluidPipeNode to = segment(next);
				if (to == null || (to.lineBuffer().amount > 0 && !to.lineBuffer().fluid.equals(fluid))) {
					continue;
				}
				long room = FluidFlowMath.room(to.lineBuffer().getCapacity(), to.lineBuffer().amount);
				if (room > 0) {
					nearer.add(to);
					totalRoom += room;
				}
			}
			long available = Math.min(from.lineBuffer().amount, totalRoom);
			long left = available;
			for (int k = 0; k < nearer.size() && left > 0; k++) {
				FluidPipeNode to = nearer.get(k);
				long room = FluidFlowMath.room(to.lineBuffer().getCapacity(), to.lineBuffer().amount);
				long share = k == nearer.size() - 1 ? left
						: FluidFlowMath.proportionalShare(available, room, totalRoom);
				long before = from.lineBuffer().amount;
				moveFluid(from.lineBuffer(), to.lineBuffer(), fluid, Math.min(share, left));
				left -= before - from.lineBuffer().amount;
			}
		}
	}

	/**
	 * Bring {@link #field} up to date: the distance, in hops, from every segment to the nearest sink segment
	 * whose consumer accepts the network's fluid this tick. Returns {@code false} —
	 * no field — when nothing is in the line or no consumer takes anything. The consumers are asked every
	 * tick; the flood runs only when their answer or the topology changed.
	 */
	private boolean distanceFromHungrySinks() {
		FluidHolder anyFluid = FluidHolder.EMPTY;
		for (int i = 0; i < topology.size(); i++) {
			FluidPipeNode pipe = segment(i);
			if (pipe != null && pipe.lineBuffer().amount > 0) {
				anyFluid = pipe.lineBuffer().fluid;
				break;
			}
		}
		lineFluid = anyFluid;
		hungrySeeds.clear();
		if (anyFluid.isEmpty()) {
			return false;
		}
		collectHungrySinks(anyFluid, hungrySeeds);
		if (hungrySeeds.isEmpty()) {
			return false;
		}
		field.update(hungrySeeds);
		return true;
	}

	/** The segments of the sinks whose consumer takes {@code anyFluid} now, in sink order, each once. */
	private void collectHungrySinks(FluidHolder anyFluid, List<BlockPos> out) {
		out.clear();
		for (Endpoint sink : sinks) {
			if (!out.contains(sink.pipe()) && consumerAccepts(sink, anyFluid)) {
				out.add(sink.pipe());
			}
		}
	}

	/** Whether the consumer behind {@code sink} would take some fluid now — asked, never moved. */
	private boolean consumerAccepts(Endpoint sink, FluidHolder networkFluid) {
		FluidPipeNode pipe = segment(sink.segment());
		FluidHolder offered = pipe != null && pipe.lineBuffer().amount > 0 ? pipe.lineBuffer().fluid : networkFluid;
		BlockPos target = sink.neighbour();
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
		// asLong, not the translation-invariant PosOrder rule — kept deliberately (MOD-313): equalising
		// halves the difference on every hop, so the pass converges to the same levels whatever order it
		// visits in. All this order owes is to be total and stable. See ItemNetwork.ENDPOINT_ORDER.
		for (int index : topology.asLongOrder()) {
			FluidPipeNode from = segment(index);
			if (from == null || from.lineBuffer().amount <= 0) {
				continue;
			}
			for (Direction dir : FluidLineTopology.DIRECTIONS) {
				int next = topology.neighbour(index, dir);
				if (next == FluidLineTopology.NONE || !topology.joins(index, dir)) {
					continue;
				}
				FluidPipeNode to = segment(next);
				if (to == null) {
					continue;
				}
				long difference = FluidFlowMath.imbalance(from.lineBuffer().amount, to.lineBuffer().amount);
				if (FluidFlowMath.tooSmallToHop(difference)) {
					continue;
				}
				moveFluid(from.lineBuffer(), to.lineBuffer(), from.lineBuffer().fluid,
						FluidFlowMath.hopAmount(difference));
				if (from.lineBuffer().amount <= 0) {
					break;
				}
			}
		}
	}

	/** Pull from each source into the segment that touches it. */
	private void fillFromSources() {
		for (Endpoint source : sources) {
			fill(source, false);
		}
	}

	/** Pull from the source into the segment touching it — or, {@code simulate}d, ask how much would come. */
	private long fill(Endpoint source, boolean simulate) {
		FluidPipeNode pipe = segment(source.segment());
		if (pipe == null) {
			return 0;
		}
		long room = FluidFlowMath.room(pipe.lineBuffer().getCapacity(), pipe.lineBuffer().amount);
		if (FluidFlowMath.noRoomLeft(room)) {
			return 0;
		}
		BlockPos donor = source.neighbour();
		if (!level.isLoaded(donor)) {
			return 0;
		}
		FluidPort port = FluidLookup.get().find(level, donor, source.side().getOpposite());
		if (port == null || !port.supportsExtraction()) {
			return 0;
		}
		// A segment already holding something only accepts more of the same; an empty one takes
		// whatever the donor offers. Normalising flowing→source here keeps a line from stalling
		// forever against a neighbour that stores the flowing variant of the same fluid.
		FluidHolder wanted = pipe.lineBuffer().amount > 0 ? pipe.lineBuffer().fluid : normalise(port.fluid());
		if (wanted.isEmpty()) {
			return 0;
		}
		return move(port, pipe.lineBuffer(), wanted, room, simulate);
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
		move(from, to, fluid, amount, false);
	}

	/**
	 * Move up to {@code amount} in its own committed transaction and count the net result toward the
	 * tick's "moved anything"; {@code simulate} rolls back instead and counts nothing. Net, not gross
	 * (MOD-283): a refused move whose extraction was refunded is 0.
	 */
	private long move(FluidPort from, FluidPort to, FluidHolder fluid, long amount, boolean simulate) {
		if (amount <= 0 || fluid.isEmpty()) {
			return 0;
		}
		if (simulate) {
			return EnergyTransactions.get().simulate(txn -> FluidMover.move(from, to, fluid, amount, txn));
		}
		long[] moved = {0};
		EnergyTransactions.get().runCommitting(txn -> moved[0] = FluidMover.move(from, to, fluid, amount, txn));
		movedThisTick += moved[0];
		return moved[0];
	}

	/** The live segment at {@code index} of {@link #topology}; a stale reference marks the network dirty. */
	private FluidPipeNode segment(int index) {
		return topology.segment(index, markStale);
	}
}
