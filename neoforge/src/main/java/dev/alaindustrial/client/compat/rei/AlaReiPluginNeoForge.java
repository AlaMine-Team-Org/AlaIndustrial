package dev.alaindustrial.client.compat.rei;

import me.shedaniel.rei.forge.REIPluginClient;

/**
 * The NeoForge entry point of the shared REI client plugin (MOD-955).
 *
 * <p>Everything the plugin does lives in {@link AlaReiPlugin} in {@code common}. What differs between the
 * loaders is only how REI FINDS it: on NeoForge REI scans the mod jar for this annotation, and only on the
 * physical client; Fabric reads the {@code rei_client} entrypoint from {@code fabric.mod.json}. Hence an empty
 * subclass here, the same shape as {@code AlaJeiPluginNeoForge}.
 */
@REIPluginClient
public final class AlaReiPluginNeoForge extends AlaReiPlugin {
}
