package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.TownWorksService;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: the client asks for the «Construction» tab of the core it has open, or presses one of its controls (MOD-857).
 * Nothing here is trusted: the server takes the core from the player's open settlement core menu, checks the player
 * stands close enough and, for a control, owns the town (R95), and answers with fresh rows ({@link TownWorksService}).
 *
 * @param action the control pressed, as {@link ConstructionRow.Action#ordinal()}, or -1 — only the rows
 * @param target what the control acts on: a building index or a job number, as the row said
 */
public record TownWorksRequestPayload(int action, int target) implements CustomPacketPayload {

	public static final Type<TownWorksRequestPayload> TYPE = new Type<>(Industrialization.id("town_works_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TownWorksRequestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TownWorksRequestPayload::action,
			ByteBufCodecs.VAR_INT, TownWorksRequestPayload::target,
			TownWorksRequestPayload::new);

	/** A request for the rows of the open core's tab. */
	public static TownWorksRequestPayload rows() {
		return new TownWorksRequestPayload(-1, 0);
	}

	/** A press of {@code action} on the row whose target is {@code target}. */
	public static TownWorksRequestPayload press(ConstructionRow.Action action, int target) {
		return new TownWorksRequestPayload(action.ordinal(), target);
	}

	/** Server side: hands the request to the service, which validates and replies. */
	public static void handle(TownWorksRequestPayload payload, ServerPlayer player) {
		Optional<ConstructionRow.Action> action = ConstructionRow.Action.byOrdinal(payload.action());
		TownWorksService.request(player, action, payload.target());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
