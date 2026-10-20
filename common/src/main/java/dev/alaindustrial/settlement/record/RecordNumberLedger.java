package dev.alaindustrial.settlement.record;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.core.guide.ArchiveRecordSync;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The world's record-number service (MOD-827): hands out shift workers' numbers {@code <letter>-<NNN>}, closes
 * the numbers of the dead, and remembers which numbers are taken by players and labs. One server-global
 * {@link SavedData}, next to the settlements' own data, read with {@link #get(MinecraftServer)}.
 *
 * <p><b>The rules</b> (decisions R3a, R3b, R10a, R35 of MOD-824) live in {@link RecordNumberBook}: a number
 * is unique in the world, follows from the world seed and the order of issue, is never a known player's
 * record, never shares its three digits with a known lab, and a closed number is never handed out again.
 *
 * <p><b>What it cannot know in advance.</b> A player's record is a function of the seed and the player's id
 * ({@code ArchiveRecord}), and a lab's number of the seed and its position — neither is known before the
 * player joins or the lab generates. When one turns up that a living worker's number already has, the worker's
 * number is marked for a new one (R41 of MOD-824, MOD-834): a player's record cannot change (it is a save-format
 * contract), so the worker is the one renumbered — quietly, when no player can see it
 * ({@code ShiftWorkerRenumbering}), through {@link #renumber}. A dead worker's closed number keeps its tombstone.
 *
 * <p><b>Server thread only.</b> Like every {@code SavedData}. Worldgen runs on worker threads, so code that
 * learns a lab's number while generating hands it over with {@code server.execute(...)}.
 *
 * <p><b>Bound to the seed on first use.</b> {@code SavedDataType} builds a new instance with no arguments,
 * so the seed is attached by {@link #get} and saved with the rest; a ledger read back keeps the seed it was
 * saved with.
 */
public final class RecordNumberLedger extends SavedData {

	private static final Codec<List<String>> NUMBERS = Codec.STRING.listOf();

	/**
	 * The saved form. Numbers are written as {@code <letter>-<NNN>} strings, in the order of the space, so the
	 * file reads as what a player sees. A string that is not a number is dropped with a warning rather than
	 * failing the whole list: a ledger that fails to decode would be replaced by an empty one, and an empty
	 * ledger would hand out the living workers' numbers again.
	 */
	public static final Codec<RecordNumberLedger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("seed").forGetter(ledger -> Optional.ofNullable(ledger.book)
					.map(RecordNumberBook::worldSeed)),
			Codec.INT.optionalFieldOf("next", 0).forGetter(ledger -> ledger.saved(RecordNumberBook::next, 0)),
			NUMBERS.optionalFieldOf("live", List.of())
					.forGetter(ledger -> ledger.savedNumbers(RecordNumberBook::live)),
			NUMBERS.optionalFieldOf("closed", List.of())
					.forGetter(ledger -> ledger.savedNumbers(RecordNumberBook::closed)),
			NUMBERS.optionalFieldOf("players", List.of())
					.forGetter(ledger -> ledger.savedNumbers(RecordNumberBook::playerCodes)),
			Codec.INT.listOf().optionalFieldOf("labs", List.of())
					.forGetter(ledger -> ledger.saved(RecordNumberBook::labNumbers, List.of())),
			NUMBERS.optionalFieldOf("renumber", List.of())
					.forGetter(ledger -> ledger.savedNumbers(RecordNumberBook::due))
	).apply(instance, RecordNumberLedger::new));

	// DataFixTypes.LEVEL for the reason GuideBookState gives: the tag has no legacy schema, and the fixer
	// never runs on our own writes.
	public static final SavedDataType<RecordNumberLedger> TYPE = new SavedDataType<>(
			Industrialization.id("record_numbers"), RecordNumberLedger::new, CODEC, DataFixTypes.LEVEL);

	/** Null until {@link #get} binds the world seed; see the class comment. */
	private RecordNumberBook book;
	/** The "space spent" warning is written once per server run, not on every refused issue. */
	private boolean warnedExhausted;

	/** A new world's ledger, not yet bound to its seed. Only {@link #TYPE} calls this; callers use {@link #get}. */
	public RecordNumberLedger() {
	}

	private RecordNumberLedger(Optional<Long> seed, int next, List<String> live, List<String> closed,
			List<String> players, List<Integer> labs, List<String> renumber) {
		seed.ifPresent(worldSeed -> book = new RecordNumberBook(worldSeed, next, read(live, "live"),
				read(closed, "closed"), read(players, "players"), labs, read(renumber, "renumber")));
	}

	/** This world's ledger, bound to its seed. */
	public static RecordNumberLedger get(MinecraftServer server) {
		RecordNumberLedger ledger = server.getDataStorage().computeIfAbsent(TYPE);
		if (ledger.book == null) {
			ledger.book = new RecordNumberBook(server.overworld().getSeed());
			ledger.setDirty();
		}
		return ledger;
	}

	/**
	 * A new shift worker's number, never handed out before in this world — or empty when the space is spent
	 * (logged once). Nothing else creates a number ({@code RecordNumberRules}).
	 */
	public Optional<RecordNumber> issue() {
		RecordNumberBook book = book();
		int before = book.next();
		Optional<RecordNumber> number = book.issue();
		if (book.next() != before) {
			setDirty();
		}
		if (number.isEmpty() && !warnedExhausted) {
			warnedExhausted = true;
			Industrialization.LOGGER.warn("[ala-towns] every record number of this world has been handed out or "
					+ "skipped; no new shift worker number can be issued");
		}
		return number;
	}

	/**
	 * Closes {@code number} for good: its holder died (R10a). The number is never handed out again, also when
	 * this ledger never issued it.
	 *
	 * @return true if it was a living worker's number — false for a second death of the same number
	 */
	public boolean close(RecordNumber number) {
		RecordNumber.Status before = book().close(number);
		if (before != RecordNumber.Status.CLOSED) {
			setDirty();
		}
		return before == RecordNumber.Status.LIVE;
	}

	/**
	 * The number of the former resident whose plaque stands at ({@code x}, {@code y}, {@code z}) — the digits a
	 * generated town engraves on its house plaques (R40 of MOD-824). A pure function of the seed and the position,
	 * safe on any thread: world generation calls it while placing the plaque, and hands the number to
	 * {@link #closeFormerResident} on the server thread.
	 */
	public static RecordNumber formerResidentAt(long worldSeed, int x, int y, int z) {
		return RecordNumberBook.formerResident(worldSeed, x, y, z);
	}

	/**
	 * Closes the number of a former resident of a generated town for good (R40): a tombstone of the previous era,
	 * never handed out. A number a living worker already holds is marked for a new one instead (R41, MOD-834); the
	 * renumbering closes it.
	 *
	 * @return true if this call closed it — false when it was closed before or is a living worker's
	 */
	public boolean closeFormerResident(RecordNumber number) {
		boolean wasDue = book().isDue(number);
		RecordNumber.Status before = book().closeFormerResident(number);
		if (before == RecordNumber.Status.NOT_ISSUED) {
			setDirty();
		} else if (before == RecordNumber.Status.LIVE && !wasDue) {
			setDirty();
			Industrialization.LOGGER.info("[ala-towns] a town plaque shows {}, the number of a living shift worker; "
					+ "the worker is marked for a new number", number);
		}
		return before == RecordNumber.Status.NOT_ISSUED;
	}

	/**
	 * What this world knows about {@code number}. A saved worker whose number is not {@code LIVE} here is not
	 * the holder of it — a copy left by a rolled-back chunk, or one that came back after its death.
	 */
	public RecordNumber.Status statusOf(RecordNumber number) {
		return book().statusOf(number);
	}

	/**
	 * Records that a player with the record {@code code} plays in this world, so no worker is given it later.
	 *
	 * @return true if a worker — living or dead — already has this number (logged the first time); a living one is
	 *     marked for a new number (R41), a dead one keeps its tombstone
	 */
	public boolean notePlayerCode(RecordNumber code) {
		RecordNumberBook book = book();
		boolean isNew = book.notePlayerCode(code);
		RecordNumber.Status status = book.statusOf(code);
		if (isNew) {
			setDirty();
			if (status != RecordNumber.Status.NOT_ISSUED) {
				Industrialization.LOGGER.info("[ala-towns] a player's record {} is already the number of a {} shift "
						+ "worker{}", code, status == RecordNumber.Status.LIVE ? "living" : "dead",
						status == RecordNumber.Status.LIVE ? "; the worker is marked for a new number" : "");
			}
		}
		return status != RecordNumber.Status.NOT_ISSUED;
	}

	/** The player-join hook ({@code ServerHookRoster.PLAYER_JOIN}): notes the joining player's record. */
	public static void notePlayerOnJoin(ServerPlayer player) {
		String record = ArchiveRecordSync.recordFor(player);
		RecordNumber code = RecordNumber.parse(record).orElseThrow(() -> new IllegalStateException(
				"the archive record " + record + " is not in the record number's shape"));
		get(player.level().getServer()).notePlayerCode(code);
	}

	/**
	 * Records that a lab with the plaque number {@code labNumber} (0 to 999, {@code ArchiveRecord.labNumber})
	 * stands in this world, so no worker is given a number with these three digits later (R35).
	 *
	 * @return true if an issued number — living or dead — already has these digits (logged the first time); the
	 *     living ones are marked for a new number (R41), the dead keep their tombstones
	 */
	public boolean noteLabNumber(int labNumber) {
		RecordNumberBook book = book();
		boolean isNew = book.noteLabNumber(labNumber);
		List<RecordNumber> clashes = book.issuedWithDigits(labNumber);
		if (isNew) {
			setDirty();
			if (!clashes.isEmpty()) {
				Industrialization.LOGGER.info("[ala-towns] lab number {} has the digits of issued numbers {}; the "
						+ "living ones are marked for a new number", labNumber, clashes);
			}
		}
		return !clashes.isEmpty();
	}

	/** Whether the living {@code number} is marked for a new one (R41, MOD-834). */
	public boolean isDueForRenumbering(RecordNumber number) {
		return book().isDue(number);
	}

	/** The numbers marked for a new one, in the order of the space; empty in a world with no late coincidence. */
	public List<RecordNumber> dueForRenumbering() {
		return book().due();
	}

	/**
	 * The new number of the worker whose number {@code old} is marked for one (R41, MOD-834): the next number the
	 * issue rule gives; {@code old} is closed for good. The caller — {@code ShiftWorkerRenumbering}, when no player
	 * can see the worker — moves the record and the body to the new number. Empty when {@code old} is not marked, or
	 * the space is spent (logged once; it stays marked).
	 */
	public Optional<RecordNumber> renumber(RecordNumber old) {
		RecordNumberBook book = book();
		if (!book.isDue(old)) {
			return Optional.empty();
		}
		int before = book.next();
		Optional<RecordNumber> fresh = book.renumber(old);
		if (fresh.isPresent() || book.next() != before) {
			setDirty();
		}
		if (fresh.isEmpty() && !warnedExhausted) {
			warnedExhausted = true;
			Industrialization.LOGGER.warn("[ala-towns] every record number of this world has been handed out or "
					+ "skipped; {} keeps its number", old);
		}
		return fresh;
	}

	public boolean isKnownPlayerCode(RecordNumber code) {
		return book().isKnownPlayerCode(code);
	}

	public boolean isKnownLabNumber(int labNumber) {
		return book().isKnownLabNumber(labNumber);
	}

	private RecordNumberBook book() {
		if (book == null) {
			throw new IllegalStateException("the record number ledger is not bound to a world seed; "
					+ "read it with RecordNumberLedger.get(server)");
		}
		return book;
	}

	private <T> T saved(Function<RecordNumberBook, T> field, T unbound) {
		return book == null ? unbound : field.apply(book);
	}

	private List<String> savedNumbers(Function<RecordNumberBook, List<RecordNumber>> field) {
		return saved(field, List.<RecordNumber>of()).stream().map(RecordNumber::toString).toList();
	}

	/** The numbers among {@code saved}; a string that is not one is dropped with a warning. */
	private static List<RecordNumber> read(List<String> saved, String field) {
		List<RecordNumber> out = new ArrayList<>(saved.size());
		for (String text : saved) {
			Optional<RecordNumber> number = RecordNumber.parse(text);
			if (number.isPresent()) {
				out.add(number.get());
			} else {
				Industrialization.LOGGER.warn("[ala-towns] dropped '{}' from the saved {} record numbers: not a "
						+ "record number", text, field);
			}
		}
		return out;
	}
}
