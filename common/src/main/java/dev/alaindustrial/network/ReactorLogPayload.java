package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.structure.ReactorLog;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The reactor's event log for the controller's «Log» tab (MOD-622).
 *
 * <p>Sent only to a player with that controller's screen open, from {@code ReactorControllerMenu#broadcastChanges}, at
 * most twice a second and only when it changed. The whole log each time: a hundred lines are about a kilobyte, and an
 * incremental sync would need sequence bookkeeping per viewer to save it.
 *
 * <p>Text travels as a kind and numbers; the client builds the line with its own language. A kind is sent by ordinal —
 * one server talks to clients of the same build — and an ordinal the client does not know reads as a neutral line.
 *
 * @param seenSeq the newest entry THIS viewer has acknowledged, for the tab's badge
 * @param entries oldest first, at most {@link ReactorLog#CAPACITY}
 */
public record ReactorLogPayload(int containerId, int seenSeq, List<ReactorLog.Entry> entries)
		implements CustomPacketPayload {

	public static final Type<ReactorLogPayload> TYPE = new Type<>(Industrialization.id("reactor_log"));

	private static final StreamCodec<ByteBuf, ReactorLog.Kind> KIND =
			ByteBufCodecs.idMapper(ReactorLog.Kind::byOrdinal, ReactorLog.Kind::ordinal);

	private static final StreamCodec<ByteBuf, ReactorLog.Entry> ENTRY = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ReactorLog.Entry::seq,
			ByteBufCodecs.VAR_LONG, ReactorLog.Entry::time,
			KIND, ReactorLog.Entry::kind,
			ByteBufCodecs.VAR_INT, ReactorLog.Entry::a,
			ByteBufCodecs.VAR_INT, ReactorLog.Entry::b,
			ByteBufCodecs.VAR_INT, ReactorLog.Entry::c,
			ByteBufCodecs.stringUtf8(ReactorLog.ACTOR_MAX), ReactorLog.Entry::actor,
			ReactorLog.Entry::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, ReactorLogPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ReactorLogPayload::containerId,
			ByteBufCodecs.VAR_INT, ReactorLogPayload::seenSeq,
			ENTRY.apply(ByteBufCodecs.list(ReactorLog.CAPACITY)), ReactorLogPayload::entries,
			ReactorLogPayload::new);

	public ReactorLogPayload {
		entries = List.copyOf(entries);
	}

	/** The newest entry's number, or 0 for an empty log. */
	public int newestSeq() {
		return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).seq();
	}

	@Override
	public Type<ReactorLogPayload> type() {
		return TYPE;
	}
}
