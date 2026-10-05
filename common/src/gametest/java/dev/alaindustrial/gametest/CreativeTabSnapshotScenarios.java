package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.CreativeTabContent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ItemLike;

/**
 * L2 characterization of the creative tabs (MOD-711, batch 0), on both loaders.
 *
 * <p><b>What it holds.</b> A recording sink is handed to every public entry point of
 * {@link CreativeTabContent} a loader calls — the mod tab ({@code main}) and the vanilla insertions
 * ({@code combat}, {@code toolsAndUtilities}, {@code ingredients}, {@code buildingBlocks},
 * {@code naturalBlocks}, {@code functionalBlocks}) — and the output, in the order given, is compared line
 * for line with {@link CreativeTabSnapshot}. The split of {@code CreativeTabContent} into domain sections
 * must leave this output unchanged, including the entries that appear in two groups today.
 *
 * <p>Lines: {@code <tab> <item id>} for an appended entry, {@code <tab> after <anchor id>: <ids>} for an
 * anchored insertion.
 *
 * <p><b>Updated only by the explicit command</b> in {@link CreativeTabSnapshot}'s javadoc.
 */
public final class CreativeTabSnapshotScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(CreativeTabSnapshotScenarios::creativeTabSnapshotMatches,
								"creative_tab_snapshot_matches")
						.fabricId("CreativeTabSnapshotGameTest", "creativeTabSnapshotMatches").ticks(20, 40));

		private Roster() {}
	}

	private CreativeTabSnapshotScenarios() {}

	static final String WRITE_TO_PROPERTY = "alaindustrial.creativeTabSnapshot.writeTo";

	/** Every line the tab entry points produce on this loader, in order. */
	static List<String> capture() {
		List<String> lines = new ArrayList<>();
		CreativeTabContent.main(item -> lines.add("main " + id(item)));
		CreativeTabContent.combat(anchored("combat", lines));
		CreativeTabContent.toolsAndUtilities(anchored("tools_and_utilities", lines));
		CreativeTabContent.ingredients(item -> lines.add("ingredients " + id(item)));
		CreativeTabContent.buildingBlocks(item -> lines.add("building_blocks " + id(item)));
		CreativeTabContent.naturalBlocks(item -> lines.add("natural_blocks " + id(item)));
		CreativeTabContent.functionalBlocks(item -> lines.add("functional_blocks " + id(item)));
		return lines;
	}

	private static CreativeTabContent.AnchoredSink anchored(String tab, List<String> lines) {
		return new CreativeTabContent.AnchoredSink() {
			@Override
			public void accept(ItemLike item) {
				lines.add(tab + " " + id(item));
			}

			@Override
			public void insertAfter(ItemLike anchor, List<ItemLike> items) {
				List<String> ids = new ArrayList<>();
				for (ItemLike item : items) {
					ids.add(id(item));
				}
				lines.add(tab + " after " + id(anchor) + ": " + String.join(" ", ids));
			}
		};
	}

	private static String id(ItemLike item) {
		return String.valueOf(BuiltInRegistries.ITEM.getKey(item.asItem()));
	}

	/** The tabs this loader is given match the reviewed reference, line for line and in order. */
	public static void creativeTabSnapshotMatches(GameTestHelper helper) {
		List<String> actual = capture();
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			rewrite(helper, Path.of(writeTo).resolve("CreativeTabSnapshot.java"), actual);
			return;
		}
		if (actual.stream().noneMatch(line -> line.startsWith("main "))) {
			helper.fail("MOD-711: CreativeTabContent.main produced no entry");
			return;
		}
		if (!CreativeTabSnapshot.CAPTURED) {
			Industrialization.LOGGER.info("MOD-711 creative tab snapshot: no reference yet — run the update command"
					+ " in CreativeTabSnapshot's javadoc ({} lines)\n{}", actual.size(), String.join("\n", actual));
			helper.succeed();
			return;
		}
		List<String> expected = CreativeTabSnapshot.LINES;
		int shared = Math.min(expected.size(), actual.size());
		for (int i = 0; i < shared; i++) {
			if (!expected.get(i).equals(actual.get(i))) {
				fail(helper, i, expected.get(i), actual.get(i), actual);
				return;
			}
		}
		if (expected.size() != actual.size()) {
			fail(helper, shared, shared < expected.size() ? expected.get(shared) : "<end>",
					shared < actual.size() ? actual.get(shared) : "<end>", actual);
			return;
		}
		helper.succeed();
	}

	private static void fail(GameTestHelper helper, int index, String expected, String actual, List<String> capture) {
		Industrialization.LOGGER.info("MOD-711 creative tab snapshot differs ({} lines)\n{}", capture.size(),
				String.join("\n", capture));
		helper.fail("MOD-711: creative tabs differ from CreativeTabSnapshot at line " + (index + 1) + ": expected '"
				+ expected + "', got '" + actual + "'. If intended, update the reference with the command in"
				+ " CreativeTabSnapshot's javadoc; the full capture is in the log.");
	}

	/** Writes the reference for {@code actual}, then fails on purpose so the run is never taken for a check. */
	private static void rewrite(GameTestHelper helper, Path file, List<String> actual) {
		StringBuilder body = new StringBuilder();
		for (int i = 0; i < actual.size(); i++) {
			body.append("\t\t\t\"").append(actual.get(i)).append('"').append(i + 1 < actual.size() ? ",\n" : "");
		}
		try {
			String old = Files.readString(file, StandardCharsets.UTF_8);
			String head = old.substring(0, old.indexOf("static final boolean CAPTURED"));
			String source = head + "static final boolean CAPTURED = true;\n\n\tstatic final List<String> LINES = List.of(\n"
					+ body + ");\n}\n";
			Files.writeString(file, source, StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			helper.fail("could not write the creative tab snapshot into " + file + ": " + e);
			return;
		}
		helper.fail("creative tab snapshot rewritten into " + file + " (" + actual.size() + " lines) — review the"
				+ " diff, then run again without -D" + WRITE_TO_PROPERTY);
	}
}
