package dev.alaindustrial.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * L1 coverage for the client half of the archive record (MOD-513): how the record reaches the page
 * ({@link GuideRecordText}), what the client agrees to keep ({@link ArchiveRecordClient}), and the
 * contract the generated books must honour for either to matter — the token is there, once, on the
 * first page of the welcome entry, in every book the mod ships.
 *
 * @implements MOD-513-BOOK
 */
class GuideRecordTextTest {

	private static final String BOOK_DIR = "assets/alaindustrial/guide_book/";

	@BeforeEach
	@AfterEach
	void forgetRecord() {
		ArchiveRecordClient.reset();
	}

	@Test
	void fillPutsTheRecordWhereTheTokenIs() {
		assertEquals("Record detected: K-482", GuideRecordText.fill("Record detected: {record}", "K-482"));
		assertEquals("A-000 / A-000", GuideRecordText.fill("{record} / {record}", "A-000"));
	}

	/** Before the server's value arrives, and for anything not shaped like a record, the page says it is still waiting. */
	@Test
	void fillShowsThePendingMarkWithoutAValidRecord() {
		String pending = "Record detected: " + GuideRecordText.PENDING;
		assertEquals(pending, GuideRecordText.fill("Record detected: {record}", null));
		assertEquals(pending, GuideRecordText.fill("Record detected: {record}", "I-482"));
		assertEquals(pending, GuideRecordText.fill("Record detected: {record}", "K-482 and more"));
		assertFalse(GuideRecordText.PENDING.contains(GuideRecordText.TOKEN));
		assertFalse(dev.alaindustrial.core.guide.ArchiveRecord.isValid(GuideRecordText.PENDING));
	}

	@Test
	void fillLeavesOtherTextAlone() {
		String plain = "1. Mine the mod's ore — start with tin. {not a token}";
		assertSame(plain, GuideRecordText.fill(plain, "K-482"));
		assertNull(GuideRecordText.fill(null, "K-482"));
	}

	@Test
	void clientKeepsOnlyRecordShapedValues() {
		assertNull(ArchiveRecordClient.current());
		assertTrue(ArchiveRecordClient.receive("K-482"));
		assertEquals("K-482", ArchiveRecordClient.current());

		// A malformed value is refused and does not replace the record already held.
		assertFalse(ArchiveRecordClient.receive("K-4820"));
		assertFalse(ArchiveRecordClient.receive(null));
		assertFalse(ArchiveRecordClient.receive("<b>K-482</b>"));
		assertEquals("K-482", ArchiveRecordClient.current());

		assertTrue(ArchiveRecordClient.receive("B-017"));
		assertEquals("B-017", ArchiveRecordClient.current());
	}

	@Test
	void leavingTheWorldForgetsTheRecord() {
		assertTrue(ArchiveRecordClient.receive("K-482"));
		ArchiveRecordClient.reset();
		assertNull(ArchiveRecordClient.current());
	}

	/**
	 * The generated books, all of them: exactly one token, and it sits in the text of the welcome
	 * entry's first page, whose title carries the untranslated {@code ALA INDUSTRIAL} stamp. A
	 * translation that dropped or doubled the token, or a generator that moved the card, fails here
	 * rather than showing a page with no record.
	 */
	@Test
	void everyBookCarriesTheTokenOnceOnTheWelcomeCard() throws IOException, URISyntaxException {
		List<Path> books = shippedBooks();
		List<String> names = books.stream().map(p -> p.getFileName().toString()).toList();
		// Guards against a vacuous pass: the two books the generator writes itself must be among them,
		// and so must at least one assembled from a translation map.
		assertTrue(names.contains("en_us.json"), "en_us book missing from " + names);
		assertTrue(names.contains("ru_ru.json"), "ru_ru book missing from " + names);
		assertTrue(names.size() > 2, "no translated book found beside en_us/ru_ru: " + names);

		for (Path book : books) {
			String json = Files.readString(book, StandardCharsets.UTF_8);
			String name = book.getFileName().toString();
			WelcomeCard card = WelcomeCard.read(json, name);
			assertEquals(1, count(json, GuideRecordText.TOKEN), name + ": the token must appear exactly once");
			assertEquals(1, count(card.text(), GuideRecordText.TOKEN), name + ": the token is not on the welcome card");
			assertTrue(card.title().contains("ALA INDUSTRIAL"), name + ": card title lost the stamp: " + card.title());
		}
	}

	/** The English source is the approved one: the runtime depends on its title and first line. */
	@Test
	void englishCardOpensWithTheApprovedLines() throws IOException {
		String json = readResource(BOOK_DIR + "en_us.json");
		WelcomeCard card = WelcomeCard.read(json, "en_us.json");
		assertEquals("ALA INDUSTRIAL GUIDE", card.title());
		assertTrue(card.text().startsWith("Record detected: " + GuideRecordText.TOKEN + "\n\n"), card.text());
		assertTrue(card.text().endsWith("No prior records were included."), card.text());
	}

	// ── helpers ──────────────────────────────────────────────────────────────────────────────────

	private static List<Path> shippedBooks() throws URISyntaxException, IOException {
		URL en = GuideRecordTextTest.class.getClassLoader().getResource(BOOK_DIR + "en_us.json");
		assertNotNull(en, BOOK_DIR + "en_us.json must be on the test runtime classpath");
		Path dir = Path.of(en.toURI()).getParent();
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
		}
	}

	private static String readResource(String name) throws IOException {
		try (InputStream in = GuideRecordTextTest.class.getClassLoader().getResourceAsStream(name)) {
			assertNotNull(in, name + " must be on the test runtime classpath");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static int count(String haystack, String needle) {
		int n = 0;
		for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
			n++;
		}
		return n;
	}

	/**
	 * The first page of the {@code welcome} entry, read out of the raw book JSON. Gson is not on the L1
	 * compile classpath, and the generator's layout is fixed ({@code "id": "welcome"}, then its
	 * {@code "pages"} array, then that page's {@code "title"} and {@code "text"} strings), so a small
	 * string reader is enough.
	 */
	private record WelcomeCard(String title, String text) {

		static WelcomeCard read(String json, String name) {
			int entry = json.indexOf("\"id\": \"welcome\"");
			assertTrue(entry >= 0, name + ": no welcome entry");
			int pages = json.indexOf("\"pages\"", entry);
			assertTrue(pages >= 0, name + ": welcome entry has no pages");
			int title = json.indexOf("\"title\": \"", pages);
			int text = json.indexOf("\"text\": \"", pages);
			assertTrue(title >= 0 && text > title, name + ": first welcome page has no title/text pair");
			return new WelcomeCard(
					readString(json, title + "\"title\": \"".length()),
					readString(json, text + "\"text\": \"".length()));
		}

		/** A JSON string body starting at {@code from} (just after its opening quote), unescaped. */
		private static String readString(String json, int from) {
			StringBuilder sb = new StringBuilder();
			for (int i = from; i < json.length(); i++) {
				char c = json.charAt(i);
				if (c == '"') {
					return sb.toString();
				}
				if (c != '\\') {
					sb.append(c);
					continue;
				}
				char e = json.charAt(++i);
				switch (e) {
					case 'n' -> sb.append('\n');
					case 't' -> sb.append('\t');
					case 'r' -> sb.append('\r');
					case 'b' -> sb.append('\b');
					case 'f' -> sb.append('\f');
					case 'u' -> {
						sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
						i += 4;
					}
					default -> sb.append(e); // \" \\ \/
				}
			}
			throw new AssertionError("unterminated JSON string at " + from);
		}
	}
}
