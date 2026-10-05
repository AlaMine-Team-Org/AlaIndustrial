package dev.alaindustrial.compat.rei;

import dev.alaindustrial.client.compat.InfoPageLayout;
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
	private final java.util.function.Supplier<Component> title;
	private final net.minecraft.world.level.ItemLike icon;
	private final List<dev.alaindustrial.client.compat.RecipeViewerInfo.Entry> pages;

	/**
	 * @param categoryId which informational category this instance serves — the class is shared by the
	 *                   evolution pages and the machine-info pages (MOD-420), which differ only in id,
	 *                   title and icon.
	 * @param title      the tab's title, built anew on each call (the form's lang key, MOD-716).
	 * @param pages      the entries this category will show. Needed because {@link #getDisplayHeight()}
	 *                   is asked for ONE height for the whole category (REI gives it no display), so the
	 *                   category has to size itself against its tallest page — see MOD-422.
	 */
	public AlaInfoCategory(me.shedaniel.rei.api.common.category.CategoryIdentifier<AlaInfoDisplay> categoryId,
			java.util.function.Supplier<Component> title, net.minecraft.world.level.ItemLike icon,
			List<dev.alaindustrial.client.compat.RecipeViewerInfo.Entry> pages) {
		this.categoryId = categoryId;
		this.title = title;
		this.icon = icon;
		this.pages = pages;
	}

	private static final int PADDING_X = 6;
	private static final int TOP_PAD = 6;
	private static final int LINE_HEIGHT = InfoPageLayout.LINE_HEIGHT;
	private static final int TITLE_TO_BODY_GAP = 4;
	private static final int BOTTOM_PAD = 8;
	/** Standard recipe width this category uses; padded on both sides for text. */
	private static final int DISPLAY_WIDTH = InfoPageLayout.WIDTH;

	@Override
	public me.shedaniel.rei.api.common.category.CategoryIdentifier<? extends AlaInfoDisplay> getCategoryIdentifier() {
		return categoryId;
	}

	@Override
	public Component getTitle() {
		return title.get();
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
	 * width {@link #setupDisplay} draws with, so the reservation cannot disagree with the drawing. The rule,
	 * its floor and its ceiling are the shared page card's ({@code InfoPageLayout}, MOD-716).
	 *
	 * <p>Measured live rather than cached: the wrap depends on the active language and on
	 * {@link dev.alaindustrial.Config} values interpolated into the lines, both of which change
	 * without this object being rebuilt.
	 *
	 * <p>Falls back to the floor if the font is not up yet — REI can query a category before the
	 * client finishes loading, and a crash there would take the whole recipe screen with it.
	 */
	private int bodyLines() {
		return InfoPageLayout.bodyLines(pages, DISPLAY_WIDTH - PADDING_X * 2);
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
				.leftAligned().noShadow().color(InfoPageLayout.TITLE_COLOR, 0xFFCCCCCC));
		y += LINE_HEIGHT + TITLE_TO_BODY_GAP;

		// Body: word-wrap each source line against the available width (Font.split-style), then render
		// one Label per visual line. createLabel needs a Component, so we seed an empty one and override
		// the text via Label.message(FormattedText) — the splitter yields FormattedText, preserving any
		// formatting, and avoids the overflow a single unwrapped Label would cause.
		for (Component line : display.lines()) {
			for (FormattedText wrapped : splitter.splitLines(line, maxTextWidth, Style.EMPTY)) {
				widgets.add(Widgets.createLabel(new Point(x, y), Component.empty())
						.leftAligned().noShadow().color(InfoPageLayout.BODY_COLOR, 0xFFBBBBBB).message(wrapped));
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
