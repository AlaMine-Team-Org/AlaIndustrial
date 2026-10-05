package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.environment.GeneratorConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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

	/** The five characters Gson html-escapes by default, as the escape sequences it writes for them. */
	private static final String[] HTML_ESCAPES = {"\\u003d", "\\u0027", "\\u003c", "\\u003e", "\\u0026"};

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
	void barefile_valuesApplied_missingKeysTakeTheBuiltinDefault(@TempDir Path dir) throws IOException {
		// A live value that differs from the compiled default (4), so "kept the live value" and "fell back
		// to the default" are told apart: with the default as the live value both read the same.
		GeneratorConfig.daylightEuPerTick = 9; // not present in the file below
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"solarEuPerTick\": 7 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(7, GeneratorConfig.solarEuPerTick, "present key applied");
		assertEquals(4, GeneratorConfig.daylightEuPerTick,
				"absent key takes the builtin default (4) - not the live value (9), and not zeroed (MOD-694 D8)");
	}

	@Test
	void commentKeys_areIgnoredByParser(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{\n"
				+ "  \"_comment_solarEuPerTick\": \"some human note\",\n"
				+ "  \"solarEuPerTick\": 5\n"
				+ "}");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(5, GeneratorConfig.solarEuPerTick, "value read despite the _comment_ sibling");
	}

	@Test
	void unknownKey_doesNotBreakParse(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"someRemovedField\": 123, \"solarEuPerTick\": 4 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "stray key is tolerated, not an error");
		assertEquals(4, GeneratorConfig.solarEuPerTick);
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
	void comments_areWrittenLiterally_withoutHtmlEscapes(@TempDir Path dir) throws IOException {
		// MOD-694: the writer used Gson's default html escaping, so "=" and the apostrophe in a doc string
		// landed in the operator's file as backslash-u sequences.
		Path f = dir.resolve("alaindustrial.json");
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(f));

		String body = Files.readString(f);
		String comment = lineContaining(body, "\"_comment_globalEuRateMultiplier\"");
		assertTrue(comment.contains("1.0 = unchanged"), "the equals sign is written literally: " + comment);
		assertTrue(comment.contains("generator's"), "so is the apostrophe: " + comment);
		for (String escape : HTML_ESCAPES) {
			assertFalse(body.contains(escape), "no html escape sequence in the file: " + escape);
		}
	}

	@Test
	void fileWithOldHtmlEscapes_isRewrittenOnce_valuesUnchanged(@TempDir Path dir) throws IOException {
		// Existing installs carry the escaped form. The self-heal compares raw text, so the first load
		// rewrites such a file into the literal form, keeps every value, and a second load leaves it be.
		Path f = dir.resolve("alaindustrial.json");
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(f));
		String literal = Files.readString(f);
		String escaped = literal.replace("&", HTML_ESCAPES[4]).replace("=", HTML_ESCAPES[0])
				.replace("'", HTML_ESCAPES[1]).replace("<", HTML_ESCAPES[2]).replace(">", HTML_ESCAPES[3]);
		assertNotEquals(literal, escaped, "sanity: the defaults file holds characters the old writer escaped");
		Files.writeString(f, escaped);
		FileTime old = FileTime.fromMillis(1_000_000_000_000L);
		Files.setLastModifiedTime(f, old);

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "the escaped file is valid json and loads");
		assertNotEquals(old, Files.getLastModifiedTime(f), "the first load rewrites the escaped file");
		assertEquals(literal, Files.readString(f), "into exactly the literal form, with every value unchanged");
		assertEquals(1.0f, Config.globalEuRateMultiplier, 0.0f, "the knob under the escaped comment is intact");
		assertEquals(1, GeneratorConfig.solarEuPerTick);

		Files.setLastModifiedTime(f, old);
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(old, Files.getLastModifiedTime(f), "a second load does not write the file again");
		assertEquals(literal, Files.readString(f));
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
		int solarBefore = GeneratorConfig.solarEuPerTick;
		int machineBefore = Config.machineEuPerTick;
		Path f = dir.resolve("alaindustrial.json");
		// A valid key BEFORE the bad one: if apply were not atomic, solarEuPerTick would already be 9.
		Files.writeString(f, "{ \"solarEuPerTick\": 9, \"machineEuPerTick\": \"oops\" }");

		assertEquals(Config.LoadResult.ERROR, Config.loadFrom(f), "wrong-type value is reported as an error");
		assertEquals(solarBefore, GeneratorConfig.solarEuPerTick, "no field applied on a failed load (atomic)");
		assertEquals(machineBefore, Config.machineEuPerTick);
	}

	@Test
	void outOfRangeValue_restoresTheDeclaredDefault(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		// windMillSampleTicks must be positive; an invalid value restores the field's own default (40).
		Files.writeString(f, "{ \"windMillSampleTicks\": -5 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(40, GeneratorConfig.windMillSampleTicks,
				"an out-of-range value falls back to the declared default, not to zero");
	}

	@Test
	void machineEuPerTickZero_restoresDefault_neverDividesByZero(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		// machineEuPerTick is used as an integer DIVISOR in AbstractProcessingMachineBlockEntity
		// (baseDuration = energy / machineEuPerTick). It must never load as 0, or a machine tick throws
		// ArithmeticException and crashes the world. A 0 in the file restores the declared default (2).
		Files.writeString(f, "{ \"machineEuPerTick\": 0 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(2, Config.machineEuPerTick,
				"machineEuPerTick=0 falls back to its default, never stays 0 (it is a division divisor)");
		assertTrue(Config.machineEuPerTick >= 1, "the divisor is always >= 1 after load");
	}

	@Test
	void boundaryFlooredFields_clampToTheirBoundaryNotTheirDefault(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		// These four deliberately clamp into range rather than restore their default — euPerXp guards a
		// division, and a negative cable loss simply means "no loss". Pinned so the registry rewrite
		// (MOD-160) cannot quietly turn them into default-restoring fields.
		Files.writeString(f, "{ \"euPerXp\": 0, \"euPerXpGenerated\": -3, \"xpLevelOneCost\": 0,"
				+ " \"copperCableLossPerBlock\": -0.5 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(1, Config.euPerXp, "euPerXp floors at 1, not at its 1000 default");
		assertEquals(1, Config.euPerXpGenerated, "euPerXpGenerated floors at 1, not at its 20000 default");
		assertEquals(1, Config.xpLevelOneCost, "xpLevelOneCost floors at 1, not at its 80 default");
		assertEquals(0.0, Config.copperCableLossPerBlock, "a negative cable loss floors at 0, not at 0.02");
	}

	@Test
	void doubleKnob_roundTripsWithoutFloatNoise(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"copperCableLossPerBlock\": 0.02 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(0.02, Config.copperCableLossPerBlock,
				"the double knob is read at double precision; routing it through float used to widen"
						+ " 0.02 into 0.019999999552965164 and rewrite the file with that noise");
		assertTrue(Files.readString(f).contains("\"copperCableLossPerBlock\": 0.02"),
				"the canonicalized file keeps the clean literal");
	}

	@Test
	void negativeBuffer_fallsBackToDefault_doesNotCrashOnPlacement(@TempDir Path dir) throws IOException {
		// Regression for the negative-config crash: a value below 1 on every EU-buffer knob used to
		// flow unchecked into EnergyBuffer's constructor and throw IllegalArgumentException at block
		// placement. Each *Buffer entry in FIELDS now carries minimum=1, so a bogus value restores the
		// declared default instead of poisoning the world. Representative cases below cover the reported
		// battery-box path plus a cross-section of machine / generator / cable buffers.
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"batteryBoxBuffer\": -5, \"maceratorBuffer\": -1, \"machineBuffer\": 0,"
				+ " \"pumpBuffer\": -10, \"generatorBuffer\": -3, \"geothermalBuffer\": 0,"
				+ " \"waterMillBuffer\": -2, \"windMillBuffer\": -1, \"t2WindMillBuffer\": -8,"
				+ " \"solarBuffer\": -4, \"cableBuffer\": -1 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(20_000, Config.batteryBoxBuffer, "the reported battery-box crash path");
		assertEquals(800, Config.maceratorBuffer);
		assertEquals(800, Config.machineBuffer);
		assertEquals(4_000, Config.pumpBuffer);
		assertEquals(4_000, GeneratorConfig.generatorBuffer);
		assertEquals(4_000, GeneratorConfig.geothermalBuffer);
		assertEquals(4_000, GeneratorConfig.waterMillBuffer);
		assertEquals(4_000, GeneratorConfig.windMillBuffer);
		assertEquals(8_000, GeneratorConfig.t2WindMillBuffer);
		assertEquals(8_000, GeneratorConfig.solarBuffer);
		assertEquals(12, Config.cableBuffer);
	}

	@Test
	void everyDeclaredTunable_hasARegistryEntry() {
		// The registry drives read/validate/serialize for every knob, so a field added to a holder but
		// missing from the registry would silently never load or persist. Compare the two counts directly.
		int registered = Config.REGISTRY.entries().size();

		long declared = 0;
		for (Class<?> holder : Config.REGISTRY.holders()) {
			declared += java.util.Arrays.stream(holder.getDeclaredFields())
					.filter(f -> java.lang.reflect.Modifier.isPublic(f.getModifiers()))
					.filter(f -> java.lang.reflect.Modifier.isStatic(f.getModifiers()))
					.filter(f -> !java.lang.reflect.Modifier.isFinal(f.getModifiers()))
					.filter(f -> f.getType().isPrimitive())
					.count();
		}

		assertEquals(declared, registered,
				"every public static tunable must have exactly one registry entry — a missing entry means"
						+ " the knob is neither loaded from nor written to the config file");
	}

	/** The single line of {@code body} holding {@code needle}; fails the test when there is none. */
	private static String lineContaining(String body, String needle) {
		for (String line : body.split("\n")) {
			if (line.contains(needle)) {
				return line.replace("\r", "");
			}
		}
		throw new AssertionError("no line containing " + needle);
	}

	private static int indexCount(String haystack, String needle) {
		int n = 0;
		for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
			n++;
		}
		return n;
	}
}
