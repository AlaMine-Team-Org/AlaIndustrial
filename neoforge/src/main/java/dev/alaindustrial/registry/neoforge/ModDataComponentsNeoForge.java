package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.AnalyzerMode;
import dev.alaindustrial.item.NetworkScanData;
import dev.alaindustrial.item.PouchContents;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge data-component registration (MOD-022 facade). NeoForge freezes the vanilla
 * {@code DATA_COMPONENT_TYPE} registry before mod construction, so the neutral {@link ModDataComponents}
 * cannot self-register there (unlike Fabric). This {@link DeferredRegister} registers on the mod bus and
 * binds the neutral handles to the deferred holders (each a {@code Supplier<DataComponentType<?>>}).
 */
public final class ModDataComponentsNeoForge {
	public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
			DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Industrialization.MOD_ID);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> STORED_ENERGY =
			DATA_COMPONENTS.register("stored_energy", ModDataComponents::createStoredEnergy);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<NetworkScanData>> NETWORK_SCAN =
			DATA_COMPONENTS.register("network_scan", ModDataComponents::createNetworkScan);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<AnalyzerMode>> NETWORK_ANALYZER_MODE =
			DATA_COMPONENTS.register("network_analyzer_mode", ModDataComponents::createNetworkAnalyzerMode);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> POUCH_ENERGY =
			DATA_COMPONENTS.register("pouch_energy", ModDataComponents::createPouchEnergy);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<PouchContents>> POUCH_CONTENTS =
			DATA_COMPONENTS.register("pouch_contents", ModDataComponents::createPouchContents);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Holder<Fluid>>> CAPSULE_FLUID =
			DATA_COMPONENTS.register("capsule_fluid", ModDataComponents::createCapsuleFluid);

	/** Bind the neutral handles to the deferred holders. Called from the {@code @Mod} ctor after register. */
	public static void init() {
		ModDataComponents.STORED_ENERGY = STORED_ENERGY;
		ModDataComponents.NETWORK_SCAN = NETWORK_SCAN;
		ModDataComponents.NETWORK_ANALYZER_MODE = NETWORK_ANALYZER_MODE;
		ModDataComponents.POUCH_ENERGY = POUCH_ENERGY;
		ModDataComponents.POUCH_CONTENTS = POUCH_CONTENTS;
		ModDataComponents.CAPSULE_FLUID = CAPSULE_FLUID;
	}

	private ModDataComponentsNeoForge() {
	}
}
