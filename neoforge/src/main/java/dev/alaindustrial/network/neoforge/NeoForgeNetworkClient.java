package dev.alaindustrial.network.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.MachineStatsClient;
import dev.alaindustrial.client.hud.TeleportFadeHud;
import dev.alaindustrial.client.hud.TeleportNotice;
import dev.alaindustrial.client.render.NetworkOverlayRenderer;
import dev.alaindustrial.client.render.RepellerDomeRenderer;
import dev.alaindustrial.network.MachineStatsPayload;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.RepellerDomePayload;
import dev.alaindustrial.network.TeleportFadePayload;
import dev.alaindustrial.network.TeleportNoticePayload;

/**
 * Client-dist receive seams for the mod's S2C payloads on NeoForge (MOD-022 Phase 3). Runs only on the
 * client (invoked from the main-thread tasks in {@link NeoForgeNetwork#register}); it is referenced
 * solely from inside those handler lambdas, so it is never linked on a dedicated server.
 *
 * <p>Each seam hands its payload straight to the loader-neutral landing point — the same call Fabric's
 * receiver makes. The Network Analyzer payload used to be parked in a field that the render hook polled
 * every frame; nothing ever cleared that field, so after leaving a world the renderer re-read the old
 * trace (MOD-665, D1). It is now pushed like every other payload here.
 */
public final class NeoForgeNetworkClient {

	private NeoForgeNetworkClient() {
	}

	/** Called on the client main thread when a {@link NetworkAnalyzerPayload} arrives. */
	public static void receive(NetworkAnalyzerPayload payload) {
		NetworkOverlayRenderer.updatePayload(payload);
		Industrialization.LOGGER.debug("NeoForge client received NetworkAnalyzerPayload ({} cables)",
				payload.cables().size());
	}

	/**
	 * Called on the client main thread when a teleport fade level arrives (MOD-106). Hands straight to
	 * the loader-neutral overlay, which is also what Fabric's receiver calls.
	 */
	public static void receiveFade(TeleportFadePayload payload) {
		TeleportFadeHud.receive(payload.strength());
	}

	/**
	 * Called on the client main thread when a teleport refusal arrives (MOD-093). Hands straight to
	 * the loader-neutral holder the remote's screen reads, same as Fabric's receiver.
	 */
	public static void receiveNotice(TeleportNoticePayload payload) {
		TeleportNotice.receive(payload.message());
	}

	/**
	 * Called on the client main thread when a repeller dome answer arrives (MOD-278). Toggles the dome
	 * for that block in the loader-neutral renderer — same call Fabric's receiver makes.
	 */
	public static void receiveRepellerDome(RepellerDomePayload payload) {
		RepellerDomeRenderer.receive(payload);
	}

	/**
	 * Called on the client main thread when a machine statistics snapshot arrives (MOD-125). Hands it to
	 * the loader-neutral landing point, which routes it to the open menu — same call Fabric's receiver makes.
	 */
	public static void receiveMachineStats(MachineStatsPayload payload) {
		MachineStatsClient.receive(payload);
	}

	/** MOD-620: the reactor controller's «Core» tab, through the same loader-neutral landing point Fabric uses. */
	public static void receiveReactorZone(dev.alaindustrial.network.ReactorZonePayload payload) {
		dev.alaindustrial.client.ReactorZoneClient.receive(payload);
	}

	/** MOD-622: the reactor controller's «Log» tab, through the same loader-neutral landing point Fabric uses. */
	public static void receiveReactorLog(dev.alaindustrial.network.ReactorLogPayload payload) {
		dev.alaindustrial.client.ReactorLogClient.receive(payload);
	}

	/** MOD-513: the player's archive record, through the same loader-neutral landing point Fabric uses. */
	public static void receiveArchiveRecord(dev.alaindustrial.network.ArchiveRecordPayload payload) {
		dev.alaindustrial.client.guide.ArchiveRecordClient.receive(payload.record());
	}

	/** MOD-628: the teleporter remote's stations, through the same loader-neutral landing point Fabric uses. */
	public static void receiveTeleportStations(dev.alaindustrial.network.TeleportStationsPayload payload) {
		dev.alaindustrial.client.TeleportStationsClient.receive(payload);
	}
}
