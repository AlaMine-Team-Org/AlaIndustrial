package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfiles;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: the client asks for a shift worker's card or a town's roster (MOD-854). Nothing here is trusted: the server
 * finds the record itself, takes the core from the player's open settlement core menu, and checks the player stands
 * close enough ({@link ShiftWorkerProfiles}); a request that fails a check gets no reply.
 *
 * @param kind   what is asked for
 * @param number the record number, for {@link Kind#CARD}; empty otherwise
 */
public record ShiftWorkerRequestPayload(Kind kind, String number) implements CustomPacketPayload {

	public static final Type<ShiftWorkerRequestPayload> TYPE = new Type<>(Industrialization.id("shift_worker_request"));

	/** What a request asks for. */
	public enum Kind {
		/** One worker's card, by record number — a roster row clicked. Replied with an opening card. */
		CARD,
		/** The roster of the town whose core the player's open menu belongs to. */
		ROSTER
	}

	private static final Kind[] KINDS = Kind.values();
	private static final StreamCodec<ByteBuf, Kind> KIND = ByteBufCodecs.idMapper(
			ordinal -> ordinal >= 0 && ordinal < KINDS.length ? KINDS[ordinal] : Kind.CARD, Kind::ordinal);

	public static final StreamCodec<RegistryFriendlyByteBuf, ShiftWorkerRequestPayload> CODEC = StreamCodec.composite(
			KIND, ShiftWorkerRequestPayload::kind,
			ByteBufCodecs.stringUtf8(ShiftWorkerCardPayload.TEXT_LIMIT), ShiftWorkerRequestPayload::number,
			ShiftWorkerRequestPayload::new);

	/** A request for the card of the worker with {@code number}. */
	public static ShiftWorkerRequestPayload card(String number) {
		return new ShiftWorkerRequestPayload(Kind.CARD, number);
	}

	/** A request for the roster of the town whose core the player has open. */
	public static ShiftWorkerRequestPayload roster() {
		return new ShiftWorkerRequestPayload(Kind.ROSTER, "");
	}

	/** Server side: hands the request to the profile service, which validates and replies. */
	public static void handle(ShiftWorkerRequestPayload payload, ServerPlayer player) {
		switch (payload.kind()) {
			case CARD -> RecordNumber.parse(payload.number())
					.ifPresent(number -> ShiftWorkerProfiles.requestCard(player, number));
			case ROSTER -> ShiftWorkerProfiles.requestRoster(player);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
