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
 * {@code alaindustrial:oil_geyser} (MOD-248) as the game sees it: the per-line adapter between the
 * {@code Feature} API and {@link OilGeyserPlacer}, which holds the whole placement and is the same source
 * on every Minecraft line (MOD-704). This class only takes the call and hands over what it carries.
 *
 * <p>The adapter is what differs between the lines — the same way as for {@link OilLakeFeature}: a record
 * of its {@link OilGeyserConfiguration} whose {@code MapCodec} is registered on 26.3, one registered
 * instance taking a {@code FeaturePlaceContext} on 26.2.
 */
public record OilGeyserFeature(OilGeyserConfiguration config) implements Feature {

	/** Registry id of the feature type; the configured-feature JSON refers to the feature by this name. */
	public static final Identifier ID = Industrialization.id("oil_geyser");

	/** Feature-type codec: the configuration's fields, inlined, under the keys the JSON already uses. */
	public static final MapCodec<OilGeyserFeature> CODEC =
			OilGeyserConfiguration.MAP_CODEC.xmap(OilGeyserFeature::new, OilGeyserFeature::config);

	@Override
	public MapCodec<OilGeyserFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random,
			BlockPos origin) {
		return OilGeyserPlacer.place(level, random, origin, config, config.fluid().value(),
				config.barrier().value());
	}
}
