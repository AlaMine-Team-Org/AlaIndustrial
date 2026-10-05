package dev.alaindustrial.registry;

import dev.alaindustrial.worldgen.AbandonedLabFeature;
import dev.alaindustrial.worldgen.OilGeyserFeature;
import dev.alaindustrial.worldgen.OilLakeFeature;
import dev.alaindustrial.worldgen.OilLakeFilter;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Central registration for worldgen injection. The configured/placed feature JSON is data-driven
 * ({@code data/alaindustrial/worldgen/...}); this class registers the mod's feature and placement-modifier
 * types and wires the placed features into biomes by replaying the shared table
 * {@link WorldgenInjections#INJECTIONS} (MOD-708).
 */
public final class ModWorldGen {
	private ModWorldGen() {
	}

	public static void init() {
		// MOD-238 audit: alaindustrial:oil_lake_filter, the placement modifier that keeps oil features
		// out of villages/mineshafts/Ancient Cities. Registered before any datapack load, because the
		// oil placed features name it and an unknown modifier type fails parsing. The same applies to
		// the two feature types (MOD-248): an unknown "type" in a configured feature is a parse error.
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, OilLakeFilter.ID, OilLakeFilter.TYPE);
		Registry.register(BuiltInRegistries.FEATURE, OilLakeFeature.ID, OilLakeFeature.INSTANCE);
		Registry.register(BuiltInRegistries.FEATURE, OilGeyserFeature.ID, OilGeyserFeature.INSTANCE);
		Registry.register(BuiltInRegistries.FEATURE, AbandonedLabFeature.ID, AbandonedLabFeature.INSTANCE);
		// MOD-708: which placed feature goes into which biomes at which step is the shared table
		// WorldgenInjections.INJECTIONS; NeoForge's biome modifier files are written from the same rows.
		for (WorldgenInjections.Injection injection : WorldgenInjections.INJECTIONS) {
			BiomeModifications.addFeature(fabricSelector(injection), injection.step(), injection.feature());
		}
	}

	/** The biomes of a row as Fabric has always picked them: by its tag, or by the overworld's biome source. */
	private static Predicate<BiomeSelectionContext> fabricSelector(WorldgenInjections.Injection injection) {
		return switch (injection.fabricSelector()) {
			case BIOME_TAG -> BiomeSelectors.tag(injection.biomes());
			case FOUND_IN_OVERWORLD -> BiomeSelectors.foundInOverworld();
		};
	}
}
