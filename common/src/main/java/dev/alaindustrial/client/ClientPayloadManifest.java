package dev.alaindustrial.client;

import dev.alaindustrial.client.guide.ArchiveRecordClient;
import dev.alaindustrial.client.hud.TeleportFadeHud;
import dev.alaindustrial.client.hud.TeleportNotice;
import dev.alaindustrial.client.render.NetworkOverlayRenderer;
import dev.alaindustrial.client.render.RepellerDomeRenderer;
import dev.alaindustrial.network.ArchiveRecordPayload;
import dev.alaindustrial.network.ConfigSyncPayload;
import dev.alaindustrial.network.MachineStatsPayload;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.ReactorLogPayload;
import dev.alaindustrial.network.ReactorZonePayload;
import dev.alaindustrial.network.RepellerDomePayload;
import dev.alaindustrial.network.TeleportFadePayload;
import dev.alaindustrial.network.TeleportNoticePayload;
import dev.alaindustrial.network.TeleportStationsPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client side of every clientbound payload, declared once for both loaders (MOD-706): which
 * loader-neutral landing point a payload is handed to, and how the hand-off is scheduled. The payloads
 * themselves are {@code dev.alaindustrial.network.ModPayloads.PAYLOADS}; this list lives in the client
 * package because its entries name client classes, which a dedicated server must never load.
 *
 * <p><b>Who replays it.</b> Fabric's client entrypoint registers one {@code ClientPlayNetworking}
 * receiver per entry ({@code IndustrializationClient}); NeoForge registers the clientbound payloads in
 * the common {@code NeoForgeNetwork} and its handler, which runs only on the client, calls
 * {@link #receive}. Before MOD-706 the same ten hand-offs were written in two Fabric client files and
 * once more as {@code receive*} bridges in {@code NeoForgeNetworkClient}.
 *
 * <p><b>{@link Dispatch} records what the hand-written Fabric receivers did, nothing more.</b> Fabric
 * already calls a play receiver on the client thread (fabric-networking-api-v1 6.3.8 for 26.3 and 6.3.3
 * for 26.2: {@code AbstractChanneledNetworkAddon.handle} throws {@code RunningOnDifferentThreadException}
 * off that thread and the packet is rescheduled), so {@code QUEUED} does not change the thread — it only
 * defers the hand-off to the client's task queue, as the four receivers wrapped in
 * {@code client().execute} did. NeoForge keeps its {@code enqueueWork} for every entry, as before. The
 * evidence is in the task's research.md; unifying the two is a behaviour change and not part of MOD-706.
 */
public final class ClientPayloadManifest {

	private ClientPayloadManifest() {
	}

	/** How Fabric hands a payload over (see the class doc; NeoForge always queues). */
	public enum Dispatch {
		/** Called straight from the receiver, on the client thread it already runs on. */
		INLINE,
		/** Deferred to the client's task queue with {@code client().execute}. */
		QUEUED
	}

	/**
	 * One clientbound payload's landing point.
	 *
	 * @param type     the payload's type
	 * @param receiver the loader-neutral landing point
	 * @param dispatch how Fabric schedules the call
	 */
	public record ClientHandlerDef<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
			Consumer<T> receiver, Dispatch dispatch) {

		public ClientHandlerDef {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(receiver, "receiver");
			Objects.requireNonNull(dispatch, "dispatch");
		}

		/** Hands {@code payload} to the landing point. */
		public void receive(T payload) {
			receiver.accept(payload);
		}

		/** Hands this entry to a loader's registrar. */
		public void bindTo(Registrar registrar) {
			registrar.receiver(this);
		}
	}

	/** A loader's registration of one client receiver. Generic per call, so no cast is needed. */
	public interface Registrar {
		<T extends CustomPacketPayload> void receiver(ClientHandlerDef<T> def);
	}

	private static <T extends CustomPacketPayload> ClientHandlerDef<T> inline(CustomPacketPayload.Type<T> type,
			Consumer<T> receiver) {
		return new ClientHandlerDef<>(type, receiver, Dispatch.INLINE);
	}

	private static <T extends CustomPacketPayload> ClientHandlerDef<T> queued(CustomPacketPayload.Type<T> type,
			Consumer<T> receiver) {
		return new ClientHandlerDef<>(type, receiver, Dispatch.QUEUED);
	}

	/** Every clientbound payload's landing point; the dispatch of each is the one its Fabric receiver had. */
	public static final List<ClientHandlerDef<?>> HANDLERS = List.of(
			// MOD-016/MOD-665: the analyzer trace, pushed into the overlay (formerly NetworkVisualizationClient).
			inline(NetworkAnalyzerPayload.TYPE, NetworkOverlayRenderer::updatePayload),
			// MOD-278: toggles this client's personal dome for that block.
			inline(RepellerDomePayload.TYPE, RepellerDomeRenderer::receive),
			// MOD-125: machine statistics for the open screen's panel.
			inline(MachineStatsPayload.TYPE, MachineStatsClient::receive),
			// MOD-620: the reactor controller's «Core» tab.
			inline(ReactorZonePayload.TYPE, ReactorZoneClient::receive),
			// MOD-622: the reactor controller's «Log» tab.
			inline(ReactorLogPayload.TYPE, ReactorLogClient::receive),
			// MOD-628: the teleporter remote's stations.
			inline(TeleportStationsPayload.TYPE, TeleportStationsClient::receive),
			// MOD-106: the jump's screen fade (formerly IndustrializationClient.registerHudAndKeys).
			queued(TeleportFadePayload.TYPE, payload -> TeleportFadeHud.receive(payload.strength())),
			// MOD-513: the archive record for the guide book's first page.
			queued(ArchiveRecordPayload.TYPE, payload -> ArchiveRecordClient.receive(payload.record())),
			// MOD-695: the server's balance, kept beside Config (never written into it).
			queued(ConfigSyncPayload.TYPE, payload -> ServerBalance.receive(payload.data())),
			// MOD-093: why a jump was refused, shown inside the remote's screen.
			queued(TeleportNoticePayload.TYPE, payload -> TeleportNotice.receive(payload.message())));

	private static final Map<CustomPacketPayload.Type<?>, ClientHandlerDef<?>> BY_TYPE = index();

	private static Map<CustomPacketPayload.Type<?>, ClientHandlerDef<?>> index() {
		Map<CustomPacketPayload.Type<?>, ClientHandlerDef<?>> byType = new HashMap<>();
		for (ClientHandlerDef<?> def : HANDLERS) {
			if (byType.put(def.type(), def) != null) {
				throw new IllegalStateException("two client handlers for payload " + def.type().id());
			}
		}
		return Map.copyOf(byType);
	}

	/**
	 * Hands a received payload to its landing point — the NeoForge entry, called from its handler's main
	 * thread task. A clientbound payload with no entry here is a wiring bug and fails loudly, as the
	 * transitional router of batch 1a did.
	 */
	public static void receive(CustomPacketPayload payload) {
		ClientHandlerDef<?> def = BY_TYPE.get(payload.type());
		if (def == null) {
			throw new IllegalStateException("no client handler for payload " + payload.type().id());
		}
		deliver(def, payload);
	}

	/**
	 * The one cast: the entry was found under this payload's own {@code type()}, and a {@code Type<T>} is
	 * only ever the {@code TYPE} constant of the payload class {@code T}.
	 */
	@SuppressWarnings("unchecked")
	private static <T extends CustomPacketPayload> void deliver(ClientHandlerDef<T> def, CustomPacketPayload payload) {
		def.receive((T) payload);
	}
}
