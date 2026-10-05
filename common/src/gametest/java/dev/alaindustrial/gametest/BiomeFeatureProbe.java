package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Reads back which placed features a live biome generates, step by step — the one place the
 * gametests look at worldgen injection (MOD-702). The two loaders inject the mod's features by
 * different means (Fabric in code through {@code BiomeModifications}, NeoForge through datapack
 * biome modifiers), so a scenario must not look at either mechanism: it asks the biome, through its
 * generation settings, which is the only view both loaders share.
 */
public final class BiomeFeatureProbe {

	private BiomeFeatureProbe() {}

	/** The placed feature {@code alaindustrial:<path>}. */
	public static ResourceKey<PlacedFeature> placed(String path) {
		return ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id(path));
	}

	/** The biome's features at one generation step; a biome whose list stops short of it has none. */
	public static HolderSet<PlacedFeature> featuresAt(GameTestHelper helper, ResourceKey<Biome> biome,
			GenerationStep.Decoration step) {
		List<HolderSet<PlacedFeature>> steps = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME)
				.getOrThrow(biome).value().getGenerationSettings().features();
		int index = step.ordinal();
		return index < steps.size() ? steps.get(index) : HolderSet.direct();
	}

	/** Whether the biome generates the feature at the given step. */
	public static boolean has(GameTestHelper helper, ResourceKey<Biome> biome, GenerationStep.Decoration step,
			ResourceKey<PlacedFeature> feature) {
		return featuresAt(helper, biome, step).stream().anyMatch(holder -> holder.is(feature));
	}

	/** Every step at which the biome generates the feature; empty when it does not generate it at all. */
	public static List<GenerationStep.Decoration> stepsOf(GameTestHelper helper, ResourceKey<Biome> biome,
			ResourceKey<PlacedFeature> feature) {
		List<GenerationStep.Decoration> found = new ArrayList<>();
		for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
			if (has(helper, biome, step, feature)) {
				found.add(step);
			}
		}
		return found;
	}
}
