package dev.alaindustrial.settlement.shift;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.record.RecordNumber;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every shift worker of the save and the town halls' log (MOD-832, ADR-050 point 8): the source of truth of the
 * workers, kept inside the towns' {@code SettlementRegistry} as its {@code shift_workers} field. The entity in the
 * world finds its record here by its own UUID; the log has one line per death (R10a, R31), each carrying its town.
 *
 * <p><b>Saved in number order</b>, so the same workers always write the same file; the form carries a version and a
 * ladder of upgrades ({@link Versioned}). The path dispatchers of the towns ({@link PathQuota}) live here too, but
 * are not saved: they are the towns' per-tick state.
 *
 * <p><b>Who keeps it dirty.</b> Every change calls the hook the owning registry installs ({@link #onChange}), which
 * marks the registry's saved data dirty. Server thread only.
 */
public final class ShiftWorkerBook {

	/**
	 * One line of a town hall's log: who died, how and when (R10a).
	 *
	 * @param settlement the town the worker belonged to, 0 — none
	 * @param number     the worker's number
	 * @param name       the worker's name, empty — none
	 * @param death      how it died
	 */
	public record LogLine(int settlement, RecordNumber number, String name, ShiftWorkerRecord.Death death) {

		public static final Codec<LogLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("settlement", 0).forGetter(LogLine::settlement),
				ShiftWorkerRecord.NUMBER_CODEC.fieldOf("number").forGetter(LogLine::number),
				Codec.STRING.optionalFieldOf("name", "").forGetter(LogLine::name),
				ShiftWorkerRecord.Death.CODEC.fieldOf("death").forGetter(LogLine::death)
		).apply(instance, LogLine::new));
	}

	private static final Codec<ShiftWorkerBook> BODY = RecordCodecBuilder.create(instance -> instance.group(
			ShiftWorkerRecord.CODEC.listOf().optionalFieldOf("workers", List.of())
					.forGetter(ShiftWorkerBook::sortedRecords),
			LogLine.CODEC.listOf().optionalFieldOf("hall_log", List.of()).forGetter(book -> book.log)
	).apply(instance, ShiftWorkerBook::new));

	/** The saved form, versioned ({@link ShiftWorkerRecord#DATA_VERSION}); no rung yet — the form is at version 1. */
	public static final Codec<ShiftWorkerBook> CODEC = new Versioned<>(BODY, ShiftWorkerRecord.DATA_VERSION,
			List.of());

	/** Records by entity UUID, in the order they were added. */
	private final Map<UUID, ShiftWorkerRecord> byEntity = new LinkedHashMap<>();
	/** The log, oldest line first. */
	private final List<LogLine> log = new ArrayList<>();
	/** Path dispatchers by town number; runtime only. */
	private final Map<Integer, PathQuota> paths = new HashMap<>();
	/** Called on every change; the owning registry marks itself dirty. */
	private Runnable onChange = () -> { };

	/** An empty book: a save with no workers yet. */
	public ShiftWorkerBook() {
	}

	private ShiftWorkerBook(List<ShiftWorkerRecord> records, List<LogLine> log) {
		for (ShiftWorkerRecord record : records) {
			ShiftWorkerRecord before = byEntity.putIfAbsent(record.entity(), record);
			if (before != null) {
				Industrialization.LOGGER.warn("[ala-towns] two saved shift workers {} and {} share the entity {}; "
						+ "kept the first", before.number(), record.number(), record.entity());
			}
		}
		this.log.addAll(log);
	}

	/** The owner's dirty hook: called after every change of the records or the log. */
	public void onChange(Runnable hook) {
		this.onChange = hook;
	}

	/** Whether the book holds nothing worth saving: no worker, no log line. */
	public boolean isEmpty() {
		return byEntity.isEmpty() && log.isEmpty();
	}

	/** The record of the worker whose entity has {@code entity} as UUID. */
	public Optional<ShiftWorkerRecord> byEntity(UUID entity) {
		return Optional.ofNullable(byEntity.get(entity));
	}

	/** The record holding {@code number}, if any. A scan: called on rare events, never per tick. */
	public Optional<ShiftWorkerRecord> byNumber(RecordNumber number) {
		for (ShiftWorkerRecord record : byEntity.values()) {
			if (record.number().equals(number)) {
				return Optional.of(record);
			}
		}
		return Optional.empty();
	}

	/** Every record, in the order they were added. */
	public Collection<ShiftWorkerRecord> all() {
		return Collections.unmodifiableCollection(byEntity.values());
	}

	/** The town hall's log, oldest line first, every town together. */
	public List<LogLine> log() {
		return Collections.unmodifiableList(log);
	}

	/** Stores {@code record}, in place of the one with the same entity UUID. */
	public void put(ShiftWorkerRecord record) {
		if (!record.equals(byEntity.put(record.entity(), record))) {
			onChange.run();
		}
	}

	/** Forgets the record of {@code entity} — a worker whose entity could not join the world. */
	public void remove(UUID entity) {
		if (byEntity.remove(entity) != null) {
			onChange.run();
		}
	}

	/** Adds a line to the hall's log. */
	public void addToLog(LogLine line) {
		log.add(line);
		onChange.run();
	}

	/** The path dispatcher of town {@code settlement} (0: workers of no town share one). */
	public PathQuota paths(int settlement) {
		return paths.computeIfAbsent(settlement, id -> new PathQuota());
	}

	private List<ShiftWorkerRecord> sortedRecords() {
		List<ShiftWorkerRecord> sorted = new ArrayList<>(byEntity.values());
		sorted.sort((a, b) -> a.number().compareTo(b.number()));
		return sorted;
	}

	/**
	 * A saved form with a version and a ladder of upgrades — the shift workers' data "with a ladder of migrations"
	 * (ADR-050 point 8, MOD-832). The body codec reads and writes the current form; the saved map carries
	 * {@value #VERSION_KEY}, and a map written by an older build is lifted one rung at a time before the body reads
	 * it.
	 *
	 * <p><b>How a field changes.</b> A new optional field with a default needs no rung — an older map simply lacks
	 * it. Anything else (a renamed or reshaped field, a changed meaning) raises the current version by one and adds
	 * the rung that rewrites the old map; the reviewed snapshot of the form ({@code ShiftWorkerRecordSnapshot}) shows
	 * the change.
	 *
	 * <p><b>A newer form than this build knows</b> is read as the current one, with a warning: refusing it would
	 * leave an empty registry, and an empty registry discards every living worker on its next load.
	 */
	static final class Versioned<A> implements Codec<A> {

		static final String VERSION_KEY = "data_version";

		/** One rung: rewrites a map of version {@code n} into version {@code n + 1}. */
		interface Rung {
			<T> Dynamic<T> upgrade(Dynamic<T> older);
		}

		private final Codec<A> body;
		private final int current;
		/** Rung {@code i} lifts version {@code i + 1} to {@code i + 2}; there are {@code current - 1} of them. */
		private final List<Rung> ladder;

		Versioned(Codec<A> body, int current, List<Rung> ladder) {
			if (ladder.size() != current - 1) {
				throw new IllegalArgumentException("version " + current + " needs " + (current - 1)
						+ " rungs, the ladder has " + ladder.size());
			}
			this.body = body;
			this.current = current;
			this.ladder = List.copyOf(ladder);
		}

		@Override
		public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
			Dynamic<T> map = new Dynamic<>(ops, input);
			int version = map.get(VERSION_KEY).asInt(current);
			if (version > current) {
				Industrialization.LOGGER.warn("[ala-towns] the saved shift workers are of version {}, newer than "
						+ "this build's {}; reading them as version {}", version, current, current);
			}
			for (int from = Math.max(1, version); from < current; from++) {
				map = ladder.get(from - 1).upgrade(map);
			}
			return body.decode(ops, map.remove(VERSION_KEY).getValue());
		}

		@Override
		public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
			return body.encode(input, ops, prefix).flatMap(map -> ops.mergeToMap(map, ops.createString(VERSION_KEY),
					ops.createInt(current)));
		}
	}
}
