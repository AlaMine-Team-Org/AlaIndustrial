package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.parse;

import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

/**
 * The saved form of the shift workers (MOD-832, ADR-050 point 8): its fields are a reviewed snapshot, a hand-written
 * save in the shape of the file reads as written and is stable through a save, and the version ladder reads an
 * unversioned or a newer map instead of dropping every living worker.
 */
public final class ShiftWorkerSaveFormatScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ShiftWorkerSaveFormatScenarios::fieldsMatchTheSnapshot,
						"shift_worker_save_format_fields_match_snapshot"),
				RosterEntry.of(ShiftWorkerSaveFormatScenarios::handWrittenSaveReadsAsWritten,
						"shift_worker_save_format_reads_as_written"),
				RosterEntry.of(ShiftWorkerSaveFormatScenarios::anyVersionStillReads,
						"shift_worker_save_format_any_version_reads"),
				RosterEntry.of(ShiftWorkerSaveFormatScenarios::townsSavedBeforeTheWorkersStillRead,
						"shift_worker_save_format_towns_before_workers_read"));

		private Roster() {}
	}

	private ShiftWorkerSaveFormatScenarios() {}

	/** System property naming the file the explicit update command writes the new snapshot into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.shiftWorkerSnapshot.writeTo";

	/** Every field of the form spelt out, in the shape the file has (version 1). */
	private static final String FULL_SAVE = "{data_version:1,"
			+ "workers:[{number:\"W-117\",entity:[I;1,2,3,4],look:\"alaindustrial:window_f1\",slim:1b,name:\"Anna\","
			+ "settlement:3,status:\"awaiting_return\","
			+ "death:{cause:\"minecraft:mob_attack\",killer:\"minecraft:zombie\",game_time:240000L}}],"
			+ "hall_log:[{settlement:3,number:\"W-117\",name:\"Anna\","
			+ "death:{cause:\"minecraft:mob_attack\",killer:\"minecraft:zombie\",game_time:240000L}}]}";

	private static final UUID ENTITY = UUIDUtil.uuidFromIntArray(new int[] {1, 2, 3, 4});

	/**
	 * The fields of the saved form — every key with its tag type, plus the version — against the reviewed
	 * {@link ShiftWorkerRecordSnapshot}. A changed field fails here until the snapshot is updated by its command,
	 * which is when the reviewer asks: optional with a default, or a new rung and a raised version?
	 *
	 * @implements MOD-832-SNAPSHOT — the shift worker's data fields are a snapshot with a migration ladder
	 * @covers R-PER-01
	 */
	public static void fieldsMatchTheSnapshot(GameTestHelper helper) {
		Tag encoded = ShiftWorkerBook.CODEC.encodeStart(ops(helper), decode(helper, FULL_SAVE)).getOrThrow();
		List<String> actual = new ArrayList<>(new TreeSet<>(fieldLines("", encoded)));
		actual.add(0, "data_version=" + ShiftWorkerRecord.DATA_VERSION);
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			try {
				Files.writeString(Path.of(writeTo), render(actual), StandardCharsets.UTF_8);
			} catch (IOException e) {
				helper.fail("could not write the shift worker snapshot to " + writeTo + ": " + e);
				return;
			}
			helper.fail("shift worker snapshot rewritten to " + writeTo + " (" + actual.size() + " lines) — review the"
					+ " diff, then run again without -D" + WRITE_TO_PROPERTY);
			return;
		}
		if (actual.equals(ShiftWorkerRecordSnapshot.LINES)) {
			helper.succeed();
			return;
		}
		helper.fail("the shift workers' saved form no longer matches ShiftWorkerRecordSnapshot.\n  snapshot: "
				+ ShiftWorkerRecordSnapshot.LINES + "\n  now:      " + actual
				+ "\nA field is part of every save: add it optional with a default, or add a rung to"
				+ " ShiftWorkerBook.Versioned and raise"
				+ " ShiftWorkerRecord.DATA_VERSION; then update the snapshot with its command.");
	}

	/**
	 * A save written by hand in the file's shape reads back field by field, and encode → decode → encode is stable.
	 *
	 * @implements MOD-832-FORMAT — the shift workers' file reads as written
	 * @covers R-PER-01
	 */
	public static void handWrittenSaveReadsAsWritten(GameTestHelper helper) {
		ShiftWorkerBook registry = decode(helper, FULL_SAVE);
		ShiftWorkerRecord record = registry.byEntity(ENTITY)
				.orElseThrow(() -> helper.assertionException("the hand-written worker did not come back"));
		RecordNumber number = RecordNumber.parse("W-117").orElseThrow();
		helper.assertTrue(record.number().equals(number) && record.slim() && record.name().equals("Anna")
				&& record.settlement() == 3 && record.status() == ShiftWorkerRecord.Status.AWAITING_RETURN
				&& record.look().equals(Identifier.parse("alaindustrial:window_f1")),
				"the hand-written worker came back as " + record);
		helper.assertTrue(record.death().isPresent() && record.death().get().gameTime() == 240000L
				&& record.death().get().killer().equals(Optional.of(Identifier.parse("minecraft:zombie"))),
				"its death came back as " + record.death());
		helper.assertTrue(registry.log().size() == 1 && registry.log().get(0).number().equals(number),
				"the hall's log came back as " + registry.log());
		Tag first = ShiftWorkerBook.CODEC.encodeStart(ops(helper), registry).getOrThrow();
		Tag second = ShiftWorkerBook.CODEC.encodeStart(ops(helper),
				ShiftWorkerBook.CODEC.parse(ops(helper), first).getOrThrow()).getOrThrow();
		helper.assertTrue(first.equals(second), "not stable through a save: " + first + " re-encodes as " + second);
		helper.succeed();
	}

	/**
	 * The ladder's edges: a map without a version reads as the current one, and a map of a newer version than this
	 * build knows still reads (with a warning) — refusing it would leave an empty registry, which discards every
	 * living worker on its next load.
	 *
	 * @implements MOD-832-LADDER — the saved form is versioned and an unknown version does not lose the workers
	 */
	public static void anyVersionStillReads(GameTestHelper helper) {
		String body = FULL_SAVE.substring("{data_version:1,".length());
		for (String version : new String[] {"", "data_version:99,"}) {
			ShiftWorkerBook registry = decode(helper, "{" + version + body);
			helper.assertTrue(registry.byEntity(ENTITY).isPresent(), "a save with '" + version + "' lost its worker");
		}
		Tag encoded = ShiftWorkerBook.CODEC.encodeStart(ops(helper), decode(helper, "{" + body)).getOrThrow();
		helper.assertTrue(encoded instanceof CompoundTag tag
				&& tag.getIntOr("data_version", -1) == ShiftWorkerRecord.DATA_VERSION,
				"a save is not written with data_version " + ShiftWorkerRecord.DATA_VERSION + ": " + encoded);
		helper.succeed();
	}

	/**
	 * The workers live in the towns' file as its optional {@code shift_workers} field: a towns' save written before
	 * MOD-832 — no such field — reads with its towns and an empty book, a save of towns without workers is written
	 * without the field, and one with workers carries them through a save.
	 *
	 * @implements MOD-832-TOWNS-FORMAT — the towns' file reads with and without the workers
	 * @covers R-PER-01
	 */
	public static void townsSavedBeforeTheWorkersStillRead(GameTestHelper helper) {
		String towns = "{settlements:[{id:7,dimension:\"minecraft:overworld\",core:[I;10,64,-20],active:1b}],next_id:8";
		SettlementRegistry old = SettlementRegistry.CODEC.parse(ops(helper), parse(towns + "}")).getOrThrow();
		helper.assertTrue(old.find(7).isPresent() && old.workers().isEmpty(), "a towns' save without workers read as "
				+ old.all() + " with workers " + old.workers().all());
		Tag rewritten = SettlementRegistry.CODEC.encodeStart(ops(helper), old).getOrThrow();
		helper.assertTrue(rewritten instanceof CompoundTag tag && !tag.contains("shift_workers"),
				"towns without workers are written with a shift_workers field: " + rewritten);
		SettlementRegistry full = SettlementRegistry.CODEC.parse(ops(helper),
				parse(towns + ",shift_workers:" + FULL_SAVE + "}")).getOrThrow();
		helper.assertTrue(full.workers().byEntity(ENTITY).isPresent() && full.workers().log().size() == 1,
				"the workers did not come back inside the towns' save");
		SettlementRegistry again = SettlementRegistry.CODEC.parse(ops(helper),
				SettlementRegistry.CODEC.encodeStart(ops(helper), full).getOrThrow()).getOrThrow();
		helper.assertTrue(again.workers().byEntity(ENTITY).equals(full.workers().byEntity(ENTITY)),
				"the worker changed through a save of the towns");
		helper.succeed();
	}

	private static RegistryOps<Tag> ops(GameTestHelper helper) {
		return helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	private static ShiftWorkerBook decode(GameTestHelper helper, String snbt) {
		return ShiftWorkerBook.CODEC.parse(ops(helper), parse(snbt)).getOrThrow();
	}

	/** {@code path.key: type} for every leaf of {@code tag}; a list is walked through its first element. */
	private static List<String> fieldLines(String path, Tag tag) {
		List<String> out = new ArrayList<>();
		if (tag instanceof CompoundTag compound) {
			for (String key : compound.keySet()) {
				if (!key.equals("data_version")) {
					out.addAll(fieldLines(path.isEmpty() ? key : path + "." + key, compound.get(key)));
				}
			}
		} else if (tag instanceof ListTag list && !list.isEmpty() && list.get(0) instanceof CompoundTag) {
			out.addAll(fieldLines(path + "[]", list.get(0)));
		} else {
			out.add(path + ": " + tag.getType().getName().toLowerCase(java.util.Locale.ROOT));
		}
		return out;
	}

	/** The whole source of {@link ShiftWorkerRecordSnapshot} for {@code lines}, LF line endings. */
	private static String render(List<String> lines) {
		StringBuilder out = new StringBuilder();
		out.append("package dev.alaindustrial.gametest;\n\n")
				.append("import java.util.List;\n\n")
				.append("/**\n")
				.append(" * Reviewed fields of the shift workers' saved form (MOD-832) — the reference\n")
				.append(" * {@link ShiftWorkerSaveFormatScenarios} compares against: every key with its tag type,\n")
				.append(" * and the version. Written by that scenario, and only on the explicit command below.\n")
				.append(" *\n")
				.append(" * <p>Update command (absolute path; Fabric lane):\n")
				.append(" * <pre>\n")
				.append(" * JAVA_TOOL_OPTIONS=\"-Dfabric-api.gametest.filter=*:shift_worker_save_format*\n")
				.append(" *   -D" + WRITE_TO_PROPERTY)
				.append("=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest/")
				.append("ShiftWorkerRecordSnapshot.java\"\n")
				.append(" *   ./gradlew :fabric:runGameTest\n")
				.append(" * </pre>\n")
				.append(" * The run fails on purpose after writing; review the diff, then commit it together\n")
				.append(" * with the change that altered the form.\n")
				.append(" */\n")
				.append("final class ShiftWorkerRecordSnapshot {\n\n")
				.append("\tprivate ShiftWorkerRecordSnapshot() {}\n\n")
				.append("\tstatic final List<String> LINES = List.of(\n");
		for (int i = 0; i < lines.size(); i++) {
			out.append("\t\t\t\"").append(lines.get(i)).append('"').append(i + 1 < lines.size() ? ",\n" : ");\n");
		}
		return out.append("}\n").toString();
	}
}
