package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * S2C: one energy network's state for the Network Analyzer item (MOD-016) — the dimension it lives
 * in, cable positions plus producer/consumer endpoints to highlight client-side, and the aggregate
 * stats for the actionbar readout. {@link #empty(ResourceKey)} (all position lists empty) tells the
 * client to clear its highlight. Carrying the dimension lets the client drop a stale highlight if
 * the player changes dimension (e.g. through a portal) before analyzing again.
 */
public record NetworkAnalyzerPayload(ResourceKey<Level> dimension, List<BlockPos> cables, List<BlockPos> producers,
		List<BlockPos> consumers, long producerSupplyEu, long consumerDemandEu, long lastTickMovedEu)
		implements CustomPacketPayload {

	public static final Type<NetworkAnalyzerPayload> TYPE = new Type<>(Industrialization.id("network_analyzer"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NetworkAnalyzerPayload> CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), NetworkAnalyzerPayload::dimension,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), NetworkAnalyzerPayload::cables,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), NetworkAnalyzerPayload::producers,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), NetworkAnalyzerPayload::consumers,
			ByteBufCodecs.VAR_LONG, NetworkAnalyzerPayload::producerSupplyEu,
			ByteBufCodecs.VAR_LONG, NetworkAnalyzerPayload::consumerDemandEu,
			ByteBufCodecs.VAR_LONG, NetworkAnalyzerPayload::lastTickMovedEu,
			NetworkAnalyzerPayload::new);

	public static NetworkAnalyzerPayload empty(ResourceKey<Level> dimension) {
		return new NetworkAnalyzerPayload(dimension, List.of(), List.of(), List.of(), 0L, 0L, 0L);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
