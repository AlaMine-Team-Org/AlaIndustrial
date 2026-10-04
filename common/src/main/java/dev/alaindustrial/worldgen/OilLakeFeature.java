package dev.alaindustrial.worldgen;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * {@code alaindustrial:oil_lake} (MOD-248) as the game sees it: the per-line adapter between the
 * {@code Feature} API and {@link OilLakePlacer}, which holds the whole placement and is the same source on
 * every Minecraft line (MOD-704). This class only takes the call and hands over what it carries.
 *
 * <p>The adapter is what differs between the lines. On 26.3 a {@code Feature} is an interface and IS its
 * own configuration: the feature is a record of its {@link OilLakeConfiguration}, the loaders register its
 * {@code MapCodec}, {@code place} takes the level, chunk generator, random source and origin as arguments,
 * and the configuration holds its state providers as {@code Holder}s. On 26.2 a {@code Feature} is a class
 * parameterised by its configuration: one stateless instance is registered, {@code place} takes a
 * {@code FeaturePlaceContext}, and the configuration holds its providers directly.
 */
public record OilLakeFeature(OilLakeConfiguration config) implements Feature {

	/** Registry id of the feature type; the configured-feature JSON refers to the feature by this name. */
	public static final Identifier ID = Industrialization.id("oil_lake");

	/** Feature-type codec: the configuration's fields, inlined, exactly as the JSON already spells them. */
	public static final MapCodec<OilLakeFeature> CODEC =
			OilLakeConfiguration.MAP_CODEC.xmap(OilLakeFeature::new, OilLakeFeature::config);

	@Override
	public MapCodec<OilLakeFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random,
			BlockPos origin) {
		return OilLakePlacer.place(level, random, origin, config, config.fluid().value(), config.barrier().value());
	}
}
