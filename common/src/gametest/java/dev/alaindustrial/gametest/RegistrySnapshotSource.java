package dev.alaindustrial.gametest;

import java.util.List;

/**
 * The Java source of the two registry-snapshot reference files (MOD-699), as the explicit update command
 * writes them. Kept apart from {@link RegistrySnapshotScenarios} so the scenario stays about checking, and
 * so the header every reviewer reads is written in exactly one place. LF line endings (ADR-009).
 */
final class RegistrySnapshotSource {

	private RegistrySnapshotSource() {}

	/**
	 * Everything but the items: blocks, block-entity pairs, menus, fluids and the shared registries, plus the
	 * {@code REMOVED} announcements, which are carried over verbatim — the command never writes one itself.
	 */
	static String main(List<String> lines, List<String> removed) {
		StringBuilder out = header("RegistrySnapshot",
				"Blocks, block-entity → block pairs, menus, fluids and the shared registries (sounds, data\n"
						+ " * components, effects, particles, criteria); the items are in {@link RegistrySnapshotItems}.");
		out.append("\t/**\n")
				.append("\t * {@code false} only in the hand-derived seed, which holds the composition without the block and\n")
				.append("\t * item properties: the scenario then compares ids alone. The update command always writes {@code true}.\n")
				.append("\t */\n")
				.append("\tstatic final boolean PROPERTIES_CAPTURED = true;\n\n")
				.append("\t/**\n")
				.append("\t * Shipped ids deliberately removed, one {@code \"<section> <id> — MOD-XXX <reason>\"} each. The update\n")
				.append("\t * command refuses to drop an id from {@link #LINES} unless it is announced here, and never adds a line.\n")
				.append("\t */\n")
				.append(list("REMOVED", removed)).append('\n');
		return lines(out, lines);
	}

	/** The items, one file of their own so neither reference outgrows a reviewer's single read. */
	static String items(List<String> lines) {
		return lines(header("RegistrySnapshotItems", "The items; everything else is in {@link RegistrySnapshot}."), lines);
	}

	private static StringBuilder header(String name, String what) {
		StringBuilder out = new StringBuilder();
		out.append("package dev.alaindustrial.gametest;\n\n")
				.append("import java.util.List;\n\n")
				.append("/**\n")
				.append(" * Reviewed content-registry snapshot (MOD-699) that {@link RegistrySnapshotScenarios} compares both\n")
				.append(" * loaders against. ").append(what).append("\n")
				.append(" *\n")
				.append(" * <p>Written by that scenario, and only on the explicit command below — never by {@code regen.py},\n")
				.append(" * a hook, {@code new_machine.py} or a merge driver. Each Minecraft line captures its own reference\n")
				.append(" * on its own branch: block properties differ between the lines on purpose.\n")
				.append(" *\n")
				.append(" * <p>Update command (absolute path to this directory; Fabric lane):\n")
				.append(" * <pre>\n")
				.append(" * JAVA_TOOL_OPTIONS=\"-Dfabric-api.gametest.filter=*:registry_snapshot_game_test_*\n")
				.append(" *   -D").append(RegistrySnapshotScenarios.WRITE_TO_PROPERTY)
				.append("=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest\"\n")
				.append(" *   ./gradlew :fabric:runGameTest\n")
				.append(" * </pre>\n")
				.append(" * The run fails on purpose after writing both files; review the diff, then commit it together with\n")
				.append(" * the change that altered the registries.\n")
				.append(" */\n")
				.append("final class ").append(name).append(" {\n\n")
				.append("\tprivate ").append(name).append("() {}\n\n");
		return out;
	}

	private static String lines(StringBuilder out, List<String> lines) {
		return out.append(list("LINES", lines)).append("}\n").toString();
	}

	private static String list(String name, List<String> entries) {
		StringBuilder out = new StringBuilder("\tstatic final List<String> ").append(name).append(" = List.of(");
		if (entries.isEmpty()) {
			return out.append(");\n").toString();
		}
		out.append('\n');
		for (int i = 0; i < entries.size(); i++) {
			out.append("\t\t\t\"").append(entries.get(i)).append('"').append(i + 1 < entries.size() ? ",\n" : ");\n");
		}
		return out.toString();
	}
}
