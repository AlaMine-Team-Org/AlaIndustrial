package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.fresh;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BatteryFed;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.Overclockable;
import dev.alaindustrial.registry.ContentManifest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * L2 snapshot of every block entity's inventory layout (MOD-701, batch 1, BE-8 point 1b).
 *
 * <p>A machine saves its inventory as ONE positional list: machine slots, then the four upgrade
 * slots, then the battery drawer. A slot added in front of the chips therefore moves every chip in
 * every existing world one place down — and nothing noticed that until this snapshot. Each line of
 * {@link BlockEntitySlotLayoutSnapshot#LINES} records, per block entity: the container size, where the
 * upgrade block starts, where the battery drawer sits, and the three markers that decide those two
 * ({@code MenuProvider} — a block entity that gains one silently grows four upgrade slots —
 * {@code Overclockable} and {@code BatteryFed}).
 *
 * <p>A difference is not necessarily wrong; it is always a save-format decision. Either the new
 * slot goes at the very end (old saves then load with every index where it was), or the change needs
 * a rung in {@code BlockEntityDataMigrations} and a raised {@code DATA_VERSION}. In both cases the
 * snapshot is then rewritten by the explicit command in {@link BlockEntitySlotLayoutSnapshot}'s
 * javadoc — never by {@code regen.py}, a hook or a merge driver: a reference that regenerates itself
 * agrees with every change and checks nothing.
 */
public final class BlockEntitySlotLayoutSnapshotScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(BlockEntitySlotLayoutSnapshotScenarios::slotLayoutMatchesSnapshot,
								"save_format_slot_layout_matches_snapshot")
						.fabricId("SaveFormatGameTest", "slotLayoutMatchesSnapshot").ticks(20, 40));

		private Roster() {}
	}

	private BlockEntitySlotLayoutSnapshotScenarios() {}

	/** System property naming the file the explicit update command writes the new snapshot into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.slotLayoutSnapshot.writeTo";

	/**
	 * @implements R-PER-01 — the inventory layout of every block entity matches the reviewed
	 *     snapshot, so a moved slot cannot reach a player's save unannounced.
	 * @covers R-PER-01
	 */
	public static void slotLayoutMatchesSnapshot(GameTestHelper helper) {
		List<String> actual = currentLayout();
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			try {
				Files.writeString(Path.of(writeTo), render(actual), StandardCharsets.UTF_8);
			} catch (IOException e) {
				helper.fail("could not write the slot-layout snapshot to " + writeTo + ": " + e);
				return;
			}
			helper.fail("slot-layout snapshot rewritten to " + writeTo + " (" + actual.size() + " lines) — review the"
					+ " diff, then run again without -D" + WRITE_TO_PROPERTY);
			return;
		}
		List<String> expected = BlockEntitySlotLayoutSnapshot.LINES;
		if (actual.equals(expected)) {
			helper.succeed();
			return;
		}
		List<String> gone = new ArrayList<>(expected);
		gone.removeAll(actual);
		List<String> added = new ArrayList<>(actual);
		added.removeAll(expected);
		helper.fail("the block-entity slot layout no longer matches BlockEntitySlotLayoutSnapshot.\n"
				+ "  snapshot: " + String.join("\n            ", gone) + "\n"
				+ "  now:      " + String.join("\n            ", added) + "\n"
				+ "A slot index is part of every player's save. Put a new slot at the very end, or add a rung to"
				+ " BlockEntityDataMigrations and raise DATA_VERSION; then update the snapshot with the explicit"
				+ " command in BlockEntitySlotLayoutSnapshot's javadoc.");
	}

	/** One line per manifest entry, sorted by id so that reordering the manifest changes nothing. */
	private static List<String> currentLayout() {
		Map<String, String> byId = new TreeMap<>();
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(def.blocks().getFirst()));
			byId.put(def.id(), line(def.id(), fresh(def.factory(), block)));
		}
		return List.copyOf(byId.values());
	}

	private static String line(String id, BlockEntity be) {
		String size = be instanceof Container container ? Integer.toString(container.getContainerSize()) : "-";
		String upgrades = "-";
		String battery = "-";
		if (be instanceof MachineBlockEntity machine) {
			if (machine.hasUpgradeSlots()) {
				upgrades = Integer.toString(machine.upgradeSlotStart());
			}
			if (machine.hasBatterySlot()) {
				battery = Integer.toString(machine.batterySlotIndex());
			}
		}
		return id + ": size=" + size + " upgrades=" + upgrades + " battery=" + battery
				+ " menu=" + yesNo(be instanceof MenuProvider)
				+ " overclockable=" + yesNo(be instanceof Overclockable)
				+ " batteryFed=" + yesNo(be instanceof BatteryFed);
	}

	private static String yesNo(boolean value) {
		return value ? "yes" : "no";
	}

	/** The whole source of {@link BlockEntitySlotLayoutSnapshot} for {@code lines}, LF line endings. */
	private static String render(List<String> lines) {
		StringBuilder out = new StringBuilder();
		out.append("package dev.alaindustrial.gametest;\n\n")
				.append("import java.util.List;\n\n")
				.append("/**\n")
				.append(" * Reviewed inventory layout of every block entity (MOD-701) — the reference\n")
				.append(" * {@link BlockEntitySlotLayoutSnapshotScenarios} compares against. Written by that scenario,\n")
				.append(" * and only on the explicit command below; never by {@code regen.py}, a hook or a merge driver.\n")
				.append(" * The same file on both Minecraft lines: a difference between them is a finding.\n")
				.append(" *\n")
				.append(" * <p>Update command (absolute path; Fabric lane):\n")
				.append(" * <pre>\n")
				.append(" * JAVA_TOOL_OPTIONS=\"-Dfabric-api.gametest.filter=*:save_format_game_test_slot_layout*\n")
				.append(" *   -D" + WRITE_TO_PROPERTY + "=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest/")
				.append("BlockEntitySlotLayoutSnapshot.java\"\n")
				.append(" *   ./gradlew :fabric:runGameTest\n")
				.append(" * </pre>\n")
				.append(" * The run fails on purpose after writing; review the diff, then commit it together with the\n")
				.append(" * change that moved the slots.\n")
				.append(" */\n")
				.append("final class BlockEntitySlotLayoutSnapshot {\n\n")
				.append("\tprivate BlockEntitySlotLayoutSnapshot() {}\n\n")
				.append("\tstatic final List<String> LINES = List.of(\n");
		for (int i = 0; i < lines.size(); i++) {
			out.append("\t\t\t\"").append(lines.get(i)).append('"').append(i + 1 < lines.size() ? ",\n" : ");\n");
		}
		return out.append("}\n").toString();
	}
}
