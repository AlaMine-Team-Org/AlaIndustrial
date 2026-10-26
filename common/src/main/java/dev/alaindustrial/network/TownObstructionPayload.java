package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: outline the block in the way of the town's work at {@code pos} for {@code ticks} (MOD-890) — the reply to a
 * {@link TownObstructionRequestPayload}, sent to the one player who pressed «show it» in the core's screen.
 *
 * @param pos   the cell to outline
 * @param ticks how long, in game ticks
 */
public record TownObstructionPayload(BlockPos pos, int ticks) implements CustomPacketPayload {

	public static final Type<TownObstructionPayload> TYPE = new Type<>(Industrialization.id("town_obstruction"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TownObstructionPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, TownObstructionPayload::pos,
			ByteBufCodecs.VAR_INT, TownObstructionPayload::ticks,
			TownObstructionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
