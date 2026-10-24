package dev.alaindustrial.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * L1: every line the settlement core's screen can show, in every language of the mod, stays inside the frame
 * (MOD-842). The playtest found the Russian hint crossing the right border and the status rows only just inside;
 * this measures them all with the vanilla default font's own advances ({@code font-advances.json}, written by
 * {@code python tools/gen_gui_font_advances.py} from the 26.3 jar and asset index) and fits them with the very
 * {@link TextFit} the screen draws with.
 *
 * <p>Numbers fill their {@code %s} wider than any shipped value (territory and limits three digits, upkeep four) and
 * the owner's name is sixteen of the widest letter a Minecraft name may hold.
 *
 * @implements MOD-842-TEXT-FITS
 */
class SettlementCoreLayoutTest {

	private static final String TABLE = "dev/alaindustrial/client/font-advances.json";
	private static final String EN_US = "assets/alaindustrial/lang/en_us.json";

	private static final Map<Integer, Integer> ADVANCES = loadAdvances();

	@Test
	void everyLineOfEveryLanguageFitsTheFrame() throws IOException, URISyntaxException {
		List<String> failures = new ArrayList<>();
		String name = widestName();
		List<Path> files = langFiles();
		assertTrue(files.size() >= 20, "expected every language file of the mod, found " + files.size());
		for (Path file : files) {
			Map<String, String> lang = read(file);
			String code = file.getFileName().toString();
			for (String key : SettlementCoreLayout.STATUS_KEYS) {
				check(failures, code, key, text(lang, key), SettlementCoreLayout.columnWidth(),
						SettlementCoreLayout.STATUS_MAX_LINES);
			}
			for (String key : SettlementCoreLayout.NUMBER_ROWS) {
				String number = SettlementCoreLayout.UPKEEP.equals(key) ? "9999" : "999";
				// The territory row carries its width and its depth (MOD-848); a pattern takes the arguments it names.
				check(failures, code, key, text(lang, key, number, number), SettlementCoreLayout.columnWidth(),
						SettlementCoreLayout.ROW_MAX_LINES);
			}
			for (String key : SettlementCoreLayout.OWNER_KEYS) {
				check(failures, code, key, text(lang, key, name), SettlementCoreLayout.columnWidth(),
						SettlementCoreLayout.ROW_MAX_LINES);
			}
			check(failures, code, SettlementCoreLayout.HINT, text(lang, SettlementCoreLayout.HINT),
					SettlementCoreLayout.hintWidth(), SettlementCoreLayout.HINT_MAX_LINES);
			// MOD-857, round 3 of 0.1.219 (R3-3, R3-6): the «Construction» tab's names, chips, headings and hints.
			for (String key : SettlementCoreLayout.WORKS_NAMES) {
				check(failures, code, key, text(lang, key), SettlementCoreLayout.worksNameWidth(), 1);
			}
			for (String key : SettlementCoreLayout.WORKS_CHIPS) {
				check(failures, code, key, text(lang, key), SettlementCoreLayout.WORKS_CHIP_MAX - 6, 1);
			}
			for (String key : SettlementCoreLayout.WORKS_SECTIONS) {
				check(failures, code, key, text(lang, key), SettlementCoreLayout.worksCardWidth() - 2, 1);
			}
			for (String key : HINTS) {
				// A building's name in the hints that name one; the materials hint lists two counted items.
				check(failures, code, key, text(lang, key, "Capsule house (type 4)"),
						SettlementCoreLayout.worksHintWidth(), SettlementCoreLayout.WORKS_HINT_MAX_LINES);
			}
			check(failures, code, SettlementCoreLayout.WORKS_CARD_STAGE,
					text(lang, SettlementCoreLayout.WORKS_CARD_STAGE, "5", "5", "Windows and doors") + "  999/999",
					SettlementCoreLayout.worksOpenWidth(), 1);
			check(failures, code, SettlementCoreLayout.WORKS_CARD_STAGES_DONE,
					text(lang, SettlementCoreLayout.WORKS_CARD_STAGES_DONE), SettlementCoreLayout.worksOpenWidth(), 1);
			String heading = text(lang, "block.alaindustrial.settlement_core");
			if (width(heading) > SettlementCoreLayout.IMAGE_WIDTH - 2 * (SettlementCoreLayout.BORDER + 2)) {
				failures.add(code + " heading: " + width(heading) + " px");
			}
		}
		assertEquals(List.of(), failures, "lines that would cross the settlement core's frame");
	}

	/** The worst case stacks inside the frame: a two-line status, five rows, then a three-line hint. */
	@Test
	void theTallestReadoutStaysInsideThePanel() {
		int afterRows = SettlementCoreLayout.firstNumberRowY(SettlementCoreLayout.STATUS_MAX_LINES)
				+ (SettlementCoreLayout.NUMBER_ROWS.size() + 1) * SettlementCoreLayout.ROW_STEP;
		int hintTop = SettlementCoreLayout.hintTop(afterRows);
		int bottom = hintTop + (SettlementCoreLayout.HINT_MAX_LINES - 1) * SettlementCoreLayout.LINE_STEP + 9;
		assertTrue(bottom <= SettlementCoreLayout.IMAGE_HEIGHT - SettlementCoreLayout.BORDER,
				"hint bottom " + bottom + " crosses the frame of height " + SettlementCoreLayout.IMAGE_HEIGHT);
		int lastRowBottom = afterRows - SettlementCoreLayout.ROW_STEP + 9;
		assertTrue(lastRowBottom <= hintTop, "the owner row runs into the hint");
		assertTrue(SettlementCoreLayout.BUTTON_Y + ZONE_BUTTON_SIZE <= hintTop, "the button runs into the hint");
		assertTrue(SettlementCoreLayout.BUTTON_X + ZONE_BUTTON_SIZE <= SettlementCoreLayout.TEXT_LEFT,
				"the button runs into the rows");
	}

	/**
	 * A «Construction» card's parts stay inside the panel and the window inside a 1280 x 720 screen at interface scale
	 * 3 (owner's round 2 of 0.1.219, R2-2; round 3, R3-3): the button right of the name, the scroll bar right of the
	 * cards, the pane under the hint bar and above the bottom border.
	 */
	@Test
	void everyPartOfAConstructionCardStaysInsideThePanel() {
		assertTrue(SettlementCoreLayout.WORKS_CONTENT_RIGHT + SettlementCoreLayout.WORKS_SCROLLBAR
				< SettlementCoreLayout.ROSTER_RIGHT, "the scroll bar runs past the panel");
		assertTrue(SettlementCoreLayout.worksNameWidth() >= 120, "a card's name has no room");
		assertTrue(SettlementCoreLayout.WORKS_CARD_TEXT + SettlementCoreLayout.WORKS_CHIP_MAX
				<= SettlementCoreLayout.worksCardWidth() - SettlementCoreLayout.WORKS_BUTTON - 6,
				"a chip runs into the button");
		assertTrue(SettlementCoreLayout.WORKS_ICON_BOX + 2 <= SettlementCoreLayout.WORKS_CARD_TEXT,
				"the picture runs into the name");
		assertTrue(SettlementCoreLayout.WORKS_VIEW_TOP < SettlementCoreLayout.WORKS_VIEW_BOTTOM
				&& SettlementCoreLayout.WORKS_VIEW_BOTTOM <= SettlementCoreLayout.IMAGE_HEIGHT
						- SettlementCoreLayout.BORDER, "the pane runs past the frame");
		assertTrue(SettlementCoreLayout.WORKS_PLAN_LABEL + 4 * (SettlementCoreLayout.WORKS_SLOT + 2) + 40
				<= SettlementCoreLayout.worksCardWidth(), "the plan slots run past the card");
		assertTrue(SettlementCoreLayout.IMAGE_WIDTH <= 426 && SettlementCoreLayout.IMAGE_HEIGHT <= 240,
				"the window must fit 426 x 240");
		assertTrue(SettlementCoreLayout.ENERGY_SPRITE_V >= SettlementCoreLayout.IMAGE_HEIGHT
				&& SettlementCoreLayout.ENERGY_SPRITE_V + 44 <= 256, "the energy sprite must sit under the panel");
	}

	/**
	 * The pictures the cards draw are the ones the icon generator lays out, in its order: a building added to one list
	 * and not the other would draw another building's picture. The generator lives in {@code tools/}, which the public
	 * mirror does not carry, so there the check is skipped.
	 */
	@Test
	void theBuildingPicturesMatchTheGenerator() throws IOException {
		Path generator = repoRoot().resolve("tools/gen_town_building_icons.py");
		assumeTrue(Files.isRegularFile(generator), "no tools/ in this tree (the public mirror)");
		String source = Files.readString(generator, StandardCharsets.UTF_8);
		List<String> styles = quoted(between(source, "for style in (", ")"));
		List<String> names = quoted(between(source, "for name in (", ")"));
		List<String> expected = new ArrayList<>();
		for (String style : styles) {
			for (String name : names) {
				expected.add(style + "/" + name);
			}
		}
		assertEquals(expected, ICONS, "TownBuildingIcons.ICONS and tools/gen_town_building_icons.py ICONS differ");
	}

	/**
	 * {@code TownBuildingIcons.ICONS}: that class touches Minecraft and cannot load on this lane, so its list is
	 * repeated here and read off its source.
	 */
	private static final List<String> ICONS = iconsFromSource();

	private static List<String> iconsFromSource() {
		try {
			String source = Files.readString(repoRoot().resolve(
					"common/src/main/java/dev/alaindustrial/client/screen/works/TownBuildingIcons.java"),
					StandardCharsets.UTF_8);
			return quoted(between(source, "ICONS = List.of(", ");"));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String between(String source, String from, String to) {
		int start = source.indexOf(from);
		assertTrue(start >= 0, "no " + from);
		start += from.length();
		return source.substring(start, source.indexOf(to, start));
	}

	private static List<String> quoted(String text) {
		List<String> out = new ArrayList<>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(text);
		while (m.find()) {
			out.add(m.group(1));
		}
		return out;
	}

	/** The repository's root: the working directory of a Gradle test task is its project's, {@code common}. */
	private static Path repoRoot() {
		Path here = Path.of("").toAbsolutePath();
		return Files.isDirectory(here.resolve("tools")) ? here : here.getParent();
	}

	/** {@code NextStepHint.KEYS}: that class touches Minecraft and cannot load on this lane. */
	private static final List<String> HINTS = List.of("sheets", "dispatcher", "assemble", "nest", "order_hall",
			"materials", "stopped", "building", "order_house", "repair", "house_plans", "limit", "done").stream()
			.map(name -> "settlement.alaindustrial.works.next." + name).toList();

	/** {@code ZonePreviewButton.SIZE}; that class touches Minecraft and cannot load on this lane. */
	private static final int ZONE_BUTTON_SIZE = 22;

	/** The fitter itself: wraps at spaces, keeps a combining mark on its letter, reports what does not fit. */
	@Test
	void fitWrapsAtSpacesThenScales() {
		TextFit.Fit one = TextFit.fit("aa bb", 100, 1, String::length);
		assertEquals(List.of("aa bb"), one.lines());
		assertEquals(1.0f, one.scale());

		TextFit.Fit wrapped = TextFit.fit("aaa bbb ccc", 7, 2, String::length);
		assertEquals(List.of("aaa bbb", "ccc"), wrapped.lines());
		assertTrue(wrapped.fits());

		TextFit.Fit scaled = TextFit.fit("aaaaaaaaa", 7, 1, String::length);
		assertEquals(0.7f, scaled.scale());
		assertTrue(scaled.fits(), "9 at 0.7 is 6.3, inside 7");

		TextFit.Fit tooLong = TextFit.fit("aaaaaaaaaaaaaaa", 7, 1, String::length);
		assertFalse(tooLong.fits());

		// A word wider than a line is cut, but never in front of a combining mark (Thai, Hindi, Bengali).
		String thai = "กิกิกิ";
		List<String> cut = TextFit.wrap(thai, 3, String::length);
		for (String line : cut) {
			assertFalse(Character.getType(line.codePointAt(0)) == Character.NON_SPACING_MARK,
					"line starts with a combining mark: " + cut);
		}
		assertEquals(thai, String.join("", cut));
	}

	private static void check(List<String> failures, String code, String key, String text, int width, int maxLines) {
		TextFit.Fit fit = TextFit.fit(text, width, maxLines, SettlementCoreLayoutTest::width);
		if (!fit.fits()) {
			failures.add(code + " " + key + ": " + width(text) + " px on " + fit.lines().size() + " lines at "
					+ fit.scale() + " (box " + width + " x " + maxLines + ")");
		}
	}

	/** The string's width in the default font, as {@code Font.width} adds it up. */
	private static int width(String text) {
		int sum = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			Integer advance = ADVANCES.get(cp);
			if (advance == null) {
				throw new AssertionError(String.format("U+%04X is not in %s: run python tools/gen_gui_font_advances.py",
						cp, TABLE));
			}
			sum += advance;
			i += Character.charCount(cp);
		}
		return sum;
	}

	/** Sixteen of the widest character a player name may hold ([A-Za-z0-9_]). */
	private static String widestName() {
		int widest = 'A';
		String allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
		for (char c : allowed.toCharArray()) {
			if (ADVANCES.get((int) c) > ADVANCES.get(widest)) {
				widest = c;
			}
		}
		return String.valueOf((char) widest).repeat(16);
	}

	/** The translation of {@code key} with its {@code %s} / {@code %1$s} filled, as TranslatableContents does. */
	private static String text(Map<String, String> lang, String key, String... args) {
		assertTrue(lang.containsKey(key), "missing key " + key);
		String pattern = lang.get(key);
		StringBuilder out = new StringBuilder();
		int next = 0;
		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			if (c != '%' || i + 1 >= pattern.length()) {
				out.append(c);
				continue;
			}
			char d = pattern.charAt(i + 1);
			if (d == '%') {
				out.append('%');
				i++;
			} else if (d == 's') {
				out.append(args[next++]);
				i++;
			} else if (Character.isDigit(d) && i + 3 < pattern.length() && pattern.charAt(i + 2) == '$') {
				out.append(args[d - '1']);
				i += 3;
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static List<Path> langFiles() throws IOException, URISyntaxException {
		Path dir = Path.of(SettlementCoreLayoutTest.class.getClassLoader().getResource(EN_US).toURI()).getParent();
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
	}

	private static Map<String, String> read(Path file) throws IOException {
		return flatObject(Files.readString(file, StandardCharsets.UTF_8));
	}

	private static Map<Integer, Integer> loadAdvances() {
		try (InputStream stream = SettlementCoreLayoutTest.class.getClassLoader().getResourceAsStream(TABLE)) {
			Map<Integer, Integer> table = new HashMap<>();
			flatObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
					.forEach((k, v) -> table.put(Integer.parseInt(k, 16), Integer.parseInt(v)));
			return table;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * A flat JSON object of strings (or numbers) — a lang file or the advance table. Gson is only on this lane's
	 * runtime classpath, and both files are one level deep, so a small reader is all it takes.
	 */
	static Map<String, String> flatObject(String json) {
		Map<String, String> out = new HashMap<>();
		int[] at = {json.indexOf('{') + 1};
		while (true) {
			skipSpace(json, at);
			if (json.charAt(at[0]) == '}') {
				return out;
			}
			String key = string(json, at);
			skipSpace(json, at);
			at[0]++; // ':'
			skipSpace(json, at);
			String value;
			if (json.charAt(at[0]) == '"') {
				value = string(json, at);
			} else {
				int start = at[0];
				while (",} \n\r\t".indexOf(json.charAt(at[0])) < 0) {
					at[0]++;
				}
				value = json.substring(start, at[0]);
			}
			out.put(key, value);
			skipSpace(json, at);
			if (json.charAt(at[0]) == ',') {
				at[0]++;
			}
		}
	}

	private static void skipSpace(String json, int[] at) {
		while (Character.isWhitespace(json.charAt(at[0]))) {
			at[0]++;
		}
	}

	private static String string(String json, int[] at) {
		StringBuilder out = new StringBuilder();
		at[0]++; // opening quote
		while (true) {
			char c = json.charAt(at[0]++);
			if (c == '"') {
				return out.toString();
			}
			if (c != '\\') {
				out.append(c);
				continue;
			}
			char e = json.charAt(at[0]++);
			switch (e) {
				case 'n' -> out.append('\n');
				case 't' -> out.append('\t');
				case 'r' -> out.append('\r');
				case 'b' -> out.append('\b');
				case 'f' -> out.append('\f');
				case 'u' -> {
					out.append((char) Integer.parseInt(json.substring(at[0], at[0] + 4), 16));
					at[0] += 4;
				}
				default -> out.append(e);
			}
		}
	}
}
