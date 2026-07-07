package dev.alaindustrial;

import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/**
 * NeoForge platform seam for the balance config (MOD-022 counterpart to {@code FabricConfigLoader}).
 * Resolves {@code config/alaindustrial.json} via the FML config directory and loads it at startup; the
 * actual parse/write is the loader-neutral {@link Config#loadFrom(Path)} in {@code common}.
 *
 * <p>Without this, NeoForge never reads the config file and every balance number silently falls back to
 * {@link Config}'s compiled defaults — diverging from Fabric whenever a server operator edits the file.
 */
final class NeoForgeConfigLoader {
	private NeoForgeConfigLoader() {
	}

	private static Path configPath() {
		return FMLPaths.CONFIGDIR.get().resolve("alaindustrial.json");
	}

	/** Load {@code config/alaindustrial.json} once at mod construction (writes defaults if absent). */
	static void register() {
		Config.loadFrom(configPath());
	}
}
