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
	private static final int PADDING_X = 6;
	private static final int TOP_PAD = 6;
	private static final int LINE_HEIGHT = 10;
	private static final int TITLE_TO_BODY_GAP = 4;
	private static final int BOTTOM_PAD = 8;
	/** Maximum body lines (post-wrap) we reserve vertical space for in the fixed category height. */
	private static final int MAX_BODY_LINES = 8;
	/** Standard recipe width this category uses; padded on both sides for text. */
	private static final int DISPLAY_WIDTH = 160;

	@Override
	public me.shedaniel.rei.api.common.category.CategoryIdentifier<? extends AlaInfoDisplay> getCategoryIdentifier() {
		return AlaInfoDisplay.CATEGORY;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("jei.alaindustrial.category.evolution");
	}

	@Override
	public Renderer getIcon() {
		// The category is about evolution chips; the day chip is a representative icon.
		return EntryStacks.of(dev.alaindustrial.registry.ModContent.ALIGNMENT_CHIP_DAY.get());
	}

	@Override
	public int getDisplayHeight() {
		// Fixed (not display-aware) — reserve space for the longest page after word-wrap.
		// MAX_BODY_LINES absorbs the T2 branches' 3 source lines expanding to ~5-8 visual lines.
		return TOP_PAD + LINE_HEIGHT + TITLE_TO_BODY_GAP + LINE_HEIGHT * MAX_BODY_LINES + BOTTOM_PAD;
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
