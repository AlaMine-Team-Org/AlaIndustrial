package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.worldgen.OilGeyserFeature;
import dev.alaindustrial.worldgen.OilLakeFeature;
import dev.alaindustrial.worldgen.OilLakeFilter;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Central registration for worldgen injection. The configured/placed feature JSON is data-driven
 * ({@code data/alaindustrial/worldgen/...}); this class only wires the placed feature into biomes.
 */
public final class ModWorldGen {
	private ModWorldGen() {
	}

	/**
	 * Biomes that generate the rare SURFACE oil patches (MOD-238). Defaults to the whole Overworld
	 * ({@code data/alaindustrial/tags/worldgen/biome/has_surface_oil_lakes.json}).
	 *
	 * <p>Split from the underground tag on purpose (MOD-238 audit): "keep the buried deposits, drop
	 * the surface blotches" is the single most common modpack request for an oil mod, and with one
	 * shared tag it is impossible without disabling oil entirely. Both tags default to the same
	 * contents, so nothing changes unless a pack overrides one of them.
	 */
	public static final TagKey<Biome> HAS_SURFACE_OIL_LAKES =
			TagKey.create(Registries.BIOME, Industrialization.id("has_surface_oil_lakes"));

	/**
	 * Biomes that generate the common UNDERGROUND oil lakes (MOD-238). Defaults to the whole
	 * Overworld ({@code data/alaindustrial/tags/worldgen/biome/has_underground_oil_lakes.json}).
	 * The NeoForge side reads the same two tags through its {@code neoforge/biome_modifier/} files.
	 */
	public static final TagKey<Biome> HAS_UNDERGROUND_OIL_LAKES =
			TagKey.create(Registries.BIOME, Industrialization.id("has_underground_oil_lakes"));

	/**
	 * Biomes that generate the rare oil geysers (MOD-248). A third tag rather than a reuse of the
	 * surface one: a geyser is a landmark, not a blotch, and "no oil puddles but keep the landmarks"
	 * is a different pack decision from "no surface oil at all".
	 */
	public static final TagKey<Biome> HAS_OIL_GEYSERS =
			TagKey.create(Registries.BIOME, Industrialization.id("has_oil_geysers"));

	/**
	 * Biomes that generate palladium ore (MOD-423). Defaults to {@code #c:is_nether}
	 * ({@code data/alaindustrial/tags/worldgen/biome/has_palladium_ore.json}), the convention tag both
	 * loaders ship, which already includes {@code #minecraft:is_nether}.
	 *
	 * <p>A mod tag rather than {@link BiomeSelectors#foundInTheNether()} on purpose (MOD-423 audit):
	 * that selector picks biomes by DIMENSION and NeoForge has no equivalent — its biome modifiers
	 * only accept an id, a list or a tag. Using it would silently generate the ore in untagged modded
	 * biomes on Fabric and not on NeoForge, and no gate in this repo compares the two worldgen paths.
	 * Going through a tag keeps both loaders on the same declaration and lets a pack switch the ore
	 * off symmetrically — which the hardcoded {@code foundInOverworld()} ore calls below still cannot.
	 */
	public static final TagKey<Biome> HAS_PALLADIUM_ORE =
			TagKey.create(Registries.BIOME, Industrialization.id("has_palladium_ore"));

	public static void init() {
		// MOD-238 audit: alaindustrial:oil_lake_filter, the placement modifier that keeps oil features
		// out of villages/mineshafts/Ancient Cities. Registered before any datapack load, because the
		// oil placed features name it and an unknown modifier type fails parsing. The same applies to
		// the two feature types (MOD-248): an unknown "type" in a configured feature is a parse error.
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, OilLakeFilter.ID, OilLakeFilter.TYPE);
		Registry.register(BuiltInRegistries.FEATURE, OilLakeFeature.ID, OilLakeFeature.INSTANCE);
		Registry.register(BuiltInRegistries.FEATURE, OilGeyserFeature.ID, OilGeyserFeature.INSTANCE);
		BiomeModifications.addFeature(
				BiomeSelectors.tag(HAS_UNDERGROUND_OIL_LAKES),
				GenerationStep.Decoration.LAKES,
				ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id("oil_lake_underground")));
		BiomeModifications.addFeature(
				BiomeSelectors.tag(HAS_UNDERGROUND_OIL_LAKES),
				GenerationStep.Decoration.LAKES,
				ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id("oil_lake_deep")));
		BiomeModifications.addFeature(
				BiomeSelectors.tag(HAS_SURFACE_OIL_LAKES),
				GenerationStep.Decoration.LAKES,
				ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id("oil_lake_surface")));
		BiomeModifications.addFeature(
				BiomeSelectors.tag(HAS_OIL_GEYSERS),
				GenerationStep.Decoration.LAKES,
				ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id("oil_geyser")));
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id("tin_ore")));
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id("silver_ore")));
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id("nickel_ore")));
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id("sulfur_ore")));
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id("uranium_ore")));
		// MOD-423 — the Nether ore. UNDERGROUND_ORES rather than vanilla's UNDERGROUND_DECORATION for
		// Nether ores: that step is empty in all five Nether biomes, so our feature is the only edge in
		// FeatureSorter's graph there — the cheapest way to stay clear of "Feature order cycle found",
		// which throws on world load. It also matches the step the NeoForge side declares.
		BiomeModifications.addFeature(
				BiomeSelectors.tag(HAS_PALLADIUM_ORE),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id("palladium_ore")));
	}
}
