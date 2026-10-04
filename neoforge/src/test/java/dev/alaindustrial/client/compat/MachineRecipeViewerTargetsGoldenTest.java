package dev.alaindustrial.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import dev.alaindustrial.registry.ModRecipes;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Characterization of the recipe-viewer click areas (MOD-716, batch 0): every rectangle of the five target
 * lists of {@link MachineRecipeViewerTargets}, keyed by screen and recipe kind, plus the two multi-family
 * kind lists, against the committed {@code recipe-viewer-targets.golden.txt}. JEI and REI both register
 * exactly these rectangles, so this file is the click area a player hits in either viewer.
 *
 * <p>Batch 7 moves the literals into the screens' own constants; the golden file must not change by a byte.
 *
 * <p><b>Updated only by an explicit command (ADR-032):</b>
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dalaindustrial.viewerTargetsGolden.writeTo=&lt;repo&gt;/neoforge/src/test/resources/dev/alaindustrial/client/compat"
 *   ./gradlew :neoforge:test --tests dev.alaindustrial.client.compat.MachineRecipeViewerTargetsGoldenTest
 * </pre>
 * The run writes the file and then fails on purpose.
 *
 * <p>Runs on the NeoForge L1.5 lane, not L1: the targets name screen classes and {@link ModRecipes} kinds,
 * which need the Minecraft jar the MC-free L1 lane does not have.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class MachineRecipeViewerTargetsGoldenTest {

	static final String WRITE_TO_PROPERTY = "alaindustrial.viewerTargetsGolden.writeTo";

	static final String GOLDEN_FILE = "recipe-viewer-targets.golden.txt";

	private static final String RESOURCE = "/dev/alaindustrial/client/compat/" + GOLDEN_FILE;

	@Test
	void everyClickAreaMatchesTheGoldenFile(MinecraftServer server) throws IOException {
		List<String> actual = capture();
		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			Path target = Path.of(writeTo).resolve(GOLDEN_FILE);
			Files.createDirectories(target.getParent());
			Files.writeString(target, String.join("\n", actual) + "\n", StandardCharsets.UTF_8);
			fail("recipe-viewer target golden rewritten at " + target + " (" + actual.size() + " lines) - review"
					+ " the diff, then run again without -D" + WRITE_TO_PROPERTY);
		}
		assertEquals(readGolden(), actual, "MOD-716: a recipe-viewer click area moved; a deliberate move updates"
				+ " the golden file with the command in this class's javadoc (ADR-032)");
	}

	/** Every list in declaration order, one line per entry. */
	static List<String> capture() {
		List<String> out = new ArrayList<>();
		for (MachineRecipeViewerTargets.Target t : MachineRecipeViewerTargets.ALL) {
			var r = t.progressArea();
			out.add("ALL " + t.screenClass().getSimpleName() + " " + t.kind().id() + " "
					+ rect(r.x(), r.y(), r.width(), r.height()));
		}
		for (MachineRecipeViewerTargets.FluidTarget t : MachineRecipeViewerTargets.FLUID_ALL) {
			var r = t.progressArea();
			out.add("FLUID_ALL " + t.screenClass().getSimpleName() + " " + t.kind().id() + " "
					+ rect(r.x(), r.y(), r.width(), r.height()));
		}
		for (MachineRecipeViewerTargets.AlloyTarget t : MachineRecipeViewerTargets.ALLOY_ALL) {
			var r = t.progressArea();
			out.add("ALLOY_ALL " + t.screenClass().getSimpleName() + " " + t.kind().id() + " "
					+ rect(r.x(), r.y(), r.width(), r.height()));
		}
		for (MachineRecipeViewerTargets.CanningTarget t : MachineRecipeViewerTargets.CANNING_ALL) {
			var r = t.progressArea();
			out.add("CANNING_ALL " + t.screenClass().getSimpleName() + " " + rect(r.x(), r.y(), r.width(), r.height()));
		}
		for (MachineRecipeViewerTargets.InfoTarget t : MachineRecipeViewerTargets.INFO_ALL) {
			var r = t.progressArea();
			out.add("INFO_ALL " + t.screenClass().getSimpleName() + " " + rect(r.x(), r.y(), r.width(), r.height()));
		}
		for (ModRecipes.Kind kind : MachineRecipeViewerTargets.SAWMILL_KINDS) {
			out.add("SAWMILL_KINDS " + kind.id());
		}
		for (ModRecipes.Kind kind : MachineRecipeViewerTargets.MUTATION_KINDS) {
			out.add("MUTATION_KINDS " + kind.id());
		}
		return out;
	}

	/** Takes the four numbers rather than the rect type, so the type can move without touching this test. */
	private static String rect(int x, int y, int width, int height) {
		return x + " " + y + " " + width + " " + height;
	}

	private static List<String> readGolden() throws IOException {
		try (InputStream in = MachineRecipeViewerTargetsGoldenTest.class.getResourceAsStream(RESOURCE)) {
			assertNotNull(in, RESOURCE + " is missing - capture it with the command in this class's javadoc");
			List<String> lines = new ArrayList<>(List.of(
					new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n", -1)));
			if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
				lines.remove(lines.size() - 1);
			}
			return lines;
		}
	}
}
