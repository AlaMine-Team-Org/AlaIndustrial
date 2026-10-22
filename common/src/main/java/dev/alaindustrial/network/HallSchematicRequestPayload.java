package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.works.HallSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: the client asks for the schematic of the town hall of the town whose core stands at {@code core} (MOD-857,
 * owner's round 2, point 7) — when the player turns the schematic on in the core's «Construction» tab, and again on a
 * slow timer while it stays on. Nothing here is trusted: the server checks the core and the distance
 * ({@link HallSchematic#request}). Visual only, so it needs no ownership.
 *
 * @param core the town's settlement core the tab was asked at
 */
public record HallSchematicRequestPayload(BlockPos core) implements CustomPacketPayload {

	public static final Type<HallSchematicRequestPayload> TYPE =
			new Type<>(Industrialization.id("hall_schematic_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HallSchematicRequestPayload> CODEC =
			StreamCodec.composite(BlockPos.STREAM_CODEC, HallSchematicRequestPayload::core,
					HallSchematicRequestPayload::new);

	/** Server side: hands the request to the service, which validates and replies. */
	public static void handle(HallSchematicRequestPayload payload, ServerPlayer player) {
		HallSchematic.request(player, payload.core());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
