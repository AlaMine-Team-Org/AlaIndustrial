package dev.alaindustrial.worldgen;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * {@code alaindustrial:oil_lake} (MOD-248) — a size-parameterised oil deposit, the mod's own
 * replacement for the deprecated and fixed-size {@code minecraft:lake}.
 *
 * <p>Three tiers ride on this one feature, differing only in configuration: the shallow surface
 * puddle, the mine-depth pool the player trips over while digging, and the rare deep reservoir. See
 * {@link OilLakeConfiguration} for why the vanilla feature could not carry them.
 *
 * <p>The geometry itself is a union of ellipsoids computed in the Minecraft-free
 * {@link OilLakeShape} (L1-tested); this class turns that grid into block writes in three passes,
 * in vanilla's order and for vanilla's reasons:
 * <ol>
 *   <li>{@linkplain #siteHoldsTheLake validate} every boundary cell and abandon the whole placement
 *       if the site cannot hold the deposit — nothing is written before this passes;</li>
 *   <li>hollow cells in the lower half become fluid, the upper half becomes cave air — the pocket
 *       above the oil is what makes an underground deposit read as a pool rather than a blob;</li>
 *   <li>solid ground bordering the pool is swapped for the barrier block, thinned to about half
 *       above the fluid line so the rim looks broken rather than built.</li>
 * </ol>
 *
 * <p>All writes use {@link Block#UPDATE_CLIENTS} (no neighbour updates), the same flag vanilla
 * features use: during decoration a neighbour update is both pointless and expensive, and for oil
 * it is actively harmful — {@code OilLiquidBlock} schedules an ignition check from
 * {@code neighborChanged}.
 */
public final class OilLakeFeature extends Feature<OilLakeConfiguration> {

	/** Registry id; the configured-feature JSON refers to the feature by this name. */
	public static final Identifier ID = Industrialization.id("oil_lake");

	/**
	 * Stateless, so one shared instance is all either loader ever registers — Fabric eagerly,
	 * NeoForge through a {@code DeferredRegister}. Mirrors how {@link OilLakeFilter} is shared.
	 */
	public static final OilLakeFeature INSTANCE = new OilLakeFeature();

	private OilLakeFeature() {
		super(OilLakeConfiguration.CODEC);
	}

	// BlockStateBase#isSolid is soft-deprecated ("go through the state, not the block") — and going
	// through the state is exactly what happens here. Vanilla's own LakeFeature calls it the same way
	// for the same decision, and there is no non-deprecated replacement that answers "will this cell
	// hold a fluid back".
	@SuppressWarnings("deprecation")
	@Override
	public boolean place(FeaturePlaceContext<OilLakeConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		OilLakeConfiguration config = context.config();
		BlockPos origin = context.origin();

		int horizontalRadius = config.horizontalRadius().sample(random);
		int verticalRadius = config.verticalRadius().sample(random);
		// Leave the bedrock shell alone: a lake whose floor lands on the world bottom has nothing to
		// sit in and would spill into the void layer.
		if (origin.getY() - verticalRadius <= level.getMinY() + 4) {
			return false;
		}
		if (!config.canPlaceFeature().test(level, origin)) {
			return false;
		}

		int width = OilLakeShape.width(horizontalRadius);
		int height = OilLakeShape.height(verticalRadius);
		boolean[] filled = OilLakeShape.build(horizontalRadius, verticalRadius,
				config.blobCount().sample(random), random::nextDouble);
		BlockPos base = origin.offset(-horizontalRadius, -verticalRadius, -horizontalRadius);

		BlockState fluid = config.fluid().getState(level, random, origin);
		BlockState barrier = config.barrier().getState(level, random, origin);
		BlockState caveAir = Blocks.CAVE_AIR.defaultBlockState();

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		if (!siteHoldsTheLake(level, config, filled, horizontalRadius, verticalRadius, width, height, base,
				fluid, cursor)) {
			return false;
		}

		boolean placedAnything = false;
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				for (int y = 0; y < height; y++) {
					if (!filled[OilLakeShape.index(horizontalRadius, verticalRadius, x, y, z)]) {
						continue;
					}
					cursor.set(base.getX() + x, base.getY() + y, base.getZ() + z);
					if (!level.isInsideBuildHeight(cursor.getY())
							|| !config.canReplaceWithAirOrFluid().test(level, cursor)) {
						continue;
					}
					boolean carvingAir = !OilLakeShape.isFluidLevel(verticalRadius, y);
					level.setBlock(cursor, carvingAir ? caveAir : fluid, Block.UPDATE_CLIENTS);
					placedAnything = true;
					if (carvingAir) {
						// Same follow-up vanilla's lake does after hollowing a cell: without it the
						// sand or gravel that was sitting on the removed block stays hanging in the air
						// until something disturbs it.
						level.scheduleTick(cursor, caveAir.getBlock(), 0);
						markAboveForPostProcessing(level, cursor);
					}
				}
			}
		}

		// Barrier pass, after the fluid is in.
		//
		// BELOW the fluid line the whole rim is converted, cave or not: that half is underground by
		// construction (the validation refused the site otherwise), so a gap there is a hole the
		// deposit would drain through and stone is the right answer.
		//
		// ABOVE it only solid ground is converted, and only about half of it. This is the line that
		// decides whether a surface deposit looks like a pool or like a boulder: the rim of the air
		// pocket is open sky, and converting THAT is what entombed the first version's beach lakes in
		// a stone dome. Thinning what remains keeps the edge broken rather than built.
		// A datapack may blank the barrier out; skip the pass rather than punch air into the rim.
		if (barrier.isAir()) {
			return placedAnything;
		}
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				for (int y = 0; y < height; y++) {
					if (filled[OilLakeShape.index(horizontalRadius, verticalRadius, x, y, z)]
							|| !OilLakeShape.bordersHollow(filled, horizontalRadius, verticalRadius,
									width, height, x, y, z)) {
						continue;
					}
					cursor.set(base.getX() + x, base.getY() + y, base.getZ() + z);
					if (!level.isInsideBuildHeight(cursor.getY())
							|| !config.canReplaceWithBarrier().test(level, cursor)) {
						continue;
					}
					boolean belowFluidLine = OilLakeShape.isFluidLevel(verticalRadius, y);
					if (belowFluidLine || (level.getBlockState(cursor).isSolid() && random.nextInt(2) != 0)) {
						level.setBlock(cursor, barrier, Block.UPDATE_CLIENTS);
						markAboveForPostProcessing(level, cursor);
					}
				}
			}
		}
		return placedAnything;
	}

	/**
	 * Whether the site can hold the lake at all, checked over every boundary cell BEFORE a single
	 * block is written.
	 *
	 * <p>This is the difference between a deposit and the stone bubble the first implementation
	 * produced. The instinct is to seal a leak by walling it off, and that is wrong in both
	 * directions: above ground the "leak" is the sky, so the lake ends up entombed in a stone dome
	 * sitting on the beach, and in an ocean the "leak" is the ocean, so a boulder appears at sea
	 * level. Vanilla's {@code LakeFeature} refuses the site instead, and that is what happens here:
	 *
	 * <ul>
	 *   <li>any boundary cell holding somebody else's <b>liquid</b> — an ocean, a river, a lava lake
	 *       — refuse, whichever half it is in;</li>
	 *   <li>a boundary cell in the fluid half that is neither solid nor our own oil <b>and sits at or
	 *       above the local terrain surface</b> means the basin is open to the sky or hanging off a
	 *       cliff — refuse.</li>
	 * </ul>
	 *
	 * <p>The liquid check must cover BOTH halves, and an audit caught the version that only checked
	 * the air pocket. In an ocean the placement heightmap ({@code WORLD_SURFACE_WG} counts water as
	 * ground) lands the origin at sea level, which puts the fluid half UNDER the water and the air
	 * pocket ABOVE it: the pocket sees only air, the "above the terrain" test never fires because the
	 * heightmap sits above those cells too, and the site is accepted. The barrier pass then turns the
	 * surrounding sea water into stone — a boulder floating at sea level, which is exactly the bug the
	 * whole validation exists to prevent.</p>
	 *
	 * <p>Air above ground is neither of those, so an ordinary surface puddle passes and simply gets
	 * no barrier over it — an open pool, the way a lava lake looks.
	 *
	 * <p><b>One deliberate deviation from vanilla — and the size limit on it.</b>
	 * {@code LakeFeature} refuses the site for ANY non-solid cell below the fluid line, cave
	 * included. That is affordable for a fixed 16 × 8 × 16 box; the deep reservoir tier here reaches
	 * a radius of 14, where the boundary is roughly four times the area and clipping a cave
	 * somewhere is close to certain — the whole 1/20 layer would quietly never generate. So an
	 * underground gap is allowed through and sealed by the barrier pass instead. The sky check is
	 * what keeps that from reintroducing the beach dome: below ground a "leak" is a cave and stone is
	 * correct, above ground it is the sky and stone is a boulder.
	 *
	 * <p>What the concession lacked until MOD-526 was a bound on HOW MUCH of the basin may be that
	 * cave. Sealing a corridor leaves a pocket in the rock; sealing a hall leaves a one-block stone
	 * bowl with oil in it hanging in mid-air, which is what a player photographed. The walk therefore
	 * counts the basin's boundary and how much of it is open, and hands the verdict to
	 * {@link OilLakeShape#basinHangsInTheOpen}. The share is measured, not guessed: over 601 accepted
	 * sites of a real world an ordinary deposit keeps it under a tenth, and the deposits past a
	 * quarter are the ones sitting inside a cavity rather than in the rock beside it.
	 */
	@SuppressWarnings("deprecation") // BlockStateBase#isSolid — see the note on place(...)
	private static boolean siteHoldsTheLake(WorldGenLevel level, OilLakeConfiguration config,
			boolean[] filled, int horizontalRadius, int verticalRadius, int width, int height,
			BlockPos base, BlockState fluid, BlockPos.MutableBlockPos cursor) {
		int basinBoundary = 0;
		int basinOpen = 0;
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {
				for (int y = 0; y < height; y++) {
					if (filled[OilLakeShape.index(horizontalRadius, verticalRadius, x, y, z)]
							|| !OilLakeShape.bordersHollow(filled, horizontalRadius, verticalRadius,
									width, height, x, y, z)) {
						continue;
					}
					cursor.set(base.getX() + x, base.getY() + y, base.getZ() + z);
					if (!level.isInsideBuildHeight(cursor.getY())) {
						return false;
					}
					BlockState state = level.getBlockState(cursor);
					boolean belowFluidLine = OilLakeShape.isFluidLevel(verticalRadius, y);
					if (belowFluidLine) {
						basinBoundary++;
					}
					if (state != fluid) {
						// Somebody else's liquid anywhere on the boundary — an ocean, a river, a lava
						// lake. Refuse whichever half it touches: below the fluid line it cannot hold
						// the deposit back, and above it the air pocket would open straight into it.
						if (state.liquid()) {
							return false;
						}
						// Below the fluid line the basin must be closed. A gap is tolerated ONLY
						// underground, where it is a cave the barrier pass can seal; at or above the
						// terrain it is open sky or a cliff face, and walling that off is what turns a
						// beach puddle into a stone boulder.
						if (belowFluidLine && !state.isSolid()) {
							basinOpen++;
							if (cursor.getY() >= level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,
									cursor.getX(), cursor.getZ())) {
								return false;
							}
						}
					}
					if (!config.canPlaceFeature().test(level, cursor)) {
						return false;
					}
				}
			}
		}
		// How much of the basin was allowed to be a cave is the whole of MOD-526: sealing a corridor
		// gives a pocket in the rock, sealing a hall gives a stone bowl hanging in mid-air.
		return !OilLakeShape.basinHangsInTheOpen(basinOpen, basinBoundary);
	}
}
