package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.works.TownPulseService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: the client asks for what the town of the core it has open needs, and for its chronicle (MOD-886). The payload
 * has no body and nothing in it is trusted: the server takes the core from the player's open settlement core menu,
 * checks the player stands close and is one of the town's own (R98), and answers with a {@link TownPulsePayload}
 * ({@link TownPulseService}).
 */
public record TownPulseRequestPayload() implements CustomPacketPayload {

	public static final Type<TownPulseRequestPayload> TYPE = new Type<>(Industrialization.id("town_pulse_request"));

	/** No body: the packet's arrival is the whole request. */
	public static final StreamCodec<RegistryFriendlyByteBuf, TownPulseRequestPayload> CODEC =
			StreamCodec.unit(new TownPulseRequestPayload());

	/** Server side: hands the request to the service, which validates and replies. */
	public static void handle(TownPulseRequestPayload payload, ServerPlayer player) {
		TownPulseService.request(player);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
