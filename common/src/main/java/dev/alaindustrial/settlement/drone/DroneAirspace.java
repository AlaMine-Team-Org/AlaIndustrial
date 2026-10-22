package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.core.drone.DroneRoute;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The air a builder drone flies through (MOD-858, owner's round 2: «the drone never flies through blocks»): which cell
 * is open to it, and the route from its nest to the cell next to the block it works ({@link DroneRoute}).
 *
 * <p><b>Open</b> is a cell with no collision, or one so low the drone hovers over it (a carpet, a nest: at most
 * {@link #LOW_TOP} high), or a doorway — a door, a trapdoor, a fence gate, open or shut: the drone slips through the
 * hall's door as a player would walk through it. Never lava, never a cell whose chunk is not loaded (a drone never
 * loads a chunk for its own sake), and never a cell the {@code loaded} probe of a game test says is gone.
 *
 * <p><b>The cost is bounded</b>: the search box is the nest and the target with {@link #MARGIN} blocks around and
 * {@link #HEADROOM} over the higher of them, the search stops after {@link #MAX_NODES} cells, and a nest spreads it
 * over ticks, {@link #NODES_PER_TICK} cells a tick. The measured numbers are in MOD-858's research.
 */
public final class DroneAirspace {

	/** Blocks of room the search box keeps sideways around the nest and the target. */
	public static final int MARGIN = 5;
	/** Blocks the search box reaches under the lower of the nest and the target. */
	public static final int BELOW = 3;
	/** Blocks the search box reaches over the higher of the nest and the target. */
	public static final int HEADROOM = 6;
	/** Blocks of room the search box keeps round the building the drone works on: out of its door and over its roof. */
	public static final int AROUND_BUILDING = 2;
	/** Cells one route search may expand at most. */
	public static final int MAX_NODES = 6000;
	/** Cells a route search expands in one tick: a longer search goes on next tick. */
	public static final int NODES_PER_TICK = 800;
	/** The highest collision top a cell may have and still be flown over, in blocks: a nest's plate, a carpet. */
	public static final double LOW_TOP = 3.0 / 16.0 + 1.0E-3;

	private DroneAirspace() {
	}

	/** Whether the drone may fly through {@code pos}; {@code loaded} answers whether its chunk is loaded. */
	public static boolean open(ServerLevel level, BlockPos pos, Predicate<BlockPos> loaded) {
		return loaded.test(pos) && open(level, pos, level.getBlockState(pos));
	}

	/** Whether a loaded cell holding {@code state} is open to the drone. */
	private static boolean open(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (state.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}
		Block block = state.getBlock();
		if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
			return true;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= LOW_TOP;
	}

	/**
	 * The route from the nest at {@code nest} to an open face-neighbour of {@code target}, searched in one go. With no
	 * {@code probe} a cell is loaded when its chunk is ({@link #chunkCells}); a game test's probe answers instead.
	 */
	public static DroneRoute route(ServerLevel level, BlockPos nest, BlockPos target,
			@Nullable Predicate<BlockPos> probe) {
		DroneRoute.Planner planner = plan(level, nest, target, probe);
		planner.advance(Integer.MAX_VALUE);
		return planner.result();
	}

	/** The same search, to be carried out {@link #NODES_PER_TICK} cells a tick ({@link DroneRoute.Planner#advance}). */
	public static DroneRoute.Planner plan(ServerLevel level, BlockPos nest, BlockPos target,
			@Nullable Predicate<BlockPos> probe) {
		return plan(level, nest, target, probe, null);
	}

	/**
	 * The same search with its box reaching over {@code building} too, {@link #AROUND_BUILDING} blocks round it
	 * (owner's round 3): a hall's fittings lie inside, and the way in — its door — may lie farther than
	 * {@link #MARGIN} from both the nest and the target. {@code building} null: the box of the nest and the target.
	 */
	public static DroneRoute.Planner plan(ServerLevel level, BlockPos nest, BlockPos target,
			@Nullable Predicate<BlockPos> probe, @Nullable BoundingBox building) {
		int minX = Math.min(nest.getX(), target.getX()) - MARGIN;
		int minZ = Math.min(nest.getZ(), target.getZ()) - MARGIN;
		int maxX = Math.max(nest.getX(), target.getX()) + MARGIN;
		int maxZ = Math.max(nest.getZ(), target.getZ()) + MARGIN;
		int low = Math.min(nest.getY(), target.getY()) - BELOW;
		int high = Math.max(nest.getY(), target.getY()) + HEADROOM;
		if (building != null) {
			minX = Math.min(minX, building.minX() - AROUND_BUILDING);
			minZ = Math.min(minZ, building.minZ() - AROUND_BUILDING);
			maxX = Math.max(maxX, building.maxX() + AROUND_BUILDING);
			maxZ = Math.max(maxZ, building.maxZ() + AROUND_BUILDING);
			low = Math.min(low, building.minY());
			high = Math.max(high, building.maxY() + AROUND_BUILDING);
		}
		int minY = Math.max(level.getMinY(), low);
		int maxY = Math.min(level.getMaxY(), high);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		DroneRoute.Cells cells = probe == null ? chunkCells(level, minX, minZ, maxX, maxZ)
				: (x, y, z) -> open(level, at.set(x, y, z), probe);
		return DroneRoute.plan(nest.getX(), nest.getY(), nest.getZ(), target.getX(), target.getY(), target.getZ(),
				cells, minX, minY, minZ, maxX, maxY, maxZ, MAX_NODES);
	}

	/**
	 * Open cells read straight from the loaded chunks of the box, each chunk looked up once per search: a cell of a
	 * chunk that is not loaded is closed, and nothing is ever loaded for it ({@link ServerChunkCache#getChunkNow}).
	 */
	private static DroneRoute.Cells chunkCells(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
		int baseX = minX >> 4;
		int baseZ = minZ >> 4;
		int spanX = (maxX >> 4) - baseX + 1;
		int spanZ = (maxZ >> 4) - baseZ + 1;
		LevelChunk[] chunks = new LevelChunk[spanX * spanZ];
		boolean[] looked = new boolean[spanX * spanZ];
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		return (x, y, z) -> {
			int cx = (x >> 4) - baseX;
			int cz = (z >> 4) - baseZ;
			if (cx < 0 || cz < 0 || cx >= spanX || cz >= spanZ) {
				return false;
			}
			int i = cx * spanZ + cz;
			if (!looked[i]) {
				looked[i] = true;
				chunks[i] = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
			}
			if (chunks[i] == null) {
				return false;
			}
			at.set(x, y, z);
			return open(level, at, chunks[i].getBlockState(at));
		};
	}
}
