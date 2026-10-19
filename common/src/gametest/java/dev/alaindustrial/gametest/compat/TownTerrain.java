package dev.alaindustrial.gametest.compat;

import dev.alaindustrial.compat.LineStructures;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Version facade (ADR-036) for laying an ALA town out off the world's own generation, the way the town game tests
 * do (MOD-830): a noise state for a seed, its climate sampler, the noise biome at a quart position and
 * {@code Structure.generate}. The signatures are the same on both lines; the bodies are this line's.
 *
 * <p><b>This twin: Minecraft 26.2</b>, where {@code RandomState.create} takes the settings first,
 * the state carries its climate sampler ({@code sampler()}), a biome source answers
 * {@code getNoiseBiome(x, y, z, sampler)} itself and {@code Structure.generate} takes no sampler (26.2 sources).
 * (26.3 takes the noise lookup first, builds the sampler with {@code createClimateSampler(SamplerContext)}, asks a
 * resolver from {@code createResolver(sampler)} and passes the sampler to {@code generate}.)
 */
public final class TownTerrain {

	private TownTerrain() {
	}

	/** The noise state of {@code seed} for the noise settings {@code noise}. */
	public static RandomState randomState(RegistryAccess access, Holder<NoiseGeneratorSettings> noise, long seed) {
		return RandomState.create(noise.value(), access.lookupOrThrow(Registries.NOISE), seed);
	}

	/** The climate sampler of {@code randomState}. */
	public static Climate.Sampler climate(RandomState randomState) {
		return randomState.sampler();
	}

	/** The noise biome {@code biomes} has at the quart position {@code (x, y, z)}. */
	public static Holder<Biome> noiseBiome(BiomeSource biomes, Climate.Sampler sampler, int x, int y, int z) {
		return biomes.getNoiseBiome(x, y, z, sampler);
	}

	/** {@code structure} laid out in {@code chunk} as world generation would, with its own biome test. */
	public static StructureStart generate(ServerLevel level, Holder<Structure> structure, ChunkGenerator generator,
			BiomeSource biomes, Climate.Sampler sampler, RandomState randomState, long seed, ChunkPos chunk) {
		return structure.value().generate(structure, level.dimension(), level.registryAccess(), generator, biomes,
				randomState, LineStructures.templates(level), seed, chunk, 0, level,
				structure.value().biomes()::contains);
	}
}
