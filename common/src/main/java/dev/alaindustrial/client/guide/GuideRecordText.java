package dev.alaindustrial.client.guide;

import dev.alaindustrial.core.guide.ArchiveRecord;
import org.jetbrains.annotations.Nullable;

/**
 * Puts the player's archive record into the guide book's prose (MOD-513).
 *
 * <p>The book is generated JSON, the same file for every player, so the record cannot be written into
 * it. The generator writes {@link #TOKEN} instead, every translation keeps that token untouched, and
 * the screen swaps it for the record at the moment it lays the page out. {@code GuideRecordTextTest}
 * checks that every locale's book carries the token exactly once, on the welcome page.
 *
 * <p>Until the record has arrived the token becomes {@link #PENDING}: three ASCII dots, which read as
 * "still detecting" next to {@code Record detected:} in any language, cannot be mistaken for a
 * record, and exist in every font the game ships.
 */
public final class GuideRecordText {

	/** What the generated book says where the record goes. Must match {@code docs/tools/gen_guide_book.py}. */
	public static final String TOKEN = "{record}";

	/** Shown in place of the record while the server's value has not arrived. */
	public static final String PENDING = "...";

	private GuideRecordText() {
	}

	/**
	 * {@code text} with every {@link #TOKEN} replaced by {@code record}, or by {@link #PENDING} when
	 * there is no record or it is not in the record's shape. Text without the token comes back as is.
	 */
	public static String fill(String text, @Nullable String record) {
		if (text == null || !text.contains(TOKEN)) {
			return text;
		}
		return text.replace(TOKEN, ArchiveRecord.isValid(record) ? record : PENDING);
	}
}
