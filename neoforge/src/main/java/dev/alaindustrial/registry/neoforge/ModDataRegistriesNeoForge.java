package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.registry.ModDataRegistries;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * NeoForge's replay of {@link ModDataRegistries#REGISTRIES} (MOD-830, ADR-050 §3): every datapack registry of the
 * mod is added on {@link DataPackRegistryEvent.NewRegistry}, the counterpart of Fabric's {@code DynamicRegistries}.
 * A registry with no network codec is read by the server only — NeoForge takes a {@code null} network codec so.
 */
public final class ModDataRegistriesNeoForge {

	private ModDataRegistriesNeoForge() {
	}

	/** The mod-bus listener: one call per entry of the shared list. */
	public static void addAll(DataPackRegistryEvent.NewRegistry event) {
		for (ModDataRegistries.DataRegistry<?> registry : ModDataRegistries.REGISTRIES) {
			add(event, registry);
		}
	}

	private static <T> void add(DataPackRegistryEvent.NewRegistry event, ModDataRegistries.DataRegistry<T> registry) {
		event.dataPackRegistry(registry.key(), registry.codec(), registry.networkCodec());
	}
}
