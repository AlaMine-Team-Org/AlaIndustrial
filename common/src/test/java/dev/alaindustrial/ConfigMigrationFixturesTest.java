package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.alaindustrial.core.reactor.ReactorConfig;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real operator files of every past schema version, loaded as they are (MOD-710, batch 0): the live
 * values after the load and the file the loader rewrites, both pinned against hand-written
 * expectations.
 *
 * <p>The fixtures under {@code common/src/test/resources/dev/alaindustrial/config/} are:
 * <ul>
 *   <li>{@code v0-flat.json} — the pre-MOD-402 flat layout: no {@code schemaVersion}, every knob at
 *       the top level, plus a key no build knows any more;</li>
 *   <li>{@code v1-no-builtin-defaults.json} — sectioned, {@code schemaVersion 1}, written before the
 *       {@code builtinDefaults} block existed (MOD-553), with an operator's own comment line;</li>
 *   <li>{@code v2-operator-edits.json} — the current layout with a trimmed {@code builtinDefaults}
 *       block: two knobs still hold the default recorded beside them (adopted), two were edited
 *       (kept), one has no recorded default at all (kept), one is retired (dropped).</li>
 * </ul>
 * Each fixture edits one knob of every registry type (int, float, double, boolean) in more than one
 * section. Every other knob is absent, so it takes this build's default (ADR-033, MOD-694), and the
 * rewritten file is therefore the golden file with the kept edits in place — stated line by line with
 * {@link ConfigText#withValue}, never re-derived from {@link Config}.
 */
class ConfigMigrationFixturesTest {

	@TempDir
	Path dir;

	@AfterEach
	void restoreDefaults() {
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(dir.resolve("restore.json")));
	}

	/** The golden file with the four edits every fixture keeps. */
	private static String goldenWithKeptEdits() {
		String expected = ConfigText.golden();
		expected = ConfigText.withValue(expected, "global", "globalMachineSpeedMultiplier", "2.5");
		expected = ConfigText.withValue(expected, "machines", "machineEuPerTick", "5");
		expected = ConfigText.withValue(expected, "machines", "mutationSlagChance", "0.5");
		return expected;
	}

	@Test
	void v0FlatFileKeepsEveryEditAndIsRewrittenSectioned() throws IOException {
		Path file = ConfigText.copy(ConfigText.DIR + "v0-flat.json", dir.resolve("alaindustrial.json"));
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(file));

		assertEquals(5, Config.machineEuPerTick);
		assertEquals(2.5f, Config.globalMachineSpeedMultiplier);
		assertEquals(0.5, Config.mutationSlagChance);
		assertEquals(false, ReactorConfig.reactorBlastFire);
		assertEquals(30, Config.cableBuffer);

		String expected = goldenWithKeptEdits();
		expected = ConfigText.withValue(expected, "machines", "reactorBlastFire", "false");
		expected = ConfigText.withValue(expected, "cables", "cableBuffer", "30");
		assertNull(ConfigText.firstDifference(expected, ConfigText.read(file)),
				"a v0 file is rewritten as the golden file with its edits; the retired key is dropped");
	}

	@Test
	void v1FileWithoutRecordedDefaultsKeepsEveryEdit() throws IOException {
		Path file = ConfigText.copy(ConfigText.DIR + "v1-no-builtin-defaults.json", dir.resolve("alaindustrial.json"));
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(file));

		assertEquals(5, Config.machineEuPerTick);
		assertEquals(2.5f, Config.globalMachineSpeedMultiplier);
		assertEquals(0.5, Config.mutationSlagChance);
		assertEquals(false, ReactorConfig.reactorBlastFire);
		assertEquals(30, Config.cableBuffer);

		String expected = goldenWithKeptEdits();
		expected = ConfigText.withValue(expected, "machines", "reactorBlastFire", "false");
		expected = ConfigText.withValue(expected, "cables", "cableBuffer", "30");
		assertNull(ConfigText.firstDifference(expected, ConfigText.read(file)),
				"a v1 file gains builtinDefaults and the canonical comments; the operator's comment is replaced");
	}

	/**
	 * The adoption rule on a real file: a knob equal to the default recorded beside it follows this
	 * build's default ({@code cableBuffer 10 -> 12}, {@code reactorBlastFire false -> true}); an edited
	 * knob, or one with no recorded default, keeps its value.
	 */
	@Test
	void v2FileAdoptsUntouchedKnobsAndKeepsEditedOnes() throws IOException {
		Path file = ConfigText.copy(ConfigText.DIR + "v2-operator-edits.json", dir.resolve("alaindustrial.json"));
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(file));

		assertEquals(5, Config.machineEuPerTick);
		assertEquals(2.5f, Config.globalMachineSpeedMultiplier);
		assertEquals(0.5, Config.mutationSlagChance);
		assertEquals(true, ReactorConfig.reactorBlastFire, "false was the recorded default: adopted");
		assertEquals(12, Config.cableBuffer, "10 was the recorded default: adopted");

		assertNull(ConfigText.firstDifference(goldenWithKeptEdits(), ConfigText.read(file)),
				"adopted knobs are written at this build's default, edited ones as edited");
	}

	/** A second load of any rewritten fixture changes nothing: the rewrite is canonical. */
	@Test
	void aRewrittenFixtureIsStableOnTheNextLoad() throws IOException {
		Path file = ConfigText.copy(ConfigText.DIR + "v0-flat.json", dir.resolve("alaindustrial.json"));
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(file));
		String once = ConfigText.read(file);
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(file));
		assertNull(ConfigText.firstDifference(once, ConfigText.read(file)));
		assertEquals(5, Config.machineEuPerTick);
	}
}
