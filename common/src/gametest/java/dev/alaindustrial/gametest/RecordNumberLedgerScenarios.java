package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.parse;

import dev.alaindustrial.core.guide.ArchiveRecordSync;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.record.RecordNumberLedger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * L2 coverage for the world's record-number ledger (MOD-827): it survives the trip to disk and back through
 * the game's own {@link SavedDataStorage}, a dead worker's number stays closed after it, the saved form reads
 * as written, and a joining player's record is noted.
 *
 * <p><b>Why a storage of its own.</b> The save-and-reload case writes into a fresh temporary directory, with
 * the server's data fixer and registries, instead of the test world's storage: reading a file back means a
 * second {@code SavedDataStorage} over the same folder, and one over the live world's folder could race the
 * server's own saves.
 */
public final class RecordNumberLedgerScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(RecordNumberLedgerScenarios::ledgerSurvivesASaveAndReload,
						"record_numbers_survive_a_save_and_reload"),
				RosterEntry.of(RecordNumberLedgerScenarios::handWrittenSaveReadsAsWritten,
						"record_numbers_read_a_hand_written_save"),
				RosterEntry.of(RecordNumberLedgerScenarios::joiningPlayersRecordIsNoted,
						"record_numbers_note_a_joining_player"));

		private Roster() {}
	}

	private RecordNumberLedgerScenarios() {}

	/**
	 * Numbers issued, two holders dead, a player and a lab noted — saved to disk by the game's storage and read
	 * back by a new one: every status is the same, the reloaded ledger goes on handing out exactly the numbers
	 * the saved one would, and through the rest of the space it never repeats a number issued before the save,
	 * a dead one included, nor the noted player's record or the noted lab's digits.
	 *
	 * @implements MOD-827-SAVE — the record numbers survive a restart; a dead worker's number is not issued again.
	 * @covers R-PER-01
	 */
	public static void ledgerSurvivesASaveAndReload(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		RecordNumberLedger saved = fresh(helper, 827L);
		List<RecordNumber> issued = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			issued.add(saved.issue().orElseThrow());
		}
		RecordNumber firstDead = issued.get(3);
		RecordNumber secondDead = issued.get(41);
		saved.close(firstDead);
		saved.close(secondDead);
		RecordNumber player = RecordNumber.parse("K-482").orElseThrow();
		saved.notePlayerCode(player);
		saved.noteLabNumber(304);

		RecordNumberLedger loaded = throughDisk(helper, server, saved);
		if (loaded == null) {
			return;
		}
		for (RecordNumber number : issued) {
			if (loaded.statusOf(number) != saved.statusOf(number)) {
				helper.fail(number + " was " + saved.statusOf(number) + " before the save and "
						+ loaded.statusOf(number) + " after it");
				return;
			}
		}
		if (loaded.statusOf(firstDead) != RecordNumber.Status.CLOSED || !loaded.isKnownPlayerCode(player)
				|| !loaded.isKnownLabNumber(304)) {
			helper.fail("after the reload: " + firstDead + " is " + loaded.statusOf(firstDead) + ", player record "
					+ player + " known=" + loaded.isKnownPlayerCode(player) + ", lab 304 known="
					+ loaded.isKnownLabNumber(304));
			return;
		}
		for (int i = 0; i < 20; i++) {
			Optional<RecordNumber> expected = saved.issue();
			Optional<RecordNumber> got = loaded.issue();
			if (!expected.equals(got)) {
				helper.fail("issue " + i + " after the reload gave " + got + ", the ledger never saved gave "
						+ expected);
				return;
			}
		}
		for (Optional<RecordNumber> next = loaded.issue(); next.isPresent(); next = loaded.issue()) {
			RecordNumber number = next.get();
			if (issued.contains(number) || number.equals(player) || number.digits() == 304) {
				helper.fail("after the reload the ledger handed out " + number + " again (dead before the save: "
						+ firstDead + ", " + secondDead + ")");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * The saved form, by hand, in the shape the file has: a number listed both live and closed reads back
	 * closed, a string that is not a number is dropped instead of failing the whole ledger, and the result is
	 * stable through another save.
	 *
	 * @implements MOD-827-FORMAT-SAVE — the ledger's file reads as written; a tombstone is never undone.
	 * @covers R-PER-01
	 */
	public static void handWrittenSaveReadsAsWritten(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		RecordNumberLedger ledger = RecordNumberLedger.CODEC.parse(ops, parse("{seed:5L,next:12,"
				+ "live:[\"W-117\",\"not-a-number\",\"C-001\"],closed:[\"K-482\",\"W-117\"],players:[\"A-000\"],"
				+ "labs:[304]}")).getOrThrow();
		RecordNumber w117 = RecordNumber.parse("W-117").orElseThrow();
		RecordNumber k482 = RecordNumber.parse("K-482").orElseThrow();
		RecordNumber c001 = RecordNumber.parse("C-001").orElseThrow();
		RecordNumber a000 = RecordNumber.parse("A-000").orElseThrow();
		if (ledger.statusOf(w117) != RecordNumber.Status.CLOSED || ledger.statusOf(k482) != RecordNumber.Status.CLOSED
				|| ledger.statusOf(c001) != RecordNumber.Status.LIVE || !ledger.isKnownPlayerCode(a000)
				|| !ledger.isKnownLabNumber(304)) {
			helper.fail("hand-written ledger did not come back: W-117 " + ledger.statusOf(w117) + ", K-482 "
					+ ledger.statusOf(k482) + ", C-001 " + ledger.statusOf(c001) + ", A-000 player "
					+ ledger.isKnownPlayerCode(a000) + ", lab 304 " + ledger.isKnownLabNumber(304));
			return;
		}
		Tag first = RecordNumberLedger.CODEC.encodeStart(ops, ledger).getOrThrow();
		Tag second = RecordNumberLedger.CODEC.encodeStart(ops, RecordNumberLedger.CODEC.parse(ops, first).getOrThrow())
				.getOrThrow();
		if (!first.equals(second)) {
			helper.fail("the ledger is not stable through a save: " + first + " re-encodes as " + second);
			return;
		}
		helper.succeed();
	}

	/**
	 * The join hook notes the player's record in the world's ledger, so no shift worker is given it later.
	 *
	 * @implements MOD-827-JOIN — a joining player's record is known to the world's ledger.
	 */
	public static void joiningPlayersRecordIsNoted(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		RecordNumberLedger.notePlayerOnJoin(player);
		RecordNumber code = RecordNumber.parse(ArchiveRecordSync.recordFor(player)).orElseThrow();
		MinecraftServer server = helper.getLevel().getServer();
		RecordNumberLedger ledger = RecordNumberLedger.get(server);
		if (ledger != RecordNumberLedger.get(server)) {
			helper.fail("the world has more than one record number ledger");
			return;
		}
		if (!ledger.isKnownPlayerCode(code)) {
			helper.fail("the joining player's record " + code + " is not noted in the world's ledger");
			return;
		}
		helper.succeed();
	}

	/** A new ledger bound to {@code seed}, as the codec makes it — the same path a save takes on the way in. */
	private static RecordNumberLedger fresh(GameTestHelper helper, long seed) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		return RecordNumberLedger.CODEC.parse(ops, parse("{seed:" + seed + "L}")).getOrThrow();
	}

	/** {@code ledger} written by one storage and read by another over the same temporary folder, or null (failed). */
	private static RecordNumberLedger throughDisk(GameTestHelper helper, MinecraftServer server,
			RecordNumberLedger ledger) {
		Path folder;
		try {
			folder = Files.createTempDirectory("alaindustrial-record-numbers");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		try {
			try (SavedDataStorage writer = storageOver(folder, server)) {
				writer.set(RecordNumberLedger.TYPE, ledger);
			}
			try (SavedDataStorage reader = storageOver(folder, server)) {
				RecordNumberLedger loaded = reader.get(RecordNumberLedger.TYPE);
				if (loaded == null || loaded == ledger) {
					helper.fail("the ledger did not come back from " + folder + ": " + loaded);
					return null;
				}
				return loaded;
			}
		} finally {
			deleteTree(folder);
		}
	}

	/**
	 * A storage over {@code folder} with the server's data fixer and registries. The constructor without a level
	 * is deprecated by NeoForge only ("use the version that has a level"); vanilla, which {@code common} compiles
	 * against, has no other, and the level is only the context NeoForge hands a level-aware saved-data type
	 * (patched {@code SavedDataStorage}); the ledger's type takes no context.
	 */
	@SuppressWarnings("deprecation")
	private static SavedDataStorage storageOver(Path folder, MinecraftServer server) {
		return new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess());
	}

	private static void deleteTree(Path folder) {
		try (Stream<Path> paths = Files.walk(folder)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("could not remove the temporary ledger folder " + folder, e);
		}
	}
}
