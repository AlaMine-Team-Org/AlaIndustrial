package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.client.guide.GuideBookClientAccess;
import dev.alaindustrial.client.guide.GuideBookScreen;
import dev.alaindustrial.client.guide.GuideContent;
import dev.alaindustrial.client.guide.GuideText;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOD-607 — how the guide book breaks lines in Thai, asked of the real font.
 *
 * <p><b>Why this needs a client and cannot be a data check.</b> Vanilla's line breaker
 * ({@code StringSplitter.LineBreakFinder.accept}, read out of the 26.2 sources) looks at exactly two
 * codepoints: newline and <em>space</em>. With no space in reach it calls
 * {@code finishIteration(adjustedPosition, style)} — the break lands on whatever character the width
 * ran out on. Thai writes without spaces between words, and the Thai book has 162 runs longer than
 * forty characters (the longest is a hundred), so in a column of at most ~470 px the breaks are
 * guaranteed to fall mid-run. Where exactly they fall depends on glyph widths, and only the live font
 * knows those.
 *
 * <p><b>Two different questions, and only one of them is a gate.</b>
 *
 * <ul>
 *   <li><b>A line that STARTS with a combining mark is broken text.</b> Thai vowel signs and tone
 *       marks (U+0E31, U+0E34–U+0E3A, U+0E47–U+0E4E) render above or below the consonant they belong
 *       to; cut between the two and the mark is left hanging alone at the start of a line, attached to
 *       nothing. Nothing about that is a matter of taste, so it fails the run.</li>
 *   <li><b>A line that breaks mid-word is measured, not enforced.</b> The JDK ships a Thai dictionary
 *       for {@link BreakIterator}, so "was this a legal place to break" has an answer — but vanilla's
 *       splitter knows nothing about Thai, and demanding zero here would be demanding a splitter of
 *       our own. The number is logged so the decision to write one (or not) is taken on evidence.</li>
 * </ul>
 *
 * <p>The column width comes from {@link GuideBookScreen#contentWidth(int)} — the book's own
 * arithmetic — so this audits the column the player actually reads.
 *
 * <p>Finally one frame of the book in Thai, for the eye: a screenshot proves nothing on its own, but
 * it is what turns "page through five entries and see" into an artefact that outlives the session
 * and can be compared after the next change.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ThaiGuideBookStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	private static final String LOCALE = "th_th";

	/**
	 * Scaled screen widths to audit, widest first.
	 *
	 * <p>Two, because the column is a function of the window: 640 is what this lane runs at
	 * (1280×720 at GUI scale 2) and 320 is the narrowest scaled width vanilla will hand a screen.
	 * The wide column leaves a space within reach almost always; the narrow one is where a run
	 * without spaces has nowhere to go, so it is the one that can actually produce a broken cluster.
	 * Auditing only the comfortable width would be auditing the case that cannot fail.
	 */
	private static final int[] SCALED_WIDTHS = {640, 320};

	private ThaiGuideBookStand() {
	}

	/** What one pass over the book found: orphaned marks, illegal breaks, and how many breaks in all. */
	private record BreakStats(List<String> orphans, int illegal, int breaks) {
	}

	/** True for a Thai mark that must never begin a line: it renders on the previous consonant. */
	private static boolean isThaiCombiningMark(int codepoint) {
		return codepoint == 0x0E31
				|| (codepoint >= 0x0E34 && codepoint <= 0x0E3A)
				|| (codepoint >= 0x0E47 && codepoint <= 0x0E4E);
	}

	private static boolean hasThai(String text) {
		return text.codePoints().anyMatch(cp -> cp >= 0x0E00 && cp <= 0x0E7F);
	}

	public static void checkThaiLineBreaks(ClientGameTestContext context) {
		switchLanguage(context, LOCALE);
		try {
			List<String> paragraphs = context.computeOnClient(mc -> collectParagraphs());
			if (paragraphs.isEmpty()) {
				throw new AssertionError("[TH] the Thai guide book yielded no Thai prose — either "
						+ "guide_book/th_th.json stopped loading or the language did not switch. "
						+ "An empty sample would let this stand pass without checking anything.");
			}

			String longest = paragraphs.stream().max((a, b) -> a.length() - b.length()).orElse("");
			int longestWidth = context.computeOnClient(mc -> mc.font.width(Component.literal(longest)));
			List<String> allOrphans = new ArrayList<>();

			for (int screenWidth : SCALED_WIDTHS) {
				int width = GuideBookScreen.contentWidth(screenWidth);

				// Break POSITIONS, not the broken strings. The first draft rebuilt the paragraph out of
				// the returned lines to recover the offsets and skipped any paragraph whose rebuild did
				// not match — which is every paragraph containing a space, because the splitter eats the
				// space it breaks on. Thai does separate phrases with spaces, so the sample came out
				// EMPTY while the font was measuring the text perfectly well, and the stand reported a
				// clean run. `splitLines(String, …, LinePosConsumer)` reports the indices directly.
				BreakStats stats = context.computeOnClient(mc -> {
					List<String> found = new ArrayList<>();
					int illegal = 0;
					int total = 0;
					BreakIterator words = BreakIterator.getLineInstance(Locale.forLanguageTag("th"));
					for (String paragraph : paragraphs) {
						// The book's own splitter, not vanilla's: auditing vanilla would audit a layout
						// the screen does not use, and the first draft of this stand did exactly that —
						// it kept reporting 29 orphaned marks after GuideText had fixed them.
						List<Integer> starts = GuideText.lineStarts(mc.font, paragraph, width);
						words.setText(paragraph);
						for (int i = 1; i < starts.size(); i++) {
							int at = starts.get(i);
							total++;
							if (!words.isBoundary(at)) {
								illegal++;
							}
							if (at < paragraph.length() && isThaiCombiningMark(paragraph.codePointAt(at))) {
								int from = Math.max(0, at - 6);
								found.add(paragraph.substring(from, Math.min(paragraph.length(), at + 6))
										+ " @" + at);
							}
						}
					}
					return new BreakStats(found, illegal, total);
				});

				LOG.info("[TH] column {} px (screen {}): {} paragraphs, longest {} chars = {} px, "
						+ "{} line breaks, {} mid-word by the JDK's Thai dictionary ({}%), {} orphaned marks",
						width, screenWidth, paragraphs.size(), longest.length(), longestWidth,
						stats.breaks(), stats.illegal(),
						stats.breaks() == 0 ? 0 : stats.illegal() * 100 / stats.breaks(),
						stats.orphans().size());

				// The vacuous-pass guard. A paragraph of 341 Thai characters cannot fit these columns
				// under any font: no break at all means the sample never reached the splitter, and every
				// assertion would be asking about text that was never laid out. Not hypothetical — it is
				// exactly what the first draft did, and it reported success.
				if (stats.breaks() == 0) {
					throw new AssertionError("[TH] the splitter found NO line break in " + paragraphs.size()
							+ " Thai paragraphs at a " + width + " px column (longest paragraph "
							+ longest.length() + " chars, measured " + longestWidth + " px). "
							+ "The verdict would be vacuous.");
				}
				allOrphans.addAll(stats.orphans());
			}

			if (!allOrphans.isEmpty()) {
				throw new AssertionError("[TH] " + allOrphans.size() + " line(s) of the Thai guide book "
						+ "begin with a combining mark — the vowel or tone sign was cut away from its "
						+ "consonant and renders attached to nothing. First few: "
						+ allOrphans.subList(0, Math.min(5, allOrphans.size()))
						+ ". Fix the break, not the text: it must move to the start of the grapheme cluster.");
			}

			context.runOnClient(mc -> GuideBookClientAccess.open());
			context.waitTicks(3);
			LOG.info("[TH] gui_guide_book_thai -> {}",
					takeCleanScreenshot(context, "gui_guide_book_thai").toAbsolutePath());
			context.runOnClient(mc -> mc.setScreenAndShow(null));
			context.waitTicks(1);
		} finally {
			switchLanguage(context, "en_us");
		}
	}

	/** Every paragraph of Thai prose in the book, split the way the screen splits it. */
	private static List<String> collectParagraphs() {
		List<String> paragraphs = new ArrayList<>();
		GuideContent.Book book = GuideContent.load();
		for (GuideContent.Tab tab : book.tabs) {
			for (GuideContent.Entry entry : tab.entries) {
				for (GuideContent.Page page : entry.pages) {
					for (String paragraph : page.text.split("\n\n")) {
						if (hasThai(paragraph)) {
							paragraphs.add(paragraph);
						}
					}
					for (String line : page.lines) {
						if (hasThai(line)) {
							paragraphs.add(line);
						}
					}
				}
			}
		}
		return paragraphs;
	}

	/**
	 * Same switch {@code RtlGuiStands} uses, waiting on the observed effect rather than on the future
	 * alone — and with a Thai probe, so a reload that lands without the mod's own {@code th_th.json}
	 * is not mistaken for success.
	 */
	private static void switchLanguage(ClientGameTestContext context, String code) {
		AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
		context.runOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			reload.set(mc.reloadResourcePacks());
		});
		boolean wantThai = LOCALE.equals(code);
		for (int i = 0; i < 100; i++) {   // 100 × 5 ticks ≈ 25 s cap on a stuck reload
			CompletableFuture<Void> future = reload.get();
			String probe = context.computeOnClient(
					mc -> Component.translatable("gui.alaindustrial.stats.title").getString());
			if (future != null && future.isDone() && hasThai(probe) == wantThai) {
				context.waitTicks(2);
				return;
			}
			context.waitTicks(5);
		}
		throw new AssertionError("[TH] language switch to '" + code + "' did not land within 25 s — "
				+ "the resource reload never finished or the probe key never resolved in Thai. "
				+ "No line-break verdict was reached.");
	}
}
