package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.works.HousePlacementWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: a press in the house laying window (MOD-877, R108) — show another plan or turn, lay the house, or call a laid
 * house off. Nothing here is trusted: the server checks the inlet, the town, the restorer and the distance, and
 * surveys the site again before it lays anything ({@link HousePlacementWindow#request}).
 *
 * @param inlet    the inlet the window is about
 * @param action   {@link #PREVIEW}, {@link #LAY} or {@link #CANCEL}
 * @param plan     the plan chosen, an index into the window's list
 * @param rotation the turn chosen, a {@code Rotation} ordinal
 */
public record HousePlacementRequestPayload(BlockPos inlet, int action, int plan, int rotation)
		implements CustomPacketPayload {

	/** Show the site for this plan and turn. */
	public static final int PREVIEW = 0;
	/** Lay the house, if the site is free. */
	public static final int LAY = 1;
	/** Call the laid house off, if its building has not started. */
	public static final int CANCEL = 2;

	public static final Type<HousePlacementRequestPayload> TYPE =
			new Type<>(Industrialization.id("house_placement_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HousePlacementRequestPayload> CODEC =
			StreamCodec.composite(BlockPos.STREAM_CODEC, HousePlacementRequestPayload::inlet,
					ByteBufCodecs.VAR_INT, HousePlacementRequestPayload::action,
					ByteBufCodecs.VAR_INT, HousePlacementRequestPayload::plan,
					ByteBufCodecs.VAR_INT, HousePlacementRequestPayload::rotation,
					HousePlacementRequestPayload::new);

	/** Server side: hands the press to the service, which validates and replies. */
	public static void handle(HousePlacementRequestPayload payload, ServerPlayer player) {
		HousePlacementWindow.request(player, payload);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
