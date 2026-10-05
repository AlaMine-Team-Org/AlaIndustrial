package dev.alaindustrial.network.neoforge;

import dev.alaindustrial.client.ClientPayloadManifest;
import dev.alaindustrial.network.ModPayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge payload registration (MOD-022 Phase 3, MOD-706): replays the shared
 * {@link ModPayloads#PAYLOADS} list on the mod-bus {@link RegisterPayloadHandlersEvent} — the counterpart
 * of {@code IndustrializationFabric.registerNetworkPayloads}. Sending is handled separately by the
 * neutral {@link dev.alaindustrial.network.NetworkDispatcher} ({@link NeoForgeNetworkDispatcher}).
 *
 * <p>Wired from {@code IndustrializationNeoForge} by adding {@link #register} as a listener.
 */
public final class NeoForgeNetwork {

	private NeoForgeNetwork() {
	}

	/**
	 * Registers every payload on the {@link ModPayloads#PROTOCOL_VERSION} channel version. Every handler
	 * hops to the main thread with {@code context.enqueueWork(...)} exactly as the hand-written lines did
	 * (the registrar's default {@code HandlerThread.MAIN} wraps it once more; both were there before).
	 * The client side — the shared {@link ClientPayloadManifest} — is reached only from inside the
	 * clientbound handler lambda, so it and the client classes it names are never linked on a dedicated
	 * server.
	 *
	 * <p>Loader asymmetry worth knowing: {@code IPayloadContext#player()} returns {@code Player} here,
	 * while Fabric's context hands back a {@code ServerPlayer} — hence the cast, which is safe because a
	 * serverbound payload is only ever handled with a server player.
	 */
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(ModPayloads.PROTOCOL_VERSION);
		ModPayloads.Registrar replayer = new ModPayloads.Registrar() {
			@Override
			public <T extends CustomPacketPayload> void clientbound(ModPayloads.PayloadDef<T> def) {
				registrar.playToClient(def.type(), def.codec(),
						(payload, context) -> context.enqueueWork(() -> ClientPayloadManifest.receive(payload)));
			}

			@Override
			public <T extends CustomPacketPayload> void serverbound(ModPayloads.PayloadDef<T> def) {
				registrar.playToServer(def.type(), def.codec(),
						(payload, context) -> context.enqueueWork(
								() -> def.handle(payload, (ServerPlayer) context.player())));
			}
		};
		for (ModPayloads.PayloadDef<?> def : ModPayloads.PAYLOADS) {
			def.bindTo(replayer);
		}
	}
}
