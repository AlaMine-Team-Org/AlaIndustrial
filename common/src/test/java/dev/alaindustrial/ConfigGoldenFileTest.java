package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Characterization of the operator's file (MOD-710, batch 0): what {@link Config#loadFrom} writes for a
 * fresh install is compared, byte for byte, with the committed
 * {@code common/src/test/resources/dev/alaindustrial/config/defaults.golden.json}.
 *
 * <p><b>Why a golden file.</b> The config is being split into a loading mechanism and per-subsystem
 * knob holders (ADR-034). The one promise that split makes to server operators is that their file does
 * not change by a single byte — section order, key order, every {@code _comment_} line, the
 * {@code builtinDefaults} block, LF line ends, no trailing newline. This test is that promise.
 *
 * <p><b>Updated only by an explicit command (ADR-032)</b>, never by the build, a hook or
 * {@code regen.py}:
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dalaindustrial.configGolden.writeTo=&lt;repo&gt;/common/src/test/resources/dev/alaindustrial/config"
 *   ./gradlew :common:test --tests dev.alaindustrial.ConfigGoldenFileTest
 * </pre>
 * The run writes the fresh file and then fails on purpose, so it can never pass for a check. Review the
 * diff and commit it with the change that altered the file — which, outside a declared operator-facing
 * change, is a defect (ADR-034).
 *
 * <p>This file is a characterization baseline, not the knob export of MOD-700
 * ({@code config-knobs.json}): the export is a derived input of the Python gates, the golden file is the
 * operator's own bytes. Two files, two owners.
 */
class ConfigGoldenFileTest {

	/** System property that turns the comparison into a rewrite of the golden file (ADR-032). */
	static final String WRITE_TO_PROPERTY = "alaindustrial.configGolden.writeTo";

	/**
	 * False while the committed golden file is a seed derived from the sources (the knob export plus
	 * the section and block docs) by an agent without a JDK, as ADR-032 permits; the first run of the
	 * update command on a machine with a JDK replaces it with the captured bytes and sets this to true.
	 * The comparison is exactly as strict either way — only the failure message differs.
	 */
	static final boolean GOLDEN_CAPTURED = true;

	private static final String REFRESH = "if the change is intended, rewrite it with JAVA_TOOL_OPTIONS=\"-D"
			+ WRITE_TO_PROPERTY + "=<repo>/common/src/test/resources/dev/alaindustrial/config\" ./gradlew"
			+ " :common:test --tests dev.alaindustrial.ConfigGoldenFileTest and review the diff (ADR-032)";

	@TempDir
	Path dir;

	/** Every test here leaves the live balance on the compiled defaults, whatever it loaded. */
	@AfterEach
	void restoreDefaults() {
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(dir.resolve("restore.json")));
	}

	/** The central claim: a fresh install writes exactly the golden bytes. */
	@Test
	void freshInstallWritesTheGoldenFileByteForByte() throws IOException {
		Path file = dir.resolve("alaindustrial.json");
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(file));
		String fresh = ConfigText.read(file);

		String writeTo = System.getProperty(WRITE_TO_PROPERTY);
		if (writeTo != null && !writeTo.isBlank()) {
			Path target = Path.of(writeTo).resolve("defaults.golden.json");
			Files.createDirectories(target.getParent());
			Files.writeString(target, fresh, StandardCharsets.UTF_8);
			fail("golden file rewritten at " + target + " - review the diff and commit it; this run fails"
					+ " on purpose so the rewrite is never mistaken for a check (ADR-032)");
		}

		String difference = ConfigText.firstDifference(ConfigText.golden(), fresh);
		assertNull(difference, "the operator's file changed: " + difference + "; "
				+ (GOLDEN_CAPTURED ? "" : "the committed golden is still a seed derived from the sources, ")
				+ REFRESH);
	}

	/**
	 * The golden file loads cleanly and is left alone: {@code LOADED}, no self-heal rewrite, not one
	 * byte changed. A canonical file that the loader itself would rewrite is not canonical.
	 */
	@Test
	void theGoldenFileLoadsWithoutBeingRewritten() throws IOException {
		Path file = ConfigText.copy(ConfigText.GOLDEN, dir.resolve("alaindustrial.json"));
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(file));
		assertNull(ConfigText.firstDifference(ConfigText.golden(), ConfigText.read(file)),
				"loading the golden file must not rewrite it");
	}

	/** The shape the operator's editor sees: LF only, no byte-order mark, no trailing newline. */
	@Test
	void theGoldenFileIsLfWithoutBomOrTrailingNewline() {
		String golden = ConfigText.golden();
		assertFalse(golden.contains("\r"), "CR in the golden file");
		assertFalse(golden.startsWith("﻿"), "byte-order mark in the golden file");
		assertTrue(golden.startsWith("{\n") && golden.endsWith("\n}"), "Gson pretty-print, no trailing newline");
	}

	/**
	 * Negative control: one value edited in a copy of the golden text is reported at the line that
	 * changed, and so is a trailing newline. Without this the comparison could be a no-op and the
	 * suite would still be green.
	 */
	@Test
	void anEditedLineIsReported() {
		String golden = ConfigText.golden();
		String edited = ConfigText.withValue(golden, "machines", "machineEuPerTick", "3");
		String difference = ConfigText.firstDifference(golden, edited);
		assertNotNull(difference, "an edited value must be reported");
		assertTrue(difference.contains("\"machineEuPerTick\": 3"), difference);
		assertNotNull(ConfigText.firstDifference(golden, golden + "\n"), "a trailing newline is a difference");
	}
}
