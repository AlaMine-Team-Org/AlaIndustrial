package dev.alaindustrial.gametest.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Version facade (ADR-036) for running one of the mod's configured worldgen features by id, as worldgen
 * would: the feature is read from the server's registry (so the shipped JSON configuration is what runs)
 * and placed with the given random source at the given origin. The signature of {@link #place} is the same
 * on both lines; the body is this line's.
 *
 * <p><b>This twin: Minecraft 26.2</b>, where a configured feature is a {@link ConfiguredFeature} — a
 * {@code Feature} paired with its configuration — held by {@code Registries.CONFIGURED_FEATURE} (read from
 * {@code worldgen/configured_feature/*.json}), and
 * {@code ConfiguredFeature.place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)} runs it (verified by
 * javap of the 26.2 {@code minecraft-merged.jar}).
 *
 * <p>It lives in {@code dev.alaindustrial.gametest.compat}: the gametest source set is its own JPMS module
 * on NeoForge (ADR-036, "Boundaries").
 */
public final class FeaturePlacement {

	private FeaturePlacement() {}

	/** Places the configured feature {@code id} at {@code origin}; what the feature itself returns. */
	public static boolean place(ServerLevel level, Identifier id, RandomSource random, BlockPos origin) {
		ConfiguredFeature<?, ?> feature = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE)
				.getValueOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE, id));
		return feature.place(level, level.getChunkSource().getGenerator(), random, origin);
	}
}
