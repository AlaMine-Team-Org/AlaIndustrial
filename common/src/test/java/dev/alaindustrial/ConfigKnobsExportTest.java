package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The machine-readable export of every {@link Knob} (MOD-700): key, section, type, compiled
 * default, bounds and doc string, one knob per line, committed as
 * {@code common/src/test/resources/dev/alaindustrial/config-knobs.json}.
 *
 * <p><b>Who reads it.</b> The Python gates and generators under {@code docs/tools/} (balance sync,
 * display_eu sync, the wiki dev-leak detector). Before this export each of them parsed
 * {@code Config.java} with its own regex, and a knob moved to another holder class would have made at
 * least one of them go quietly blind. The export is built from the annotation and from the canonical
 * file, never from source text, so it survives any reshaping of the declarations.
 *
 * <p><b>Derived, and refreshed only on purpose.</b> This test writes the fresh export to
 * {@link #FRESH} and fails when the committed copy differs; the committed copy is replaced by an
 * explicit command ({@code python docs/tools/lib/config_knobs_lib.py --accept}), never by the build, so a
 * changed knob is a visible diff in review rather than a silent regeneration.
 *
 * <p><b>Where the numbers come from.</b> The default is read from the {@code builtinDefaults} block of
 * the canonical file {@link Config#loadFrom} writes into an empty directory — the value compiled into
 * this build, rendered exactly as the operator's file renders it. The bounds come from the annotation.
 *
 * <p><b>Compared line by line, not byte by byte.</b> Gson is {@code testRuntimeOnly} on {@code common},
 * so there is no JSON parser on the compile classpath (see {@link ConfigSchemaTest}); the export is
 * therefore written in a fixed one-knob-per-line form, pure ASCII, and compared after line endings are
 * normalised — a checkout that turned LF into CRLF is the same content.
 */
class ConfigKnobsExportTest {

	/** Classpath location of the committed export. */
	private static final String COMMITTED = "dev/alaindustrial/config-knobs.json";

	/** Where the fresh export is written, relative to the {@code common} project directory. */
	private static final Path FRESH = Path.of("build", "config-knobs", "config-knobs.json");

	/** Format version of the export itself; bumped only when a consumer has to change with it. */
	private static final int SCHEMA_VERSION = 1;

	/**
	 * Every class that declares knobs, as the registry scans them (one source, MOD-710): a holder added
	 * to the registry is exported, and nothing on the Python side changes.
	 */
	private static final List<Class<?>> HOLDERS = Config.REGISTRY.holders();

	private static final String REFRESH = "refresh it with: ./gradlew :common:test --tests"
			+ " dev.alaindustrial.ConfigKnobsExportTest, then python docs/tools/lib/config_knobs_lib.py --accept";

	/**
	 * The central claim: the committed export is exactly what this build's knobs produce. The fresh
	 * export is written first, so a red run always leaves the file {@code --accept} needs.
	 */
	@Test
	void committedExportMatchesTheCompiledKnobs(@TempDir Path dir) throws IOException {
		List<String> fresh = render(entries(HOLDERS, canonicalDefaults(dir)));
		Files.createDirectories(FRESH.getParent());
		Files.writeString(FRESH, String.join("\n", fresh) + "\n", StandardCharsets.UTF_8);

		String committed = readCommitted();
		assertNotNull(committed, "the committed export " + COMMITTED + " is missing; " + REFRESH);
		String difference = firstDifference(committed, fresh);
		assertNull(difference, "config-knobs.json is stale: " + difference + "; " + REFRESH);
	}

	/**
	 * One entry per annotated field, one {@code builtinDefaults} key per entry, and no key twice. This
	 * replaces the old regex gate's "number of {@code @Knob(} markers == number of parsed declarations"
	 * guard: a knob the export missed would be a knob every Python gate silently stops seeing.
	 */
	@Test
	void exportHoldsEveryKnobExactlyOnce(@TempDir Path dir) throws IOException {
		String canonical = canonicalDefaults(dir);
		List<Entry> entries = entries(HOLDERS, canonical);

		int annotated = 0;
		for (Class<?> holder : HOLDERS) {
			annotated += knobFields(holder).size();
		}
		assertEquals(annotated, entries.size(), "one export entry per @Knob field");
		assertEquals(defaultsBlockSize(canonical), entries.size(),
				"one export entry per builtinDefaults key");
		Set<String> keys = new HashSet<>();
		for (Entry entry : entries) {
			assertTrue(keys.add(entry.key()), "knob key exported twice: " + entry.key());
		}
		assertTrue(entries.size() > 100, "sanity: the export should hold every tunable, got " + entries.size());
	}

	/**
	 * Negative control for {@link #committedExportMatchesTheCompiledKnobs}: one value edited by hand is
	 * reported, and reported at the line that changed. Without this the comparison could be a no-op and
	 * the suite would still be green.
	 */
	@Test
	void aHandEditedValueIsReported(@TempDir Path dir) throws IOException {
		List<String> fresh = render(entries(HOLDERS, canonicalDefaults(dir)));
		assertNull(firstDifference(String.join("\r\n", fresh) + "\r\n", fresh),
				"CRLF line endings are the same content");

		int line = fresh.size() / 2;
		List<String> edited = new ArrayList<>(fresh);
		edited.set(line, edited.get(line).replace("\"default\": ", "\"default\": 9"));
		String difference = firstDifference(String.join("\n", edited) + "\n", fresh);
		assertNotNull(difference, "an edited default must be reported");
		assertTrue(difference.startsWith("line " + (line + 1) + ":"), difference);
	}

	/**
	 * Negative control for {@link #exportHoldsEveryKnobExactlyOnce}: a knob whose default never reached
	 * the canonical file — a holder the registry does not scan — cannot be exported quietly.
	 */
	@Test
	void aKnobMissingFromTheDefaultsBlockFailsTheExport(@TempDir Path dir) throws IOException {
		String canonical = canonicalDefaults(dir);
		try {
			entries(List.of(Config.class, StrayHolder.class), canonical);
		} catch (AssertionError expected) {
			assertTrue(expected.getMessage().contains("strayKnob"), expected.getMessage());
			return;
		}
		fail("a @Knob with no builtinDefaults entry must fail the export");
	}

	/** Every character outside printable ASCII is escaped, the way Python's json module writes it. */
	@Test
	void jsonStringEscapesMatchPythonsAsciiForm() {
		assertEquals("\"a \\\"b\\\" \\\\ \\u2014 \\u00d7 \\n\"", json("a \"b\" \\ \u2014 \u00d7 \n"));
		assertFalse(json("\u00e9").contains("\u00e9"), "non-ASCII must be escaped");
	}

	// --- building the export ----------------------------------------------------------------------

	/** One exported knob. {@code min}/{@code floorTo} are {@code null} when the annotation leaves them out. */
	private record Entry(String key, String section, String type, String defaultText,
			Double min, boolean exclusive, Double floorTo, String doc) {
	}

	/**
	 * The export's entries in registry order (section, then key — the order {@code buildRegistry}
	 * sorts by), each carrying its default as written in {@code canonical}'s {@code builtinDefaults}.
	 */
	private static List<Entry> entries(List<Class<?>> holders, String canonical) {
		List<Entry> out = new ArrayList<>();
		for (Class<?> holder : holders) {
			for (Field field : knobFields(holder)) {
				Knob knob = field.getAnnotation(Knob.class);
				String key = field.getName();
				String defaultText = valueIn(canonical, "builtinDefaults", key);
				if (defaultText == null) {
					throw new AssertionError("@Knob field " + holder.getSimpleName() + "." + key
							+ " has no builtinDefaults entry in the canonical file");
				}
				out.add(new Entry(key, knob.section().id, field.getType().getName(), defaultText,
						Double.isInfinite(knob.min()) ? null : knob.min(), knob.exclusive(),
						Double.isNaN(knob.floorTo()) ? null : knob.floorTo(), knob.doc()));
			}
		}
		out.sort(Comparator.comparingInt((Entry e) -> sectionOrdinal(e.section()))
				.thenComparing(Entry::key));
		return out;
	}

	/** The fields of {@code holder} that carry {@link Knob}. */
	private static List<Field> knobFields(Class<?> holder) {
		List<Field> out = new ArrayList<>();
		for (Field field : holder.getDeclaredFields()) {
			if (field.getAnnotation(Knob.class) != null) {
				out.add(field);
			}
		}
		return out;
	}

	/** Declaration order of the section whose json id is {@code id}. */
	private static int sectionOrdinal(String id) {
		for (Section section : Section.values()) {
			if (section.id.equals(id)) {
				return section.ordinal();
			}
		}
		throw new AssertionError("unknown section id " + id);
	}

	/** The export as lines: a fixed header, one line per knob, a fixed footer. */
	private static List<String> render(List<Entry> entries) {
		List<String> lines = new ArrayList<>();
		lines.add("{");
		lines.add("  \"_comment\": " + json("Generated by ConfigKnobsExportTest (MOD-700) - do not edit by hand."
				+ " One entry per @Knob: key, section, type, compiled default, min/exclusive/floorTo, doc."
				+ " Refresh: run the test, then python docs/tools/lib/config_knobs_lib.py --accept.") + ",");
		lines.add("  \"schemaVersion\": " + SCHEMA_VERSION + ",");
		lines.add("  \"knobs\": [");
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			StringBuilder line = new StringBuilder("    {");
			line.append("\"key\": ").append(json(e.key()));
			line.append(", \"section\": ").append(json(e.section()));
			line.append(", \"type\": ").append(json(e.type()));
			line.append(", \"default\": ").append(e.defaultText());
			if (e.min() != null) {
				line.append(", \"min\": ").append(e.min());
			}
			line.append(", \"exclusive\": ").append(e.exclusive());
			if (e.floorTo() != null) {
				line.append(", \"floorTo\": ").append(e.floorTo());
			}
			line.append(", \"doc\": ").append(json(e.doc()));
			line.append(i + 1 < entries.size() ? "}," : "}");
			lines.add(line.toString());
		}
		lines.add("  ]");
		lines.add("}");
		return lines;
	}

	/**
	 * {@code s} as a JSON string literal in the form Python's {@code json.dumps(s)} produces: quote and
	 * backslash escaped, the five short control escapes, and every other character outside printable
	 * ASCII as a lowercase {@code \\uXXXX} (UTF-16 units, so a supplementary character becomes a pair).
	 */
	private static String json(String s) {
		StringBuilder out = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				case '\b' -> out.append("\\b");
				case '\f' -> out.append("\\f");
				default -> {
					if (c >= ' ' && c <= '~') {
						out.append(c);
					} else {
						out.append(String.format("\\u%04x", (int) c));
					}
				}
			}
		}
		return out.append('"').toString();
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * The canonical file {@link Config#loadFrom} writes for an absent path. Its {@code builtinDefaults}
	 * block holds the defaults compiled into this build whatever the live values are, which is why the
	 * export reads its numbers from there and not from the static fields.
	 */
	private static String canonicalDefaults(Path dir) throws IOException {
		Path file = dir.resolve("alaindustrial.json");
		assertEquals(Config.LoadResult.DEFAULTS_WRITTEN, Config.loadFrom(file));
		return Files.readString(file);
	}

	/** The committed export's text, or {@code null} when it is not on the test classpath. */
	private static String readCommitted() throws IOException {
		try (InputStream in = ConfigKnobsExportTest.class.getClassLoader().getResourceAsStream(COMMITTED)) {
			return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * The first line where {@code committed} and {@code fresh} disagree, as
	 * {@code "line N: committed <a> != fresh <b>"}, or {@code null} when they hold the same lines.
	 */
	private static String firstDifference(String committed, List<String> fresh) {
		List<String> lines = new ArrayList<>(List.of(committed.replace("\r\n", "\n").split("\n", -1)));
		if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
			lines.remove(lines.size() - 1);
		}
		int common = Math.min(lines.size(), fresh.size());
		for (int i = 0; i < common; i++) {
			if (!lines.get(i).equals(fresh.get(i))) {
				return "line " + (i + 1) + ": committed " + lines.get(i) + " != fresh " + fresh.get(i);
			}
		}
		if (lines.size() != fresh.size()) {
			return "line " + (common + 1) + ": committed has " + lines.size() + " lines, fresh has " + fresh.size();
		}
		return null;
	}

	/**
	 * The value written under {@code key} inside the top-level object {@code block}, as raw text, or
	 * {@code null} when that object has no such key. Same textual reading as {@link ConfigSchemaTest}:
	 * the canonical form is Gson pretty-print, one property per line, blocks at two spaces of indent and
	 * their members at four.
	 */
	private static String valueIn(String canonical, String block, String key) {
		boolean inBlock = false;
		for (String rawLine : canonical.split("\n")) {
			String line = rawLine.replace("\r", "");
			String trimmed = line.strip();
			if (line.startsWith("  \"") && trimmed.endsWith(": {")) {
				inBlock = block.equals(nameOf(trimmed));
			} else if (inBlock && line.startsWith("    \"") && nameOf(trimmed).equals(key)) {
				String value = trimmed.substring(trimmed.indexOf(':') + 1).strip();
				return value.endsWith(",") ? value.substring(0, value.length() - 1) : value;
			}
		}
		return null;
	}

	/** Number of members of the top-level {@code builtinDefaults} object. */
	private static int defaultsBlockSize(String canonical) {
		int n = 0;
		boolean inBlock = false;
		for (String rawLine : canonical.split("\n")) {
			String line = rawLine.replace("\r", "");
			String trimmed = line.strip();
			if (line.startsWith("  \"") && trimmed.endsWith(": {")) {
				inBlock = "builtinDefaults".equals(nameOf(trimmed));
			} else if (inBlock && line.startsWith("    \"")) {
				n++;
			}
		}
		return n;
	}

	/** {@code "someKey": 5,} → {@code someKey}. */
	private static String nameOf(String trimmedLine) {
		return trimmedLine.substring(1, trimmedLine.indexOf('"', 1));
	}

	/** A holder the registry never scans: its knob has no {@code builtinDefaults} entry. */
	static final class StrayHolder {
		@Knob(section = Section.GLOBAL, min = 0, doc = "Test-only knob outside the registry.")
		public static int strayKnob = 1;

		private StrayHolder() {
		}
	}
}
