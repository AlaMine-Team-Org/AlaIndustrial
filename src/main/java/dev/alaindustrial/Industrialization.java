package dev.alaindustrial;

import dev.alaindustrial.command.AlaCommand;
import dev.alaindustrial.core.NetworkManager;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.registry.ModBlockEntities;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModCriteria;
import dev.alaindustrial.registry.ModItems;
import dev.alaindustrial.registry.ModMenus;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModWorldGen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for Industrialization. Registration is delegated to the dedicated
 * {@code Mod*Registry} classes; this class only wires them together and logs startup.
 */
public class Industrialization implements ModInitializer {
	public static final String MOD_ID = "alaindustrial";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Config.register();
		dev.alaindustrial.registry.ModDataComponents.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModItems.init();
		ModRecipes.init();
		ModCriteria.init();
		ModWorldGen.init();

		// S2C payload for the Network Analyzer item (MOD-016): common-side type registration, the
		// client registers its receiver separately in IndustrializationClient.
		PayloadTypeRegistry.clientboundPlay().register(NetworkAnalyzerPayload.TYPE, NetworkAnalyzerPayload.CODEC);

		// Energy networks: tick every per-level NetworkManager once per server tick; drop a level's
		// transient state when that level unloads and all of it on server stop, so per-level networks
		// never leak across dimension or world reloads.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (net.minecraft.server.level.ServerLevel lvl : server.getAllLevels()) {
				NetworkManager.tickAll(lvl);
			}
		});
		ServerLevelEvents.UNLOAD.register((server, level) -> NetworkManager.clear(level));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NetworkManager.clearAll());

		// /ala build-visibility command (version + status), available to everyone.
		AlaCommand.register();

		LOGGER.info("Industrialization initialized.");
	}

	/** Build a {@link Identifier} in the {@code alaindustrial} namespace. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
