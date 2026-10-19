package dev.alaindustrial.registry.neoforge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.worldgen.AbandonedLabFeature;
import dev.alaindustrial.worldgen.OilGeyserFeature;
import dev.alaindustrial.worldgen.OilLakeFeature;
import dev.alaindustrial.worldgen.OilLakeFilter;
import dev.alaindustrial.worldgen.town.AlaTownStructure;
import dev.alaindustrial.worldgen.town.AlaTownTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
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
 * <p>{@code BuiltInRegistries.PLACEMENT_MODIFIER_TYPE} and {@code BuiltInRegistries.FEATURE_TYPE} are
 * frozen before mod construction on NeoForge, so these must be {@link DeferredRegister}s — and they
 * must fire before datapack load, or the JSON referring to {@code alaindustrial:oil_lake_filter} /
 * {@code alaindustrial:oil_lake} / {@code alaindustrial:oil_geyser} fails to parse.
 *
 * <p><b>MOD-226 (26.3): what is registered is the {@link MapCodec}, not an instance.</b> A feature is
 * now its own configuration — a record decoded from {@code data/<ns>/worldgen/feature/} — so the type
 * registry moved from {@code Registries.FEATURE} (which in 26.3 holds the decoded instances) to the
 * new {@code Registries.FEATURE_TYPE}, and both it and {@code PLACEMENT_MODIFIER_TYPE} are declared
 * {@code Registry<MapCodec<? extends X>>}. Verified against the 26.3 sources, not from memory:
 * {@code Feature.DIRECT_CODEC} dispatches on {@code BuiltInRegistries.FEATURE_TYPE} and
 * {@code PlacementModifier.CODEC} on {@code BuiltInRegistries.PLACEMENT_MODIFIER_TYPE}. The registry
 * ids the JSON names are unchanged.
 */
public final class ModWorldGenNeoForge {

	public static final DeferredRegister<MapCodec<? extends PlacementModifier>> PLACEMENT_MODIFIER_TYPES =
			DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES =
			DeferredRegister.create(Registries.FEATURE_TYPE, Industrialization.MOD_ID);

	/** {@code alaindustrial:oil_lake_filter} — keeps oil features out of protected structures. */
	public static final DeferredHolder<MapCodec<? extends PlacementModifier>, MapCodec<OilLakeFilter>>
			OIL_LAKE_FILTER =
			PLACEMENT_MODIFIER_TYPES.register(OilLakeFilter.ID.getPath(), () -> OilLakeFilter.CODEC);

	/** {@code alaindustrial:oil_lake} — the size-parameterised deposit (MOD-248). */
	public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<OilLakeFeature>> OIL_LAKE =
			FEATURE_TYPES.register(OilLakeFeature.ID.getPath(), () -> OilLakeFeature.CODEC);

	/** {@code alaindustrial:oil_geyser} — spout, shaft and flooded dome (MOD-248). */
	public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<OilGeyserFeature>> OIL_GEYSER =
			FEATURE_TYPES.register(OilGeyserFeature.ID.getPath(), () -> OilGeyserFeature.CODEC);

	/** {@code alaindustrial:abandoned_lab} — the lore lab: camouflaged hatch, shaft, lab template (MOD-513). */
	public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<AbandonedLabFeature>> ABANDONED_LAB =
			FEATURE_TYPES.register(AbandonedLabFeature.ID.getPath(), () -> AbandonedLabFeature.CODEC);

	public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
			DeferredRegister.create(Registries.STRUCTURE_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
			DeferredRegister.create(Registries.STRUCTURE_PIECE, Industrialization.MOD_ID);

	/** {@code alaindustrial:ala_town} — the ALA town's structure type (MOD-830), shared with Fabric. */
	public static final DeferredHolder<StructureType<?>, StructureType<AlaTownStructure>> ALA_TOWN =
			STRUCTURE_TYPES.register(AlaTownTypes.STRUCTURE_TYPE_ID.getPath(), () -> AlaTownTypes.STRUCTURE_TYPE);

	/** {@code alaindustrial:ala_town_lab} — the lab piece under a town hall (MOD-830), shared with Fabric. */
	public static final DeferredHolder<StructurePieceType, StructurePieceType> ALA_TOWN_LAB =
			STRUCTURE_PIECES.register(AlaTownTypes.LAB_PIECE_ID.getPath(), () -> AlaTownTypes.LAB_PIECE);

	/** The two ALA town registers on the mod bus (MOD-830). */
	public static void registerTownTypes(net.neoforged.bus.api.IEventBus modBus) {
		STRUCTURE_TYPES.register(modBus);
		STRUCTURE_PIECES.register(modBus);
	}

	private ModWorldGenNeoForge() {
	}
}
