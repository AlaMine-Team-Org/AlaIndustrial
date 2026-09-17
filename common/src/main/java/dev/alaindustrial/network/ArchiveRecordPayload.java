package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player's archive record (MOD-513), sent by the server once per login for the guide book's first
 * page.
 *
 * <p>A finished value, not the inputs: the client of a dedicated server never learns the world seed,
 * so it could not work the record out, and the seed is exactly what the game keeps from it. The
 * client checks the shape before showing anything ({@code ArchiveRecordClient}).
 *
 * <p>The codec is over a plain {@link ByteBuf}, which both loaders accept for a clientbound play
 * payload ({@code StreamCodec<? super RegistryFriendlyByteBuf, T>}), and the read is bounded: a
 * record is five characters, so a longer string is refused while decoding rather than stored.
 */
public record ArchiveRecordPayload(String record) implements CustomPacketPayload {

	/** Longest string the decoder accepts. Well above a record's length, well below anything useful. */
	public static final int MAX_LENGTH = 16;

	public static final Type<ArchiveRecordPayload> TYPE = new Type<>(Industrialization.id("archive_record"));

	public static final StreamCodec<ByteBuf, ArchiveRecordPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(MAX_LENGTH), ArchiveRecordPayload::record,
			ArchiveRecordPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
