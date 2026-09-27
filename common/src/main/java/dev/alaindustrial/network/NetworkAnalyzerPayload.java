package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.tool.AnalyzerMode;
import dev.alaindustrial.network.NetworkTraverser.TraversalResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
 * <p>{@code storage} carries storage-sink endpoints (BatteryBox) drawn as bridge nodes (MOD-047).
 *
 * <p>MOD-665: {@code endpointFaces} holds one byte per endpoint — producers, then consumers, then
 * storage, in list order — with bit {@code 1 << Direction.ordinal()} set for each face really wired to
 * a cable, so the overlay draws only the legs the network has (D5). {@code truncated} says the lists
 * were cut to {@link PayloadBudget#MAX_POSITIONS}, keeping what lies nearest the clicked cable (D4).
 */
public record NetworkAnalyzerPayload(ResourceKey<Level> dimension, List<BlockPos> cables, List<BlockPos> producers,
		List<BlockPos> consumers, List<BlockPos> storage, byte[] endpointFaces, AnalyzerMode mode,
		long producerSupplyEu, long consumerDemandEu, long lastTickMovedEu, boolean truncated)
		implements CustomPacketPayload {

	public static final Type<NetworkAnalyzerPayload> TYPE = new Type<>(Industrialization.id("network_analyzer"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NetworkAnalyzerPayload> CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), NetworkAnalyzerPayload::dimension,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadBudget.MAX_POSITIONS)), NetworkAnalyzerPayload::cables,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadBudget.MAX_POSITIONS)), NetworkAnalyzerPayload::producers,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadBudget.MAX_POSITIONS)), NetworkAnalyzerPayload::consumers,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(PayloadBudget.MAX_POSITIONS)), NetworkAnalyzerPayload::storage,
			ByteBufCodecs.byteArray(4 * PayloadBudget.MAX_POSITIONS), NetworkAnalyzerPayload::endpointFaces,
			AnalyzerMode.STREAM_CODEC, NetworkAnalyzerPayload::mode,
			ByteBufCodecs.VAR_LONG, NetworkAnalyzerPayload::producerSupplyEu,
			ByteBufCodecs.VAR_LONG, NetworkAnalyzerPayload::consumerDemandEu,
			ByteBufCodecs.VAR_LONG, NetworkAnalyzerPayload::lastTickMovedEu,
			ByteBufCodecs.BOOL, NetworkAnalyzerPayload::truncated,
			NetworkAnalyzerPayload::new);

	public static NetworkAnalyzerPayload empty(ResourceKey<Level> dimension) {
		return new NetworkAnalyzerPayload(dimension, List.of(), List.of(), List.of(), List.of(), new byte[0],
				AnalyzerMode.TRAVERSE, 0L, 0L, 0L, false);
	}

	/**
	 * Builds the payload for a scan, fitting it into {@code maxPositions} (D4). When the lists fit they
	 * go out in the traversal's own order; when they do not, each kind keeps its positions nearest to
	 * {@code clicked} (ties in {@link NetworkTopology#POSITION_ORDER}), so the player still sees the
	 * part of the base around the cable they clicked.
	 */
	public static NetworkAnalyzerPayload of(ResourceKey<Level> dimension, TraversalResult result, AnalyzerMode mode,
			BlockPos clicked, int maxPositions) {
		List<BlockPos> cables = result.cableList();
		List<BlockPos> producers = result.producerList();
		List<BlockPos> consumers = result.consumerList();
		List<BlockPos> storage = result.storageList();
		boolean truncated = PayloadBudget.truncates(cables.size(), producers.size(), consumers.size(),
				storage.size(), maxPositions);
		if (truncated) {
			int[] allowed = PayloadBudget.fit(cables.size(), producers.size(), consumers.size(), storage.size(),
					maxPositions);
			cables = nearest(cables, clicked, allowed[0]);
			producers = nearest(producers, clicked, allowed[1]);
			consumers = nearest(consumers, clicked, allowed[2]);
			storage = nearest(storage, clicked, allowed[3]);
		}
		return new NetworkAnalyzerPayload(dimension, cables, producers, consumers, storage,
				faceBytes(result.endpointFaces(), result.cableFlow(), cables, producers, consumers, storage), mode,
				result.supply(),
				result.demand(), result.moved(), truncated);
	}

	private static List<BlockPos> nearest(List<BlockPos> positions, BlockPos clicked, int keep) {
		if (positions.size() <= keep) {
			return positions;
		}
		List<BlockPos> sorted = new ArrayList<>(positions);
		sorted.sort(Comparator.<BlockPos>comparingLong(p -> p.distManhattan(clicked))
				.thenComparing(NetworkTopology.POSITION_ORDER));
		return List.copyOf(sorted.subList(0, keep));
	}

	/**
	 * Three bytes per endpoint (wired, taking now, giving now) in producers ++ consumers ++ storage order,
	 * then one byte per cable in {@code cables} order: the faces through which the network hands EU on to
	 * the next cable (MOD-665).
	 */
	private static byte[] faceBytes(Map<BlockPos, Integer> faces, Map<BlockPos, Integer> cableFlow,
			List<BlockPos> cables, List<BlockPos> producers, List<BlockPos> consumers, List<BlockPos> storage) {
		int endpoints = producers.size() + consumers.size() + storage.size();
		byte[] out = new byte[3 * endpoints + cables.size()];
		int i = 0;
		for (List<BlockPos> group : List.of(producers, consumers, storage)) {
			for (BlockPos pos : group) {
				int packed = faces.getOrDefault(pos, 0);
				out[i++] = (byte) NetworkTopology.wiredFaces(packed);
				out[i++] = (byte) NetworkTopology.takeFaces(packed);
				out[i++] = (byte) NetworkTopology.emitFaces(packed);
			}
		}
		for (BlockPos cable : cables) {
			out[i++] = (byte) (int) cableFlow.getOrDefault(cable, 0);
		}
		return out;
	}

	/**
	 * The faces the server sent for {@code index} in producers ++ consumers ++ storage order, packed by
	 * {@link NetworkTopology#packFaces}: three bytes per endpoint — wired, taking now, giving now.
	 */
	public int packedFaces(int index) {
		if (3 * index + 2 >= endpointFaces.length) {
			return 0;
		}
		return NetworkTopology.packFaces(endpointFaces[3 * index], endpointFaces[3 * index + 1],
				endpointFaces[3 * index + 2]);
	}

	/** The faces through which cable {@code index} (in {@link #cables} order) hands EU on to the next cable. */
	public int cableFlowFaces(int index) {
		int at = 3 * (producers.size() + consumers.size() + storage.size()) + index;
		return at < endpointFaces.length ? endpointFaces[at] & NetworkTopology.ALL_FACES : 0;
	}

	/** Every face wired for {@code index}, whichever way it works. */
	public int faceMask(int index) {
		return NetworkTopology.wiredFaces(packedFaces(index));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
