package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.works.TownObstructionService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: «show it» at an «In the way: X at x y z» line of the core's «Construction» tab (MOD-890). Nothing in it is
 * trusted: the server answers only one of the town's own with its core's screen open, and only for a cell one of the
 * town's stopped jobs names ({@link TownObstructionService}).
 *
 * @param pos the cell the line names
 */
public record TownObstructionRequestPayload(BlockPos pos) implements CustomPacketPayload {

	public static final Type<TownObstructionRequestPayload> TYPE =
			new Type<>(Industrialization.id("town_obstruction_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TownObstructionRequestPayload> CODEC =
			StreamCodec.composite(BlockPos.STREAM_CODEC, TownObstructionRequestPayload::pos,
					TownObstructionRequestPayload::new);

	/** Server side: hands the request to the service, which validates and replies. */
	public static void handle(TownObstructionRequestPayload payload, ServerPlayer player) {
		TownObstructionService.request(player, payload.pos());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
