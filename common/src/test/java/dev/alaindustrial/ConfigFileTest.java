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

	@Test
	void outOfRangeValue_restoresTheDeclaredDefault(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		// windMillSampleTicks must be positive; an invalid value restores the field's own default (40).
		Files.writeString(f, "{ \"windMillSampleTicks\": -5 }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(40, Config.windMillSampleTicks,
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
		assertEquals(4_000, Config.generatorBuffer);
		assertEquals(4_000, Config.geothermalBuffer);
		assertEquals(4_000, Config.waterMillBuffer);
		assertEquals(4_000, Config.windMillBuffer);
		assertEquals(8_000, Config.t2WindMillBuffer);
		assertEquals(8_000, Config.solarBuffer);
		assertEquals(12, Config.cableBuffer);
	}

	@Test
	void everyDeclaredTunable_hasARegistryEntry() throws ReflectiveOperationException {
		// The registry drives read/validate/serialize for every knob, so a field added to the class but
		// forgotten in FIELDS would silently never load or persist. Compare the two counts directly.
		java.lang.reflect.Field fieldsList = Config.class.getDeclaredField("FIELDS");
		fieldsList.setAccessible(true);
		int registered = ((java.util.List<?>) fieldsList.get(null)).size();

		long declared = java.util.Arrays.stream(Config.class.getDeclaredFields())
				.filter(f -> java.lang.reflect.Modifier.isPublic(f.getModifiers()))
				.filter(f -> java.lang.reflect.Modifier.isStatic(f.getModifiers()))
				.filter(f -> !java.lang.reflect.Modifier.isFinal(f.getModifiers()))
				.filter(f -> f.getType().isPrimitive())
				.count();

		assertEquals(declared, registered,
				"every public static tunable must have exactly one FIELDS entry — a missing entry means"
						+ " the knob is neither loaded from nor written to the config file");
	}

	private static int indexCount(String haystack, String needle) {
		int n = 0;
		for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
			n++;
		}
		return n;
	}
}
