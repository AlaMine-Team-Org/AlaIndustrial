package dev.alaindustrial.gametest.compat;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Version facade (ADR-036) for painting one biome over a whole chunk, the way {@code /fillbiome} does, so a
 * gametest that builds its own site does not depend on the biome the lane's test world happens to have
 * there (the two lines' gametest worlds are generated differently). The signature of {@link #fill} is the
 * same on both lines; the body is this line's.
 *
 * <p><b>This twin: Minecraft 26.2</b>, where {@code ChunkAccess.fillBiomesFromNoise} takes a
 * {@code BiomeResolver} whose one method is {@code getNoiseBiome(int, int, int, Climate.Sampler)} plus the
 * sampler to hand it — the level's own, from its {@code RandomState} (verified by javap of the 26.2
 * {@code minecraft-merged.jar}). The resolver ignores the sampler.
 */
public final class SiteBiome {

	private SiteBiome() {}

	/** Every biome cell of {@code chunk} becomes {@code biome}; the chunk is marked for saving. */
	public static void fill(ServerLevel level, ChunkAccess chunk, Holder<Biome> biome) {
		chunk.fillBiomesFromNoise((x, y, z, sampler) -> biome, level.getChunkSource().randomState().sampler());
		chunk.markUnsaved();
	}
}
