package dev.alaindustrial.core.fluid;

import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.item.PipeFaceMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
 * <p>Order within a tick is drain-first: push into sinks, then even out between segments, then pull
 * from sources. Draining first frees room in the segments that pulling is about to use, so a running
 * line moves fluid every tick instead of stalling behind its own full buffers.
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
				FluidPort port = FluidLookup.get().find(level, neighbour, dir.getOpposite());
				if (port == null) {
					continue;
				}
				if (mode == PipeFaceMode.EXTRACT && port.supportsExtraction()) {
					sources.add(new Endpoint(pos.immutable(), dir));
				} else if (mode == PipeFaceMode.INSERT && port.supportsInsertion()) {
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
	 * Even out neighbouring segments by one hop. Moving only half of the difference keeps a line from
	 * sloshing back and forth between two segments on consecutive ticks.
	 */
	private void propagateOneHop() {
		List<BlockPos> ordered = new ArrayList<>(pipes);
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
				long difference = from.fluidBuffer.amount - to.fluidBuffer.amount;
				if (difference <= 1) {
					continue;
				}
				moveFluid(from.fluidBuffer, to.fluidBuffer, from.fluidBuffer.fluid, difference / 2);
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
			long room = pipe.fluidBuffer.getCapacity() - pipe.fluidBuffer.amount;
			if (room <= 0) {
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
