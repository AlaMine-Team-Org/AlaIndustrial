package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.client.guide.ArchiveRecordClient;
import dev.alaindustrial.client.guide.GuideBookClientAccess;
import dev.alaindustrial.client.guide.GuideBookScreen;
import dev.alaindustrial.client.guide.GuideContent;
import dev.alaindustrial.client.guide.GuideRecordText;
import dev.alaindustrial.core.guide.ArchiveRecord;
import dev.alaindustrial.core.guide.ArchiveRecordSync;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MOD-513 — the player's archive record on the guide book's first page, end to end on a real login.
 *
 * <p>This lane joins an integrated server, so the path under test is the shipping one: the join hook
 * computes the record, the payload crosses the local connection, the client keeps it, the book lays it
 * out. Each step is asked of the thing that owns it rather than assumed from the one before:
 *
 * <ol>
 *   <li><b>Delivered.</b> The client holds a record without anyone putting it there.</li>
 *   <li><b>The server's own.</b> It equals what the server computes for this player, and that equals
 *       the pure function of the server's world seed and this player's profile id.</li>
 *   <li><b>Drawn.</b> The open book's rows contain the card's first line with that record in it —
 *       read from the laid-out page, not from the JSON, so a screen that forgot to fill the token fails.</li>
 *   <li><b>Late arrival.</b> With the record gone the page shows the pending mark; a record arriving
 *       while the book is open replaces it without reopening; a malformed one is refused.</li>
 * </ol>
 *
 * <p>The frame is for the eye; the checks above are what fail the run.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GuideBookRecordStand {

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** Ticks for the login packet to land. It is sent on join, so it is normally there long before this runs. */
	private static final int DELIVERY_TIMEOUT_TICKS = 200;

	private GuideBookRecordStand() {
	}

	public static void check(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// 1. Delivered by the login, not by this stand.
		context.waitFor(mc -> ArchiveRecordClient.current() != null, DELIVERY_TIMEOUT_TICKS);
		String delivered = context.computeOnClient(mc -> ArchiveRecordClient.current());
		UUID self = context.computeOnClient(mc -> mc.player.getUUID());

		// 2. The server's value, and the server's value is the function of its seed and this profile.
		TestServerContext server = singleplayer.getServer();
		String serverSays = server.computeOnServer(mc -> {
			ServerPlayer player = mc.getPlayerList().getPlayer(self);
			if (player == null) {
				throw new AssertionError("[RECORD] the server has no player " + self);
			}
			return ArchiveRecordSync.recordFor(player);
		});
		long seed = server.computeOnServer(mc -> mc.overworld().getSeed());
		if (!serverSays.equals(delivered)) {
			throw new AssertionError("[RECORD] the client holds " + delivered + " but the server computes "
					+ serverSays + " for this player");
		}
		String pure = ArchiveRecord.of(seed, self);
		if (!pure.equals(delivered)) {
			throw new AssertionError("[RECORD] the delivered record " + delivered + " is not the one the world "
					+ "seed and this profile give (" + pure + ")");
		}
		LOG.info("[RECORD] login delivered {} — equal to the server's value and to ArchiveRecord.of(seed, uuid)",
				delivered);

		// 3. Drawn on the first page of the open book.
		String expectedLine = firstCardLine(context, delivered);
		context.runOnClient(mc -> GuideBookClientAccess.open());
		context.waitForScreen(GuideBookScreen.class);
		context.waitTicks(2);
		assertPageShows(context, expectedLine, "the record the server sent");
		assertNoLineFeedGlyphs(context);
		LOG.info("[RECORD] gui_guide_book_record -> {}",
				takeCleanScreenshot(context, "gui_guide_book_record").toAbsolutePath());

		// 4. Late arrival: pending mark, then a record that lands while the book is open, then junk.
		String other = anotherRecord(seed, self, delivered);
		try {
			context.runOnClient(mc -> ArchiveRecordClient.reset());
			context.waitTicks(2);
			assertPageShows(context, firstCardLine(context, null), "the pending mark");

			context.runOnClient(mc -> ArchiveRecordClient.receive(other));
			context.waitTicks(2);
			assertPageShows(context, firstCardLine(context, other), "a record that arrived with the book open");

			boolean kept = context.computeOnClient(mc -> ArchiveRecordClient.receive("I-" + other.substring(2)));
			context.waitTicks(2);
			if (kept) {
				throw new AssertionError("[RECORD] the client kept a record starting with a banned letter");
			}
			assertPageShows(context, firstCardLine(context, other), "the last valid record after a malformed one");
		} finally {
			// Leave the client exactly as the login left it.
			context.runOnClient(mc -> ArchiveRecordClient.receive(delivered));
			context.waitTicks(2);
		}
		assertPageShows(context, expectedLine, "the restored record");

		context.runOnClient(mc -> mc.setScreenAndShow(null));
		context.waitTicks(1);
	}

	/**
	 * The card's first paragraph as the current language's book writes it, with {@code record} filled in
	 * (or the pending mark for {@code null}) — built from the book the screen itself loads, so the check
	 * holds in any language the lane happens to run in.
	 */
	private static String firstCardLine(ClientGameTestContext context, String record) {
		String text = context.computeOnClient(mc -> {
			GuideContent.Book book = GuideContent.load();
			return book.tabs.get(0).entries.get(0).pages.get(0).text;
		});
		if (!text.contains(GuideRecordText.TOKEN)) {
			throw new AssertionError("[RECORD] the book's first page has no " + GuideRecordText.TOKEN
					+ " — the card moved or the token was lost: " + text);
		}
		return GuideRecordText.fill(text, record).split("\n\n")[0];
	}

	private static void assertPageShows(ClientGameTestContext context, String line, String what) {
		List<String> rows = context.computeOnClient(mc -> mc.gui.screen() instanceof GuideBookScreen book
				? book.visibleText() : List.of());
		if (rows.isEmpty()) {
			throw new AssertionError("[RECORD] no guide book page on screen while checking " + what);
		}
		for (String row : rows) {
			if (row.contains(GuideRecordText.TOKEN)) {
				throw new AssertionError("[RECORD] the page shows the raw token instead of " + what + ": " + rows);
			}
		}
		if (!rows.contains(line)) {
			throw new AssertionError("[RECORD] the page does not show " + what + " — expected the row «" + line
					+ "» among " + rows);
		}
	}

	/**
	 * No row on the page carries a control character. The "First steps" list under the card is where a
	 * kept line feed used to surface as a boxed "LF" glyph; its first item must be on this page, or the
	 * check would pass without having looked at a single list line.
	 */
	private static void assertNoLineFeedGlyphs(ClientGameTestContext context) {
		String firstItem = context.computeOnClient(mc -> GuideContent.load().tabs.get(0).entries.get(0).pages.get(1)
				.text.split("\n")[0]);
		List<String> rows = context.computeOnClient(mc -> mc.gui.screen() instanceof GuideBookScreen book
				? book.visibleText() : List.of());
		if (rows.stream().noneMatch(row -> row.startsWith(firstItem))) {
			throw new AssertionError("[RECORD] the first 'First steps' item «" + firstItem + "» is not on the "
					+ "welcome page, so the line-feed check would look at no list line: " + rows);
		}
		for (String row : rows) {
			if (row.chars().anyMatch(c -> c < 0x20)) {
				throw new AssertionError("[RECORD] a book row carries a control character, which the font draws "
						+ "as a glyph box: «" + row.replace("\n", "\\n") + "»");
			}
		}
	}

	/** A valid record different from {@code delivered}, derived rather than typed so no example value is hard-coded. */
	private static String anotherRecord(long seed, UUID self, String delivered) {
		for (long step = 1; step < 64; step++) {
			String candidate = ArchiveRecord.of(seed + step, self);
			if (!candidate.equals(delivered)) {
				return candidate;
			}
		}
		throw new AssertionError("[RECORD] 63 neighbouring seeds all gave " + delivered);
	}
}
