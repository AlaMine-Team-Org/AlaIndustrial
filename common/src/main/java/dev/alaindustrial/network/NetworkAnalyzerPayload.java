package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.AnalyzerMode;
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
 * S2C: one (or more) energy network's state for the Network Analyzer item (MOD-016 / MOD-047) — the
 * dimension it lives in, cable positions plus producer/consumer/storage endpoints to highlight
 * client-side, the aggregate stats for the actionbar readout, and the mode the scan ran in.
 * {@link #empty(ResourceKey)} (all position lists empty) tells the client to clear its highlight.
 * Carrying the dimension lets the client drop a stale highlight if the player changes dimension
 * (e.g. through a portal) before analyzing again.
 *
 * <p>{@code storage} carries storage-sink endpoints (BatteryBox) drawn as bridge nodes; it is empty
 * in {@link AnalyzerMode#STOP_AT_STORAGE} mode. {@code mode} lets the client reflect the active mode
 * in the tooltip/actionbar indicator. Both fields were added in MOD-047.
 */
public record NetworkAnalyzerPayload(ResourceKey<Level> dimension, List<BlockPos> cables, List<BlockPos> producers,
		List<BlockPos> consumers, List<BlockPos> storage, AnalyzerMode mode, long producerSupplyEu,
		long consumerDemandEu, long lastTickMovedEu) implements CustomPacketPayload {

	public static final Type<NetworkAnalyzerPayload> TYPE = new Type<>(Industrialization.id("network_analyzer"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NetworkAnalyzerPayload> CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), NetworkAnalyzerPayload::dimension,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), NetworkAnalyzerPayload::cables,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), NetworkAnalyzerPayload::producers,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), NetworkAnalyzerPayload::consumers,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), NetworkAnalyzerPayload::storage,
			AnalyzerMode.STREAM_CODEC, NetworkAnalyzerPayload::mode,
			ByteBufCodecs.VAR_LONG, NetworkAnalyzerPayload::producerSupplyEu,
			ByteBufCodecs.VAR_LONG, NetworkAnalyzerPayload::consumerDemandEu,
			ByteBufCodecs.VAR_LONG, NetworkAnalyzerPayload::lastTickMovedEu,
			NetworkAnalyzerPayload::new);

	public static NetworkAnalyzerPayload empty(ResourceKey<Level> dimension) {
		return new NetworkAnalyzerPayload(dimension, List.of(), List.of(), List.of(), List.of(),
				AnalyzerMode.TRAVERSE, 0L, 0L, 0L);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
