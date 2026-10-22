package dev.alaindustrial.worldgen.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/**
 * Intact ALA town buildings ({@code structure/ala_town/<style>/intact/<piece>.nbt}, the templates the building drone
 * restores a ruin to) are never placed by world generation. A town reaches its templates only through its structure's
 * start pool and the pools its templates' jigsaw blocks name, so none of those may name an intact location: no template
 * pool or town structure of the mod mentions one, no template pool lives in an {@code intact} folder, and no ruin
 * template carries the text (its jigsaw blocks name pools by id, stored as plain text in the NBT).
 */
class IntactTownTemplatesAreNotGeneratedTest {

	private static final Path DATA = Path.of("src/main/resources/data/alaindustrial");

	/** How an intact template's location reads in any id: {@code alaindustrial:ala_town/<style>/intact/<piece>}. */
	private static final String INTACT = "/intact/";

	@Test
	void theCheckSeesAnIntactLocation() {
		assertTrue(namesIntact("{\"location\": \"alaindustrial:ala_town/plains/intact/town_hall\"}"));
		assertFalse(namesIntact("{\"location\": \"alaindustrial:ala_town/plains/town_hall\"}"));
	}

	@Test
	void noTownPoolOrStructureNamesAnIntactTemplate() throws IOException {
		List<String> offending = new ArrayList<>();
		List<Path> files = new ArrayList<>(json(DATA.resolve("worldgen/template_pool")));
		int pools = files.size();
		files.addAll(json(DATA.resolve("worldgen/structure")));
		for (Path file : files) {
			String where = slashed(DATA.relativize(file));
			if (namesIntact(where) || namesIntact(Files.readString(file, StandardCharsets.UTF_8))) {
				offending.add(where);
			}
		}
		assertTrue(pools > 40, "only " + pools + " template pools under " + DATA + ": the check went blind");
		assertTrue(offending.isEmpty(), "world generation would reach an intact building through " + offending);
	}

	@Test
	void noRuinJigsawNamesAnIntactPool() throws IOException {
		List<String> offending = new ArrayList<>();
		int ruins = 0;
		try (Stream<Path> walk = Files.walk(DATA.resolve("structure/ala_town"))) {
			for (Path file : walk.filter(f -> f.toString().endsWith(".nbt")).toList()) {
				if (namesIntact(slashed(file))) {
					continue;
				}
				ruins++;
				try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
					if (namesIntact(new String(in.readAllBytes(), StandardCharsets.ISO_8859_1))) {
						offending.add(slashed(DATA.relativize(file)));
					}
				}
			}
		}
		assertTrue(ruins > 40, "only " + ruins + " ruin templates: the check went blind");
		assertTrue(offending.isEmpty(), "a ruin's jigsaw block names an intact pool: " + offending);
	}

	private static boolean namesIntact(String text) {
		return text.contains(INTACT);
	}

	private static List<Path> json(Path folder) throws IOException {
		try (Stream<Path> walk = Files.walk(folder)) {
			return walk.filter(f -> f.toString().endsWith(".json")).toList();
		}
	}

	private static String slashed(Path path) {
		return path.toString().replace('\\', '/');
	}
}
