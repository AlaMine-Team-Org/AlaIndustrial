package dev.alaindustrial.gametest;

import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.registry.ModMobs;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.record.RecordNumberLedger;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkerRenumbering;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * The hidden renumbering of a shift worker (R41 of MOD-824, MOD-834) on both gametest lanes: a number that turns up
 * later as a player's record or a town plaque marks the living worker; the worker keeps its number while a player can
 * see it, gets a new one when nobody can — out of the world, or a body loading that shows no number yet — and keeps its
 * look, body, name and town; the old number is closed, the hall's log has one dry line and the record its history,
 * and all of it survives a restart.
 *
 * <p><b>Why the "nobody sees it" case goes through a body out of the world.</b> The sweep asks every player of the
 * level, and in a gametest run the other scenarios' players stand all around this one; a body in the world cannot be
 * proven unseen here. The rule itself — no observer within the sight radius — is pinned by {@code RenumberWindowTest};
 * a player next to the body is unambiguous, so the "seen" case is proven here with one. The marks come from an exact
 * number (a player's record, a plaque), never from a lab's digits, which would mark the workers of the scenarios
 * running next to this one too.
 */
public final class ShiftWorkerRenumberingScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ShiftWorkerRenumberingScenarios::renumberedOnlyWhenNobodySeesIt,
						"shift_worker_renumbered_only_unseen").ticks(40),
				RosterEntry.of(ShiftWorkerRenumberingScenarios::aBodyLoadingAfterTheMarkShowsTheNewNumber,
						"shift_worker_renumbered_before_shown").ticks(40),
				RosterEntry.of(ShiftWorkerRenumberingScenarios::theServerTickSweepRenumbers,
						"shift_worker_renumbered_by_the_sweep").ticks(60));

		private Roster() {}
	}

	private ShiftWorkerRenumberingScenarios() {}

	private static final BlockPos STAND = new BlockPos(3, 1, 3);

	/**
	 * A player joins later with the record a living worker holds: the worker is marked and keeps its number while a
	 * player stands next to it; once its body is out of the world it gets a new number from the ledger, the old one is
	 * closed, look, body, name and town are the same, the record keeps the old number in its history and the hall's log
	 * has one line about it. The body loaded again shows the new number, and the registry and the ledger come back from
	 * a restart with all of it.
	 *
	 * @implements MOD-834-RENUMBER — a late coincidence marks the worker; it is renumbered only out of sight
	 * @implements MOD-834-RESTART — the renumbering survives a restart of the world
	 * @covers R-PER-01
	 */
	public static void renumberedOnlyWhenNobodySeesIt(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ShiftWorker worker = spawn(helper);
		ShiftWorkerRecord before = record(helper, worker);
		RecordNumber old = before.number();
		ServerPlayer watcher = AlaGameTestHelper.mockPlayerInLevel(helper);
		watcher.setPos(worker.position());

		ledger(helper).notePlayerCode(old);
		helper.assertTrue(ledger(helper).isDueForRenumbering(old), "a player's record " + old
				+ " that a living worker holds did not mark it");
		helper.assertTrue(ShiftWorkerRenumbering.tryRenumber(server, before).isEmpty()
				&& worker.number().equals(old.toString()) && record(helper, worker).number().equals(old),
				"the worker was renumbered under a player's eyes: the back says " + worker.number());

		CompoundTag saved = save(helper, worker);
		worker.discard();
		Optional<RecordNumber> renumbered = ShiftWorkerRenumbering.tryRenumber(server, before);
		helper.assertTrue(renumbered.isPresent(), "a worker out of the world was not renumbered");
		RecordNumber fresh = renumbered.get();
		ShiftWorkerRecord after = record(helper, worker);
		helper.assertTrue(after.number().equals(fresh) && !fresh.equals(old)
				&& after.formerNumbers().equals(List.of(old)), "the record after the renumbering: " + after);
		helper.assertTrue(after.look().equals(before.look()) && after.slim() == before.slim()
				&& after.name().equals(before.name()) && after.settlement() == before.settlement()
				&& after.status() == before.status(), "the worker changed beyond its number: " + before + " -> "
				+ after);
		helper.assertTrue(ledger(helper).statusOf(old) == RecordNumber.Status.CLOSED
				&& ledger(helper).statusOf(fresh) == RecordNumber.Status.LIVE
				&& !ledger(helper).isDueForRenumbering(old), "the ledger after the renumbering: " + old + " "
				+ ledger(helper).statusOf(old) + ", " + fresh + " " + ledger(helper).statusOf(fresh));
		checkLogLine(helper, ShiftWorkers.book(server), old, fresh);

		SettlementRegistry registry = throughDisk(helper, SettlementRegistry.TYPE, SettlementRegistry.get(server));
		RecordNumberLedger ledger = throughDisk(helper, RecordNumberLedger.TYPE, ledger(helper));
		Optional<ShiftWorkerRecord> back = registry.workers().byEntity(worker.getUUID());
		helper.assertTrue(back.equals(Optional.of(after)), "after a restart the record is " + back + ", saved "
				+ after);
		helper.assertTrue(ledger.statusOf(old) == RecordNumber.Status.CLOSED
				&& ledger.statusOf(fresh) == RecordNumber.Status.LIVE && !ledger.isDueForRenumbering(old),
				"after a restart the ledger holds " + old + " " + ledger.statusOf(old) + ", " + fresh + " "
						+ ledger.statusOf(fresh));
		checkLogLine(helper, registry.workers(), old, fresh);

		ShiftWorker copy = load(helper, saved);
		helper.assertTrue(helper.getLevel().addFreshEntity(copy), "the reloaded worker could not be added");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!copy.isRemoved() && copy.number().equals(fresh.toString()),
					"the reloaded body shows " + copy.number() + " (removed " + copy.isRemoved() + "), the record "
							+ fresh);
			copy.discard();
			helper.succeed();
		});
	}

	/**
	 * A town plaque turns up with the number of a living worker whose chunk is not loaded; the body that loads next
	 * shows the new number from the start — the bind gives it before the body shows any — and stays in the world.
	 *
	 * @implements MOD-834-ON-LOAD — a worker out of the world is renumbered before its body shows a number
	 */
	public static void aBodyLoadingAfterTheMarkShowsTheNewNumber(GameTestHelper helper) {
		ShiftWorker worker = spawn(helper);
		RecordNumber old = record(helper, worker).number();
		CompoundTag saved = save(helper, worker);
		worker.discard();

		ledger(helper).closeFormerResident(old);
		helper.assertTrue(ledger(helper).isDueForRenumbering(old)
				&& ledger(helper).statusOf(old) == RecordNumber.Status.LIVE,
				"a plaque with a living worker's number " + old + " did not mark it, or closed it");
		ShiftWorker copy = load(helper, saved);
		helper.assertTrue(copy.number().isEmpty(), "a loaded body shows " + copy.number() + " before its bind");
		helper.assertTrue(helper.getLevel().addFreshEntity(copy), "the reloaded worker could not be added");
		helper.assertTrue(ShiftWorkers.bind(helper.getLevel(), copy), "the reloaded worker was refused");
		ShiftWorkerRecord after = record(helper, copy);
		helper.assertTrue(!after.number().equals(old) && copy.number().equals(after.number().toString())
				&& after.formerNumbers().equals(List.of(old)),
				"the bound body shows " + copy.number() + ", its record " + after);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!copy.isRemoved() && copy.number().equals(after.number().toString()),
					"after its first ticks the body shows " + copy.number() + " (removed " + copy.isRemoved() + ")");
			copy.discard();
			helper.succeed();
		});
	}

	/**
	 * Nothing here calls the renumbering: a worker out of the world is marked, and the server's own tick — the sweep of
	 * {@code ServerHookRoster.SERVER_TICK} on both loaders — gives it its new number within a sweep or two.
	 *
	 * @implements MOD-834-SWEEP — the server tick renumbers a marked worker nobody sees
	 */
	public static void theServerTickSweepRenumbers(GameTestHelper helper) {
		ShiftWorker worker = spawn(helper);
		RecordNumber old = record(helper, worker).number();
		worker.discard();
		ledger(helper).notePlayerCode(old);
		helper.succeedWhen(() -> {
			ShiftWorkerRecord now = record(helper, worker);
			helper.assertTrue(!now.number().equals(old) && now.formerNumbers().equals(List.of(old))
					&& ledger(helper).statusOf(old) == RecordNumber.Status.CLOSED,
					"the sweep has not renumbered " + old + " yet: " + now);
		});
	}

	/** The hall's log has exactly one line about {@code old}: its re-registration as {@code fresh}, in words. */
	private static void checkLogLine(GameTestHelper helper, ShiftWorkerBook book, RecordNumber old,
			RecordNumber fresh) {
		List<ShiftWorkerBook.LogLine> lines = book.log().stream()
				.filter(line -> line.about(old) && line.note().isEmpty()).toList(); // MOD-886: events aside
		helper.assertTrue(lines.size() == 1 && lines.get(0).death().isEmpty()
				&& lines.get(0).renumbering().map(ShiftWorkerBook.Renumbering::number).equals(Optional.of(fresh)),
				"the hall's log about " + old + ": " + lines);
		Component text = ShiftWorkerRenumbering.logLineText(lines.get(0)).orElseThrow();
		helper.assertTrue(text.getContents() instanceof TranslatableContents contents
				&& contents.getKey().equals(ShiftWorkerRenumbering.LOG_LINE_KEY)
				&& List.of(contents.getArgs()).equals(List.of(old.toString(), fresh.toString())),
				"the log line reads " + text);
	}

	private static ShiftWorker spawn(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		return ShiftWorkers.spawn(helper.getLevel(), helper.absolutePos(STAND), 0)
				.orElseThrow(() -> helper.assertionException("ShiftWorkers.spawn made no worker"));
	}

	private static ShiftWorkerRecord record(GameTestHelper helper, ShiftWorker worker) {
		return ShiftWorkers.book(helper.getLevel().getServer()).byEntity(worker.getUUID())
				.orElseThrow(() -> helper.assertionException("no record for the worker " + worker.getUUID()));
	}

	private static RecordNumberLedger ledger(GameTestHelper helper) {
		return RecordNumberLedger.get(helper.getLevel().getServer());
	}

	private static CompoundTag save(GameTestHelper helper, ShiftWorker worker) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
				helper.getLevel().registryAccess());
		worker.saveWithoutId(output);
		return output.buildResult();
	}

	private static ShiftWorker load(GameTestHelper helper, CompoundTag tag) {
		ShiftWorker copy = ModMobs.SHIFT_WORKER.type().create(helper.getLevel(), EntitySpawnReason.LOAD);
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		return copy;
	}

	/** {@code data} written by one storage and read by another over the same temporary folder: a restart. */
	private static <T extends SavedData> T throughDisk(GameTestHelper helper, SavedDataType<T> type, T data) {
		MinecraftServer server = helper.getLevel().getServer();
		Path folder;
		try {
			folder = Files.createTempDirectory("alaindustrial-renumbering");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		try {
			try (SavedDataStorage writer = storageOver(folder, server)) {
				writer.set(type, data);
			}
			try (SavedDataStorage reader = storageOver(folder, server)) {
				T loaded = reader.get(type);
				if (loaded == null || loaded == data) {
					throw helper.assertionException("the saved data did not come back from " + folder);
				}
				return loaded;
			}
		} finally {
			deleteTree(folder);
		}
	}

	/** See {@code RecordNumberLedgerScenarios#storageOver}: deprecated by NeoForge only. */
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
			throw new UncheckedIOException("could not remove the temporary folder " + folder, e);
		}
	}
}
