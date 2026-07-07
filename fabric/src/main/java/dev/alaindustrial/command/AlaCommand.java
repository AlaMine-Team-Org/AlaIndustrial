package dev.alaindustrial.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Fabric registration seam for the {@code /ala} build-visibility command. The command tree itself is
 * loader-neutral in {@link AlaCommandCommon} (common); this only hooks it onto Fabric's
 * {@code CommandRegistrationCallback}. NeoForge registers the same tree via {@code RegisterCommandsEvent}.
 */
public final class AlaCommand {
	private AlaCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				AlaCommandCommon.register(dispatcher));
	}
}
