package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.teleporter.TeleportEngine;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What the remote's screen may know about each bound station (MOD-628).
 *
 * <p>Sent only to a player with the remote's screen open, from {@code TeleporterRemoteMenu#broadcastChanges}, at most
 * once a second and only when it changed. Built by {@code TeleportStationSnapshot} without loading a single chunk.
 *
 * <p>Names and positions do not travel: the client already holds them in the remote's own component, and the
 * stations here follow that list's order.
 *
 * @param cooldownSeconds the viewer's own recharge, 0 when a jump may start now
 * @param stations one per bound point, in the remote's order
 */
public record TeleportStationsPayload(int containerId, int cooldownSeconds, List<Station> stations)
		implements CustomPacketPayload {

	public static final Type<TeleportStationsPayload> TYPE = new Type<>(Industrialization.id("teleport_stations"));

	/** The server knows this station: a live read or a registry record. Without it no other flag means anything. */
	public static final int KNOWN = 1;
	/** Read from the loaded station just now, rather than from the registry. */
	public static final int LIVE = 1 << 1;
	/** Someone else's private station: charge, price, chip and capsule are withheld by the server. */
	public static final int HIDDEN = 1 << 2;
	public static final int FORMED = 1 << 3;
	public static final int CHIP = 1 << 4;
	public static final int PRIVATE = 1 << 5;
	/** In the viewer's dimension. */
	public static final int SAME_DIMENSION = 1 << 6;

	/**
	 * One station.
	 *
	 * @param denial why a targeted jump there would be refused, or {@code OK} — the station's side of
	 *     {@link TeleportEngine#checkPolicy}, decided without loading its chunk
	 * @param energy its charge; 0 when {@link #HIDDEN} or unknown
	 * @param cost the exact price of a jump there for this viewer; 0 when hidden, unknown or in another dimension
	 * @param updatedAgoSeconds how old the record is; 0 for a live read
	 */
	public record Station(int flags, TeleportEngine.Denial denial, long energy, long cost, int updatedAgoSeconds) {

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	private static final TeleportEngine.Denial[] DENIALS = TeleportEngine.Denial.values();

	// By ordinal: one server talks to clients of the same build. An ordinal the client does not know reads as
	// NO_STATION, the one denial that claims nothing about the station.
	private static final StreamCodec<ByteBuf, TeleportEngine.Denial> DENIAL = ByteBufCodecs.idMapper(
			ordinal -> ordinal >= 0 && ordinal < DENIALS.length ? DENIALS[ordinal] : TeleportEngine.Denial.NO_STATION,
			TeleportEngine.Denial::ordinal);

	private static final StreamCodec<ByteBuf, Station> STATION = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Station::flags,
			DENIAL, Station::denial,
			ByteBufCodecs.VAR_LONG, Station::energy,
			ByteBufCodecs.VAR_LONG, Station::cost,
			ByteBufCodecs.VAR_INT, Station::updatedAgoSeconds,
			Station::new);

	// No size cap: the list is as long as the remote's, and that limit is a server setting with no ceiling.
	public static final StreamCodec<RegistryFriendlyByteBuf, TeleportStationsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TeleportStationsPayload::containerId,
			ByteBufCodecs.VAR_INT, TeleportStationsPayload::cooldownSeconds,
			STATION.apply(ByteBufCodecs.list()), TeleportStationsPayload::stations,
			TeleportStationsPayload::new);

	public TeleportStationsPayload {
		stations = List.copyOf(stations);
	}

	@Override
	public Type<TeleportStationsPayload> type() {
		return TYPE;
	}
}
