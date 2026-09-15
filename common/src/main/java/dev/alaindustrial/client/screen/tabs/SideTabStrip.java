package dev.alaindustrial.client.screen.tabs;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The strip of tabs down a panel's left edge (MOD-617, shared since MOD-628).
 *
 * <p><b>A component, not a base class.</b> The screens that carry it extend different bases — the reactor
 * controller a {@code MachineScreen}, the teleporter remote a plain container screen — so each holds a strip
 * and asks it to draw, to name the tab under the mouse and to report the area it covers outside the panel.
 *
 * <p><b>The tabs are the game's own.</b> They are blitted from the vanilla advancement screen's left-side
 * sprites by id at run time, with an item for an icon, so the strip reads as a Minecraft screen rather than a
 * lookalike. The sprites are referenced, never copied.
 *
 * <p><b>The strip's distance from the panel's top is the one thing screens choose.</b> At 0 the first tab
 * takes the corner sprite, the way the advancement window draws it. Anywhere lower every tab takes the middle
 * sprite: the corner sprite is shaped for a tab standing in the corner, and below the panel's rounded corner
 * it would leave a step.
 *
 * <p>Vanilla shows the strip only once there are two tabs. It is shown from the first here, because tabs
 * arrive one release at a time and the frame they share is part of what each one ships.
 */
public final class SideTabStrip {

	/** The vanilla left tab: 32×28, overlapping the window by four pixels (AdvancementTabType.LEFT). */
	public static final int TAB_W = 32;
	public static final int TAB_H = 28;
	public static final int TAB_OVERLAP = 4;
	private static final int ICON_X = 10;
	private static final int ICON_Y = 5;

	private static final Identifier TOP = Identifier.withDefaultNamespace("advancements/tab_left_top");
	private static final Identifier TOP_SELECTED = Identifier.withDefaultNamespace("advancements/tab_left_top_selected");
	private static final Identifier MIDDLE = Identifier.withDefaultNamespace("advancements/tab_left_middle");
	private static final Identifier MIDDLE_SELECTED =
			Identifier.withDefaultNamespace("advancements/tab_left_middle_selected");

	private static final int BADGE_EDGE = 0xFF000000;

	private final int topOffset;

	/** @param topOffset how far below the panel's top edge the first tab starts, in GUI pixels */
	public SideTabStrip(int topOffset) {
		this.topOffset = topOffset;
	}

	/**
	 * Draws the strip over the panel's left edge, the way the advancement screen draws its tabs over its window,
	 * so the selected tab's sprite merges into the frame. A tab other than the selected one carries its page's
	 * badge, if the page has one.
	 */
	public void draw(GuiGraphicsExtractor graphics, int panelLeft, int panelTop, List<? extends TabPage> pages,
			int selected) {
		for (int i = 0; i < pages.size(); i++) {
			int x = x(panelLeft);
			int y = y(panelTop, i);
			boolean isSelected = i == selected;
			Identifier sprite = i == 0 && topOffset == 0
					? (isSelected ? TOP_SELECTED : TOP)
					: (isSelected ? MIDDLE_SELECTED : MIDDLE);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, TAB_W, TAB_H);
			graphics.item(pages.get(i).icon(), x + ICON_X, y + ICON_Y);
			int badge = isSelected ? 0 : pages.get(i).badgeColour();
			if (badge != 0) {
				graphics.fill(x + 3, y + 3, x + 9, y + 9, BADGE_EDGE);
				graphics.fill(x + 4, y + 4, x + 8, y + 8, badge);
			}
		}
	}

	/** The tab under the mouse, or -1. Only the part outside the panel counts: the overlap is the frame's. */
	public int tabAt(double mouseX, double mouseY, int panelLeft, int panelTop, int count) {
		int x = x(panelLeft);
		if (mouseX < x || mouseX >= x + TAB_W - TAB_OVERLAP) {
			return -1;
		}
		for (int i = 0; i < count; i++) {
			int y = y(panelTop, i);
			if (mouseY >= y && mouseY < y + TAB_H) {
				return i;
			}
		}
		return -1;
	}

	/** The part of the strip outside the panel — where JEI and REI park their bookmarks, so they must keep off it. */
	public Rect2i area(int panelLeft, int panelTop, int count) {
		return new Rect2i(x(panelLeft), panelTop + topOffset, TAB_W - TAB_OVERLAP, TAB_H * count);
	}

	private static int x(int panelLeft) {
		return panelLeft - TAB_W + TAB_OVERLAP;
	}

	private int y(int panelTop, int index) {
		return panelTop + topOffset + index * TAB_H;
	}
}
