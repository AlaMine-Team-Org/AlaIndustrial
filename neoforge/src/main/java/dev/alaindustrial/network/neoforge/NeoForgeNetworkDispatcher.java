package dev.alaindustrial.network.neoforge;

import dev.alaindustrial.network.NetworkDispatcher;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge implementation of the neutral {@link NetworkDispatcher} SPI (MOD-022 Phase 3). Sends via
 * the NeoForge {@code PacketDistributor} family. Installed once from {@code IndustrializationNeoForge}.
 *
 * <p>{@link ClientPacketDistributor} is a client-dist class (it dereferences
 * {@code Minecraft.getInstance()}); it is referenced only inside {@link #sendToServer}, which is
 * lazily linked on first call, so this class stays loadable on a dedicated server where no caller
 * invokes the C2S path.
 */
public final class NeoForgeNetworkDispatcher implements NetworkDispatcher {

	@Override
	public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		PacketDistributor.sendToPlayer(player, payload);
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		ClientPacketDistributor.sendToServer(payload);
	}
}
