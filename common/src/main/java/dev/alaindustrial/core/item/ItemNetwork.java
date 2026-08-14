package dev.alaindustrial.core.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;

/** One connected component of item-pipe segments, ticked once by {@link ItemNetworkManager}. */
public final class ItemNetwork {
	private record Endpoint(BlockPos pos, Direction side) { }

	/**
	 * Stable order for {@link #sources}/{@link #targets} (MOD-115). The endpoints are collected into a
	 * {@code HashSet}, whose iteration order shifts when the set's composition changes; a numeric
	 * round-robin cursor over that shifting order would occasionally skip or double-serve an endpoint.
	 * Sorting by {@code (BlockPos, side)} gives the cursor a stable meaning across rebuilds.
	 *
	 * <p><b>{@code asLong()} on purpose, and NOT the translation-invariant {@link
	 * dev.alaindustrial.core.energy.PosOrder} rule (MOD-313, reviewed and kept).</b> The energy core needs
	 * translation invariance because its order decides how a fixed amount of EU is SPLIT between equally
	 * entitled consumers — so "the same layout behaves the same wherever it is built" is a balance
	 * property there. Here the order only picks where the round robin starts its lap: every source and
	 * every target is served within the cycle regardless, so moving a pipe network can at most shift
	 * which endpoint goes first, never how much anything gets. What this comparator owes the cursor is
	 * that it be TOTAL and STABLE across rebuilds, which {@code asLong} is. Changing it would reorder
	 * service in every existing build for no behavioural gain, so it stays — the mismatch with
	 * {@code PosOrder} is a decision, not an oversight.
	 */
	private static final Comparator<Endpoint> ENDPOINT_ORDER =
			Comparator.comparingLong((Endpoint e) -> e.pos().asLong()).thenComparingInt(e -> e.side().ordinal());

	private final ServerLevel level;
	private final Set<BlockPos> pipes = new LinkedHashSet<>();
	private final List<Endpoint> sources = new ArrayList<>();
	private final List<Endpoint> targets = new ArrayList<>();
	private boolean endpointsDirty = true;
	private int sourceCursor;
	private int targetCursor;
	/**
	 * Ticks left before this network may move items again (MOD-108). Starts at 0 so a freshly built or
	 * newly fed pipe reacts on its first tick instead of idling for a second — the wait is between
	 * transfers, not before the first one.
	 */
	private int transferCooldown;

	ItemNetwork(ServerLevel level) {
		this.level = level;
	}

	ServerLevel level() { return level; }
	Set<BlockPos> pipes() { return pipes; }
	int size() { return pipes.size(); }
	boolean contains(BlockPos pos) { return pipes.contains(pos); }
	boolean isEmpty() { return pipes.isEmpty(); }
	void addPipe(BlockPos pos) { if (pipes.add(pos.immutable())) endpointsDirty = true; }
	void removePipe(BlockPos pos) { if (pipes.remove(pos)) endpointsDirty = true; }
	void absorb(ItemNetwork other) { pipes.addAll(other.pipes); endpointsDirty = true; }
	void markDirty() { endpointsDirty = true; }

	boolean isAwake() {
		if (endpointsDirty) refreshEndpoints();
		return !sources.isEmpty() && !targets.isEmpty();
	}

	/**
	 * Once every {@link Config#itemPipeTransferIntervalTicks} ticks (MOD-108 balance gate), serves
	 * <b>every</b> target endpoint with up to {@link Config#itemPipeItemsPerTransfer} items — parallel
	 * distribution (MOD-115). One chest feeding several furnaces fills them together, instead of the old
	 * behaviour that served a single endpoint per interval and made every extra machine slower.
	 *
	 * <p>Both cursors rotate one step per interval and independently, over the sorted (stable) endpoint
	 * lists, so that when a source runs short no single endpoint is permanently favoured (fair N→M, no
	 * lockstep). The cooldown is only spent when something actually moved: a network whose source is
	 * empty or whose targets are all full keeps checking every tick and resumes the instant that changes.
	 */
	int tick() {
		if (endpointsDirty) refreshEndpoints();
		if (sources.isEmpty() || targets.isEmpty()) return 0;
		if (transferCooldown > 0) {
			transferCooldown--;
			return 0;
		}
		int per = Config.itemPipeItemsPerTransfer;
		int sourceCount = sources.size();
		int targetCount = targets.size();
		int totalMoved = 0;
		for (int t = 0; t < targetCount; t++) {
			Endpoint target = targets.get((targetCursor + t) % targetCount);
			ItemPort to = ItemLookup.get().find(level, target.pos(), target.side());
			if (to == null) continue;
			for (int s = 0; s < sourceCount; s++) {
				Endpoint source = sources.get((sourceCursor + s) % sourceCount);
				if (!mayFeed(source, target)) continue;
				ItemPort from = ItemLookup.get().find(level, source.pos(), source.side());
				if (from == null) continue;
				int moved = ItemMover.move(from, to, per);
				if (moved > 0) {
					totalMoved += moved;
					break; // this target is served for the interval; move on to the next target
				}
			}
		}
		if (totalMoved > 0) {
			sourceCursor = (sourceCursor + 1) % sourceCount;
			targetCursor = (targetCursor + 1) % targetCount;
			transferCooldown = Math.max(0, Config.itemPipeTransferIntervalTicks - 1);
		}
		return totalMoved;
	}

	/**
	 * Has the player given any face on this network a real role yet?
	 *
	 * <p>{@code DISABLED} deliberately does not count. It removes a face from the network rather than
	 * assigning it a job, and treating "I switched one side off" as "I have configured this line"
	 * would silently deactivate every other side — the opposite of what that click means.
	 *
	 * <p>Scoped to the whole network, not to the individual pair, and that is the point: a route is
	 * configured or it is not, and the answer must not depend on which end of it you look from.
	 */
	private boolean hasExplicitRole() {
		for (BlockPos pipe : pipes) {
			if (!(level.getBlockEntity(pipe) instanceof ItemPipeBlockEntity entity)) continue;
			for (Direction direction : Direction.values()) {
				PipeFaceMode mode = entity.faceMode(direction);
				if (mode == PipeFaceMode.EXTRACT || mode == PipeFaceMode.INSERT) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Whether items may flow from {@code source} to {@code target}.
	 *
	 * <p>Different blocks: always. The same block is the interesting case (MOD-234). A chest drawn
	 * from and pushed into by the same network would shuffle items between its own slots forever and
	 * burn the network's whole throughput on nothing, which is why this used to be refused outright.
	 * But a <b>machine</b> keeps its slots in separate roles, and "take the result, put it back in the
	 * input" is exactly how a player automates repeat processing — feeding duplicated diamonds back
	 * into the incubator, say. So the same block is allowed only when it sorts its slots by face and
	 * the slots it will hand out do not overlap the slots it will accept: that holds for a machine and
	 * fails for a chest, with no block-specific knowledge in the pipe.
	 *
	 * <p>The rule is deliberately generic, so any machine that sorts slots by face can now feed itself
	 * — a macerator wired output-to-input will push its own dust back in and sit there with a full
	 * input slot. That takes a player wiring a pipe from a machine back to the same machine on purpose;
	 * nothing is duplicated or lost, so it is left as the player's choice rather than special-cased.
	 */
	private boolean mayFeed(Endpoint source, Endpoint target) {
		if (source.pos().equals(target.pos())) {
			return sortsSlotsByFace(source.pos(), source.side(), target.side());
		}
		// Two DIFFERENT blocks, and both ends both give and take (MOD-405). That is what an unconfigured
		// line looks like: NEUTRAL declares a face as source AND target, so a plain chest–pipe–chest run
		// has two endpoints that each qualify for both roles, and the cursors send items one way this
		// interval and back the next. Forever, for nothing — and every leg rewrites both containers,
		// which saves the chunk and broadcasts the block entity to everyone watching.
		//
		// Refusing needs BOTH conditions. A pair that is symmetric only in ROLE is not enough: chest →
		// machine with both faces NEUTRAL is symmetric that way too, and it is the ordinary automation
		// loop (ore goes in, dust comes back). What separates them is whether the block sorts its slots
		// by face: the machine hands out only its output and accepts only its input, so what returns is
		// a different item, while a chest hands back exactly what it was just given. So the transfer is
		// refused only when NEITHER end sorts — the same test MOD-234 already applies to a single block,
		// now asked of the pair.
		//
		// The documented chest → chest layout is untouched: EXTRACT on one end and INSERT on the other
		// leaves each endpoint in a single role, `bothWays` is false, and items flow as before.
		boolean bothWays = targets.contains(source) && sources.contains(target);
		if (!bothWays) {
			return true;
		}
		return sortsSlotsByFace(source.pos(), source.side(), target.side())
				|| sortsSlotsByFace(target.pos(), target.side(), source.side());
	}

	/**
	 * Whether the container at {@code pos} keeps the slots it hands out through {@code outSide} separate
	 * from the slots it accepts through {@code inSide}.
	 *
	 * <p>True for a machine (output slots are not input slots), false for a chest and for anything that
	 * is not face-aware at all — a plain container gives back exactly what it was handed, which is the
	 * churn loop. Deliberately reads the container's own contract rather than testing for block types,
	 * so a machine added later gets the right answer without the pipe knowing about it.
	 */
	private boolean sortsSlotsByFace(BlockPos pos, Direction outSide, Direction inSide) {
		if (!(level.getBlockEntity(pos) instanceof WorldlyContainer sided)) {
			return false;
		}
		for (int slot : sided.getSlotsForFace(outSide)) {
			ItemStack held = sided.getItem(slot);
			if (!sided.canTakeItemThroughFace(slot, held, outSide)) {
				continue;
			}
			// A slot this face gives away that the other face would also take back: that is the churn
			// loop, and one shared slot is enough to refuse the pair.
			if (sided.canPlaceItemThroughFace(slot, held, inSide)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Rebuilds the source and target lists from the face modes around the network.
	 *
	 * <p><b>Explicit configuration switches the automatic mode off (MOD-408).</b> As soon as ANY face
	 * on this network carries a real role — {@code EXTRACT} or {@code INSERT} — every {@code NEUTRAL}
	 * face becomes passive: a stretch of the route, neither a source nor a destination.
	 *
	 * <p>Without that rule {@code NEUTRAL} means "takes and gives", so a player who set {@code EXTRACT}
	 * on one end and left the other alone still got a transfer — and {@code INSERT} became pointless,
	 * because "EXTRACT + nothing" and "EXTRACT + INSERT" did the same thing. It also sat badly next to
	 * MOD-405: two untouched ends move nothing, one touched end moves items. From the outside that
	 * reads as random.
	 *
	 * <p>The rule in one sentence: <i>configure nothing and the pipe decides; configure anything and
	 * it does exactly what you configured.</i> The automatic mode is untouched for a line nobody has
	 * set up — including its refusal to shuffle items between two plain containers.
	 */
	private void refreshEndpoints() {
		sources.clear();
		targets.clear();
		Set<Endpoint> sourceSeen = new LinkedHashSet<>();
		Set<Endpoint> targetSeen = new LinkedHashSet<>();
		boolean configured = hasExplicitRole();
		for (BlockPos pipe : pipes) {
			if (!(level.getBlockEntity(pipe) instanceof ItemPipeBlockEntity entity)) continue;
			for (Direction direction : Direction.values()) {
				if (!ItemPipeBlock.shouldConnectTo(level, pipe, direction)) continue;
				BlockPos neighbour = pipe.relative(direction);
				if (pipes.contains(neighbour)) continue;
				PipeFaceMode mode = entity.faceMode(direction);
				if (mode == PipeFaceMode.DISABLED) continue;
				// A NEUTRAL face on a configured network carries no role at all.
				if (configured && mode == PipeFaceMode.NEUTRAL) continue;
				Endpoint endpoint = new Endpoint(neighbour.immutable(), direction.getOpposite());
				if (mode == PipeFaceMode.NEUTRAL || mode == PipeFaceMode.EXTRACT) sourceSeen.add(endpoint);
				if (mode == PipeFaceMode.NEUTRAL || mode == PipeFaceMode.INSERT) targetSeen.add(endpoint);
			}
		}
		sources.addAll(sourceSeen);
		targets.addAll(targetSeen);
		sources.sort(ENDPOINT_ORDER);
		targets.sort(ENDPOINT_ORDER);
		endpointsDirty = false;
		if (!sources.isEmpty()) sourceCursor %= sources.size(); else sourceCursor = 0;
		if (!targets.isEmpty()) targetCursor %= targets.size(); else targetCursor = 0;
	}
}
