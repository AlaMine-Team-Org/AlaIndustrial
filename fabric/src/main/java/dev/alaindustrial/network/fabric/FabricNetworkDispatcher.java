package dev.alaindustrial.network.fabric;

import dev.alaindustrial.network.NetworkDispatcher;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric implementation of the neutral {@link NetworkDispatcher} SPI (MOD-022 Phase 3). Sends via the
 * Fabric networking API. Installed once from {@code IndustrializationFabric}.
 *
 * <p>{@code ClientPlayNetworking} is a client-only class, so {@link #sendToServer} is dispatched
 * lazily — the method body only runs on the client thread that actually sends, keeping this class
 * loadable on a dedicated server (where the client class is absent).
 */
public final class FabricNetworkDispatcher implements NetworkDispatcher {

	@Override
	public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
