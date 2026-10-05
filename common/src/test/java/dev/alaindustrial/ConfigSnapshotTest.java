package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.config.ConfigSchema;
import dev.alaindustrial.config.KnobEntry;
import dev.alaindustrial.config.Section;
import dev.alaindustrial.core.environment.GeneratorConfig;
import java.io.IOException;
import java.lang.reflect.Field;
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
 * L1 unit tests for {@link Config}'s registry-driven snapshot/load round-trip.
 *
 * <p><b>What this suite is for.</b> {@link ConfigFileTest} drives {@link Config#loadFrom(Path)}
 * end-to-end on a handful of named knobs, and {@link ConfigSchemaTest} covers the file's shape. This
 * suite is the only one that asserts something about <em>every</em> knob at once: that each one is
 * written to the file AND read back from it. A knob that falls out of either half is invisible
 * otherwise — no compiler error, no exception, just a server whose setting silently does nothing.
 *
 * <p><b>Why the sentinels are generated (MOD-553).</b> They used to be a hand-written map of 371
 * literals, kept in step with {@code Config} by two guard tests, and it still fell 21 keys behind
 * once. That map existed to kill PIT mutants on the per-field getter lambdas ({@code () -> field}),
 * and those lambdas are gone: the registry now reaches its fields reflectively, so there is no
 * per-knob bytecode left to mutate and no per-knob literal left to maintain. What remains is the
 * round trip, and a generated sentinel exercises it exactly as well as a typed one — provided it is
 * distinct per knob and distinct from the knob's own default, which {@link #sentinelFor} guarantees
 * by construction.
 *
 * <p>The sentinels are the assertion oracle and are never re-read from production code: they are
 * computed once, held in a map, and compared against what comes back out of the file.
 *
 * <p>{@code loadFrom} mutates {@link Config}'s static fields, so the pristine defaults are captured
 * once — both into a baseline file and into {@link #DEFAULTS} — and restored after every test (same
 * idiom as {@link ConfigFileTest}).
 */
class ConfigSnapshotTest {

	@TempDir
	static Path sharedDir;
	/** Snapshot of the pristine compiled defaults, written before any test mutates the static fields. */
	static Path baseline;

	/**
	 * The value compiled into this build for every registered knob, read straight off the static
	 * fields while they are still pristine.
	 *
	 * <p>This is the suite's own record, not the registry's captured fallback: it is what
	 * {@link #resetToDefaults()} restores mid-test, so a round trip that quietly failed to load
	 * anything is caught against a value this class holds rather than one production hands back.
	 */
	private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

	/** Every registered knob's key, in registry order. */
	private static final List<String> KEYS = new ArrayList<>();

	/** The declared field behind each key, as the registry resolved it. */
	private static final Map<String, Field> FIELDS = new LinkedHashMap<>();

	/** Per-knob sentinel: distinct from every other knob's, and from the knob's own default. */
	private static final Map<String, Object> SENTINELS = new LinkedHashMap<>();

	@BeforeAll
	static void captureDefaults() throws ReflectiveOperationException {
		baseline = sharedDir.resolve("baseline.json");
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(baseline));

		int index = 0;
		for (KnobEntry entry : Config.REGISTRY.entries()) {
			String key = entry.key();
			KEYS.add(key);
			FIELDS.put(key, entry.field());
			DEFAULTS.put(key, field(key).get(null));
			SENTINELS.put(key, sentinelFor(key, entry, index++));
		}
		assertTrue(KEYS.size() > 100, "sanity: the registry should hold every tunable, got " + KEYS.size());
	}

	@AfterEach
	void restoreDefaults() {
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(baseline));
	}

	// --- the round trip ---------------------------------------------------------------------------

	/**
	 * The suite's central claim: set every knob to its sentinel, let the mod write the canonical file,
	 * wipe the live values back to the compiled defaults, load the file — and every knob is its
	 * sentinel again.
	 *
	 * <p>Both halves have to work for this to pass. A knob missing from the registry is never written,
	 * so after the wipe it stays at its default and fails here; a knob written but never staged fails
	 * the same way. The wipe is what makes it a real test rather than a no-op: without it the fields
	 * would still hold their sentinels from before the load, and a completely dead load path would
	 * pass.
	 *
	 * <p>Booleans are forced to the opposite of their compiled default so their half of the round trip
	 * is not vacuous; the companion test below forces the other polarity, so every boolean knob is
	 * exercised non-vacuously by one of the two.
	 */
	@Test
	void everyKnobRoundTripsThroughTheFile(@TempDir Path dir) throws Exception {
		assertRoundTrip(dir, sentinelsWithBooleans(true));
	}

	/** @see #everyKnobRoundTripsThroughTheFile — same round trip, opposite boolean polarity. */
	@Test
	void everyKnobRoundTripsThroughTheFile_oppositeBooleanPolarity(@TempDir Path dir) throws Exception {
		assertRoundTrip(dir, sentinelsWithBooleans(false));
	}

	/**
	 * The write half on its own, asserted textually: after setting every knob to its sentinel and
	 * letting the mod canonicalize the file, each knob's line must read {@code "key": <sentinel>} in
	 * exactly the form its primitive type renders in.
	 *
	 * <p>The round trip above would survive an int that started serializing as {@code 7.0}, because
	 * Gson reads it back as 7. This one would not — the numeric FORM of the file is part of its
	 * contract with the operator who edits it by hand.
	 */
	@Test
	void snapshotWritesEveryKnobsLiveValueInItsOwnNumericForm(@TempDir Path dir) throws Exception {
		Map<String, Object> desired = sentinelsWithBooleans(true);

		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, sentinelFile(desired));
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "the sentinel file loads and is canonicalized");

		String canonical = Files.readString(f);
		List<String> missing = new ArrayList<>();
		for (String key : KEYS) {
			String line = "\"" + key + "\": " + literal(desired.get(key));
			if (!canonical.contains(line)) {
				missing.add(line);
			}
		}
		assertTrue(missing.isEmpty(), "knobs the canonical file does not carry in their own form: " + missing);
	}

	/**
	 * An absent key takes the knob's builtin default — not its current live value, and not 0/false
	 * (MOD-694, owner decision D8 = 3b). A hand-trimmed file stays legal and a newly added knob stays
	 * harmless on an old file, while the effective balance never depends on earlier reloads.
	 */
	@Test
	void absentKeysTakeTheBuiltinDefault(@TempDir Path dir) throws Exception {
		applyAll(sentinelsWithBooleans(true));

		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{}");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertAll(DEFAULTS, "absent key '%s' must take its builtin default, not the live value or 0/false");
	}

	// --- the registry's own shape -----------------------------------------------------------------

	/**
	 * The registry is ordered by section (enum order) and then by key name — and that has to be
	 * asserted, because the alternative is invisible.
	 *
	 * <p>{@code buildRegistry()} walks {@code getDeclaredFields()}, whose order the JLS does not
	 * specify. Sorting is what turns "the order HotSpot happens to return today" into a property of the
	 * declared data, so the canonical file's key order is the same on every JVM. If this ever fails,
	 * the sort was dropped or weakened, and the symptom on a real server would be a config file
	 * rewritten with the same values in a different sequence on some machines and not others.
	 */
	@Test
	void registryOrderIsSortedBySectionThenKey() throws ReflectiveOperationException {
		List<String> rendered = new ArrayList<>();
		for (KnobEntry entry : Config.REGISTRY.entries()) {
			Section section = entry.section();
			// Zero-padded: a bare ordinal would sort "10 x" before "2 y" and fail on the eleventh
			// section rather than on a real ordering defect.
			rendered.add(String.format("%02d %s", section.ordinal(), entry.key()));
		}

		List<String> sorted = new ArrayList<>(rendered);
		sorted.sort(null);
		assertEquals(sorted, rendered,
				"the registry must be sorted by section then key; an order taken from getDeclaredFields()"
						+ " is a property of the JVM, not of this class");
	}

	/**
	 * Every knob's key is its java field's own name. The registry derives one from the other, so this
	 * cannot drift by editing — but it can by refactoring, and a renamed field silently retiring an
	 * operator's key is exactly the kind of quiet break the config file must not have.
	 */
	@Test
	void everyRegistryKeyNamesARealField() throws ReflectiveOperationException {
		for (String key : KEYS) {
			assertEquals(key, field(key).getName(), "registry key '" + key + "' must be its field's own name");
			assertTrue(field(key).getType().isPrimitive(),
					"registry key '" + key + "' must name a primitive static field");
		}
	}

	// --- narrow reader guards (hand-written literals) ----------------------------------------------

	/**
	 * A present boolean key applies the file's value in both polarities. Hand-written literals, and
	 * deliberately the polarity opposite to the compiled default in each half, so a reader that
	 * returned a constant would be caught either way.
	 */
	@Test
	void presentBooleanKey_appliesTheFilesValue(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"bonusChestEnabled\": false, \"oilBurns\": false }");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(false, Config.bonusChestEnabled, "present boolean key applies false, not the default true");
		assertEquals(false, Config.oilBurns, "present boolean key applies false, not the default true");
	}

	@Test
	void presentBooleanKey_appliesTrueOverridingFalseDefault(@TempDir Path dir) throws IOException {
		Config.bonusChestEnabled = false;
		Config.oilBurns = false;
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"bonusChestEnabled\": true, \"oilBurns\": true }");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(true, Config.bonusChestEnabled, "present boolean key applies true over a false default");
		assertEquals(true, Config.oilBurns, "present boolean key applies true over a false default");
	}

	/**
	 * windMillRainFactor default is 1.5f; 2.25f is a hand-written literal distinct from both the
	 * default and from 0.0f, so a reader returning either constant is caught.
	 */
	@Test
	void presentFloatKey_appliesTheFilesValue(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, "{ \"windMillRainFactor\": 2.25 }");
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f));
		assertEquals(2.25f, GeneratorConfig.windMillRainFactor, 0.0f,
				"present float key applies 2.25, not 0.0f and not the 1.5f default");
	}

	/**
	 * Config.reload() delegates to loadFrom(configPath.get()); bind the supplier to the baseline and
	 * confirm reload returns LOADED.
	 */
	@Test
	void reload_readsTheBoundConfigPath() {
		java.util.function.Supplier<Path> saved = Config.configPath;
		try {
			Config.configPath = () -> baseline;
			assertEquals(Config.LoadResult.LOADED, Config.reload(),
					"reload() reads the loader-bound configPath and reports its load result");
		} finally {
			Config.configPath = saved;
		}
	}

	// --- helpers ----------------------------------------------------------------------------------

	/**
	 * Set every knob to {@code desired}, write the canonical file, wipe the live values back to the
	 * compiled defaults, load the file back, and assert every knob is {@code desired} again.
	 */
	private static void assertRoundTrip(Path dir, Map<String, Object> desired) throws Exception {
		// Since MOD-694 a load starts from the builtin defaults, so the file itself has to carry the
		// sentinels: a bare "{}" would (correctly) reset every knob instead of snapshotting the live ones.
		Path f = dir.resolve("alaindustrial.json");
		Files.writeString(f, sentinelFile(desired));
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "canonicalizing write");
		assertAll(desired, "knob '%s' was not read from the sentinel file");

		// The wipe: without it the fields would still hold `desired` and a dead load path would pass.
		applyAll(DEFAULTS);
		assertEquals(Config.LoadResult.LOADED, Config.loadFrom(f), "read the canonical file back");

		assertAll(desired, "knob '%s' did not survive the save/load round trip — it is missing from the"
				+ " registry, or written but never staged");
	}

	/** Every knob's sentinel, with the booleans forced to {@code polarity}. */
	private static Map<String, Object> sentinelsWithBooleans(boolean polarity)
			throws ReflectiveOperationException {
		Map<String, Object> out = new LinkedHashMap<>();
		for (String key : KEYS) {
			out.put(key, field(key).getType() == boolean.class ? polarity : SENTINELS.get(key));
		}
		return out;
	}

	/**
	 * A schema-current file holding every knob at {@code values}, each key inside its own section
	 * object; no {@code builtinDefaults} block, so every value counts as an operator edit and is kept.
	 */
	private static String sentinelFile(Map<String, Object> values) {
		// Assembled by hand: Gson is testRuntimeOnly for :common, so the test source cannot import it.
		Map<String, StringBuilder> bodies = new LinkedHashMap<>();
		for (Section section : Section.values()) {
			bodies.put(section.id, new StringBuilder());
		}
		for (KnobEntry entry : Config.REGISTRY.entries()) {
			StringBuilder body = bodies.get(entry.section().id);
			if (!body.isEmpty()) {
				body.append(",\n");
			}
			body.append("    \"").append(entry.key()).append("\": ").append(literal(values.get(entry.key())));
		}
		StringBuilder file = new StringBuilder("{\n  \"schemaVersion\": ").append(ConfigSchema.VERSION);
		for (Map.Entry<String, StringBuilder> e : bodies.entrySet()) {
			file.append(",\n  \"").append(e.getKey()).append("\": {\n").append(e.getValue()).append("\n  }");
		}
		return file.append("\n}\n").toString();
	}

	/** Assign every value in {@code values} to its static field. */
	private static void applyAll(Map<String, Object> values) throws ReflectiveOperationException {
		for (Map.Entry<String, Object> e : values.entrySet()) {
			field(e.getKey()).set(null, e.getValue());
		}
	}

	/** Assert every static field holds the value {@code expected} recorded for it. */
	private static void assertAll(Map<String, Object> expected, String messageFormat)
			throws ReflectiveOperationException {
		for (Map.Entry<String, Object> e : expected.entrySet()) {
			assertEquals(e.getValue(), field(e.getKey()).get(null),
					String.format(messageFormat, e.getKey()));
		}
	}

	/**
	 * A sentinel for one knob: far above every default and every declared minimum, spaced so no two
	 * knobs can share one, and never equal to the knob's own compiled default.
	 *
	 * <p>The spacing is what makes a cross-wired accessor visible: if the entry for knob A read or
	 * wrote knob B's field, the round trip would hand back B's sentinel and the comparison would fail.
	 * The collision nudge is deliberately half a step (int {@code +1} on a stride of 3, float/double
	 * {@code +0.25} on a stride of 0.5), so a nudged sentinel can never land on another knob's.
	 *
	 * <p>A sentinel below the knob's declared minimum would be clamped on load and the test would fail
	 * for the wrong reason, so the bound is read from the registry and the sentinel lifted above it.
	 * Today nothing comes close (the largest minimum in the file is 97) — the lift is there so a future
	 * knob with a big floor does not turn this suite red for a reason that is not a defect.
	 */
	private static Object sentinelFor(String key, KnobEntry entry, int index)
			throws ReflectiveOperationException {
		Class<?> type = field(key).getType();
		if (type == boolean.class) {
			return null; // booleans get a polarity, not a magnitude — see sentinelsWithBooleans
		}
		double min = entry.min();
		double floor = Double.isInfinite(min) ? 0.0 : min;
		Object compiled = DEFAULTS.get(key);

		if (type == int.class) {
			long candidate = (long) Math.max(1_000_000L, Math.ceil(floor) + 1_000_000L) + 3L * index;
			if (candidate == ((Integer) compiled).intValue()) {
				candidate += 1;
			}
			assertNotEquals(compiled, (int) candidate, "sentinel for " + key + " must differ from its default");
			return (int) candidate;
		}
		if (type == float.class) {
			float candidate = (float) (Math.max(1000.0, floor + 1000.0) + 0.5 * index);
			if (Float.compare(candidate, ((Float) compiled).floatValue()) == 0) {
				candidate += 0.25f;
			}
			return candidate;
		}
		if (type == double.class) {
			double candidate = Math.max(1000.0, floor + 1000.0) + 0.5 * index;
			if (Double.compare(candidate, ((Double) compiled).doubleValue()) == 0) {
				candidate += 0.25;
			}
			return candidate;
		}
		throw new AssertionError("unexpected tunable type for " + key + ": " + type);
	}

	/**
	 * Render a value the way Gson serializes it: ints bare, floats/doubles by {@code toString},
	 * booleans as {@code true}/{@code false}. Used only to build the substring the canonical file must
	 * contain — never re-reads a live field.
	 */
	private static String literal(Object v) {
		return v.toString();
	}

	/** The declared field behind {@code key}, as the registry resolved it. */
	private static Field field(String key) {
		Field field = FIELDS.get(key);
		if (field == null) {
			throw new AssertionError("no registry entry for " + key);
		}
		return field;
	}
}
