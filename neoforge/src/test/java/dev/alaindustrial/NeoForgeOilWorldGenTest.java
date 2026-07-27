package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.neoforge.ModFluidsNeoForge;
import dev.alaindustrial.worldgen.OilLakeFilter;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MOD-238 audit — NeoForge L1 guards for the parts of the oil feature that live in registries and
 * datapack files rather than in world behaviour, so no gametest can reach them.
 *
 * <p>Boots a real {@link MinecraftServer} through {@link EphemeralTestServerProvider}, which means
 * every mod-bus {@code RegisterEvent} has fired and the datapacks are loaded — the placed features,
 * the structure tag and the fluid type are asserted as the game itself would see them. Four
 * regressions are covered:
 *
 * <ul>
 *   <li>the {@code alaindustrial:oil_lake_filter} placement modifier type is registered and BOTH
 *       oil-lake placed features end with it (the structural filter);</li>
 *   <li>the {@code #alaindustrial:no_oil_lakes} structure tag resolves and actually names the
 *       structures it is supposed to protect;</li>
 *   <li>the surface and underground biome tags are separate keys (a modpack must be able to keep one
 *       and drop the other);</li>
 *   <li>the oil {@link FluidType} declares its entity physics explicitly, and {@code oil_bucket} is
 *       EXACTLY a {@link BucketItem}.</li>
 * </ul>
 */
@ExtendWith(EphemeralTestServerProvider.class)
class NeoForgeOilWorldGenTest {

	private static Identifier id(String path) {
		return Industrialization.id(path);
	}

	private static PlacedFeature placedFeature(MinecraftServer server, String path) {
		Registry<PlacedFeature> registry = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
		PlacedFeature feature = registry.getValue(id(path));
		assertNotNull(feature, "placed feature alaindustrial:" + path + " failed to load — an unknown"
				+ " placement modifier type in its JSON makes the whole file fail to parse");
		return feature;
	}

	/**
	 * The custom placement modifier type exists in the registry. Without it the placed-feature JSON
	 * below cannot parse at all, so this is the first thing to check when they go missing.
	 */
	@Test
	void oilLakeFilterTypeRegistered() {
		assertTrue(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE.containsKey(OilLakeFilter.ID),
				"PLACEMENT_MODIFIER_TYPE missing " + OilLakeFilter.ID
						+ " — the NeoForge DeferredRegister in ModWorldGenNeoForge regressed");
		assertSame(OilLakeFilter.TYPE, BuiltInRegistries.PLACEMENT_MODIFIER_TYPE.getValue(OilLakeFilter.ID),
				"a different instance is registered than the one OilLakeFilter#type() returns");
	}

	/**
	 * Both oil lakes run the structure filter, and it runs LAST — after the rarity/height/biome
	 * filters, so the (comparatively expensive) structure lookup only happens for candidates that
	 * survived everything else.
	 */
	@ParameterizedTest(name = "{0} ends with the oil structure filter")
	@ValueSource(strings = { "oil_lake_underground", "oil_lake_surface", "oil_lake_deep", "oil_geyser" })
	void everyOilFeatureRunsTheStructureFilter(String path, MinecraftServer server) {
		List<PlacementModifier> placement = placedFeature(server, path).placement();
		assertFalse(placement.isEmpty(), path + " has no placement modifiers at all");
		assertInstanceOf(OilLakeFilter.class, placement.get(placement.size() - 1),
				"alaindustrial:" + path + " must end with alaindustrial:oil_lake_filter, but its last"
						+ " modifier is " + placement.get(placement.size() - 1).type()
						+ " — an oil lake could overwrite a village/mineshaft/Ancient City");
	}

	/**
	 * The two custom feature types are in {@code BuiltInRegistries.FEATURE} (MOD-248). Without them
	 * every oil configured feature fails to parse on its {@code "type"} line, which surfaces as "no
	 * oil in the world" rather than as an error the player can act on.
	 */
	@ParameterizedTest(name = "feature type alaindustrial:{0} is registered")
	@ValueSource(strings = { "oil_lake", "oil_geyser" })
	void bothOilFeatureTypesAreRegistered(String path) {
		assertTrue(BuiltInRegistries.FEATURE.containsKey(id(path)),
				"FEATURE registry missing alaindustrial:" + path
						+ " — the NeoForge DeferredRegister in ModWorldGenNeoForge regressed");
	}

	/**
	 * Every oil deposit tier resolves to a configured feature (MOD-248). The four placed features
	 * above only prove the placement parsed; this proves the thing being placed exists and is of the
	 * mod's own feature type rather than a leftover {@code minecraft:lake}.
	 */
	@ParameterizedTest(name = "configured feature alaindustrial:{0} loads")
	@ValueSource(strings = { "oil_lake_small", "oil_lake_medium", "oil_lake_large", "oil_geyser" })
	void everyOilDepositTierLoads(String path, MinecraftServer server) {
		Registry<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> registry =
				server.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
		net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?> configured =
				registry.getValue(id(path));
		assertNotNull(configured, "configured feature alaindustrial:" + path + " failed to load");
		Identifier featureId = BuiltInRegistries.FEATURE.getKey(configured.feature());
		assertTrue(featureId != null && Industrialization.MOD_ID.equals(featureId.getNamespace()),
				"alaindustrial:" + path + " is built on " + featureId
						+ " — the deposit tiers must use the mod's own size-aware feature");
	}

	/** The protected-structure tag resolves and names the structures it exists to protect. */
	@ParameterizedTest(name = "#alaindustrial:no_oil_lakes contains {0}")
	@ValueSource(strings = { "minecraft:ancient_city", "minecraft:stronghold", "minecraft:mineshaft",
			"minecraft:village_plains" })
	void noOilLakesTagCoversTheProtectedStructures(String structureId, MinecraftServer server) {
		Registry<Structure> structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		ResourceKey<Structure> key =
				ResourceKey.create(Registries.STRUCTURE, Identifier.parse(structureId));
		Holder.Reference<Structure> holder = structures.get(key).orElseThrow(
				() -> new AssertionError("vanilla structure " + structureId + " does not exist in 26.2"));
		assertTrue(holder.is(OilLakeFilter.NO_OIL_LAKES),
				structureId + " is not in #alaindustrial:no_oil_lakes — oil lakes may overwrite it"
						+ " (check data/alaindustrial/tags/worldgen/structure/no_oil_lakes.json)");
	}

	/**
	 * Surface and underground lakes are selected by SEPARATE biome tags. "Drop the ugly surface
	 * patches, keep the buried deposits" is the standard modpack request; with one shared tag the
	 * only available answer was "disable oil entirely".
	 */
	@Test
	void surfaceAndUndergroundLakesUseSeparateBiomeTags(MinecraftServer server) {
		Registry<net.minecraft.world.level.biome.Biome> biomes =
				server.registryAccess().lookupOrThrow(Registries.BIOME);
		net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> surface =
				net.minecraft.tags.TagKey.create(Registries.BIOME, id("has_surface_oil_lakes"));
		net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> underground =
				net.minecraft.tags.TagKey.create(Registries.BIOME, id("has_underground_oil_lakes"));
		assertTrue(biomes.get(surface).isPresent(),
				"#alaindustrial:has_surface_oil_lakes is missing — surface oil lakes generate nowhere");
		assertTrue(biomes.get(underground).isPresent(),
				"#alaindustrial:has_underground_oil_lakes is missing — underground oil lakes generate nowhere");
		assertTrue(biomes.get(surface).orElseThrow().size() > 0, "the surface oil-lake biome tag is empty");
		assertTrue(biomes.get(underground).orElseThrow().size() > 0,
				"the underground oil-lake biome tag is empty");
		net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> geysers =
				net.minecraft.tags.TagKey.create(Registries.BIOME, id("has_oil_geysers"));
		assertTrue(biomes.get(geysers).isPresent(),
				"#alaindustrial:has_oil_geysers is missing — oil geysers generate nowhere");
		assertTrue(biomes.get(geysers).orElseThrow().size() > 0, "the oil-geyser biome tag is empty");
	}

	/**
	 * The oil {@link FluidType} declares its entity physics explicitly instead of inheriting
	 * water-like defaults ({@code canSwim/canDrown/canPushEntity = true}, {@code motionScale 0.014},
	 * {@code fallDistanceModifier 0.5}). Those defaults are inert in NeoForge 26.2.0.8-beta — the
	 * whole {@code IEntityExtension} fluid-type integration is commented out — which is exactly why
	 * this guard exists: the day it is re-enabled, silent defaults would make NeoForge players swim
	 * and drown in oil while Fabric players fall through it, and nothing else in the build would fail.
	 */
	@Test
	void oilFluidTypeDeclaresItsEntityPhysics() {
		FluidType type = new FluidType(ModFluidsNeoForge.oilTypeProperties());
		assertFalse(type.canSwim(null), "oil must not be swimmable (Fabric has no swimming in it)");
		assertFalse(type.canDrownIn(null), "oil must not drown entities (Fabric cannot)");
		assertFalse(type.canPushEntity(null), "oil must not push entities (Fabric does not)");
		assertEquals(0.0D, type.motionScale(null), "oil must not impart motion");
		assertEquals(1.0F, type.getFallDistanceModifier(null), "oil must not soften a fall");
		assertFalse(type.supportsBoating(null), "boats must not float on oil");
		assertEquals("block.alaindustrial.oil", type.getDescriptionId(),
				"the fluid type must reuse the block's translated name; the derived"
						+ " fluid_type.alaindustrial.oil key has no translation in any locale");
	}

	/**
	 * {@code oil_bucket} must stay EXACTLY a {@link BucketItem}, never a subclass.
	 * {@code CapabilityHooks} auto-registers the item fluid capability with
	 * {@code item.getClass() == BucketItem.class} — an identity check, not {@code instanceof} — so a
	 * subclass silently loses cross-mod fluid-container compatibility with no error anywhere.
	 */
	@Test
	void oilBucketIsExactlyABucketItem() {
		assertEquals(BucketItem.class, ModContent.OIL_BUCKET.get().getClass(),
				"oil_bucket must be a plain BucketItem: NeoForge's CapabilityHooks registers"
						+ " Capabilities.Fluid.ITEM by exact class identity, so a subclass would be"
						+ " invisible to every other mod's fluid handling");
	}
}
