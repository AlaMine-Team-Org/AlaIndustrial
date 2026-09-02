package dev.alaindustrial.client.compat.jei;

import mezz.jei.api.JeiPlugin;

/**
 * The NeoForge entry point of the shared JEI integration (MOD-558).
 *
 * <p>Everything the plugin does lives in {@link AlaJeiPlugin} in {@code common} — both loaders
 * compile against the same {@code jei-26.2-common-api}, so there was nothing loader-specific left in
 * it once the workstation blocks moved onto the recipe families themselves. What genuinely differs is
 * only how JEI FINDS the plugin: NeoForge scans the mod jar for this annotation, while Fabric reads
 * the {@code jei_mod_plugin} entrypoint from {@code fabric.mod.json} and needs no class of its own.
 * Hence an empty subclass here and no counterpart on the Fabric side.
 */
@JeiPlugin
public final class AlaJeiPluginNeoForge extends AlaJeiPlugin {
}
