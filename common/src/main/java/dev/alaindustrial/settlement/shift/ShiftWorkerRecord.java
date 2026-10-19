package dev.alaindustrial.settlement.shift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.settlement.record.RecordNumber;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * One shift worker as the server knows it (MOD-832, ADR-050 point 8): the source of truth. The entity in the world
 * keeps none of this — it finds its record by its own UUID — so the number, the look and the status live in exactly
 * one place, and the hidden renumbering of decision R41 (MOD-834) changes the number here and nowhere else.
 * Immutable: every change is a new record the registry stores in place of the old one.
 *
 * <p><b>The saved form is a save-format contract.</b> New fields come only as optional ones with a default, and
 * {@link #DATA_VERSION} with the ladder in {@link ShiftWorkerBook} is how an older record would be upgraded; the
 * reviewed shape is pinned by {@code ShiftWorkerRecordSnapshot}.
 *
 * @param number     the record number, issued by {@code RecordNumberLedger} (MOD-827)
 * @param entity     the UUID of the worker's entity in the world; a loaded entity with another UUID is not this worker
 * @param look       the id of the worker's {@code shift_worker_look}, chosen once (ADR-050 point 6)
 * @param slim       the slim body — a woman (R3g); kept here so a look that left the datapack is replaced by one of the
 *                   same body
 * @param name       the name a player gave (R3v, a later stage); empty — the worker is known by the number
 * @param settlement the number of the worker's town ({@code SettlementRegistry}), 0 — none; the town's owner is the
 *                   worker's (R34), and the town's core decides whether it works or hibernates (R10)
 * @param status     alive, dead for good, or dead and waiting to be brought back (R30, R39)
 * @param death      how it died, while it is dead
 */
public record ShiftWorkerRecord(RecordNumber number, UUID entity, Identifier look, boolean slim, String name,
		int settlement, Status status, Optional<Death> death) {

	/** The version of the saved form this build writes (ADR-050 point 8, the ladder in {@link ShiftWorkerBook}). */
	public static final int DATA_VERSION = 1;

	/** Where the worker is in its life (R10a, R30, R39). */
	public enum Status implements StringRepresentable {
		/** In the world, working. */
		ALIVE("alive"),
		/** Dead for good: the number is closed (R10a, R30 on). */
		DEAD("dead"),
		/** Dead with permadeath off (R39): the number stays the worker's, the capsule may bring it back (MOD-833). */
		AWAITING_RETURN("awaiting_return");

		public static final Codec<Status> CODEC = StringRepresentable.fromEnum(Status::values);
		private final String name;

		Status(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/**
	 * How a worker died — one line of the town hall's log (R10a: number, name, cause, date).
	 *
	 * @param cause    the id of the damage type that killed it, {@code minecraft:mob_attack} and the like
	 * @param killer   the entity type of whoever dealt the blow, or empty
	 * @param gameTime the game time of the death; the day is {@code gameTime / 24000}
	 */
	public record Death(Identifier cause, Optional<Identifier> killer, long gameTime) {

		public static final Codec<Death> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("cause").forGetter(Death::cause),
				Identifier.CODEC.optionalFieldOf("killer").forGetter(Death::killer),
				Codec.LONG.fieldOf("game_time").forGetter(Death::gameTime)
		).apply(instance, Death::new));
	}

	/** A record number written as {@code <letter>-<NNN>}; a string in another shape fails to decode. */
	public static final Codec<RecordNumber> NUMBER_CODEC = Codec.STRING.comapFlatMap(
			text -> RecordNumber.parse(text).map(DataResult::success)
					.orElseGet(() -> DataResult.error(() -> "not a record number: " + text)),
			RecordNumber::toString);

	public static final Codec<ShiftWorkerRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			NUMBER_CODEC.fieldOf("number").forGetter(ShiftWorkerRecord::number),
			UUIDUtil.CODEC.fieldOf("entity").forGetter(ShiftWorkerRecord::entity),
			Identifier.CODEC.fieldOf("look").forGetter(ShiftWorkerRecord::look),
			Codec.BOOL.optionalFieldOf("slim", false).forGetter(ShiftWorkerRecord::slim),
			Codec.STRING.optionalFieldOf("name", "").forGetter(ShiftWorkerRecord::name),
			Codec.INT.optionalFieldOf("settlement", 0).forGetter(ShiftWorkerRecord::settlement),
			Status.CODEC.optionalFieldOf("status", Status.ALIVE).forGetter(ShiftWorkerRecord::status),
			Death.CODEC.optionalFieldOf("death").forGetter(ShiftWorkerRecord::death)
	).apply(instance, ShiftWorkerRecord::new));

	/** A worker just issued: alive, no name, no death. */
	public static ShiftWorkerRecord issued(RecordNumber number, UUID entity, Identifier look, boolean slim,
			int settlement) {
		return new ShiftWorkerRecord(number, entity, look, slim, "", settlement, Status.ALIVE, Optional.empty());
	}

	/** This record after the worker died; dead for good with {@code permadeath}, waiting to come back without (R39). */
	public ShiftWorkerRecord died(Death how, boolean permadeath) {
		return new ShiftWorkerRecord(number, entity, look, slim, name, settlement,
				permadeath ? Status.DEAD : Status.AWAITING_RETURN, Optional.of(how));
	}

	/**
	 * This record brought back by the reception capsule (R39, MOD-833): alive again in a new body {@code newEntity},
	 * with the same number, look, name and town, its death forgotten — the hall's log keeps the line about it.
	 */
	public ShiftWorkerRecord returned(UUID newEntity) {
		return new ShiftWorkerRecord(number, newEntity, look, slim, name, settlement, Status.ALIVE, Optional.empty());
	}

	/** This record with another look — the old one left the datapack and the rule chose again. */
	public ShiftWorkerRecord withLook(Identifier newLook) {
		return new ShiftWorkerRecord(number, entity, newLook, slim, name, settlement, status, death);
	}

	/** This record with another look and its body — a look chosen by hand (a stand, an admin's command). */
	public ShiftWorkerRecord withLook(Identifier newLook, boolean newSlim) {
		return new ShiftWorkerRecord(number, entity, newLook, newSlim, name, settlement, status, death);
	}
}
