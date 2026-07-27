package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.worldgen.OilGeyserConfiguration;
import dev.alaindustrial.worldgen.OilGeyserFeature;
import dev.alaindustrial.worldgen.OilLakeConfiguration;
import dev.alaindustrial.worldgen.OilLakeFeature;
import dev.alaindustrial.worldgen.OilLakeFilter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge registration of the mod's worldgen extension points (MOD-238 audit, extended in
 * MOD-248). Mirrors the eager Fabric registration in {@code ModWorldGen}.
 *
 * <p>Only the registry <em>types</em> live here: the configured/placed features themselves are
 * data-driven and shared, and biome injection on NeoForge is a datapack
 * {@code neoforge:add_features} biome modifier, not code.
 *
 * <p>{@code BuiltInRegistries.PLACEMENT_MODIFIER_TYPE} and {@code BuiltInRegistries.FEATURE} are
 * frozen before mod construction on NeoForge, so these must be {@link DeferredRegister}s — and they
 * must fire before datapack load, or the JSON referring to {@code alaindustrial:oil_lake_filter} /
 * {@code alaindustrial:oil_lake} / {@code alaindustrial:oil_geyser} fails to parse.
 */
public final class ModWorldGenNeoForge {

	public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIER_TYPES =
			DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<Feature<?>> FEATURES =
			DeferredRegister.create(Registries.FEATURE, Industrialization.MOD_ID);

	/** {@code alaindustrial:oil_lake_filter} — keeps oil features out of protected structures. */
	public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<OilLakeFilter>>
			OIL_LAKE_FILTER =
			PLACEMENT_MODIFIER_TYPES.register(OilLakeFilter.ID.getPath(), () -> OilLakeFilter.TYPE);

	/** {@code alaindustrial:oil_lake} — the size-parameterised deposit (MOD-248). */
	public static final DeferredHolder<Feature<?>, Feature<OilLakeConfiguration>> OIL_LAKE =
			FEATURES.register(OilLakeFeature.ID.getPath(), () -> OilLakeFeature.INSTANCE);

	/** {@code alaindustrial:oil_geyser} — spout, shaft and flooded dome (MOD-248). */
	public static final DeferredHolder<Feature<?>, Feature<OilGeyserConfiguration>> OIL_GEYSER =
			FEATURES.register(OilGeyserFeature.ID.getPath(), () -> OilGeyserFeature.INSTANCE);

	private ModWorldGenNeoForge() {
	}
}
