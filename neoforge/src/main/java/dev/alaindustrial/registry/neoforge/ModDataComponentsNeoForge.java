package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge data-component registration: a replay of the shared {@link ModDataComponents#COMPONENTS} list
 * (MOD-022 facade, MOD-555).
 *
 * <p>NeoForge freezes the vanilla {@code DATA_COMPONENT_TYPE} registry before mod construction, so the
 * neutral {@link ModDataComponents} cannot self-register there the way it can on Fabric. What is left in
 * this file is the NeoForge registration MECHANISM and only that: the {@link DeferredRegister}, its lazy
 * {@code register}, and the binding of each resulting {@link DeferredHolder} — itself a
 * {@code Supplier<DataComponentType<T>>} — into the entry's handle.
 *
 * <p>Before MOD-555 this file also carried the LIST: 21 holder fields, each with its full generic
 * signature spelled out, plus 21 assignment lines in {@code init()}.
 */
public final class ModDataComponentsNeoForge {
	public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
			DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Industrialization.MOD_ID);

	/** Every shared entry, queued and bound the moment this class loads. See {@code ModSoundsNeoForge}. */
	private static final List<DeferredHolder<DataComponentType<?>, ?>> REGISTERED = registerAll();

	private static List<DeferredHolder<DataComponentType<?>, ?>> registerAll() {
		List<DeferredHolder<DataComponentType<?>, ?>> registered = new ArrayList<>();
		for (ModDataComponents.ComponentDef<?> def : ModDataComponents.COMPONENTS) {
			registered.add(register(def));
		}
		return List.copyOf(registered);
	}

	/** One entry. Separate from {@link #registerAll()} only to capture the entry's type parameter. */
	private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
			ModDataComponents.ComponentDef<T> def) {
		DeferredHolder<DataComponentType<?>, DataComponentType<T>> holder =
				DATA_COMPONENTS.register(def.id().getPath(), def.factory());
		def.bind().accept(holder);
		return holder;
	}

	/** Class-load trigger for the {@code @Mod} ctor; also checks the replay covered the whole list. */
	public static void init() {
		if (REGISTERED.size() != ModDataComponents.COMPONENTS.size()) {
			throw new IllegalStateException("ModDataComponentsNeoForge registered " + REGISTERED.size()
					+ " of " + ModDataComponents.COMPONENTS.size() + " shared data components");
		}
	}

	private ModDataComponentsNeoForge() {
	}
}
