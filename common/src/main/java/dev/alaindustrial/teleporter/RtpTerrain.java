package dev.alaindustrial.teleporter;

import dev.alaindustrial.core.teleport.RtpSiteFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The world half of the random-jump search (MOD-116) — everything {@link RtpSiteFinder} deliberately
 * refuses to know about.
 *
 * <p><b>Why there are two height probes and not one.</b> {@code Level#getHeight} does NOT generate a
 * missing chunk: for a chunk that is not loaded it silently answers {@code getMinY()} — the bottom of
 * the world (verified against the 26.2 source, {@code Level#getHeight}). A random point 5000 blocks
 * out is essentially never loaded, so a search built on that method alone would read "ground at
 * y=-64" for every candidate and either refuse forever or aim the player at bedrock. The chunk has to
 * be forced, and forcing one on ground nobody has visited runs the whole worldgen pipeline on the
 * server thread.
 *
 * <p>So {@link #noiseGroundY} asks the chunk GENERATOR instead of the world:
 * {@code ChunkGenerator#getBaseHeight} samples the terrain noise for a single column and generates
 * nothing. It cannot see trees, structures or anything a player has built, which is exactly why it is
 * only ever used to throw candidates away — the survivor is then measured properly by
 * {@link #safeFeetY}, which is the one that pays for a chunk.
 */
final class RtpTerrain implements RtpSiteFinder.Terrain {

	private final ServerLevel level;

	RtpTerrain(ServerLevel level) {
		this.level = level;
	}

	/**
	 * Solid ground as the generator's noise sees it, without touching the chunk store.
	 *
	 * <p>{@code OCEAN_FLOOR_WG} rather than {@code WORLD_SURFACE_WG}: its predicate is
	 * {@code blocksMotion}, so fluids do not count and the answer is the sea BED under an ocean rather
	 * than the water's surface. That is what makes the caller's "is this above sea level?" test mean
	 * "is this dry land?".
	 */
	@Override
	public int noiseGroundY(int x, int z) {
		ServerChunkCache chunks = level.getChunkSource();
		return chunks.getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level,
				chunks.randomState());
	}

	/**
	 * The real landing height, loading (and if need be generating) the chunk to get it.
	 *
	 * <p>Uses {@code MOTION_BLOCKING_NO_LEAVES} so a canopy is not mistaken for ground — landing on
	 * leaves drops the player through them. The block below must hold them up, the two blocks they
	 * occupy must be clear of both blocks and fluid, and a short list of surfaces that would hurt on
	 * arrival is refused outright.
	 */
	// MOD-498 — BlockStateBase#blocksMotion() is a soft-deprecated legacy solidity heuristic (it defers to
	// the equally deprecated isSolid(), backed by the legacySolid field, with hardcoded cobweb and
	// bamboo-sapling exceptions). Nothing models it: getCollisionShape / isCollisionShapeFullBlock answer
	// a different question. Vanilla itself reads it from non-deprecated code — Heightmap.MOTION_BLOCKING
	// and the default isSuffocating predicate — and "can the player stand on this" is exactly that notion.
	@SuppressWarnings("deprecation")
	@Override
	public int safeFeetY(int x, int z) {
		BlockPos column = new BlockPos(x, level.getSeaLevel(), z);
		level.getChunkAt(column); // the one deliberate, paid-for chunk load per surviving candidate
		int feet = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		// A column with nothing in it answers minY; treat that as "no answer", never as ground.
		if (feet <= level.getMinY() + 1 || feet >= level.getMaxY() - 1) {
			return RtpSiteFinder.NO_SITE;
		}

		BlockPos feetPos = new BlockPos(x, feet, z);
		BlockState ground = level.getBlockState(feetPos.below());
		if (!ground.blocksMotion() || !ground.getFluidState().isEmpty() || isHostileFooting(ground)) {
			return RtpSiteFinder.NO_SITE;
		}
		if (!isClear(feetPos) || !isClear(feetPos.above())) {
			return RtpSiteFinder.NO_SITE;
		}
		return feet;
	}

	@Override
	public boolean isInsideBorder(int x, int z) {
		return level.getWorldBorder().isWithinBounds((double) x, (double) z);
	}

	/** Room for the player: no block in the way and no fluid to arrive inside of. */
	// MOD-498 — the negative half of the same legacy solidity heuristic used in safeFeetY above:
	// blocksMotion() is soft-deprecated with no modelled replacement, and vanilla still reads it from
	// non-deprecated code (Heightmap.MOTION_BLOCKING). It has to be the SAME predicate as the ground
	// check, or a cell could count as both solid footing and free headroom.
	@SuppressWarnings("deprecation")
	private boolean isClear(BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.blocksMotion() && state.getFluidState().isEmpty()
				&& !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)
				&& !state.is(Blocks.POWDER_SNOW) && !state.is(Blocks.SWEET_BERRY_BUSH);
	}

	/** Ground that holds weight but hurts whoever lands on it. */
	private static boolean isHostileFooting(BlockState ground) {
		return ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS)
				|| ground.is(Blocks.CAMPFIRE) || ground.is(Blocks.SOUL_CAMPFIRE)
				|| ground.is(Blocks.POINTED_DRIPSTONE) || ground.is(Blocks.WITHER_ROSE);
	}
}
