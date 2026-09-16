package dev.alaindustrial.item.teleport;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.core.teleport.RemoteLog;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How a remote's log (MOD-631) is saved with the item and sent with it.
 *
 * <p>On disk an entry names its kind by id and leaves out what is zero or empty, so a line costs only what it says. On
 * the wire the kind goes by ordinal and every number as a VarInt: fifty typical lines are about 1.5 KB.
 */
public final class RemoteLogCodecs {

	private RemoteLogCodecs() {
	}

	private static final Codec<RemoteLog.Station> STATION = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("name", "").forGetter(RemoteLog.Station::name),
			Codec.INT.optionalFieldOf("number", 0).forGetter(RemoteLog.Station::number))
			.apply(instance, RemoteLog.Station::new));

	private static final Codec<RemoteLog.Entry> ENTRY = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("seq").forGetter(RemoteLog.Entry::seq),
			Codec.LONG.fieldOf("time").forGetter(RemoteLog.Entry::time),
			Codec.STRING.xmap(RemoteLog.Kind::byId, RemoteLog.Kind::id).fieldOf("kind").forGetter(RemoteLog.Entry::kind),
			Codec.BOOL.optionalFieldOf("random", false).forGetter(RemoteLog.Entry::random),
			STATION.optionalFieldOf("station", RemoteLog.Station.NONE).forGetter(RemoteLog.Entry::station),
			STATION.optionalFieldOf("renamed_to", RemoteLog.Station.NONE).forGetter(RemoteLog.Entry::renamedTo),
			Codec.INT.optionalFieldOf("a", 0).forGetter(RemoteLog.Entry::a),
			Codec.INT.optionalFieldOf("b", 0).forGetter(RemoteLog.Entry::b),
			Codec.INT.optionalFieldOf("c", 0).forGetter(RemoteLog.Entry::c))
			.apply(instance, RemoteLog.Entry::new));

	public static final Codec<RemoteLog> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("next_seq", 1).forGetter(RemoteLog::nextSeq),
			Codec.INT.optionalFieldOf("seen_seq", 0).forGetter(RemoteLog::seenSeq),
			ENTRY.listOf().optionalFieldOf("entries", List.of()).forGetter(RemoteLog::entries))
			.apply(instance, RemoteLog::new));

	private static final StreamCodec<ByteBuf, RemoteLog.Station> STATION_STREAM = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(RemoteLog.NAME_MAX), RemoteLog.Station::name,
			ByteBufCodecs.VAR_INT, RemoteLog.Station::number,
			RemoteLog.Station::new);

	private static final StreamCodec<ByteBuf, RemoteLog.Entry> ENTRY_STREAM = StreamCodec.of(
			(buf, entry) -> {
				ByteBufCodecs.VAR_INT.encode(buf, entry.seq());
				ByteBufCodecs.VAR_LONG.encode(buf, entry.time());
				ByteBufCodecs.VAR_INT.encode(buf, entry.kind().ordinal());
				ByteBufCodecs.BOOL.encode(buf, entry.random());
				STATION_STREAM.encode(buf, entry.station());
				STATION_STREAM.encode(buf, entry.renamedTo());
				ByteBufCodecs.VAR_INT.encode(buf, entry.a());
				ByteBufCodecs.VAR_INT.encode(buf, entry.b());
				ByteBufCodecs.VAR_INT.encode(buf, entry.c());
			},
			buf -> new RemoteLog.Entry(
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_LONG.decode(buf),
					RemoteLog.Kind.byOrdinal(ByteBufCodecs.VAR_INT.decode(buf)),
					ByteBufCodecs.BOOL.decode(buf),
					STATION_STREAM.decode(buf),
					STATION_STREAM.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf)));

	public static final StreamCodec<ByteBuf, RemoteLog> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RemoteLog::nextSeq,
			ByteBufCodecs.VAR_INT, RemoteLog::seenSeq,
			ENTRY_STREAM.apply(ByteBufCodecs.list(RemoteLog.CAPACITY)), RemoteLog::entries,
			RemoteLog::new);
}
