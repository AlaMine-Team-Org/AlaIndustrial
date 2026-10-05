package dev.alaindustrial.network;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Every network payload the mod sends, declared once for both loaders (MOD-706, after the MOD-555
 * pattern): its type, its codec, which way it travels and — for a serverbound one — the server handler.
 *
 * <p>The registration MECHANISM differs per loader — Fabric's {@code PayloadTypeRegistry} plus a
 * {@code ServerPlayNetworking} receiver, NeoForge's {@code PayloadRegistrar} on the
 * {@code RegisterPayloadHandlersEvent} — and so does the hop onto the server thread each of them wrote
 * by hand; that stays in the loader's replayer. Nothing else does: {@link #PAYLOADS} says which payloads
 * exist, and each loader replays it in one loop, {@code for (PayloadDef<?> def : ModPayloads.PAYLOADS)
 * def.bindTo(registrar)} — the MOD-555 form, which {@code loader_parity_check.py} looks for. The client
 * side of a clientbound payload is declared next to it in
 * {@code dev.alaindustrial.client.ClientPayloadManifest},
 * because a client receiver touches client classes that a dedicated server must never load.
 *
 * <p><b>Before MOD-706 a payload was written four to five times</b> — a type registration and a receiver
 * in {@code IndustrializationFabric}, a client receiver in one of two Fabric client files, a
 * {@code playToClient/playToServer} line in {@code NeoForgeNetwork} and a {@code receive*} bridge in
 * {@code NeoForgeNetworkClient}. A payload forgotten on one loader compiled: NeoForge threw when it was
 * sent, Fabric dropped it silently. {@code payload_parity_check.py} reads this list and checks that both
 * loaders replay it.
 *
 * <p>The order is the registration order both loaders had by hand — clientbound first, then
 * serverbound — kept so the move is a pure restructuring. Nothing reads it.
 */
public final class ModPayloads {

	/**
	 * The NeoForge channel version every payload is registered under (the argument of
	 * {@code RegisterPayloadHandlersEvent.registrar}). Fabric has no channel version.
	 */
	public static final String PROTOCOL_VERSION = "1";

	private ModPayloads() {
	}

	/** Which way a payload travels. */
	public enum Direction {
		/** Server to client; handled on the client. */
		CLIENTBOUND,
		/** Client to server; handled by {@link PayloadDef#serverHandler}. */
		SERVERBOUND
	}

	/**
	 * What the server does with a serverbound payload. Each loader calls it on the server thread, exactly
	 * where its hand-written receiver did.
	 */
	@FunctionalInterface
	public interface ServerHandler<T extends CustomPacketPayload> {
		void handle(T payload, ServerPlayer player);
	}

	/**
	 * One payload.
	 *
	 * @param type          the payload's type (its {@code TYPE} constant)
	 * @param codec         its stream codec — both loaders take a {@code StreamCodec<? super
	 *                      RegistryFriendlyByteBuf, T>} for the play phase
	 * @param direction     which way it travels
	 * @param serverHandler what the server does with it; present exactly for a serverbound payload
	 */
	public record PayloadDef<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Direction direction,
			@Nullable ServerHandler<T> serverHandler) {

		public PayloadDef {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(codec, "codec");
			Objects.requireNonNull(direction, "direction");
			if ((direction == Direction.SERVERBOUND) != (serverHandler != null)) {
				throw new IllegalArgumentException(type.id() + ": a serverbound payload needs a server handler "
						+ "and a clientbound one must not have one");
			}
		}

		/** Runs the server handler; only ever called for a serverbound payload, on the server thread. */
		public void handle(T payload, ServerPlayer player) {
			if (serverHandler == null) {
				throw new IllegalStateException(type.id() + " is clientbound and has no server handler");
			}
			serverHandler.handle(payload, player);
		}

		/** Hands this entry to the registrar method of its direction. */
		public void bindTo(Registrar registrar) {
			switch (direction) {
				case CLIENTBOUND -> registrar.clientbound(this);
				case SERVERBOUND -> registrar.serverbound(this);
			}
		}
	}

	/**
	 * A loader's registration of one payload, per direction. Generic per call, so the type, the codec and
	 * the handler stay tied to one payload class without a cast.
	 */
	public interface Registrar {
		<T extends CustomPacketPayload> void clientbound(PayloadDef<T> def);

		<T extends CustomPacketPayload> void serverbound(PayloadDef<T> def);
	}

	private static <T extends CustomPacketPayload> PayloadDef<T> clientbound(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		return new PayloadDef<>(type, codec, Direction.CLIENTBOUND, null);
	}

	private static <T extends CustomPacketPayload> PayloadDef<T> serverbound(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec, ServerHandler<T> handler) {
		return new PayloadDef<>(type, codec, Direction.SERVERBOUND, handler);
	}

	/** Every payload of the mod, in the registration order both loaders used before MOD-706. */
	public static final List<PayloadDef<?>> PAYLOADS = List.of(
			// MOD-016: the Network Analyzer's trace of a cable network.
			clientbound(NetworkAnalyzerPayload.TYPE, NetworkAnalyzerPayload.CODEC),
			// MOD-278: the repeller dome answer — personal, one per button press.
			clientbound(RepellerDomePayload.TYPE, RepellerDomePayload.CODEC),
			// MOD-125: one machine's career statistics, pushed from its open menu every 40 ticks.
			clientbound(MachineStatsPayload.TYPE, MachineStatsPayload.CODEC),
			// MOD-620: the reactor's core, stack by stack, at most once a second from an open controller.
			clientbound(ReactorZonePayload.TYPE, ReactorZonePayload.CODEC),
			// MOD-622: the reactor's event log, at most twice a second from an open controller.
			clientbound(ReactorLogPayload.TYPE, ReactorLogPayload.CODEC),
			// MOD-628: the teleporter remote's stations, at most once a second from an open remote.
			clientbound(TeleportStationsPayload.TYPE, TeleportStationsPayload.CODEC),
			// MOD-106: the teleport screen-fade level, every tick of a jump's last second.
			clientbound(TeleportFadePayload.TYPE, TeleportFadePayload.CODEC),
			// MOD-093: why a jump was refused, shown inside the remote's screen.
			clientbound(TeleportNoticePayload.TYPE, TeleportNoticePayload.CODEC),
			// MOD-513: the player's archive record for the guide book's first page, once per login.
			clientbound(ArchiveRecordPayload.TYPE, ArchiveRecordPayload.CODEC),
			// MOD-695: the server's client-visible balance, at login and after every config reload.
			clientbound(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC),
			// MOD-093: renaming a teleport point — the one string that needs a payload of its own.
			serverbound(TeleportRenamePayload.TYPE, TeleportRenamePayload.CODEC, TeleportRenamePayload::handle),
			// MOD-592: a recipe-viewer drop onto a magnet filter cell.
			serverbound(MagnetFilterSamplePayload.TYPE, MagnetFilterSamplePayload.CODEC,
					MagnetFilterSamplePayload::handle),
			// MOD-127: the step-assist toggle on the worn Fluxweave leggings.
			serverbound(FluxweaveStepAssistPayload.TYPE, FluxweaveStepAssistPayload.CODEC,
					FluxweaveStepAssistPayload::handle),
			// MOD-482: the column bore's on/off switch; the reply is an action-bar line.
			serverbound(DrillColumnTogglePayload.TYPE, DrillColumnTogglePayload.CODEC,
					DrillColumnTogglePayload::handle),
			// MOD-483: every skill purchase and reset; the skills attachment syncs itself back.
			serverbound(SkillActionPayload.TYPE, SkillActionPayload.CODEC, SkillActionPayload::handle));
}
