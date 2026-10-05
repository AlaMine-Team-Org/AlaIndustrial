package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server's client-visible balance (MOD-695), sent at login and after every config reload so a
 * client connected to a dedicated server shows the server's numbers, not its own file's.
 *
 * <p>The body is {@link dev.alaindustrial.KnobSnapshot#encode()} — a Minecraft-free layout the L1
 * suite round-trips knob by knob — carried as one byte array, bounded while decoding by
 * {@link PayloadBudget#MAX_CONFIG_SNAPSHOT_BYTES}. Same shape as {@link ArchiveRecordPayload}: a plain
 * {@link ByteBuf} codec, which both loaders accept for a clientbound play payload.
 *
 * @param data the encoded snapshot; the client decodes it and keeps its local numbers if it cannot
 */
public record ConfigSyncPayload(byte[] data) implements CustomPacketPayload {

	public static final Type<ConfigSyncPayload> TYPE = new Type<>(Industrialization.id("config_sync"));

	public static final StreamCodec<ByteBuf, ConfigSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.byteArray(PayloadBudget.MAX_CONFIG_SNAPSHOT_BYTES), ConfigSyncPayload::data,
			ConfigSyncPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
