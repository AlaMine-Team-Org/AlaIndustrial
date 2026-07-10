package dev.alaindustrial.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Snapshot of the last Network Analyzer scan (MOD-016 follow-up), carried on the item itself as a
 * data component ({@link dev.alaindustrial.registry.ModDataComponents#NETWORK_SCAN}) so the reading
 * survives in the item tooltip after the actionbar message fades. Purely diagnostic, read-only data
 * — it never affects gameplay, and re-scanning simply overwrites it.
 *
 * <p>{@code producers}/{@code consumers}/{@code storage} are endpoint counts (storage sinks such as
 * BatteryBox are tallied separately in MOD-047 Traverse mode and are 0 in STOP_AT_STORAGE mode);
 * {@code supply}/{@code demand} are the network's instantaneous EU/t estimate at scan time and
 * {@code moved} the EU delivered on the network's most recent tick — the same three figures the
 * actionbar reports.
 */
public record NetworkScanData(int cables, int producers, int consumers, int storage, long supply, long demand,
		long moved) {

	/** Compact constructor — kept for callers that don't care about storage (defaults to 0). */
	public NetworkScanData(int cables, int producers, int consumers, long supply, long demand, long moved) {
		this(cables, producers, consumers, 0, supply, demand, moved);
	}

	public static final Codec<NetworkScanData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("cables").forGetter(NetworkScanData::cables),
			Codec.INT.fieldOf("producers").forGetter(NetworkScanData::producers),
			Codec.INT.fieldOf("consumers").forGetter(NetworkScanData::consumers),
			Codec.INT.optionalFieldOf("storage").xmap(o -> o.orElse(0), Optional::of).forGetter(NetworkScanData::storage),
			Codec.LONG.fieldOf("supply").forGetter(NetworkScanData::supply),
			Codec.LONG.fieldOf("demand").forGetter(NetworkScanData::demand),
			Codec.LONG.fieldOf("moved").forGetter(NetworkScanData::moved))
			.apply(instance, NetworkScanData::new));

	public static final StreamCodec<ByteBuf, NetworkScanData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, NetworkScanData::cables,
			ByteBufCodecs.VAR_INT, NetworkScanData::producers,
			ByteBufCodecs.VAR_INT, NetworkScanData::consumers,
			ByteBufCodecs.VAR_INT, NetworkScanData::storage,
			ByteBufCodecs.VAR_LONG, NetworkScanData::supply,
			ByteBufCodecs.VAR_LONG, NetworkScanData::demand,
			ByteBufCodecs.VAR_LONG, NetworkScanData::moved,
			NetworkScanData::new);
}
