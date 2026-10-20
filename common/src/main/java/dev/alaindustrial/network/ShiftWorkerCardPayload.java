package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: everything a shift worker's card shows (MOD-854), as plain data — sent to ONE player, the viewer, by
 * {@code ShiftWorkerProfiles.sendCard}. The client keeps no worker state of its own; a refresh is the same payload
 * with {@code open = false}. Everyone may view any card read-only (R74); {@code viewerIsOwner} is what unlocks the
 * «+», rename and respec controls, and the server checks it again on every action.
 *
 * @param open           open the card screen (a right click, a roster row) — false: refresh it if it is open
 * @param entityId       the worker's loaded entity id for the live model, {@code -1} when not loaded or dead
 * @param number         the record number as shown, {@code W-117}
 * @param name           the given name, empty — none
 * @param slim           the slim body (a woman)
 * @param look           the look id, for the model when the entity is not loaded
 * @param status         alive, dead, or waiting to be brought back
 * @param settlement     the town's number, 0 — none
 * @param settlementName the town's name as the server resolves it
 * @param viewerIsOwner  whether the viewer may invest, rename and respec
 * @param charge         EU in the battery now
 * @param capacity       the battery capacity
 * @param health         current health (maximum while not loaded and alive, 0 when dead)
 * @param maxHealth      maximum health with the «Strength» points
 * @param euDrained      «Passed through the battery»: every EU that has left it
 * @param level          level 1..40
 * @param xpIntoLevel    XP earned past the current level's threshold
 * @param xpForNext      XP between this level's threshold and the next one's; 0 at the top level
 * @param rankIndex      rank 0..7 ("rank 1" … "rank 8")
 * @param freePoints     points not yet invested
 * @param statCap        points one stat holds at most
 * @param statCounts     points per stat, in {@link ShiftWorkerProfile.Stat} order
 * @param statActive     whether each stat works in this build, in the same order
 * @param biography      the four biography lines, resolved on the server (empty until the biography exists)
 * @param marks          the service marks, oldest first, at most {@link ShiftWorkerProfile#MAX_MARKS}
 * @param issuedGameTime when the capsule issued the worker, {@code -1} unknown
 * @param formerNumbers  the numbers it held before, oldest first, each with when it was re-registered
 * @param death          how it died, while dead
 * @param returnPrice    EU bringing it back costs, while it waits to come back; 0 otherwise
 * @param atCapsule      whether the worker stands at a reception capsule of its town
 * @param canRespec      whether the viewer can take all points back now (owner, at a capsule that holds the price)
 * @param respecCost     the price of that
 */
public record ShiftWorkerCardPayload(boolean open, int entityId, String number, String name, boolean slim,
		Identifier look, ShiftWorkerRecord.Status status, int settlement, Component settlementName,
		boolean viewerIsOwner, long charge, long capacity, float health, float maxHealth, long euDrained, int level,
		long xpIntoLevel, long xpForNext, int rankIndex, int freePoints, int statCap, int[] statCounts,
		boolean[] statActive, List<Component> biography, List<ShiftWorkerProfile.ServiceMark> marks,
		long issuedGameTime, List<FormerNumber> formerNumbers, Optional<ShiftWorkerRecord.Death> death,
		long returnPrice, boolean atCapsule, boolean canRespec, long respecCost) implements CustomPacketPayload {

	public static final Type<ShiftWorkerCardPayload> TYPE = new Type<>(Industrialization.id("shift_worker_card"));

	/** Longest name or number string that decodes; a worker's name is at most 16 characters. */
	static final int TEXT_LIMIT = 64;
	/** Most biography lines that decode. */
	static final int MAX_BIOGRAPHY = 8;
	/** Most former numbers that decode. */
	static final int MAX_FORMER = 32;
	/** Most arguments of one mark that decode. */
	static final int MAX_MARK_ARGS = 8;
	private static final int STATS = ShiftWorkerProfile.Stat.values().length;

	public ShiftWorkerCardPayload {
		biography = List.copyOf(biography);
		marks = List.copyOf(marks);
		formerNumbers = List.copyOf(formerNumbers);
		if (statCounts.length != STATS || statActive.length != STATS) {
			throw new IllegalArgumentException("one count and one flag per stat, " + STATS + " each");
		}
	}

	/**
	 * A number this worker held before (R3, R41).
	 *
	 * @param number   the old number
	 * @param gameTime when it was re-registered under the next one, {@code -1} unknown
	 */
	public record FormerNumber(String number, long gameTime) {
	}

	private static final ShiftWorkerRecord.Status[] STATUSES = ShiftWorkerRecord.Status.values();
	private static final ShiftWorkerProfile.ServiceMark.Kind[] KINDS = ShiftWorkerProfile.ServiceMark.Kind.values();

	// By ordinal: one server talks to clients of the same build; an unknown ordinal reads as the first value.
	static final StreamCodec<ByteBuf, ShiftWorkerRecord.Status> STATUS = ByteBufCodecs.idMapper(
			ordinal -> ordinal >= 0 && ordinal < STATUSES.length ? STATUSES[ordinal] : STATUSES[0],
			ShiftWorkerRecord.Status::ordinal);

	private static final StreamCodec<ByteBuf, ShiftWorkerProfile.ServiceMark.Kind> KIND = ByteBufCodecs.idMapper(
			ordinal -> ordinal >= 0 && ordinal < KINDS.length ? KINDS[ordinal] : KINDS[0],
			ShiftWorkerProfile.ServiceMark.Kind::ordinal);

	private static final StreamCodec<ByteBuf, ShiftWorkerProfile.ServiceMark> MARK = StreamCodec.composite(
			KIND, ShiftWorkerProfile.ServiceMark::kind,
			ByteBufCodecs.VAR_LONG, ShiftWorkerProfile.ServiceMark::gameTime,
			ByteBufCodecs.stringUtf8(TEXT_LIMIT).apply(ByteBufCodecs.list(MAX_MARK_ARGS)),
			ShiftWorkerProfile.ServiceMark::args,
			ShiftWorkerProfile.ServiceMark::new);

	private static final StreamCodec<ByteBuf, FormerNumber> FORMER = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(TEXT_LIMIT), FormerNumber::number,
			ByteBufCodecs.VAR_LONG, FormerNumber::gameTime,
			FormerNumber::new);

	private static final StreamCodec<ByteBuf, ShiftWorkerRecord.Death> DEATH = StreamCodec.composite(
			Identifier.STREAM_CODEC, ShiftWorkerRecord.Death::cause,
			ByteBufCodecs.optional(Identifier.STREAM_CODEC), ShiftWorkerRecord.Death::killer,
			ByteBufCodecs.VAR_LONG, ShiftWorkerRecord.Death::gameTime,
			ShiftWorkerRecord.Death::new);

	private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> BIOGRAPHY =
			ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BIOGRAPHY));
	private static final StreamCodec<ByteBuf, List<ShiftWorkerProfile.ServiceMark>> MARKS =
			MARK.apply(ByteBufCodecs.list(ShiftWorkerProfile.MAX_MARKS));
	private static final StreamCodec<ByteBuf, List<FormerNumber>> FORMERS =
			FORMER.apply(ByteBufCodecs.list(MAX_FORMER));
	private static final StreamCodec<ByteBuf, String> TEXT = ByteBufCodecs.stringUtf8(TEXT_LIMIT);

	public static final StreamCodec<RegistryFriendlyByteBuf, ShiftWorkerCardPayload> CODEC =
			StreamCodec.of((buf, card) -> card.write(buf), ShiftWorkerCardPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(open);
		buf.writeVarInt(entityId);
		TEXT.encode(buf, number);
		TEXT.encode(buf, name);
		buf.writeBoolean(slim);
		Identifier.STREAM_CODEC.encode(buf, look);
		STATUS.encode(buf, status);
		buf.writeVarInt(settlement);
		ComponentSerialization.STREAM_CODEC.encode(buf, settlementName);
		buf.writeBoolean(viewerIsOwner);
		buf.writeVarLong(charge);
		buf.writeVarLong(capacity);
		buf.writeFloat(health);
		buf.writeFloat(maxHealth);
		buf.writeVarLong(euDrained);
		buf.writeVarInt(level);
		buf.writeVarLong(xpIntoLevel);
		buf.writeVarLong(xpForNext);
		buf.writeVarInt(rankIndex);
		buf.writeVarInt(freePoints);
		buf.writeVarInt(statCap);
		for (int i = 0; i < STATS; i++) {
			buf.writeVarInt(statCounts[i]);
			buf.writeBoolean(statActive[i]);
		}
		BIOGRAPHY.encode(buf, biography);
		MARKS.encode(buf, marks);
		buf.writeLong(issuedGameTime);
		FORMERS.encode(buf, formerNumbers);
		ByteBufCodecs.optional(DEATH).encode(buf, death);
		buf.writeVarLong(returnPrice);
		buf.writeBoolean(atCapsule);
		buf.writeBoolean(canRespec);
		buf.writeVarLong(respecCost);
	}

	private static ShiftWorkerCardPayload read(RegistryFriendlyByteBuf buf) {
		boolean open = buf.readBoolean();
		int entityId = buf.readVarInt();
		String number = TEXT.decode(buf);
		String name = TEXT.decode(buf);
		boolean slim = buf.readBoolean();
		Identifier look = Identifier.STREAM_CODEC.decode(buf);
		ShiftWorkerRecord.Status status = STATUS.decode(buf);
		int settlement = buf.readVarInt();
		Component settlementName = ComponentSerialization.STREAM_CODEC.decode(buf);
		boolean viewerIsOwner = buf.readBoolean();
		long charge = buf.readVarLong();
		long capacity = buf.readVarLong();
		float health = buf.readFloat();
		float maxHealth = buf.readFloat();
		long euDrained = buf.readVarLong();
		int level = buf.readVarInt();
		long xpIntoLevel = buf.readVarLong();
		long xpForNext = buf.readVarLong();
		int rankIndex = buf.readVarInt();
		int freePoints = buf.readVarInt();
		int statCap = buf.readVarInt();
		int[] statCounts = new int[STATS];
		boolean[] statActive = new boolean[STATS];
		for (int i = 0; i < STATS; i++) {
			statCounts[i] = buf.readVarInt();
			statActive[i] = buf.readBoolean();
		}
		List<Component> biography = BIOGRAPHY.decode(buf);
		List<ShiftWorkerProfile.ServiceMark> marks = MARKS.decode(buf);
		long issuedGameTime = buf.readLong();
		List<FormerNumber> formerNumbers = FORMERS.decode(buf);
		Optional<ShiftWorkerRecord.Death> death = ByteBufCodecs.optional(DEATH).decode(buf);
		long returnPrice = buf.readVarLong();
		boolean atCapsule = buf.readBoolean();
		boolean canRespec = buf.readBoolean();
		long respecCost = buf.readVarLong();
		return new ShiftWorkerCardPayload(open, entityId, number, name, slim, look, status, settlement, settlementName,
				viewerIsOwner, charge, capacity, health, maxHealth, euDrained, level, xpIntoLevel, xpForNext,
				rankIndex, freePoints, statCap, statCounts, statActive, biography, marks, issuedGameTime,
				formerNumbers, death, returnPrice, atCapsule, canRespec, respecCost);
	}

	/** The count of points in {@code stat}. */
	public int count(ShiftWorkerProfile.Stat stat) {
		return statCounts[stat.ordinal()];
	}

	/** Whether {@code stat} works in this build. */
	public boolean active(ShiftWorkerProfile.Stat stat) {
		return statActive[stat.ordinal()];
	}

	/** Whether the record is alive (the card is not stamped closed or waiting). */
	public boolean alive() {
		return status == ShiftWorkerRecord.Status.ALIVE;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
