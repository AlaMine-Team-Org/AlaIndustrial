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
 * {@code alaindustrial:abandoned_lab} (MOD-513) as the game sees it: the per-line adapter between the
 * {@code Feature} API and {@link AbandonedLabPlacer}, which holds the whole placement and is the same
 * source on every Minecraft line (MOD-704). This class only takes the call and hands over what it needs.
 *
 * <p>The adapter is what differs between the lines — the same way as for {@link OilLakeFeature}, with no
 * configuration (an empty record and a unit codec on 26.3, {@code NoneFeatureConfiguration} on 26.2) —
 * plus the server's structure-template manager, {@code getStructureTemplateManager()} on 26.3 and
 * {@code getStructureManager()} on 26.2.
 */
public record AbandonedLabFeature() implements Feature {

	/** Registry id of the feature type; the configured-feature JSON refers to the feature by this name. */
	public static final Identifier ID = Industrialization.id("abandoned_lab");

	/**
	 * The feature takes no configuration, which in 26.3 is a unit codec rather than the deleted
	 * {@code NoneFeatureConfiguration} — so the JSON is the {@code "type"} line and nothing else.
	 */
	public static final MapCodec<AbandonedLabFeature> CODEC = MapCodec.unit(AbandonedLabFeature::new);

	@Override
	public MapCodec<AbandonedLabFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random,
			BlockPos origin) {
		return AbandonedLabPlacer.place(level, random, origin,
				level.getLevel().getServer().getStructureTemplateManager());
	}
}
