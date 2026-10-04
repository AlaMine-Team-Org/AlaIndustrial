package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The reload path an operator actually uses — {@link Config#reload()} through the loader-bound
 * {@link Config#configPath} ({@code /ala config reload}, datapack {@code /reload}) — pinned as it
 * behaves after MOD-694 (MOD-710, batch 0).
 *
 * <p>Each case first loads a file with an edited knob, so the live value differs from the default,
 * then removes something and reloads. ADR-033 point 3: the load starts from this build's defaults and
 * lays the file over them, so whatever is missing — one key, a whole section, the whole file — comes
 * back at the compiled default, never at the value the previous reload left behind; and the file on
 * disk is rewritten to the golden bytes.
 */
class ConfigReloadCharacterizationTest {

	@TempDir
	Path dir;

	private Path file;
	private Supplier<Path> savedPath;

	@BeforeEach
	void bindConfigPath() throws IOException {
		savedPath = Config.configPath;
		file = dir.resolve("alaindustrial.json");
		Config.configPath = () -> file;
		String edited = ConfigText.withValue(ConfigText.golden(), "machines", "machineEuPerTick", "5");
		edited = ConfigText.withValue(edited, "cables", "cableBuffer", "30");
		write(edited);
		assertEquals(Config.LoadResult.LOADED, Config.reload());
		assertEquals(5, Config.machineEuPerTick);
		assertEquals(30, Config.cableBuffer);
	}

	@AfterEach
	void restore() {
		Config.configPath = savedPath;
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(dir.resolve("restore.json")));
	}

	/** An untouched file reloads as {@code LOADED}, keeps the edits and is not rewritten. */
	@Test
	void reloadOfAnUnchangedFileKeepsItsEditsAndBytes() throws IOException {
		String before = ConfigText.read(file);
		assertEquals(Config.LoadResult.LOADED, Config.reload());
		assertEquals(5, Config.machineEuPerTick);
		assertNull(ConfigText.firstDifference(before, ConfigText.read(file)));
	}

	/** A deleted key comes back at the compiled default; its neighbour's edit survives. */
	@Test
	void aRemovedKeyTakesTheCompiledDefault() throws IOException {
		write(ConfigText.withoutKey(ConfigText.read(file), "machines", "machineEuPerTick"));
		assertEquals(Config.LoadResult.LOADED, Config.reload());

		assertEquals(2, Config.machineEuPerTick, "absent key = compiled default, not the live 5");
		assertEquals(30, Config.cableBuffer, "the other section's edit is kept");
		String expected = ConfigText.withValue(ConfigText.golden(), "cables", "cableBuffer", "30");
		assertNull(ConfigText.firstDifference(expected, ConfigText.read(file)),
				"the key is written back at its default");
	}

	/** A deleted section: every knob in it comes back at the compiled default. */
	@Test
	void aRemovedSectionTakesTheCompiledDefaults() throws IOException {
		write(ConfigText.withoutSection(ConfigText.read(file), "cables"));
		assertEquals(Config.LoadResult.LOADED, Config.reload());

		assertEquals(12, Config.cableBuffer, "absent section = compiled defaults, not the live 30");
		assertEquals(5, Config.machineEuPerTick, "the other section's edit is kept");
		String expected = ConfigText.withValue(ConfigText.golden(), "machines", "machineEuPerTick", "5");
		assertNull(ConfigText.firstDifference(expected, ConfigText.read(file)),
				"the section is written back with its defaults");
	}

	/** A deleted file: the live balance is reset and the golden file is written. */
	@Test
	void aRemovedFileResetsEveryKnobAndWritesTheGoldenFile() throws IOException {
		Files.delete(file);
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.reload());

		assertEquals(2, Config.machineEuPerTick);
		assertEquals(12, Config.cableBuffer);
		assertNull(ConfigText.firstDifference(ConfigText.golden(), ConfigText.read(file)));
	}

	/** A broken file is refused atomically: {@code ERROR}, live values and file both untouched. */
	@Test
	void aBrokenFileLeavesTheLiveBalanceAndTheFileAlone() throws IOException {
		String broken = ConfigText.withValue(ConfigText.read(file), "machines", "machineEuPerTick", "\"five\"");
		write(broken);
		assertEquals(Config.LoadResult.ERROR, Config.reload());

		assertEquals(5, Config.machineEuPerTick);
		assertEquals(30, Config.cableBuffer);
		assertNull(ConfigText.firstDifference(broken, ConfigText.read(file)));
	}

	private void write(String text) throws IOException {
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}
}
