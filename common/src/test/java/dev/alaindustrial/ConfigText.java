package dev.alaindustrial;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Textual helpers shared by the config characterization suites (MOD-710, batch 0).
 *
 * <p>Gson is {@code testRuntimeOnly} on {@code common}, so the L1 suites have no JSON parser on their
 * compile classpath (see {@link ConfigSchemaTest}). The canonical file is plain Gson pretty-print, one
 * property per line: top-level members at two spaces of indent, section members at four. Every helper
 * here relies on exactly that shape, and {@link ConfigGoldenFileTest} is what pins the shape.
 */
final class ConfigText {

	/** Classpath directory holding the golden file and the migration fixtures. */
	static final String DIR = "dev/alaindustrial/config/";

	/** Classpath location of the characterization baseline of a fresh install's file. */
	static final String GOLDEN = DIR + "defaults.golden.json";

	private ConfigText() {
	}

	/** The classpath resource {@code name} as UTF-8 text; fails loudly when it is missing. */
	static String resource(String name) {
		try (InputStream in = ConfigText.class.getClassLoader().getResourceAsStream(name)) {
			if (in == null) {
				throw new AssertionError("test resource " + name + " is missing from the classpath");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The committed golden file, as text. */
	static String golden() {
		return resource(GOLDEN);
	}

	/** Copy the classpath resource {@code name} to {@code target} byte for byte and return the target. */
	static Path copy(String name, Path target) throws IOException {
		Files.writeString(target, resource(name), StandardCharsets.UTF_8);
		return target;
	}

	/** The file at {@code path} as UTF-8 text. */
	static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	/**
	 * The first line where {@code expected} and {@code actual} disagree, as
	 * {@code "line N: expected <a> != actual <b>"}, or {@code null} when both hold exactly the same
	 * characters. Byte-level differences a line split cannot see — a trailing newline, a CR — are
	 * reported too, so {@code null} means identical text.
	 */
	static String firstDifference(String expected, String actual) {
		if (expected.equals(actual)) {
			return null;
		}
		String[] a = expected.split("\n", -1);
		String[] b = actual.split("\n", -1);
		int common = Math.min(a.length, b.length);
		for (int i = 0; i < common; i++) {
			if (!a[i].equals(b[i])) {
				return "line " + (i + 1) + ": expected " + a[i] + " != actual " + b[i];
			}
		}
		return "line " + (common + 1) + ": expected " + a.length + " line(s), actual " + b.length;
	}

	/**
	 * {@code text} with the value of {@code key} inside the top-level object {@code block} replaced by
	 * the raw json {@code value} (a number, {@code true}/{@code false}). Fails when the key is absent,
	 * so a fixture expectation can never silently describe a key that is not there.
	 */
	static String withValue(String text, String block, String key, String value) {
		List<String> lines = lines(text);
		int at = memberLine(lines, block, key);
		String line = lines.get(at);
		boolean comma = line.endsWith(",");
		lines.set(at, "    \"" + key + "\": " + value + (comma ? "," : ""));
		return String.join("\n", lines);
	}

	/**
	 * {@code text} without the member {@code key} of section {@code block} and without its
	 * {@code _comment_} line — what an operator gets by deleting the knob from their file. Only a
	 * member that is not the last of its section is accepted, so the result stays strict JSON.
	 */
	static String withoutKey(String text, String block, String key) {
		List<String> lines = lines(text);
		int at = memberLine(lines, block, key);
		if (!lines.get(at).endsWith(",")) {
			throw new AssertionError(key + " is the last member of " + block + "; pick another key");
		}
		lines.remove(at);
		int comment = memberLine(lines, block, "_comment_" + key);
		lines.remove(comment);
		return String.join("\n", lines);
	}

	/** {@code text} without the top-level section {@code block} and its {@code _comment_} line. */
	static String withoutSection(String text, String block) {
		List<String> lines = lines(text);
		List<String> out = new ArrayList<>();
		boolean skipping = false;
		boolean found = false;
		for (String line : lines) {
			if (skipping) {
				if (line.startsWith("  }")) {
					skipping = false;
				}
				continue;
			}
			if (line.startsWith("  \"_comment_" + block + "\": ")) {
				continue;
			}
			if (line.equals("  \"" + block + "\": {")) {
				skipping = true;
				found = true;
				continue;
			}
			out.add(line);
		}
		if (!found) {
			throw new AssertionError("no section " + block + " in the text");
		}
		return String.join("\n", out);
	}

	/**
	 * The raw json value of {@code key} inside the top-level object {@code block}, or {@code null} when
	 * that object has no such member.
	 */
	static String valueIn(String text, String block, String key) {
		List<String> lines = lines(text);
		int at = find(lines, block, key);
		if (at < 0) {
			return null;
		}
		String trimmed = lines.get(at).strip();
		String value = trimmed.substring(trimmed.indexOf("\": ") + 3);
		return value.endsWith(",") ? value.substring(0, value.length() - 1) : value;
	}

	/** Every member name of the top-level object {@code block}, {@code _comment_*} lines excluded. */
	static List<String> keysOf(String text, String block) {
		List<String> out = new ArrayList<>();
		boolean inBlock = false;
		for (String line : lines(text)) {
			if (line.startsWith("  \"") && line.endsWith(": {")) {
				inBlock = line.equals("  \"" + block + "\": {");
			} else if (line.startsWith("  }")) {
				inBlock = false;
			} else if (inBlock && line.startsWith("    \"")) {
				String name = line.substring(5, line.indexOf('"', 5));
				if (!name.startsWith("_comment_")) {
					out.add(name);
				}
			}
		}
		return out;
	}

	private static int memberLine(List<String> lines, String block, String key) {
		int at = find(lines, block, key);
		if (at < 0) {
			throw new AssertionError("no member " + key + " in " + block);
		}
		return at;
	}

	private static int find(List<String> lines, String block, String key) {
		boolean inBlock = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.startsWith("  \"") && line.endsWith(": {")) {
				inBlock = line.equals("  \"" + block + "\": {");
			} else if (line.startsWith("  }")) {
				inBlock = false;
			} else if (inBlock && line.startsWith("    \"" + key + "\": ")) {
				return i;
			}
		}
		return -1;
	}

	private static List<String> lines(String text) {
		return new ArrayList<>(List.of(text.split("\n", -1)));
	}
}
