package dev.alaindustrial.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * A golden master of a world characterization (MOD-715, ADR-032): a Java source in this package holding
 * one {@code List<String>}, rewritten only by the explicit command its javadoc names, and compared line
 * by line so a failure names the first line that moved.
 *
 * <p>Lines are kept short on purpose: the golden is reviewed as a diff, and a line wider than the
 * repository's 120 columns would also fail the width gate on the generated source.
 */
final class GoldenLines {

	/** Longest line a golden may hold, so the rendered source stays inside the width limit. */
	static final int MAX_LINE = 100;

	private GoldenLines() {
	}

	/**
	 * Compare {@code actual} with {@code expected}, or — when the system property {@code writeToProperty}
	 * names a file — write the golden source there and fail on purpose. Succeeds the test on a match.
	 */
	static void check(GameTestHelper helper, String goldenClass, String writeToProperty, String filter,
			List<String> expected, List<String> actual) {
		for (String line : actual) {
			if (line.length() > MAX_LINE || line.contains("\"") || line.contains("\\")) {
				helper.fail("golden line unfit for a Java literal or wider than " + MAX_LINE + ": " + line);
				return;
			}
		}
		String writeTo = System.getProperty(writeToProperty);
		if (writeTo != null && !writeTo.isBlank()) {
			try {
				Files.writeString(Path.of(writeTo), render(goldenClass, writeToProperty, filter, actual),
						StandardCharsets.UTF_8);
			} catch (IOException e) {
				helper.fail("could not write the golden " + goldenClass + " to " + writeTo + ": " + e);
				return;
			}
			helper.fail(goldenClass + " rewritten to " + writeTo + " (" + actual.size() + " lines) - review the"
					+ " diff, then run again without -D" + writeToProperty);
			return;
		}
		String difference = firstDifference(expected, actual);
		if (difference != null) {
			helper.fail(goldenClass + " no longer matches.\n" + difference + "\nA behaviour-preserving change"
					+ " must not move these numbers; a deliberate behaviour change rewrites the golden with -D"
					+ writeToProperty + " (ADR-032).");
			return;
		}
		helper.succeed();
	}

	/** {@code null} when the two lists are equal, otherwise a description of the first difference. */
	static String firstDifference(List<String> expected, List<String> actual) {
		int common = Math.min(expected.size(), actual.size());
		for (int i = 0; i < common; i++) {
			if (!expected.get(i).equals(actual.get(i))) {
				return "  line " + (i + 1) + "\n  golden: " + expected.get(i) + "\n  now:    " + actual.get(i);
			}
		}
		if (expected.size() != actual.size()) {
			return "  golden has " + expected.size() + " lines, now " + actual.size();
		}
		return null;
	}

	/** The whole source of the golden class, LF line endings (ADR-009). */
	static String render(String goldenClass, String property, String filter, List<String> lines) {
		StringBuilder out = new StringBuilder();
		out.append("package dev.alaindustrial.gametest;\n\n")
				.append("import java.util.List;\n\n")
				.append("/**\n")
				.append(" * Golden master of a world characterization (MOD-715, ADR-032), written only by the\n")
				.append(" * explicit command below; never by {@code regen.py}, a hook or a merge driver. A\n")
				.append(" * behaviour-preserving change leaves every line as it is.\n")
				.append(" *\n")
				.append(" * <pre>\n")
				.append(" * JAVA_TOOL_OPTIONS=\"-Dfabric-api.gametest.filter=").append(filter).append("\n")
				.append(" *   -D").append(property).append("=&lt;this file&gt;\"\n")
				.append(" *   ./gradlew :fabric:runGameTest\n")
				.append(" * </pre>\n")
				.append(" * The run fails on purpose after writing. Captured on one lane, it must pass on both.\n")
				.append(" */\n")
				.append("final class ").append(goldenClass).append(" {\n\n")
				.append("\tprivate ").append(goldenClass).append("() {\n\t}\n\n")
				.append("\tstatic final List<String> LINES = List.of(\n");
		for (int i = 0; i < lines.size(); i++) {
			out.append("\t\t\t\"").append(lines.get(i)).append('"').append(i + 1 < lines.size() ? ",\n" : ");\n");
		}
		out.append("}\n");
		return out.toString();
	}
}
