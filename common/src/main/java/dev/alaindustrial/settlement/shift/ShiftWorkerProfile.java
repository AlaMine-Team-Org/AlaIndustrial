package dev.alaindustrial.settlement.shift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * A shift worker's profile (MOD-854, epic decisions R67–R70): its battery, the EU that has left it — the only source of
 * its level — the points it has invested and its service marks. Saved as the one optional field {@code profile} of
 * {@link ShiftWorkerRecord}; immutable, every change is a new profile.
 *
 * <p><b>Save format.</b> Every field is optional with a default, and {@link #FRESH} is the default of the whole
 * field: a record written before MOD-854 (the first slice) reads as a worker with a full battery at level 1, and a
 * profile still equal to {@link #FRESH} is not written at all. The arithmetic lives in {@link ShiftWorkerLevels}
 * (Minecraft-free, L1).
 *
 * @param charge         EU in the battery; {@link #FULL} means "full" — whatever the capacity is when it is read
 *                       ({@link #charge(long)}); the logic writes a real number the first time it drains
 * @param euDrained      every EU that has left the battery, ever — the level's only source (charging counts for
 *                       nothing, R67)
 * @param highestLevel   the highest level reached; a knob change never lowers a worker below it, only a return (R39,
 *                       {@link #returnedOneLevelDown()}) does
 * @param investments    the stats invested in, in the order the points went in — the count per stat is derived, and a
 *                       return takes the LAST one back (R39, R69)
 * @param issuedGameTime when the reception capsule issued the worker, {@code -1} unknown (a first-slice worker)
 * @param marks          the service marks, oldest first, at most {@link #MAX_MARKS} — the newest are kept
 * @param biography      the ids of the four biography fragments, in slot order (ADR-050 point 7); empty until chosen
 *                       — an older save, or a world without the registry — and then chosen on the first card
 */
public record ShiftWorkerProfile(long charge, long euDrained, int highestLevel, List<Stat> investments,
		long issuedGameTime, List<ServiceMark> marks, List<Identifier> biography) {

	/** The charge that means "full" — the profile of a first-slice worker or a worker just issued. */
	public static final long FULL = -1L;
	/** Service marks a profile keeps; an older one leaves when a newer one comes. */
	public static final int MAX_MARKS = 12;
	/** A profile nobody has touched: full battery, nothing drained, level 1, no points, no marks. */
	public static final ShiftWorkerProfile FRESH = new ShiftWorkerProfile(FULL, 0L, 1, List.of(), -1L, List.of(),
			List.of());

	public ShiftWorkerProfile {
		investments = List.copyOf(investments);
		marks = marks.size() > MAX_MARKS ? List.copyOf(marks.subList(marks.size() - MAX_MARKS, marks.size()))
				: List.copyOf(marks);
		biography = List.copyOf(biography);
		highestLevel = Math.max(1, highestLevel);
		euDrained = Math.max(0L, euDrained);
	}

	/** The four stats (R69); only {@link #STRENGTH} and {@link #BATTERY} work before stage 5a. */
	public enum Stat implements StringRepresentable {
		/** +health per point. */
		STRENGTH("strength", true),
		/** +battery capacity per point. */
		BATTERY("battery", true),
		/** +one carried slot per point — from stage 5a. */
		CAPACITY("capacity", false),
		/** +3 % work pace per point — from stage 5a. */
		DEXTERITY("dexterity", false);

		public static final Codec<Stat> CODEC = StringRepresentable.fromEnum(Stat::values);
		private final String name;
		private final boolean active;

		Stat(String name, boolean active) {
			this.name = name;
			this.active = active;
		}

		/** Whether a point here does anything in this build; an inactive stat is shown greyed and takes no points. */
		public boolean active() {
			return active;
		}

		/** The stat's lang key, {@code shift_worker.alaindustrial.stat.<name>}. */
		public String langKey() {
			return "shift_worker.alaindustrial.stat." + name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/**
	 * One service mark of the «Personal file» tab: plain data the client turns into a line through the lang key of its
	 * {@link Kind} with {@link #args} as the arguments, so a line reads in the viewer's language.
	 *
	 * @param kind     what happened
	 * @param gameTime when; the day is {@code gameTime / 24000}
	 * @param args     the line's arguments as text, in the order the kind's javadoc lists them
	 */
	public record ServiceMark(Kind kind, long gameTime, List<String> args) {

		public ServiceMark {
			args = List.copyOf(args);
		}

		/** What a mark records. Each kind's lang key is {@code shift_worker.alaindustrial.mark.<name>}. */
		public enum Kind implements StringRepresentable {
			/** Issued by the reception capsule; no args. */
			ISSUED("issued"),
			/** A name was written; args: the name. */
			NAMED("named"),
			/** Wounded; args: the attacker's entity type id (empty — unknown), health left, maximum health. */
			WOUNDED("wounded"),
			/** Reached a level; args: the level. */
			LEVEL("level"),
			/** Reached a rank; args: the rank number 1..8. */
			RANK("rank"),
			/** Brought back by the capsule (R39); args: the level it came back at. */
			RETURNED("returned"),
			/** Re-registered under a new number (R41, MOD-834); args: the old number, the new number. */
			RENUMBERED("renumbered");

			public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
			private final String name;

			Kind(String name) {
				this.name = name;
			}

			/** The lang key of a line of this kind. */
			public String langKey() {
				return "shift_worker.alaindustrial.mark." + name;
			}

			@Override
			public String getSerializedName() {
				return name;
			}
		}

		public static final Codec<ServiceMark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Kind.CODEC.fieldOf("kind").forGetter(ServiceMark::kind),
				Codec.LONG.optionalFieldOf("game_time", 0L).forGetter(ServiceMark::gameTime),
				Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(ServiceMark::args)
		).apply(instance, ServiceMark::new));
	}

	public static final Codec<ShiftWorkerProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("charge", FULL).forGetter(ShiftWorkerProfile::charge),
			Codec.LONG.optionalFieldOf("eu_drained", 0L).forGetter(ShiftWorkerProfile::euDrained),
			Codec.INT.optionalFieldOf("highest_level", 1).forGetter(ShiftWorkerProfile::highestLevel),
			Stat.CODEC.listOf().optionalFieldOf("investments", List.of()).forGetter(ShiftWorkerProfile::investments),
			Codec.LONG.optionalFieldOf("issued_game_time", -1L).forGetter(ShiftWorkerProfile::issuedGameTime),
			ServiceMark.CODEC.listOf().optionalFieldOf("marks", List.of()).forGetter(ShiftWorkerProfile::marks),
			Identifier.CODEC.listOf().optionalFieldOf("biography", List.of()).forGetter(ShiftWorkerProfile::biography)
	).apply(instance, ShiftWorkerProfile::new));

	// ── derived values ──────────────────────────────────────────────────────────────────────────

	/** Points invested in {@code stat}. */
	public int count(Stat stat) {
		int n = 0;
		for (Stat invested : investments) {
			if (invested == stat) {
				n++;
			}
		}
		return n;
	}

	/** The count per stat, in {@link Stat} order. */
	public int[] counts() {
		int[] counts = new int[Stat.values().length];
		for (Stat invested : investments) {
			counts[invested.ordinal()]++;
		}
		return counts;
	}

	/** The level, at the live knobs ({@link ShiftWorkerLevels#level(long, int)}). */
	public int level() {
		return ShiftWorkerLevels.level(euDrained, highestLevel);
	}

	/** Points not yet invested. */
	public int freePoints() {
		return ShiftWorkerLevels.freePoints(level(), investments.size());
	}

	/** Battery capacity, at the live knobs. */
	public long capacity() {
		return ShiftWorkerLevels.capacity(count(Stat.BATTERY));
	}

	/** Maximum health, at the live knobs. */
	public int maxHealth() {
		return ShiftWorkerLevels.maxHealth(count(Stat.STRENGTH));
	}

	/** The charge as a number: {@link #FULL} reads as {@code capacity}, anything else is capped at it. */
	public static long charge(long stored, long capacity) {
		return stored < 0 ? capacity : Math.min(stored, capacity);
	}

	/** {@link #charge(long, long)} at this profile's own capacity. */
	public long chargeNow() {
		return charge(charge, capacity());
	}

	// ── changes ─────────────────────────────────────────────────────────────────────────────────

	/** This profile with {@code newCharge} in the battery. */
	public ShiftWorkerProfile withCharge(long newCharge) {
		return new ShiftWorkerProfile(newCharge, euDrained, highestLevel, investments, issuedGameTime, marks,
				biography);
	}

	/**
	 * This profile after {@code eu} more left the battery: the charge lowered (from its real value), the drained total
	 * raised and the highest level carried up. Does not write the level marks — the caller compares
	 * {@link #level()} before and after.
	 */
	public ShiftWorkerProfile drained(long eu) {
		long spent = Math.max(0L, Math.min(eu, chargeNow()));
		long total = euDrained + spent;
		int reached = ShiftWorkerLevels.level(total, highestLevel);
		return new ShiftWorkerProfile(chargeNow() - spent, total, reached, investments, issuedGameTime, marks,
				biography);
	}

	/**
	 * This profile as if {@code eu} more had left the battery, the charge untouched: the drained total raised and the
	 * highest level carried up. The test command's lift ({@code /ala worker xp}); the marks are the caller's, as in
	 * {@link #drained}.
	 */
	public ShiftWorkerProfile trained(long eu) {
		long total = euDrained + Math.max(0L, eu);
		int reached = ShiftWorkerLevels.level(total, highestLevel);
		return new ShiftWorkerProfile(charge, total, reached, investments, issuedGameTime, marks, biography);
	}

	/** This profile with one more point in {@code stat}; the caller checks free points and the cap. */
	public ShiftWorkerProfile invested(Stat stat) {
		List<Stat> next = new ArrayList<>(investments);
		next.add(stat);
		return new ShiftWorkerProfile(charge, euDrained, highestLevel, next, issuedGameTime, marks, biography);
	}

	/** This profile with every point taken back (the paid redistribution at the capsule, R69). */
	public ShiftWorkerProfile respecced() {
		return new ShiftWorkerProfile(charge, euDrained, highestLevel, List.of(), issuedGameTime, marks, biography);
	}

	/**
	 * This profile one level down — a dead worker brought back (R39, R56): the drained total set to the threshold of
	 * level N−1, the highest level to N−1, and the last invested point taken back so free points stay what they were.
	 * At level 1 only the drained total is reset to 0.
	 */
	public ShiftWorkerProfile returnedOneLevelDown() {
		int down = Math.max(1, level() - 1);
		List<Stat> next = new ArrayList<>(investments);
		if (level() > 1 && !next.isEmpty()) {
			next.remove(next.size() - 1);
		}
		return new ShiftWorkerProfile(charge, ShiftWorkerLevels.drainedForLevel(down), down, next, issuedGameTime,
				marks, biography);
	}

	/** This profile with the issuing time. */
	public ShiftWorkerProfile issuedAt(long gameTime) {
		return new ShiftWorkerProfile(charge, euDrained, highestLevel, investments, gameTime, marks, biography);
	}

	/** This profile with {@code mark} added; the oldest leaves past {@link #MAX_MARKS}. */
	public ShiftWorkerProfile marked(ServiceMark mark) {
		List<ServiceMark> next = new ArrayList<>(marks);
		next.add(mark);
		return new ShiftWorkerProfile(charge, euDrained, highestLevel, investments, issuedGameTime, next, biography);
	}

	/** This profile with its newest mark replaced by {@code mark} (one fight is one WOUNDED mark); adds it if none. */
	public ShiftWorkerProfile withNewestMark(ServiceMark mark) {
		List<ServiceMark> next = new ArrayList<>(marks);
		if (!next.isEmpty()) {
			next.remove(next.size() - 1);
		}
		next.add(mark);
		return new ShiftWorkerProfile(charge, euDrained, highestLevel, investments, issuedGameTime, next, biography);
	}

	/** This profile with the biography fragments {@code ids}, in slot order. */
	public ShiftWorkerProfile withBiography(List<Identifier> ids) {
		return new ShiftWorkerProfile(charge, euDrained, highestLevel, investments, issuedGameTime, marks, ids);
	}
}
