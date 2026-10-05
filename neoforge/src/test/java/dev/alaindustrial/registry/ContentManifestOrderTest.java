package dev.alaindustrial.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Characterization of the registration ORDER of {@link ContentManifest} (MOD-711, batch 0).
 *
 * <p><b>Why order, on top of the registry snapshot.</b> The MOD-699 snapshot is sorted: it sees which ids
 * exist and what their properties are, and it asserts only the two load-bearing pairs. Restructuring the
 * manifest (self-collecting lists, the split into domain files) must keep every list in exactly the order
 * it has today, or move entries only as a declared, reviewed reordering. This test holds the four lists
 * line for line against {@link ContentManifestOrder}.
 *
 * <p>Runs with a live server (L1.5) only because initialising the manifest touches game classes (armor
 * materials, tool tiers); it reads nothing from the registries.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class ContentManifestOrderTest {

	/** System property naming the DIRECTORY the explicit update command writes {@code ContentManifestOrder.java} into. */
	static final String WRITE_TO_PROPERTY = "alaindustrial.manifestOrder.writeTo";

	private static final String REFERENCE_FILE = "ContentManifestOrder.java";

	/** The current order of the four lists, one line per entry. */
	static List<String> capture() {
		List<String> lines = new ArrayList<>();
		for (ContentManifest.BlockDef<?> def : ContentManifest.BLOCKS) {
			lines.add("block " + def.id());
		}
		for (ContentManifest.ItemDef def : ContentManifest.ITEMS) {
			lines.add("item " + def.id());
		}
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			lines.add("block_entity " + def.id() + ": " + String.join(" ", def.blocks()));
		}
		for (ContentManifest.MenuDef<?> def : ContentManifest.MENUS) {
			lines.add("menu " + def.id());
		}
		return lines;
	}

	@Test
	void manifestListsKeepTheirOrder() throws IOException {
		List<String> actual = capture();
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			rewrite(Path.of(writeTo).resolve(REFERENCE_FILE), actual);
			fail("ContentManifestOrder rewritten (" + actual.size() + " lines) — review the diff, then run again"
					+ " without -D" + WRITE_TO_PROPERTY);
		}
		List<String> expected = ContentManifestOrder.LINES;
		int first = firstDifference(expected, actual);
		if (first >= 0) {
			fail("MOD-711: ContentManifest order differs from ContentManifestOrder at line " + (first + 1)
					+ ": expected '" + at(expected, first) + "', got '" + at(actual, first) + "'. A reordering must"
					+ " be declared and the reference updated with the command in ContentManifestOrder's javadoc.");
		}
		assertEquals(expected.size(), actual.size(), "line count");
	}

	static int firstDifference(List<String> expected, List<String> actual) {
		int shared = Math.min(expected.size(), actual.size());
		for (int i = 0; i < shared; i++) {
			if (!expected.get(i).equals(actual.get(i))) {
				return i;
			}
		}
		return expected.size() == actual.size() ? -1 : shared;
	}

	private static String at(List<String> lines, int index) {
		return index < lines.size() ? lines.get(index) : "<end of list>";
	}

	/** Keeps the reference file's header and replaces its list body. */
	private static void rewrite(Path file, List<String> lines) throws IOException {
		String old = Files.readString(file, StandardCharsets.UTF_8);
		String marker = "List.of(\n";
		int cut = old.indexOf(marker);
		if (cut < 0) {
			fail("cannot find the list in " + file);
		}
		StringBuilder out = new StringBuilder(old.substring(0, cut + marker.length()));
		for (int i = 0; i < lines.size(); i++) {
			out.append("\t\t\t\"").append(lines.get(i)).append('"').append(i + 1 < lines.size() ? ",\n" : ");\n");
		}
		out.append("}\n");
		Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
	}
}
