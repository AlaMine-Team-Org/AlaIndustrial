package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.works.BuildingCard;
import dev.alaindustrial.settlement.works.ConstructionRow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: a press on a town building's card (round 2 of 0.1.219, Q2) — its order button, or only a refresh. Nothing here
 * is trusted: the server finds the building again from the block, checks the distance, the town and the restorer, and
 * presses only a button the card offers ({@link BuildingCard#request}).
 *
 * @param anchor the block the card was opened from: the house's inlet or the building's plaque
 * @param action the button pressed, as {@link ConstructionRow.Action#ordinal()}, or -1 — only the card
 */
public record BuildingCardRequestPayload(BlockPos anchor, int action) implements CustomPacketPayload {

	public static final Type<BuildingCardRequestPayload> TYPE =
			new Type<>(Industrialization.id("building_card_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BuildingCardRequestPayload> CODEC =
			StreamCodec.composite(BlockPos.STREAM_CODEC, BuildingCardRequestPayload::anchor,
					ByteBufCodecs.VAR_INT, BuildingCardRequestPayload::action,
					BuildingCardRequestPayload::new);

	/** Server side: hands the press to the card, which validates and replies. */
	public static void handle(BuildingCardRequestPayload payload, ServerPlayer player) {
		BuildingCard.request(player, payload);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
