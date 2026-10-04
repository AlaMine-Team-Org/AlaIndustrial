package dev.alaindustrial.core.energy;

import java.util.List;

/**
 * Renders and compares the golden masters of the network core's L1.5 characterization (MOD-715, ADR-032):
 * a golden is a Java source holding one {@code List<String>}, written only by the explicit command named in
 * its javadoc, and compared line by line so a failure names the first line that moved.
 */
final class GoldenSource {

	private GoldenSource() {
	}

	/**
	 * The whole source of a golden class: package {@code pkg}, class {@code className}, one list
	 * {@code LINES}; LF line endings (ADR-009).
	 */
	static String render(String className, String pkg, String owner, String property, String gradleArgs,
			List<String> lines) {
		StringBuilder out = new StringBuilder();
		out.append("package ").append(pkg).append(";\n\n")
				.append("import java.util.List;\n\n")
				.append("/**\n")
				.append(" * Golden master read by {@link ").append(owner).append("} (MOD-715, ADR-032).\n")
				.append(" * Written by that test, and only on the explicit command below; never by\n")
				.append(" * {@code regen.py}, a hook or a merge driver. A behaviour-preserving change\n")
				.append(" * leaves every line as it is.\n")
				.append(" *\n")
				.append(" * <pre>\n")
				.append(" * JAVA_TOOL_OPTIONS=\"-D").append(property).append("=&lt;this file&gt;\"\n")
				.append(" *   ./gradlew ").append(gradleArgs).append("\n")
				.append(" * </pre>\n")
				.append(" */\n")
				.append("final class ").append(className).append(" {\n\n")
				.append("\tprivate ").append(className).append("() {\n\t}\n\n")
				.append("\tstatic final List<String> LINES = List.of(\n");
		for (int i = 0; i < lines.size(); i++) {
			out.append("\t\t\t\"").append(lines.get(i)).append('"').append(i + 1 < lines.size() ? ",\n" : ");\n");
		}
		out.append("}\n");
		return out.toString();
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
}
