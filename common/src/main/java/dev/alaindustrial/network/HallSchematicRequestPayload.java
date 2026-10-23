package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.works.HallSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: the client asks for the schematic of the town hall of the town whose core stands at {@code core} (MOD-857,
 * owner's round 2, point 7) — when the player turns the schematic on in the core's «Construction» tab, and again on a
 * slow timer while it stays on. Nothing here is trusted: the server checks the core and the distance
 * ({@link HallSchematic#request}). Visual only, so it needs no ownership.
 *
 * @param core     the town's settlement core the tab was asked at
 * @param building the building whose schematic is asked for (round 2 of 0.1.219, Q6): its index in the town,
 *                 {@link #NETWORK} for the town's road network, {@link #DEFAULT} for the order the dispatcher shows
 */
public record HallSchematicRequestPayload(BlockPos core, int building) implements CustomPacketPayload {

	/** The town's road network rather than a building. */
	public static final int NETWORK = -1;
	/** The order the town's dispatcher shows, or the hall. */
	public static final int DEFAULT = -2;

	/** A request for the order the dispatcher shows. */
	public HallSchematicRequestPayload(BlockPos core) {
		this(core, DEFAULT);
	}

	public static final Type<HallSchematicRequestPayload> TYPE =
			new Type<>(Industrialization.id("hall_schematic_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HallSchematicRequestPayload> CODEC =
			StreamCodec.composite(BlockPos.STREAM_CODEC, HallSchematicRequestPayload::core,
					ByteBufCodecs.VAR_INT, HallSchematicRequestPayload::building, HallSchematicRequestPayload::new);

	/** Server side: hands the request to the service, which validates and replies. */
	public static void handle(HallSchematicRequestPayload payload, ServerPlayer player) {
		HallSchematic.request(player, payload.core(), payload.building());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
