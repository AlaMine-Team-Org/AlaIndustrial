package dev.alaindustrial;

import dev.alaindustrial.registry.ModRecipes;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * MOD-750: asks NeoForge to send the client every recipe type of the mod. NeoForge syncs only the types
 * requested through {@link OnDatapackSyncEvent#sendRecipes}, and the event starts empty; JEI requests
 * just seven vanilla types, so without this a dedicated-server client never receives
 * {@code alaindustrial:*} recipes and every machine category in JEI stays empty (single player hides it:
 * the JEI plugin reads the integrated server's recipe manager directly). The list is
 * {@link ModRecipes#syncedRecipeTypes()}, derived from the families both loaders register, so a new
 * family is synced without touching this class. The Fabric counterpart is the serializer loop in
 * {@code IndustrializationFabric#onInitialize} (MOD-543).
 */
final class NeoForgeRecipeSync {
	private NeoForgeRecipeSync() {
	}

	/** Fires on a player join and on {@code /reload}; collects the types for that one send. */
	static void requestModRecipeTypes(OnDatapackSyncEvent event) {
		event.sendRecipes(ModRecipes.syncedRecipeTypes());
	}
}
