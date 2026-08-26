package dev.alaindustrial.core.structure;

import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * How a controller with no sealed room finds the fuel racks it drives (MOD-469).
 *
 * <p><b>A physical walk, not a sphere.</b> The search starts at the controller and steps outwards
 * through blocks that can carry a reaction: fuel racks, and the shielding-alloy shell a player may be
 * part-way through building. Open ground carries nothing, so a controller cannot drive racks standing
 * across a gap from it.
 *
 * <p>It began as a radius, and a playtest killed that in one screenshot: a controller on bare earth
 * with a column three blocks away lit up and produced, which reads exactly like power teleporting.
 * Standing them touching (the same player's next screenshot) is what feels right, and connectivity is
 * the rule that says so. {@code reactorBareSearchRadius} survives only as a bound on how far the walk
 * may travel, so a pathological chain cannot march across a continent.
 *
 * <p>The walk is cheap for the same reason the old sphere was: it only ever visits blocks that
 * conduct, which is a handful even for a large core, and it runs on the scan timer rather than per
 * tick. Rival controllers are still read out of the chunks' own block-entity maps, the same shape as
 * {@link dev.alaindustrial.core.radiation.RadiationSources#collectRods}.
 *
 * <p><b>Two rules stop the same rods being burnt twice</b>, and they are not a matter of taste: without
 * them two controllers standing near one heap of racks would each count all of it and each produce full
 * power from it, which is energy from nothing.
 *
 * <ul>
 * <li><b>A sealed room's racks are nobody else's.</b> A rack inside a rival's formed shell is invisible
 * here, so a bare controller parked outside somebody's working reactor cannot feed off it.</li>
 * <li><b>Between two bare controllers, the nearer one takes the rack.</b> Ties are broken on the
 * controller's coordinates rather than on distance alone, so both machines reach the same verdict about
 * every rack — a rule that decided differently on each side would hand the rack to both.</li>
 * </ul>
 *
 * <p><b>The known gap:</b> a rival in an unloaded chunk cannot be seen, so a rack on the far edge of a
 * chunk boundary can briefly be claimed twice. It costs a few EU a tick until the neighbour loads, and
 * closing it properly would mean forcing chunks to load — which is a far worse thing to do to a server
 * than the leak it would fix.
 */
public final class BareReactorScan {

	private BareReactorScan() {
	}

	/** What a bare controller found: the racks it owns and the live rods in them. */
	public record Result(List<BlockPos> racks, int rods) {

		public static final Result EMPTY = new Result(List.of(), 0);

		public boolean isEmpty() {
			return racks.isEmpty();
		}
	}

	/**
	 * Racks this controller may burn, with the two anti-duplication rules already applied.
	 *
	 * @param controller where the controller stands — the centre of both the search and the claim
	 * @param radius     {@code Config.reactorBareSearchRadius}
	 */
	public static Result scan(ServerLevel level, BlockPos controller, int radius) {
		if (radius <= 0) {
			return Result.EMPTY;
		}
		// A physical walk out from the controller, NOT a sphere. See the class doc: a radius let a
		// controller drive racks standing three blocks away across open ground, which read to the player
		// as power teleporting (playtest, 2026-08-26).
		List<BlockPos> racks = new ArrayList<>();
		int rods = 0;
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		seen.add(controller);
		queue.add(controller);
		List<ReactorControllerBlockEntity> rivals = collectRivals(level, controller, radius * 2);
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos next = at.relative(dir);
				if (!seen.add(next) || !within(next, controller, radius)) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				boolean rack = level.getBlockEntity(next) instanceof FuelRodAssemblyBlockEntity;
				// Racks conduct, and so does the shell a player is part-way through building — a reactor
				// half walled in must keep working, or "a breached room falls softly into bare mode" would
				// be a lie. Nothing else does: open ground does not carry a reaction.
				if (!rack && !state.is(ModTags.Blocks.MELTPROOF)) {
					continue;
				}
				queue.add(next);
				if (level.getBlockEntity(next) instanceof FuelRodAssemblyBlockEntity fuelled
						&& fuelled.hasFuel() && !claimedByAnother(next, controller, rivals, radius)) {
					racks.add(next.immutable());
					rods += fuelled.getRods();
				}
			}
		}
		return racks.isEmpty() ? Result.EMPTY : new Result(List.copyOf(racks), rods);
	}

	/** Every other reactor controller near enough to contest a rack, in loaded chunks only. */
	private static List<ReactorControllerBlockEntity> collectRivals(ServerLevel level, BlockPos centre,
			int radius) {
		List<ReactorControllerBlockEntity> rivals = new ArrayList<>();
		for (LevelChunk chunk : chunksAround(level, centre, radius)) {
			for (var entry : chunk.getBlockEntities().entrySet()) {
				BlockEntity be = entry.getValue();
				if (be instanceof ReactorControllerBlockEntity rival && !entry.getKey().equals(centre)) {
					rivals.add(rival);
				}
			}
		}
		return rivals;
	}

	/**
	 * Whether some other controller has a better claim to this rack than the one asking.
	 *
	 * <p>A sealed rival wins outright, whatever the distance — its room is a working machine and this is
	 * not a race. Between bare controllers the nearer wins, and the tie-break is on coordinates so both
	 * sides of the comparison agree.
	 */
	private static boolean claimedByAnother(BlockPos rack, BlockPos mine,
			List<ReactorControllerBlockEntity> rivals, int radius) {
		for (ReactorControllerBlockEntity rival : rivals) {
			if (rival.isRoomSealed()) {
				if (rival.sealedBoxContains(rack)) {
					return true;
				}
				// A sealed rival takes no interest in anything outside its own shell: its room scan walks
				// walls, not radii, so it never competes for a loose rack in the open.
				continue;
			}
			BlockPos theirs = rival.getBlockPos();
			if (within(rack, theirs, radius) && beats(theirs, mine, rack)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether controller {@code a} outranks controller {@code b} for this rack.
	 *
	 * <p>Distance first, then a plain lexicographic walk of the coordinates. <b>Not
	 * {@code BlockPos.asLong}</b> — that packing is not monotone in the coordinates, so two machines
	 * comparing through it can disagree about which of them is "smaller", and a rack both sides think
	 * they lost is a rack nobody burns (or, the other way round, one both sides claim).
	 */
	private static boolean beats(BlockPos a, BlockPos b, BlockPos rack) {
		// distSqr answers in double, but both operands here are whole-block positions, so every value it
		// can return is an exact integer and the comparison is not the floating-point trap it looks like.
		double da = a.distSqr(rack);
		double db = b.distSqr(rack);
		if (da != db) {
			return da < db;
		}
		if (a.getX() != b.getX()) {
			return a.getX() < b.getX();
		}
		if (a.getY() != b.getY()) {
			return a.getY() < b.getY();
		}
		return a.getZ() < b.getZ();
	}

	private static boolean within(BlockPos at, BlockPos centre, int radius) {
		return at.distSqr(centre) <= (long) radius * radius;
	}

	/** The loaded chunks covering a square of {@code radius} blocks around a point. */
	private static List<LevelChunk> chunksAround(ServerLevel level, BlockPos centre, int radius) {
		int minChunkX = SectionPos.blockToSectionCoord(centre.getX() - radius);
		int maxChunkX = SectionPos.blockToSectionCoord(centre.getX() + radius);
		int minChunkZ = SectionPos.blockToSectionCoord(centre.getZ() - radius);
		int maxChunkZ = SectionPos.blockToSectionCoord(centre.getZ() + radius);
		List<LevelChunk> chunks = new ArrayList<>();
		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				// getChunkNow, never getChunk: a scan that force-loads its neighbours turns one reactor
				// into a chunk loader, and a bare reactor is exactly the machine a player leaves running
				// in the middle of nowhere.
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk != null) {
					chunks.add(chunk);
				}
			}
		}
		return chunks;
	}
}
