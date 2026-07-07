package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.advancement.NetworkEnergizedTrigger;
import dev.alaindustrial.registry.ModCriteria;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge advancement-criterion registration (MOD-022 facade). NeoForge freezes the vanilla
 * {@code TRIGGER_TYPES} registry before mod construction, so the neutral {@link ModCriteria} cannot
 * self-register there (unlike Fabric). This {@link DeferredRegister} registers the trigger on the mod bus
 * and {@link #init()} binds the neutral handle to the deferred holder (a lazy {@code Supplier}).
 */
public final class ModCriteriaNeoForge {
	public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
			DeferredRegister.create(Registries.TRIGGER_TYPE, Industrialization.MOD_ID);

	public static final DeferredHolder<CriterionTrigger<?>, NetworkEnergizedTrigger> NETWORK_ENERGIZED =
			TRIGGERS.register("network_energized", ModCriteria::createNetworkEnergized);

	/** Bind the neutral handle to the deferred holder. Called from the {@code @Mod} ctor after register. */
	public static void init() {
		ModCriteria.NETWORK_ENERGIZED = NETWORK_ENERGIZED::get;
	}

	private ModCriteriaNeoForge() {
	}
}
