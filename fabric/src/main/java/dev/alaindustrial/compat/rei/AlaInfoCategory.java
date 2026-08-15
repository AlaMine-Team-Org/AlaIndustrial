package dev.alaindustrial.compat.rei;

import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/**
 * REI category for informational pages (blocks/items with no crafting recipe) — the solar panel
 * evolution line today. Renders a title plus the description lines, left-aligned, on the standard
 * recipe background.
 *
 * <p><b>Word wrap:</b> REI's {@code Label} widget draws a single line and does not wrap, so long
 * descriptions would overflow the panel. Each description {@link Component} is therefore split with
 * {@link StringSplitter#splitLines} against the available inner width before rendering — one
 * {@code Label} per resulting visual line. The split happens at render-setup time using the live
 * font, so it tracks locale and zoom correctly.
 *
 * <p>One category serves every informational display (all {@link AlaInfoDisplay}s share the id
 * {@link AlaInfoDisplay#CATEGORY}); the title and text come from the display itself, so adding a new
 * evolution line is a new {@link dev.alaindustrial.client.compat.RecipeViewerInfo.Entry}, not a new category.
 */
public final class AlaInfoCategory implements DisplayCategory<AlaInfoDisplay> {
	private final me.shedaniel.rei.api.common.category.CategoryIdentifier<AlaInfoDisplay> categoryId;
	private final String titleKey;
	private final net.minecraft.world.level.ItemLike icon;
	private final List<dev.alaindustrial.client.compat.RecipeViewerInfo.Entry> pages;

	/**
	 * @param categoryId which informational category this instance serves — the class is shared by the
	 *                   evolution pages and the machine-info pages (MOD-420), which differ only in id,
	 *                   title and icon.
	 * @param pages      the entries this category will show. Needed because {@link #getDisplayHeight()}
	 *                   is asked for ONE height for the whole category (REI gives it no display), so the
	 *                   category has to size itself against its tallest page — see MOD-422.
	 */
	public AlaInfoCategory(me.shedaniel.rei.api.common.category.CategoryIdentifier<AlaInfoDisplay> categoryId,
			String titleKey, net.minecraft.world.level.ItemLike icon,
			List<dev.alaindustrial.client.compat.RecipeViewerInfo.Entry> pages) {
		this.categoryId = categoryId;
		this.titleKey = titleKey;
		this.icon = icon;
		this.pages = pages;
	}

	private static final int PADDING_X = 6;
	private static final int TOP_PAD = 6;
	private static final int LINE_HEIGHT = 10;
	private static final int TITLE_TO_BODY_GAP = 4;
	private static final int BOTTOM_PAD = 8;
	/**
	 * Floor for the reserved body lines, not a ceiling (MOD-422).
	 *
	 * <p>It used to be the ceiling, and that silently truncated nothing — it overflowed instead: the
	 * draw loop below emits one label per visual line however many there are, and the panel neither
	 * clips nor scrolls, so everything past the reserved height was painted outside the background.
	 * English fit in eight lines, which is why the defect was invisible on the development language;
	 * the Russian "mutation grades" page measured nineteen. Height is now derived from the real text
	 * (see {@link #bodyLines()}), and this constant only keeps short pages from collapsing.
	 */
	private static final int MIN_BODY_LINES = 8;

	/**
	 * Hard ceiling, derived from REI's own layout maths — not a taste call.
	 *
	 * <p>{@code DefaultDisplayViewingScreen} sizes its window as
	 * {@code 36 + (displayHeight + 4) * (displaysPerPage + 1)}, so the screen a category demands grows
	 * with this class's height. Minecraft's smallest supported GUI is 240 logical pixels tall, which
	 * leaves {@code 240 - 36 - 4 = 200} for the card, i.e. seventeen body lines. Growing past that
	 * trades one defect for a worse one: instead of text spilling over the panel, the whole panel stops
	 * fitting the screen on a small window or a large GUI scale — and there the player cannot even
	 * scroll to the rest.
	 *
	 * <p>Nothing shipped comes close (the longest page is eleven lines after MOD-422 trimmed the
	 * mutation-grade text), so this is a backstop against a future edit, not a live constraint.
	 */
	private static final int MAX_BODY_LINES = 17;
	/** Standard recipe width this category uses; padded on both sides for text. */
	private static final int DISPLAY_WIDTH = 160;

	@Override
	public me.shedaniel.rei.api.common.category.CategoryIdentifier<? extends AlaInfoDisplay> getCategoryIdentifier() {
		return categoryId;
	}

	@Override
	public Component getTitle() {
		return Component.translatable(titleKey);
	}

	@Override
	public Renderer getIcon() {
		return EntryStacks.of(icon);
	}

	@Override
	public int getDisplayHeight() {
		return TOP_PAD + LINE_HEIGHT + TITLE_TO_BODY_GAP + LINE_HEIGHT * bodyLines() + BOTTOM_PAD;
	}

	/**
	 * Body lines to reserve: the tallest page in this category, measured with the SAME splitter and
	 * width {@link #setupDisplay} draws with, so the reservation cannot disagree with the drawing.
	 *
	 * <p>Measured live rather than cached: the wrap depends on the active language and on
	 * {@link dev.alaindustrial.Config} values interpolated into the lines, both of which change
	 * without this object being rebuilt.
	 *
	 * <p>Falls back to the floor if the font is not up yet — REI can query a category before the
	 * client finishes loading, and a crash there would take the whole recipe screen with it.
	 */
	private int bodyLines() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.font == null) {
			return MIN_BODY_LINES;
		}
		StringSplitter splitter = client.font.getSplitter();
		int maxTextWidth = DISPLAY_WIDTH - PADDING_X * 2;
		int longest = MIN_BODY_LINES;
		for (dev.alaindustrial.client.compat.RecipeViewerInfo.Entry page : pages) {
			int lines = 0;
			for (Component line : dev.alaindustrial.client.compat.RecipeViewerInfo.buildLines(page)) {
				lines += splitter.splitLines(line, maxTextWidth, Style.EMPTY).size();
			}
			longest = Math.max(longest, lines);
		}
		return Math.min(longest, MAX_BODY_LINES);
	}

	@Override
	public List<Widget> setupDisplay(AlaInfoDisplay display, Rectangle bounds) {
		List<Widget> widgets = new ArrayList<>();
		widgets.add(Widgets.createRecipeBase(bounds));

		StringSplitter splitter = Minecraft.getInstance().font.getSplitter();
		int x = bounds.getX() + PADDING_X;
		int y = bounds.getY() + TOP_PAD;
		int maxTextWidth = bounds.getWidth() - PADDING_X * 2;

		// Title — left-aligned, slightly darker.
		widgets.add(Widgets.createLabel(new Point(x, y), display.title())
				.leftAligned().noShadow().color(0xFF303030, 0xFFCCCCCC));
		y += LINE_HEIGHT + TITLE_TO_BODY_GAP;

		// Body: word-wrap each source line against the available width (Font.split-style), then render
		// one Label per visual line. createLabel needs a Component, so we seed an empty one and override
		// the text via Label.message(FormattedText) — the splitter yields FormattedText, preserving any
		// formatting, and avoids the overflow a single unwrapped Label would cause.
		for (Component line : display.lines()) {
			for (FormattedText wrapped : splitter.splitLines(line, maxTextWidth, Style.EMPTY)) {
				widgets.add(Widgets.createLabel(new Point(x, y), Component.empty())
						.leftAligned().noShadow().color(0xFF404040, 0xFFBBBBBB).message(wrapped));
				y += LINE_HEIGHT;
			}
		}

		return widgets;
	}

	@Override
	public int getDisplayWidth(AlaInfoDisplay display) {
		return DISPLAY_WIDTH;
	}
}
