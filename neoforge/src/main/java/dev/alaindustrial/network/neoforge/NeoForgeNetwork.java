package dev.alaindustrial.network.neoforge;

import dev.alaindustrial.network.NetworkAnalyzerPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge payload registration + dispatch wiring for the Network Analyzer S2C payload (MOD-022
 * Phase 3). The payload record and its {@code StreamCodec} live in {@code common}
 * ({@link NetworkAnalyzerPayload}); this class is the NeoForge counterpart to the Fabric
 * {@code PayloadTypeRegistry.clientboundPlay().register(...)} call and the Fabric client receiver.
 *
 * <p>Wired from {@code IndustrializationNeoForge} by adding {@link #register} as a listener for the
 * mod-bus {@link RegisterPayloadHandlersEvent}. Sending is handled separately by the neutral
 * {@link dev.alaindustrial.network.NetworkDispatcher} ({@link NeoForgeNetworkDispatcher}).
 */
public final class NeoForgeNetwork {

	private NeoForgeNetwork() {
	}

	/**
	 * Registers the S2C Network Analyzer payload on the {@code "1"} channel version. The client-side
	 * receive handler ({@link NeoForgeNetworkClient#receive}) hops to the main thread via
	 * {@code context.enqueueWork(...)} before touching client state, mirroring how the Fabric receiver
	 * runs on the render thread. {@link NeoForgeNetworkClient} is a client-dist class referenced only
	 * from inside the handler lambda, so it is never linked on a dedicated server.
	 */
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToClient(NetworkAnalyzerPayload.TYPE, NetworkAnalyzerPayload.CODEC,
				(payload, context) -> context.enqueueWork(() -> NeoForgeNetworkClient.receive(payload)));
	}
}
