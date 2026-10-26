package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: the shift workers of one town for the «Workers» and «Closed» tabs of its core's screen (MOD-854) — sent to ONE
 * player, as the reply to a {@link ShiftWorkerRequestPayload} of kind {@code ROSTER}. A row is clicked to ask for its
 * card by number.
 *
 * @param core       the core the roster was asked at
 * @param settlement the town's number
 * @param active     the living workers and the dead waiting to be brought back (R39), in number order
 * @param closed     the dead for good (R30), in number order
 */
public record ShiftWorkerRosterPayload(BlockPos core, int settlement, List<Row> active, List<Row> closed)
		implements CustomPacketPayload {

	public static final Type<ShiftWorkerRosterPayload> TYPE = new Type<>(Industrialization.id("shift_worker_roster"));

	/** Most rows one list carries — far above any tier's worker limit. */
	public static final int MAX_ROWS = 512;

	public ShiftWorkerRosterPayload {
		active = List.copyOf(active);
		closed = List.copyOf(closed);
	}

	/**
	 * One row.
	 *
	 * @param number the record number
	 * @param name   the given name, empty — none
	 * @param level  the level
	 * @param status the record's status
	 * @param alive  whether it is alive ({@code status == ALIVE})
	 * @param look   the id of the worker's look, for the face at the row's start (owner request 7, stage 6 round 4)
	 * @param slim   the slim body, as on the card
	 * @param profession its trade's serialized name (MOD-888), empty — none: the row names the trade
	 */
	public record Row(String number, String name, int level, ShiftWorkerRecord.Status status, boolean alive,
			Identifier look, boolean slim, String profession) {

		/** A row of a worker without work. */
		public Row(String number, String name, int level, ShiftWorkerRecord.Status status, boolean alive,
				Identifier look, boolean slim) {
			this(number, name, level, status, alive, look, slim, "");
		}
	}

	private static final StreamCodec<ByteBuf, Row> ROW = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(ShiftWorkerCardPayload.TEXT_LIMIT), Row::number,
			ByteBufCodecs.stringUtf8(ShiftWorkerCardPayload.TEXT_LIMIT), Row::name,
			ByteBufCodecs.VAR_INT, Row::level,
			ShiftWorkerCardPayload.STATUS, Row::status,
			ByteBufCodecs.BOOL, Row::alive,
			Identifier.STREAM_CODEC, Row::look,
			ByteBufCodecs.BOOL, Row::slim,
			ByteBufCodecs.stringUtf8(ShiftWorkerCardPayload.TEXT_LIMIT), Row::profession,
			Row::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, ShiftWorkerRosterPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ShiftWorkerRosterPayload::core,
			ByteBufCodecs.VAR_INT, ShiftWorkerRosterPayload::settlement,
			ROW.apply(ByteBufCodecs.list(MAX_ROWS)), ShiftWorkerRosterPayload::active,
			ROW.apply(ByteBufCodecs.list(MAX_ROWS)), ShiftWorkerRosterPayload::closed,
			ShiftWorkerRosterPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
