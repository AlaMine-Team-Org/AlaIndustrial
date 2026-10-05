package dev.alaindustrial.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * A reviewed reference of text lines that a characterization scenario compares its observation with
 * (MOD-712, batch 0) — the pattern of {@link BlockEntitySlotLayoutSnapshotScenarios}, written once for
 * the machine-base suites.
 *
 * <p>Each reference is a Java class holding {@code static final List<String> LINES}, compiled into both
 * gametest lanes, so one file serves Fabric and NeoForge alike. It is rewritten ONLY by the explicit
 * command: run the lane with {@code -D}{@value #WRITE_DIR_PROPERTY}{@code =<dir>}, and every scenario
 * that uses this class writes {@code <dir>/<ReferenceClass>.java} and then fails on purpose. Never by
 * {@code regen.py}, a hook or a merge driver: a reference that regenerates itself from the code it
 * guards agrees with every change and checks nothing (ADR-032).
 */
final class ReferenceLines {

	/** System property naming the directory the explicit update command writes the new references into. */
	static final String WRITE_DIR_PROPERTY = "alaindustrial.referenceLines.writeDir";

	/** How many differing lines a failure message shows on each side before it stops listing. */
	private static final int SHOWN = 25;

	private ReferenceLines() {}

	/**
	 * Succeed when {@code actual} equals {@code expected}; otherwise fail naming the lines that went and
	 * came. With the write property set, write the reference class instead and fail on purpose.
	 *
	 * @param referenceClass simple name of the reference class ({@code <dir>/<referenceClass>.java})
	 * @param about one javadoc sentence: what the reference records and which scenario reads it
	 * @param testIdGlob the Fabric filter glob that runs the scenario, for the update command
	 */
	static void compare(GameTestHelper helper, String referenceClass, String about, String testIdGlob,
			List<String> actual, List<String> expected) {
		String writeDir = System.getProperty(WRITE_DIR_PROPERTY);
		if (writeDir != null && !writeDir.isBlank()) {
			Path target = Path.of(writeDir, referenceClass + ".java");
			try {
				Files.writeString(target, render(referenceClass, about, testIdGlob, actual), StandardCharsets.UTF_8);
			} catch (IOException e) {
				helper.fail("could not write " + target + ": " + e);
				return;
			}
			helper.fail(referenceClass + " rewritten to " + target + " (" + actual.size() + " lines) — review the"
					+ " diff, then run again without -D" + WRITE_DIR_PROPERTY);
			return;
		}
		if (actual.equals(expected)) {
			helper.succeed();
			return;
		}
		List<String> gone = new ArrayList<>(expected);
		gone.removeAll(actual);
		List<String> added = new ArrayList<>(actual);
		added.removeAll(expected);
		helper.fail(referenceClass + " no longer matches (" + gone.size() + " lines gone, " + added.size()
				+ " new; order counts too).\n  reference: " + String.join("\n             ", head(gone))
				+ "\n  now:       " + String.join("\n             ", head(added))
				+ "\nA behaviour-preserving change must leave this reference alone. If the change is meant, update"
				+ " it with the explicit command in " + referenceClass + "'s javadoc and commit the reviewed diff.");
	}

	private static List<String> head(List<String> lines) {
		if (lines.size() <= SHOWN) {
			return lines;
		}
		List<String> shown = new ArrayList<>(lines.subList(0, SHOWN));
		shown.add("… " + (lines.size() - SHOWN) + " more");
		return shown;
	}

	/** The whole source of the reference class, LF line endings. */
	static String render(String referenceClass, String about, String testIdGlob, List<String> lines) {
		StringBuilder out = new StringBuilder();
		out.append("package dev.alaindustrial.gametest;\n\n")
				.append("import java.util.List;\n\n")
				.append("/**\n")
				.append(javadoc(about))
				.append(" *\n")
				.append(" * <p>Written by that scenario, and only on the explicit command below (ADR-032); never by\n")
				.append(" * {@code regen.py}, a hook or a merge driver. The same file serves both gametest lanes.\n")
				.append(" *\n")
				.append(" * <p>Update command (absolute directory; Fabric lane):\n")
				.append(" * <pre>\n")
				.append(" * JAVA_TOOL_OPTIONS=\"-Dfabric-api.gametest.filter=").append(testIdGlob).append('\n')
				.append(" *   -D").append(WRITE_DIR_PROPERTY)
				.append("=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest\"\n")
				.append(" *   ./gradlew :fabric:runGameTest\n")
				.append(" * </pre>\n")
				.append(" * The run fails on purpose after writing; review the diff, then commit it together\n")
				.append(" * with the change that is meant to move it.\n")
				.append(" */\n")
				.append("final class ").append(referenceClass).append(" {\n\n")
				.append("\tprivate ").append(referenceClass).append("() {}\n\n");
		if (lines.isEmpty()) {
			out.append("\tstatic final List<String> LINES = List.of();\n");
		} else {
			out.append("\tstatic final List<String> LINES = List.of(\n");
			for (int i = 0; i < lines.size(); i++) {
				out.append("\t\t\t").append(literal(lines.get(i))).append(i + 1 < lines.size() ? ",\n" : ");\n");
			}
		}
		return out.append("}\n").toString();
	}

	/** {@code text} as javadoc lines of at most {@link #JAVADOC_WIDTH} characters, broken between words. */
	private static String javadoc(String text) {
		StringBuilder out = new StringBuilder();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (line.length() > 0 && line.length() + 1 + word.length() > JAVADOC_WIDTH) {
				out.append(" * ").append(line).append('\n');
				line.setLength(0);
			}
			if (line.length() > 0) {
				line.append(' ');
			}
			line.append(word);
		}
		return out.append(" * ").append(line).append('\n').toString();
	}

	/** Characters of javadoc text per line of a reference class. */
	private static final int JAVADOC_WIDTH = 100;

	/**
	 * A Java string literal of {@code line}, split into {@code +}-joined pieces that keep the file narrow;
	 * a piece ends before a space where one is near enough, so words stay whole.
	 */
	private static String literal(String line) {
		StringBuilder out = new StringBuilder();
		int from = 0;
		do {
			int to = Math.min(line.length(), from + PIECE);
			if (to < line.length()) {
				int space = line.lastIndexOf(' ', to);
				if (space > from + PIECE / 2) {
					to = space;
				}
			}
			if (from > 0) {
				out.append("\n\t\t\t\t\t+ ");
			}
			out.append('"').append(escape(line.substring(from, to))).append('"');
			from = to;
		} while (from < line.length());
		return out.toString();
	}

	/** Characters of a reference line per source line of the reference class (the §7 width is 120). */
	private static final int PIECE = 90;

	private static String escape(String line) {
		return line.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
