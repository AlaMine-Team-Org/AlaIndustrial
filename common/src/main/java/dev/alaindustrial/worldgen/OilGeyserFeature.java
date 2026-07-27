package dev.alaindustrial.worldgen;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * {@code alaindustrial:oil_geyser} (MOD-248) — the rarest and largest oil deposit in the mod, built
 * as one connected object from the surface down to just above bedrock:
 *
 * <ol>
 *   <li><b>Spout.</b> One to a few oil sources standing above the terrain — the whole of what the
 *       deposit shows above ground. The oil that then oozes out of it and spreads a few cells across
 *       the terrain is ordinary fluid physics, not generated content: it is flowing oil, which no
 *       bucket can pick up.</li>
 *   <li><b>Shaft.</b> A one-block column of oil straight down to the dome, with the four horizontal
 *       neighbours of every level sealed <em>where they would leak</em>. Without the seal the shaft
 *       drains into the first cave or ravine it crosses and the player finds an empty hole. The seal
 *       stops below the terrain surface — see {@link #placeShaft}.</li>
 *   <li><b>Dome.</b> A sphere of oil sources at the bottom, wrapped in the same one-layer seal.</li>
 * </ol>
 *
 * <p><b>Why one feature and not three.</b> Sub-features (a {@code random_selector} or a sequence)
 * are placed independently and would each get their own origin, so the spout, the shaft and the
 * dome would end up in different columns. The three parts share an X/Z axis by construction, which
 * only holds if one {@code place} call draws all of them.
 *
 * <p><b>Why the 300-block vertical run is safe.</b> The ±1-chunk write window that caps the dome
 * radius ({@link OilGeyserConfiguration}) is horizontal only: {@code WorldGenRegion.ensureCanWrite}
 * tests chunk coordinates and imposes no Y limit outside the chunk-upgrade path, and writes outside
 * build height are dropped by {@code ProtoChunk} rather than logged. The feature still checks
 * {@code isInsideBuildHeight} itself so the drop never happens silently.
 */
public final class OilGeyserFeature extends Feature<OilGeyserConfiguration> {

	/** Registry id; the configured-feature JSON refers to the feature by this name. */
	public static final Identifier ID = Industrialization.id("oil_geyser");

	/** Stateless; one shared instance, registered by each loader. */
	public static final OilGeyserFeature INSTANCE = new OilGeyserFeature();

	/**
	 * Vertical clearance the shaft must have between the terrain and the top of the dome. Below this
	 * the "long descent" the deposit is built around stops existing and the geyser degenerates into a
	 * puddle sitting on a cavern, so the placement is dropped instead.
	 */
	private static final int MIN_SHAFT_LENGTH = 24;

	private OilGeyserFeature() {
		super(OilGeyserConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<OilGeyserConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		OilGeyserConfiguration config = context.config();
		BlockPos origin = context.origin();

		int surfaceY = origin.getY();
		int domeRadius = config.domeRadius().sample(random);
		// Clamp up if this dimension's floor is higher than the configured depth, then re-check that
		// what is left is still a shaft and not a dent in the ground.
		int domeCenterY = Math.max(config.domeCenterY(), level.getMinY() + domeRadius + 4);
		if (surfaceY - (domeCenterY + domeRadius) < MIN_SHAFT_LENGTH) {
			return false;
		}

		BlockState fluid = config.fluid().getState(level, random, origin);
		BlockState barrier = config.barrier().getState(level, random, origin);

		placeDome(level, config, new BlockPos(origin.getX(), domeCenterY, origin.getZ()), domeRadius,
				fluid, barrier);
		placeShaft(level, config, origin.getX(), origin.getZ(), domeCenterY + domeRadius - 1, surfaceY - 1,
				fluid, barrier);
		placeSpout(level, config, origin, config.spoutHeight().sample(random), fluid);
		return true;
	}

	/**
	 * A cell the shaft or the dome would drain through: anything that will not hold a fluid back.
	 *
	 * <p>Sealing rather than refusing is right HERE and wrong for {@link OilLakeFeature}: everything
	 * this feature seals sits hundreds of blocks underground, where "leak" means a cave or an
	 * aquifer and a stone wall is the correct answer. A lake, by contrast, can sit on a beach, where
	 * the "leak" is the sky and walling it off produces a stone dome on the sand.
	 *
	 * <p>Two things this predicate has to get right, both found by audit:
	 * <ul>
	 *   <li><b>Our own oil is not a leak.</b> The shaft starts one level inside the dome, so its
	 *       horizontal neighbours down there are dome interior that {@code placeDome} has already
	 *       flooded. Treating that as a leak walled four stones into the top of every dome.</li>
	 *   <li><b>"Not air and not fluid" is the wrong question.</b> Cave vines, moss carpet, glow
	 *       lichen and hanging roots are none of those and still let oil straight through. The
	 *       question is whether the cell will hold a fluid back, which is exactly
	 *       {@code isSolid()} — the same predicate the lake's site validation uses.</li>
	 * </ul>
	 */
	@SuppressWarnings("deprecation") // BlockStateBase#isSolid, soft-deprecated; see OilLakeFeature
	private static boolean leaks(WorldGenLevel level, BlockPos pos, BlockState ownFluid) {
		BlockState state = level.getBlockState(pos);
		return state != ownFluid && !state.isSolid();
	}

	/** Sphere of oil with a one-layer seal wherever the shell would otherwise open into a cavity. */
	private static void placeDome(WorldGenLevel level, OilGeyserConfiguration config, BlockPos center,
			int radius, BlockState fluid, BlockState barrier) {
		int inner = radius * radius;
		int outer = (radius + 1) * (radius + 1);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -radius - 1; dx <= radius + 1; dx++) {
			for (int dz = -radius - 1; dz <= radius + 1; dz++) {
				for (int dy = -radius - 1; dy <= radius + 1; dy++) {
					int distance = dx * dx + dy * dy + dz * dz;
					if (distance > outer) {
						continue;
					}
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (!level.isInsideBuildHeight(cursor.getY())
							|| !config.canReplace().test(level, cursor)) {
						continue;
					}
					if (distance <= inner) {
						level.setBlock(cursor, fluid, Block.UPDATE_CLIENTS);
					} else if (leaks(level, cursor, fluid)) {
						level.setBlock(cursor, barrier, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/**
	 * One-block column of oil from the dome up to the terrain, sealed against caves as it goes.
	 *
	 * <p><b>The seal stops below the surface (MOD-250).</b> Near the top of the shaft a horizontal
	 * neighbour is open sky or a plant, which the leak test rightly calls a leak — and the barrier put
	 * there was a stone block sitting in the grass beside the spout, the single most visible artefact
	 * of the whole deposit. A cell at or above its own column's terrain height is therefore never
	 * sealed: underground the shaft stays watertight, and at the surface the geyser is allowed to ooze,
	 * which is what a geyser does. "Terrain height" is read per neighbour column, not from the spout,
	 * so the rule holds on a slope too.
	 */
	private static void placeShaft(WorldGenLevel level, OilGeyserConfiguration config, int x, int z,
			int fromY, int toY, BlockState fluid, BlockState barrier) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
		for (int y = fromY; y <= toY; y++) {
			if (!level.isInsideBuildHeight(y)) {
				continue;
			}
			cursor.set(x, y, z);
			// Seal first: once the column cell holds oil, its own fluid state would make the
			// neighbours look wet and the leak test would stop being about the terrain.
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				int sideX = x + direction.getStepX();
				int sideZ = z + direction.getStepZ();
				// getHeight returns the first air Y, so the topmost terrain block is ground - 1.
				int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, sideX, sideZ);
				if (y >= ground - 1) {
					continue;
				}
				side.set(sideX, y, sideZ);
				if (leaks(level, side, fluid) && config.canReplace().test(level, side)) {
					level.setBlock(side, barrier, Block.UPDATE_CLIENTS);
				}
			}
			if (config.canReplace().test(level, cursor)) {
				level.setBlock(cursor, fluid, Block.UPDATE_CLIENTS);
			}
		}
	}

	/** The few oil sources standing above the terrain — the "it is erupting" read. */
	private static void placeSpout(WorldGenLevel level, OilGeyserConfiguration config, BlockPos origin,
			int height, BlockState fluid) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = 0; i < height; i++) {
			cursor.set(origin.getX(), origin.getY() + i, origin.getZ());
			if (level.isInsideBuildHeight(cursor.getY()) && config.canReplace().test(level, cursor)) {
				level.setBlock(cursor, fluid, Block.UPDATE_CLIENTS);
			}
		}
	}
}
