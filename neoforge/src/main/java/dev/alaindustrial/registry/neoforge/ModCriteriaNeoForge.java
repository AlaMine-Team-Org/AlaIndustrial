package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModCriteria;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge advancement-criterion registration: a replay of the shared {@link ModCriteria#TRIGGERS} list
 * (MOD-022 facade, MOD-555).
 *
 * <p>NeoForge freezes the vanilla {@code TRIGGER_TYPES} registry before mod construction, so the neutral
 * {@link ModCriteria} cannot self-register there the way it can on Fabric. What is left here is the
 * NeoForge registration MECHANISM and only that.
 */
public final class ModCriteriaNeoForge {
	public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
			DeferredRegister.create(Registries.TRIGGER_TYPE, Industrialization.MOD_ID);

	/** Every shared entry, queued and bound the moment this class loads. See {@code ModSoundsNeoForge}. */
	private static final List<DeferredHolder<CriterionTrigger<?>, ?>> REGISTERED = registerAll();

	private static List<DeferredHolder<CriterionTrigger<?>, ?>> registerAll() {
		List<DeferredHolder<CriterionTrigger<?>, ?>> registered = new ArrayList<>();
		for (ModCriteria.CriterionDef<?> def : ModCriteria.TRIGGERS) {
			registered.add(register(def));
		}
		return List.copyOf(registered);
	}

	/** One entry. Separate from {@link #registerAll()} only to capture the entry's type parameter. */
	private static <T extends CriterionTrigger<?>> DeferredHolder<CriterionTrigger<?>, T> register(
			ModCriteria.CriterionDef<T> def) {
		DeferredHolder<CriterionTrigger<?>, T> holder = TRIGGERS.register(def.id(), def.factory());
		def.bind().accept(holder::get);
		return holder;
	}

	/** Class-load trigger for the {@code @Mod} ctor; also checks the replay covered the whole list. */
	public static void init() {
		if (REGISTERED.size() != ModCriteria.TRIGGERS.size()) {
			throw new IllegalStateException("ModCriteriaNeoForge registered " + REGISTERED.size() + " of "
					+ ModCriteria.TRIGGERS.size() + " shared advancement criteria");
		}
	}

	private ModCriteriaNeoForge() {
	}
}
