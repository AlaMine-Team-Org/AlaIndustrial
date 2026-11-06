package dev.alaindustrial.compat.rei;

import me.shedaniel.rei.forge.REIPluginCommon;

/**
 * The NeoForge entry point of the shared REI common plugin (MOD-955): the display filler that runs where the
 * recipe manager lives, the integrated server in singleplayer and the dedicated server in multiplayer, and
 * syncs the displays to the clients.
 *
 * <p>The body is {@link AlaReiCommonPlugin} in {@code common}. REI finds this subclass by its annotation on
 * both physical sides; Fabric reads the {@code rei_common} entrypoint from {@code fabric.mod.json} instead.
 */
@REIPluginCommon
public final class AlaReiCommonPluginNeoForge extends AlaReiCommonPlugin {
}
