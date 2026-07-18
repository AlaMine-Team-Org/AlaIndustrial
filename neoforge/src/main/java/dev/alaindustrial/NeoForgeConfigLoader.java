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
 *
 * <p>Reload parity (MOD-100, absorbs MOD-041): Fabric re-reads on {@code END_DATA_PACK_RELOAD}; NeoForge
 * re-reads via an {@code OnDatapackSyncEvent} listener wired in {@code IndustrializationNeoForge} that calls
 * {@link #reload()} on {@code /reload} (guarded to the all-players sync, not per-join). The startup load stays
 * here in {@link #register()} so an absent file is written before the first tick.
 */
final class NeoForgeConfigLoader {
	private NeoForgeConfigLoader() {
	}

	private static Path configPath() {
		return FMLPaths.CONFIGDIR.get().resolve("alaindustrial.json");
	}

	/** Load {@code config/alaindustrial.json} once at mod construction (writes defaults if absent), and bind
	 * {@link Config#configPath} so loader-neutral callers ({@code /ala config reload}) resolve the same file. */
	static void register() {
		Config.configPath = NeoForgeConfigLoader::configPath;
		Config.reload();
	}

	/** Re-read the config on a datapack {@code /reload}, matching Fabric's behaviour. */
	static void reload() {
		Config.reload();
	}
}
