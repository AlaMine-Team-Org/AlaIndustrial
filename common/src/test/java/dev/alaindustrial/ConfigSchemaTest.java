package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L1 unit tests for the config FILE LAYOUT (MOD-402): {@code schemaVersion}, thematic sections and
 * the migration ladder.
 *
 * <p>{@link ConfigFileTest} covers the load contract itself (atomic apply, clamping, self-heal) and
 * {@link ConfigSnapshotTest} covers the registry's getters; this suite covers the third thing MOD-402
 * added — <em>which shape the file has and what happens when that shape is not the current one</em>:
 * <ul>
 *   <li>a pre-MOD-402 flat file still loads, keeps every operator value, and is rewritten sectioned;</li>
 *   <li>a sectioned file is byte-identical on a second load (no churn on {@code /reload});</li>
 *   <li>a file from a FUTURE schema is refused — nothing applied, balance back on the compiled
 *       defaults, and the file left untouched on disk;</li>
 *   <li>the ladder is well-formed: exactly one migration per version hop, ascending.</li>
 * </ul>
 *
 * <p>Gson is {@code testRuntimeOnly} on {@code common} (it is a Minecraft transitive, pinned only for
 * runtime), so this suite cannot import a JSON parser. It reads the canonical file textually instead —
 * which is fine, because the canonical form is plain Gson pretty-print: exactly one property per line,
 * sections at two spaces of indent and their fields at four.
 *
 * <p>{@code loadFrom} mutates {@link Config}'s static fields, so the pristine defaults are captured
 * once into a baseline file and restored after every test — the same idiom as the two suites above,
 * which is what keeps {@link ConfigBalanceTest} independent of run order.
 */
class ConfigSchemaTest {

	@TempDir
	static Path sharedDir;
	/** Snapshot of the pristine compiled defaults, written before any test mutates the static fields. */
	static Path baseline;

	@BeforeAll
	static void captureDefaults() {
		baseline = sharedDir.resolve("baseline.json");
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(baseline));
	}

	@AfterEach
	void restoreDefaults() {
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(baseline));
	}

	// --- the flat → sectioned migration ----------------------------------------------------------

	/**
	 * The backward-compatibility guarantee, stated as a test: a file in the pre-MOD-402 flat layout
	 * (no {@code schemaVersion}, every knob at the top level) still applies every value the operator
	 * had edited, and is rewritten in the sectioned form with the current version.
	 *
	 * <p>One field of each registry type is edited, so the migration is proven for int, float, double
	 * and boolean rather than for ints alone. Every expected value is the hand-written literal from the
	 * file, never re-derived from {@link Config}.
	 *
	 * <p>Red without {@code migrateFlatToSections}: staging reads a field only from its own section, so
	 * a flat file whose keys were never relocated would apply nothing and silently keep the defaults —
	 * which is precisely the regression this asserts against.
	 */
	@Test
	void flatFile_migratesToSections_andKeepsEveryOperatorValue(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{\n"
				+ "  \"_comment_solarEuPerTick\": \"a note the old file carried\",\n"
				+ "  \"solarEuPerTick\": 42,\n"
				+ "  \"windMillRainFactor\": 3.25,\n"
				+ "  \"copperCableLossPerBlock\": 0.077,\n"
				+ "  \"oilBurns\": false\n"
				+ "}");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "an old flat file still loads");
		assertEquals(42, Config.solarEuPerTick, "int value survives the flat → sectioned migration");
		assertEquals(3.25f, Config.windMillRainFactor, 0.0f, "float value survives the migration");
		assertEquals(0.077, Config.copperCableLossPerBlock, 0.0, "double value survives the migration");
		assertFalse(Config.oilBurns, "boolean value survives the migration");

		String body = Files.readString(f);
		assertTrue(body.contains("\"schemaVersion\": " + Config.SCHEMA_VERSION),
				"the rewritten file records the schema version it is now in");
		assertTrue(body.contains("\"generators\": {"), "the rewritten file is sectioned");
		assertEquals("generators", keyToSection(body).get("solarEuPerTick"),
				"the migrated key landed in the section its FIELDS entry declares");
		assertTrue(body.contains("\"solarEuPerTick\": 42"), "and it kept the operator's value");
	}

	/**
	 * A file already in the current shape must not be rewritten on load: the self-heal compares content
	 * and only writes on a real difference, so {@code /reload} does not churn the file. Byte equality of
	 * the two reads is the assertion — a version or section that failed to round-trip shows up here.
	 */
	@Test
	void sectionedFile_reloadsWithoutRewriting(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"solarEuPerTick\": 5 }");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "first load migrates + canonicalizes");
		String afterFirst = Files.readString(f);

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "second load reads the canonical form");
		assertEquals(afterFirst, Files.readString(f),
				"a canonical, current-version file must be left byte-identical on reload");
		assertEquals(5, Config.solarEuPerTick, "and the value round-trips through the sectioned form");
	}

	/** A fresh install writes the version straight away, so the very first file is already migratable. */
	@Test
	void defaultsWritten_carryTheSchemaVersionAndEverySection(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(f));

		String body = Files.readString(f);
		assertTrue(body.contains("\"schemaVersion\": " + Config.SCHEMA_VERSION),
				"a freshly written file states its schema version");
		for (Config.Section section : Config.Section.values()) {
			assertTrue(body.contains("\"" + section.id + "\": {"),
					"section '" + section.id + "' is written even on a fresh file");
		}
	}

	// --- the future-version guard ----------------------------------------------------------------

	/**
	 * The whole point of the version field: a file written by a NEWER build must not be read as if this
	 * build understood it. Nothing from it is applied, the live balance goes back to the values compiled
	 * into this build, and the file is left exactly as it was so the operator does not lose it by
	 * booting an older jar once.
	 *
	 * <p>Each field is deliberately set to a non-default sentinel first — otherwise "the defaults are in
	 * place" would be true before the call and the assertion would prove nothing. The expected defaults
	 * are hand-written literals (1 / 1.5 / 0.02 / true), never read back from the production registry.
	 */
	@Test
	void futureSchemaVersion_appliesNothingAndFallsBackToCompiledDefaults(@TempDir Path dir)
			throws IOException {
		Config.solarEuPerTick = 77;
		Config.windMillRainFactor = 9.5f;
		Config.copperCableLossPerBlock = 0.5;
		Config.oilBurns = false;

		Path f = dir.resolve("alaindustrial.json");
		String written = "{\n"
				+ "  \"schemaVersion\": " + (Config.SCHEMA_VERSION + 1) + ",\n"
				+ "  \"generators\": { \"solarEuPerTick\": 123, \"windMillRainFactor\": 8.5 },\n"
				+ "  \"cables\": { \"copperCableLossPerBlock\": 0.9 },\n"
				+ "  \"world\": { \"oilBurns\": true }\n"
				+ "}";
		Files.writeString(f, written);

		// Its own outcome, not the generic ERROR: this branch REPLACES the running balance with the
		// compiled defaults, while ERROR leaves it alone — and `/ala config reload` has to tell an admin
		// which of the two happened.
		assertEquals(Config.LoadResult.SCHEMA_TOO_NEW, Config.loadFrom(f),
				"a newer schema is reported as its own outcome, not quietly accepted and not as a parse error");
		assertNotEquals(123, Config.solarEuPerTick, "the newer file's value must NOT be applied");
		assertEquals(1, Config.solarEuPerTick, "int falls back to the value compiled into this build");
		assertEquals(1.5f, Config.windMillRainFactor, 0.0f, "float falls back to its compiled default");
		assertEquals(0.02, Config.copperCableLossPerBlock, 0.0, "double falls back to its compiled default");
		assertTrue(Config.oilBurns, "boolean falls back to its compiled default");
		assertEquals(written, Files.readString(f),
				"the file from the future is left untouched — the mod must not overwrite what it cannot read");
	}

	/**
	 * {@code schemaVersion} obeys the same present-but-wrong-type contract as any other key: the load is
	 * rejected whole rather than falling back to "no version, so it must be the old flat layout" — which
	 * would read a modern file with the wrong rules.
	 */
	@Test
	void nonNumericSchemaVersion_isRejected_andNothingIsApplied(@TempDir Path dir) throws IOException {
		int solarBefore = Config.solarEuPerTick;
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"schemaVersion\": \"one\", \"generators\": { \"solarEuPerTick\": 8 } }");

		assertEquals(Config.LoadResult.ERROR, Config.loadFrom(f));
		assertEquals(solarBefore, Config.solarEuPerTick, "no field applied on a rejected load");
	}

	// --- section-shaped reads --------------------------------------------------------------------

	/**
	 * Atomicity, restated for the sectioned layout: a wrong type in ONE section must not leave the
	 * fields of an EARLIER section applied. Staging walks the registry in order, so {@code generators}
	 * is fully parsed before the bad key in {@code machines} is reached — if the commit were not
	 * deferred, {@code solarEuPerTick} would already be 9.
	 */
	@Test
	void wrongTypeInsideASection_leavesEverySectionUnapplied(@TempDir Path dir) throws IOException {
		int solarBefore = Config.solarEuPerTick;
		int machineBefore = Config.machineEuPerTick;
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"schemaVersion\": " + Config.SCHEMA_VERSION + ","
				+ " \"generators\": { \"solarEuPerTick\": 9 },"
				+ " \"machines\": { \"machineEuPerTick\": \"oops\" } }");

		assertEquals(Config.LoadResult.ERROR, Config.loadFrom(f));
		assertEquals(solarBefore, Config.solarEuPerTick, "a later section's typo does not half-apply an earlier one");
		assertEquals(machineBefore, Config.machineEuPerTick);
	}

	/**
	 * A section key holding something that is not an object is an operator typo that would otherwise
	 * silently drop a whole group of knobs (every key in it reads as absent). It is treated like any
	 * other wrong-type value: the load aborts before a single commit.
	 */
	@Test
	void sectionThatIsNotAnObject_isRejected(@TempDir Path dir) throws IOException {
		int machineBefore = Config.machineEuPerTick;
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"schemaVersion\": " + Config.SCHEMA_VERSION + ","
				+ " \"generators\": 5, \"machines\": { \"machineEuPerTick\": 6 } }");

		assertEquals(Config.LoadResult.ERROR, Config.loadFrom(f),
				"a section that is not an object is an error, not an empty section");
		assertEquals(machineBefore, Config.machineEuPerTick, "and nothing from any section is applied");
	}

	/**
	 * Tolerance is unchanged by sectioning: a key the mod no longer has, and a whole section name it
	 * never had, are both ignored rather than fatal — a config carried across mod versions keeps working.
	 */
	@Test
	void unknownKeyAndUnknownSection_areTolerated(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"schemaVersion\": " + Config.SCHEMA_VERSION + ","
				+ " \"generators\": { \"someRemovedField\": 123, \"solarEuPerTick\": 4 },"
				+ " \"sectionFromAnotherEra\": { \"whatever\": 1 } }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "stray key and stray section are tolerated");
		assertEquals(4, Config.solarEuPerTick, "the real key next to them still applies");
	}

	/**
	 * A missing section behaves exactly like a missing key: every field in it keeps its live value.
	 * This is what makes a hand-trimmed file (only the two sections an operator cares about) legal.
	 */
	@Test
	void absentSection_keepsEveryFieldInIt(@TempDir Path dir) throws IOException {
		Config.machineEuPerTick = 6;
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"schemaVersion\": " + Config.SCHEMA_VERSION + ","
				+ " \"generators\": { \"solarEuPerTick\": 4 } }");

		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(4, Config.solarEuPerTick, "the present section applies");
		assertEquals(6, Config.machineEuPerTick, "a field whose whole section is absent keeps its live value");
	}

	// --- structural guards on the layout itself ---------------------------------------------------

	/**
	 * Every registered field is written into the section its own {@code FIELDS} entry declares, and no
	 * field goes missing from the file.
	 *
	 * <p>The oracle is the production registry read reflectively — not a hand-written key→section table.
	 * A second table would be the very "parallel list" this design exists to avoid, and it would drift
	 * exactly the way this repo's other hand-maintained lists have.
	 */
	@Test
	void everyRegisteredFieldIsWrittenIntoItsDeclaredSection(@TempDir Path dir)
			throws IOException, ReflectiveOperationException {
		Path f = dir.resolve("alaindustrial.json");
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(f));
		Map<String, String> written = keyToSection(Files.readString(f));

		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (Object entry : registry()) {
			String key = fieldValue(entry, "key", String.class);
			Config.Section declared = fieldValue(entry, "section", Config.Section.class);
			checked++;
			if (!declared.id.equals(written.get(key))) {
				wrong.add(key + ": declared '" + declared.id + "', written under '" + written.get(key) + "'");
			}
		}

		assertTrue(checked > 100, "sanity: the registry should hold every tunable, got " + checked);
		assertEquals(checked, written.size(),
				"the canonical file must hold exactly one line per registered tunable");
		assertTrue(wrong.isEmpty(), "fields written outside their declared section: " + wrong);
	}

	/**
	 * No {@link Config.Section} may be empty. An empty section renders as {@code "name": {}} — a heading
	 * over nothing, which reads to an operator as "this group of knobs was removed". It is also the
	 * shape a section left behind by a deleted feature would have.
	 */
	@Test
	void everySectionHoldsAtLeastOneField() throws ReflectiveOperationException {
		Map<Config.Section, Integer> counts = new LinkedHashMap<>();
		for (Config.Section section : Config.Section.values()) {
			counts.put(section, 0);
		}
		for (Object entry : registry()) {
			Config.Section section = fieldValue(entry, "section", Config.Section.class);
			counts.merge(section, 1, Integer::sum);
		}

		List<Config.Section> empty = new ArrayList<>();
		for (Map.Entry<Config.Section, Integer> e : counts.entrySet()) {
			if (e.getValue() == 0) {
				empty.add(e.getKey());
			}
		}
		assertTrue(empty.isEmpty(), "sections with no fields at all: " + empty);
	}

	/**
	 * The migration ladder must have exactly one rung per version hop, in ascending order:
	 * {@code MIGRATIONS.get(i).fromVersion() == i} and {@code MIGRATIONS.size() == SCHEMA_VERSION}.
	 *
	 * <p>This is the guard that makes "bump the version" safe. Raising {@link Config#SCHEMA_VERSION}
	 * without appending a migration would leave every existing file silently unconverted; appending a
	 * migration without raising the version would leave it never running. Both fail here instead.
	 */
	@Test
	void migrationLadderCoversEveryVersionHopExactlyOnce() throws ReflectiveOperationException {
		Field migrations = Config.class.getDeclaredField("MIGRATIONS");
		migrations.setAccessible(true);
		List<?> ladder = (List<?>) migrations.get(null);

		assertEquals(Config.SCHEMA_VERSION, ladder.size(),
				"one migration per version hop: schemaVersion " + Config.SCHEMA_VERSION
						+ " needs exactly that many steps (0→1, 1→2, ...)");
		for (int i = 0; i < ladder.size(); i++) {
			Object step = ladder.get(i);
			Method fromVersion = step.getClass().getDeclaredMethod("fromVersion");
			fromVersion.setAccessible(true);
			assertEquals(i, (int) (Integer) fromVersion.invoke(step),
					"migration " + i + " must convert version " + i + " → " + (i + 1)
							+ "; the ladder is walked in list order and must be ascending and gapless");
		}
	}

	// --- helpers ----------------------------------------------------------------------------------

	/** {@code Config.FIELDS}, read reflectively — the production oracle for key and section. */
	private static List<?> registry() throws ReflectiveOperationException {
		Field fields = Config.class.getDeclaredField("FIELDS");
		fields.setAccessible(true);
		return (List<?>) fields.get(null);
	}

	/** Read a field declared on {@code ConfigField} (the shared superclass of every registry entry). */
	private static <T> T fieldValue(Object entry, String name, Class<T> type)
			throws ReflectiveOperationException {
		Field field = entry.getClass().getSuperclass().getDeclaredField(name);
		field.setAccessible(true);
		return type.cast(field.get(entry));
	}

	/**
	 * Map every knob in a canonical config file to the section it was written under.
	 *
	 * <p>Textual on purpose: Gson is {@code testRuntimeOnly} on {@code common}, so the L1 suite has no
	 * JSON parser on its compile classpath. The canonical form is plain Gson pretty-print, which puts
	 * exactly one property per line — a section header sits at two spaces of indent and opens a brace,
	 * its fields at four. {@code _comment_*} lines are skipped; they are documentation, not knobs.
	 */
	private static Map<String, String> keyToSection(String canonical) {
		Map<String, String> out = new LinkedHashMap<>();
		String current = null;
		for (String rawLine : canonical.split("\n")) {
			String line = rawLine.replace("\r", "");
			String trimmed = line.strip();
			if (line.startsWith("  \"") && trimmed.endsWith(": {")) {
				current = nameOf(trimmed);
			} else if (line.startsWith("    \"") && current != null) {
				String key = nameOf(trimmed);
				if (!key.startsWith("_comment_")) {
					out.put(key, current);
				}
			}
		}
		return out;
	}

	/** {@code "someKey": 5,} → {@code someKey}. */
	private static String nameOf(String trimmedLine) {
		return trimmedLine.substring(1, trimmedLine.indexOf('"', 1));
	}
}
