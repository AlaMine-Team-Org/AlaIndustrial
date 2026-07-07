package dev.alaindustrial;

import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric platform seam for the balance config. Resolves {@code config/alaindustrial.json} via the
 * Fabric loader's config directory and re-reads it on every datapack {@code /reload}; the actual
 * parse/write is the loader-neutral {@link Config#loadFrom(Path)} in {@code common}.
 *
 * <p>MOD-022 Phase 3: NeoForge resolves its config directory differently (FML paths) and hooks a
 * different reload event — that side gets its own loader calling the same {@link Config#loadFrom}.
 */
final class FabricConfigLoader {
	private FabricConfigLoader() {
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("alaindustrial.json");
	}

	/** Load once at startup and re-load on every {@code /reload} (datapack reload). */
	static void register() {
		Config.loadFrom(configPath());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> Config.loadFrom(configPath()));
	}
}
