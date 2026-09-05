package dev.alaindustrial.network.neoforge;

import dev.alaindustrial.network.MachineStatsPayload;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.RepellerDomePayload;
import dev.alaindustrial.network.TeleportFadePayload;
import dev.alaindustrial.network.TeleportNoticePayload;
import dev.alaindustrial.network.FluxweaveStepAssistPayload;
import dev.alaindustrial.network.TeleportRenamePayload;
import net.minecraft.server.level.ServerPlayer;
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
		// MOD-278: the repeller dome answer — personal, one per button press; the client toggles it.
		registrar.playToClient(RepellerDomePayload.TYPE, RepellerDomePayload.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> NeoForgeNetworkClient.receiveRepellerDome(payload)));
		// MOD-125: one machine's career statistics, pushed from its open menu every 40 ticks.
		registrar.playToClient(MachineStatsPayload.TYPE, MachineStatsPayload.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> NeoForgeNetworkClient.receiveMachineStats(payload)));
		// Teleport screen-fade level (MOD-106) — one float per tick of a jump's last second. The client
		// clears itself once the levels stop arriving, so a cancelled warmup needs no packet of its own.
		registrar.playToClient(TeleportFadePayload.TYPE, TeleportFadePayload.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> NeoForgeNetworkClient.receiveFade(payload)));
		// Why a jump was refused (MOD-093) — shown inside the remote's screen, which covers the action
		// bar the refusal would otherwise land on.
		registrar.playToClient(TeleportNoticePayload.TYPE, TeleportNoticePayload.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> NeoForgeNetworkClient.receiveNotice(payload)));
		// The mod's first C2S payload (MOD-093): renaming a teleport point. Every other button on that
		// screen rides vanilla's container-button packet, which needs no registration — only a name,
		// being a string, needs a payload of our own.
		//
		// Loader asymmetry worth knowing: IPayloadContext#player() returns Player here, while Fabric's
		// context hands back a ServerPlayer directly — hence the cast, which is safe because a
		// serverbound payload is only ever handled with a server player.
		registrar.playToServer(TeleportRenamePayload.TYPE, TeleportRenamePayload.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> TeleportRenamePayload.handle(payload, (ServerPlayer) context.player())));
		registrar.playToServer(FluxweaveStepAssistPayload.TYPE, FluxweaveStepAssistPayload.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> FluxweaveStepAssistPayload.handle(payload, (ServerPlayer) context.player())));
		// MOD-483: no clientbound half — the skills attachment mirrors itself to its owner.
		registrar.playToServer(dev.alaindustrial.network.SkillActionPayload.TYPE,
				dev.alaindustrial.network.SkillActionPayload.CODEC,
				(payload, context) -> context.enqueueWork(
						() -> dev.alaindustrial.network.SkillActionPayload.handle(
								payload, (ServerPlayer) context.player())));
	}
}
