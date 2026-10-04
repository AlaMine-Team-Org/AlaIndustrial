package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Which placed feature of the mod is generated in which biomes, at which generation step — declared once
 * (MOD-708) for both loaders. The placed and configured features themselves are data
 * ({@code data/alaindustrial/worldgen/...}); this table only says where they go.
 *
 * <p>Fabric replays {@link #INJECTIONS} in code: {@code ModWorldGen.init} makes one
 * {@code BiomeModifications.addFeature} per row. NeoForge injects through data, the biome modifier files
 * {@code neoforge/src/main/resources/data/alaindustrial/neoforge/biome_modifier/<name>.json}, and those
 * files are written FROM this table: one file per {@link Injection#neoForgeModifier() modifier name}, its
 * features in row order. The NeoForge L1.5 test {@code BiomeModifierTableTest} fails while a file differs
 * from what the table writes, and its javadoc holds the command that rewrites them.
 *
 * <p>A new injected feature is one row here, then that command; no loader source changes.
 *
 * <p><b>The selectors are the ones each loader had.</b> Seven rows name a biome tag of the mod, which both
 * loaders read. The five overworld ores are picked by {@code #minecraft:is_overworld} on NeoForge but by
 * {@code BiomeSelectors.foundInOverworld()} on Fabric ({@link FabricSelector#FOUND_IN_OVERWORLD}): the
 * biomes the overworld's biome source can generate, which leaves out an overworld biome no generator places
 * and takes a modded biome that lacks the tag. Moving them to one selector changes where ores generate, so
 * it is a decision of its own, not part of this table.
 */
public final class WorldgenInjections {
	private WorldgenInjections() {
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
	public static final TagKey<Biome> HAS_SURFACE_OIL_LAKES = tag("has_surface_oil_lakes");

	/**
	 * Biomes that generate the common UNDERGROUND oil lakes (MOD-238). Defaults to the whole
	 * Overworld ({@code data/alaindustrial/tags/worldgen/biome/has_underground_oil_lakes.json}).
	 */
	public static final TagKey<Biome> HAS_UNDERGROUND_OIL_LAKES = tag("has_underground_oil_lakes");

	/**
	 * Biomes that generate the rare oil geysers (MOD-248). A third tag rather than a reuse of the
	 * surface one: a geyser is a landmark, not a blotch, and "no oil puddles but keep the landmarks"
	 * is a different pack decision from "no surface oil at all".
	 */
	public static final TagKey<Biome> HAS_OIL_GEYSERS = tag("has_oil_geysers");

	/**
	 * Biomes that generate palladium ore (MOD-423). Defaults to {@code #c:is_nether}
	 * ({@code data/alaindustrial/tags/worldgen/biome/has_palladium_ore.json}), the convention tag both
	 * loaders ship, which already includes {@code #minecraft:is_nether}.
	 *
	 * <p>A mod tag rather than Fabric's {@code BiomeSelectors.foundInTheNether()} on purpose (MOD-423
	 * audit): that selector picks biomes by DIMENSION and NeoForge has no equivalent — its biome modifiers
	 * only accept an id, a list or a tag. Using it would silently generate the ore in untagged modded
	 * biomes on Fabric and not on NeoForge. Going through a tag keeps both loaders on the same declaration
	 * and lets a pack switch the ore off symmetrically — which the overworld ores, picked by
	 * {@link FabricSelector#FOUND_IN_OVERWORLD} on Fabric, still cannot.
	 */
	public static final TagKey<Biome> HAS_PALLADIUM_ORE = tag("has_palladium_ore");

	/**
	 * Biomes where wild kok-sagyz grows (MOD-537, narrowed in MOD-584). The set is six explicit
	 * vanilla ids in {@code data/alaindustrial/tags/worldgen/biome/has_kok_sagyz.json} — NOT the
	 * {@code #c:is_plains}/{@code #c:is_savanna} convention tags an earlier version of this comment
	 * claimed; those are wider and would drag in snowy plains and other mods' biomes.
	 *
	 * <p>Dry savanna for the steppe look, plus meadow and the two windswept hill biomes: the real
	 * species is an endemic of the Tien Shan intermountain valleys at 1800-2000 m, on meadows and
	 * gravelly slopes. Plains were dropped in MOD-584 — ordinary green lowland is the one place it
	 * has no business being. Desert was considered and rejected: the plant is a mesophyte with a
	 * high moisture requirement that survives drought by going dormant, not by thriving.
	 *
	 * <p>The placed feature spawns mature plants (age 3); the roots regrow from random ticks
	 * because the plants stand on dirt/grass.
	 */
	public static final TagKey<Biome> HAS_KOK_SAGYZ = tag("has_kok_sagyz");

	/**
	 * Biomes where the abandoned lab of the lore can appear (MOD-513). Defaults to the forty land
	 * biomes the entrance camouflage was approved for
	 * ({@code data/alaindustrial/tags/worldgen/biome/has_abandoned_labs.json}); a pack can add a modded
	 * biome (it gets the plains camouflage) or empty the tag to switch the labs off.
	 */
	public static final TagKey<Biome> HAS_ABANDONED_LABS = tag("has_abandoned_labs");

	/** How Fabric picks the biomes of a row. */
	public enum FabricSelector {
		/** {@code BiomeSelectors.tag(biomes)} — the same tag NeoForge names. */
		BIOME_TAG,
		/**
		 * {@code BiomeSelectors.foundInOverworld()} — the biomes the overworld's biome source can generate,
		 * where NeoForge names the row's tag ({@code #minecraft:is_overworld}).
		 */
		FOUND_IN_OVERWORLD
	}

	/**
	 * One placed feature injected into biomes.
	 *
	 * @param feature          the placed feature {@code alaindustrial:<path>}
	 * @param step             the generation step it runs at
	 * @param biomes           the biome tag NeoForge names; Fabric's too, unless {@code fabricSelector} says
	 *                         otherwise
	 * @param fabricSelector   how Fabric picks the biomes
	 * @param neoForgeModifier the name of the NeoForge biome modifier file the feature is listed in; rows
	 *                         sharing one name share its step and biomes, and the file lists them in row order
	 */
	public record Injection(ResourceKey<PlacedFeature> feature, GenerationStep.Decoration step, TagKey<Biome> biomes,
			FabricSelector fabricSelector, String neoForgeModifier) {
		public Injection {
			Objects.requireNonNull(feature, "feature");
			Objects.requireNonNull(step, "step");
			Objects.requireNonNull(biomes, "biomes");
			Objects.requireNonNull(fabricSelector, "fabricSelector");
			Objects.requireNonNull(neoForgeModifier, "neoForgeModifier");
		}
	}

	/** Every injection, in the order Fabric registers them and NeoForge's shared files list them. */
	public static final List<Injection> INJECTIONS = List.of(
			byTag("oil_lake_underground", GenerationStep.Decoration.LAKES, HAS_UNDERGROUND_OIL_LAKES,
					"oil_lakes_underground"),
			byTag("oil_lake_deep", GenerationStep.Decoration.LAKES, HAS_UNDERGROUND_OIL_LAKES, "oil_lakes_deep"),
			byTag("oil_lake_surface", GenerationStep.Decoration.LAKES, HAS_SURFACE_OIL_LAKES, "oil_lakes_surface"),
			byTag("oil_geyser", GenerationStep.Decoration.LAKES, HAS_OIL_GEYSERS, "oil_geysers"),
			overworldOre("tin_ore"),
			overworldOre("silver_ore"),
			overworldOre("nickel_ore"),
			overworldOre("sulfur_ore"),
			overworldOre("uranium_ore"),
			// MOD-423 — the Nether ore. UNDERGROUND_ORES rather than vanilla's UNDERGROUND_DECORATION for
			// Nether ores: that step is empty in all five Nether biomes, so this feature is the only edge in
			// FeatureSorter's graph there — the cheapest way to stay clear of "Feature order cycle found",
			// which throws on world load.
			byTag("palladium_ore", GenerationStep.Decoration.UNDERGROUND_ORES, HAS_PALLADIUM_ORE, "nether_ores"),
			// MOD-513 — the abandoned lab. SURFACE_STRUCTURES, the step the entrance spec names: after the
			// surface is laid, before trees and grass, so the site is judged on bare ground.
			byTag("abandoned_lab", GenerationStep.Decoration.SURFACE_STRUCTURES, HAS_ABANDONED_LABS,
					"abandoned_labs"),
			byTag("kok_sagyz", GenerationStep.Decoration.VEGETAL_DECORATION, HAS_KOK_SAGYZ, "kok_sagyz"));

	/** A feature both loaders inject into the biomes of one tag. */
	private static Injection byTag(String feature, GenerationStep.Decoration step, TagKey<Biome> biomes,
			String neoForgeModifier) {
		return new Injection(placed(feature), step, biomes, FabricSelector.BIOME_TAG, neoForgeModifier);
	}

	/** An overworld ore: NeoForge's shared {@code ores} file by tag, Fabric by the overworld's biome source. */
	private static Injection overworldOre(String feature) {
		return new Injection(placed(feature), GenerationStep.Decoration.UNDERGROUND_ORES, BiomeTags.IS_OVERWORLD,
				FabricSelector.FOUND_IN_OVERWORLD, "ores");
	}

	private static ResourceKey<PlacedFeature> placed(String path) {
		return ResourceKey.create(Registries.PLACED_FEATURE, Industrialization.id(path));
	}

	private static TagKey<Biome> tag(String path) {
		return TagKey.create(Registries.BIOME, Industrialization.id(path));
	}
}
