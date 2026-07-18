package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L1 unit tests for the config <b>file</b> logic (MOD-100): parse, forward/backward-compat, the
 * self-documenting {@code _comment_*} keys, canonicalize-on-load, idempotency, and atomic apply.
 *
 * <p>Unlike {@link ConfigBalanceTest} (which only reads the compiled-in defaults) these drive
 * {@link Config#loadFrom(Path)} end-to-end against a temp file. That is only possible because MOD-100
 * de-Minecraft-ified {@code loadFrom} (plain Gson, no {@code net.minecraft.GsonHelper}) — the L1 suite
 * runs without the Minecraft jar. {@code loadFrom} mutates {@link Config}'s static fields, so the
 * pristine defaults are captured once and restored after every test to keep {@link ConfigBalanceTest}
 * (which asserts those defaults) independent of run order.
 */
class ConfigFileTest {

	@TempDir
	static Path sharedDir;
	/** Snapshot of the pristine compiled defaults, written before any test mutates the static fields. */
	static Path baseline;

	@BeforeAll
	static void captureDefaults() {
		baseline = sharedDir.resolve("baseline.json");
		// File absent -> writes the current (still pristine) defaults; used to restore between tests.
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(baseline));
	}

	@AfterEach
	void restoreDefaults() {
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(baseline));
	}

	@Test
	void barefile_valuesApplied_missingKeysKeepCurrent(@TempDir Path dir) throws IOException {
		int keptDaylight = Config.daylightEuPerTick; // not present in the file below
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"solarEuPerTick\": 7 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(7, Config.solarEuPerTick, "present key applied");
		assertEquals(keptDaylight, Config.daylightEuPerTick, "absent key keeps current value, not zeroed");
	}

	@Test
	void commentKeys_areIgnoredByParser(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{\n"
				+ "  \"_comment_solarEuPerTick\": \"some human note\",\n"
				+ "  \"solarEuPerTick\": 5\n"
				+ "}");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(5, Config.solarEuPerTick, "value read despite the _comment_ sibling");
	}

	@Test
	void unknownKey_doesNotBreakParse(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"someRemovedField\": 123, \"solarEuPerTick\": 4 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "stray key is tolerated, not an error");
		assertEquals(4, Config.solarEuPerTick);
	}

	@Test
	void defaultsWritten_containCommentsBeforeTheirField(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(f));

		String body = Files.readString(f);
		int comment = body.indexOf("\"_comment_solarEuPerTick\"");
		int field = body.indexOf("\"solarEuPerTick\"");
		assertTrue(comment >= 0, "generated file carries inline comments");
		assertTrue(field > comment, "the comment renders on the line above its field");
	}

	@Test
	void canonicalize_rewritesBareFileWithComments_regressionOfMainHole(@TempDir Path dir) throws IOException {
		// The main hole the audit caught: loadFrom only wrote when the file was ABSENT, so an existing
		// comment-less file never gained the inline docs. Canonicalize-on-load must fix that. This test is
		// red without the canonicalize step (a bare file would stay bare) — it is the regression guard.
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"solarEuPerTick\": 3 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		String body = Files.readString(f);
		assertTrue(body.contains("\"_comment_solarEuPerTick\""), "existing bare file gains inline comments");
		assertTrue(body.contains("\"solarEuPerTick\": 3"), "the operator's edited value is preserved");
	}

	@Test
	void canonicalize_isIdempotent(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"solarEuPerTick\": 3 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		String afterFirst = Files.readString(f);
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		String afterSecond = Files.readString(f);

		assertEquals(afterFirst, afterSecond, "a second load of a canonical file must not rewrite it");
		assertFalse(afterSecond.contains("_comment_solarEuPerTick\": \"some"),
				"comment keys are not duplicated on re-serialization");
		// exactly one comment key per field name
		assertEquals(indexCount(afterSecond, "\"_comment_solarEuPerTick\""), 1);
	}

	@Test
	void wrongType_isAtomic_liveBalanceUnchanged(@TempDir Path dir) throws IOException {
		int solarBefore = Config.solarEuPerTick;
		int machineBefore = Config.machineEuPerTick;
		Path f = dir.resolve("alaindustrial.json");
		// A valid key BEFORE the bad one: if apply were not atomic, solarEuPerTick would already be 9.
		Files.writeString(f, "{ \"solarEuPerTick\": 9, \"machineEuPerTick\": \"oops\" }");

		assertEquals(Config.LoadResult.ERROR, Config.loadFrom(f), "wrong-type value is reported as an error");
		assertEquals(solarBefore, Config.solarEuPerTick, "no field applied on a failed load (atomic)");
		assertEquals(machineBefore, Config.machineEuPerTick);
	}

	private static int indexCount(String haystack, String needle) {
		int n = 0;
		for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
			n++;
		}
		return n;
	}
}
