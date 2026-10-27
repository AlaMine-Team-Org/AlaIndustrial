package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.registry.ModDataRegistries;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;

/**
 * NeoForge's replay of {@link ModDataRegistries#REGISTRIES} (MOD-830, ADR-050 §3): every datapack registry of the
 * mod is added on {@link NewDatapackRegistryEvent} (26.3.0.20-beta replaced
 * {@code DataPackRegistryEvent.NewRegistry} with it, MOD-910), the counterpart of Fabric's {@code DynamicRegistries}.
 * A registry with no network codec is read by the server only — NeoForge takes a {@code null} network codec so.
 */
public final class ModDataRegistriesNeoForge {

	private ModDataRegistriesNeoForge() {
	}

	/** The mod-bus listener: one call per entry of the shared list. */
	public static void addAll(NewDatapackRegistryEvent event) {
		for (ModDataRegistries.DataRegistry<?> registry : ModDataRegistries.REGISTRIES) {
			add(event, registry);
		}
	}

	private static <T> void add(NewDatapackRegistryEvent event, ModDataRegistries.DataRegistry<T> registry) {
		event.worldRegistry(registry.key(), registry.codec(), registry.networkCodec());
	}
}
