package dev.alaindustrial.client.guide;

import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.client.guide.GuideContent.Book;
import dev.alaindustrial.client.guide.GuideContent.Entry;
import dev.alaindustrial.client.guide.GuideContent.Page;
import dev.alaindustrial.client.guide.GuideContent.Tab;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Guide Book reader (MOD-067, phase D). Full-screen, client-only {@link Screen}: a left column of
 * tab buttons, a paginated entry list per tab, and an entry view whose content is <em>reflowed</em>
 * into height-fitting pages so nothing overflows onto the nav. Entry headers, list rows and recipe
 * cells show live item icons via {@link GuiGraphicsExtractor#item}; the recipe 3×3 grid is baked from
 * the mod's recipe JSON by the generator (client can't resolve recipes by id in 26.2).
 *
 * <p>26.2 render pipeline: all drawing happens in {@link #extractBackground}; nav is vanilla buttons.
 */
public final class GuideBookScreen extends Screen {
	private static final int HEADER = 22;   // dark header band height (title + Wiki + Close)
	private static final int TAB_W = 116;
	private static final int TAB_H = 18;
	private static final int ROW = 20;
	private static final int CELL = 18;     // recipe slot pitch
	private static final int ICON = 16;
	private static final int GUTTER = 22;   // left space for a list-row icon
	private static final int HEADER_BG = 0xFF000000; // black title bar
	private static final int TITLE_COLOR = 0xFFF4F4F4; // light title on the black bar
	private static final String WIKI_BASE = "https://alamine-team-org.github.io/AlaIndustrial/";

	private final Book book;
	private int tab = 0;
	private int entry = -1;       // -1 = entry-list mode
	private int page = 0;         // reflowed screen-page index in entry mode
	private int listOffset = 0;

	// Layout (recomputed each init()).
	private int panelX, panelY, panelW, panelH;
	private int contentX, contentTop, contentRight, contentBottom, contentW;
	private int perPage = 1;
	private int wikiIconX; // where the knowledge-book icon is drawn on the Wiki button

	// List mode: parallel arrays of visible entry index → row Y (for drawing icons in the gutter).
	private final List<int[]> listIcons = new ArrayList<>(); // {entryIndex, iconY}
	// Entry mode: content reflowed into pages of rows.
	private List<List<Row>> flowPages = List.of();
	private int flowTop;

	public GuideBookScreen() {
		super(Component.translatable("item.alaindustrial.guide_book"));
		this.book = GuideContent.load();
		// Land straight on the Welcome page (the first tab is a single-entry intro) instead of a list.
		if (!book.tabs.isEmpty() && book.tabs.get(0).entries.size() == 1) {
			this.entry = 0;
		}
	}

	@Override
	protected void init() {
		panelW = Math.min(this.width - 32, 500);
		panelH = Math.min(this.height - 32, 300);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		int tabX = panelX + 8;
		int bodyTop = panelY + HEADER + 6;
		contentX = tabX + TAB_W + 10;
		contentTop = bodyTop;
		contentRight = panelX + panelW - 10;
		contentBottom = panelY + panelH - 10;
		contentW = contentRight - contentX;
		listIcons.clear();

		// Header buttons (right side): Close, and a compact icon Wiki button to its left.
		Component closeLabel = Component.translatable("guide.alaindustrial.close");
		int closeW = this.font.width(closeLabel) + 12;
		int closeX = panelX + panelW - closeW - 6;
		addRenderableWidget(Button.builder(closeLabel, b -> this.onClose()).bounds(closeX, panelY + 3, closeW, 16).build());
		wikiIconX = closeX - 26;
		Button wiki = Button.builder(Component.empty(), b -> openWiki()).bounds(wikiIconX, panelY + 3, 22, 16).build();
		wiki.setTooltip(Tooltip.create(Component.translatable("guide.alaindustrial.wiki")));
		addRenderableWidget(wiki);

		if (book.tabs.isEmpty()) {
			return;
		}
		tab = Math.max(0, Math.min(tab, book.tabs.size() - 1));
		for (int i = 0; i < book.tabs.size(); i++) {
			int idx = i;
			Button b = Button.builder(Component.literal(book.tabs.get(i).title), p -> selectTab(idx))
					.bounds(tabX, bodyTop + i * ROW, TAB_W, TAB_H).build();
			if (i == tab) {
				b.active = false;
			}
			addRenderableWidget(b);
		}

		Tab cur = book.tabs.get(tab);
		if (entry < 0) {
			buildList(cur);
		} else {
			buildEntry(cur);
		}
	}

	private void buildList(Tab cur) {
		int avail = contentBottom - contentTop - 4;
		perPage = Math.max(1, avail / ROW);
		boolean needNav = cur.entries.size() > perPage;
		if (needNav) {
			perPage = Math.max(1, (avail - ROW) / ROW);
		}
		int maxOffset = Math.max(0, cur.entries.size() - perPage);
		listOffset = Math.max(0, Math.min(listOffset, maxOffset));
		int shown = Math.min(perPage, cur.entries.size() - listOffset);
		for (int k = 0; k < shown; k++) {
			int ei = listOffset + k;
			int rowY = contentTop + k * ROW;
			addRenderableWidget(Button.builder(Component.literal(cur.entries.get(ei).title), p -> openEntry(ei))
					.bounds(contentX + GUTTER, rowY, contentW - GUTTER, TAB_H).build());
			listIcons.add(new int[]{ei, rowY + 1});
		}
		if (needNav) {
			int navY = contentTop + perPage * ROW + 2;
			addNav(contentX + GUTTER, navY, listOffset > 0, listOffset + perPage < cur.entries.size(),
					() -> { listOffset = Math.max(0, listOffset - perPage); rebuildWidgets(); },
					() -> { listOffset += perPage; rebuildWidgets(); });
		}
	}

	private void buildEntry(Tab cur) {
		Entry e = cur.entries.get(clamp(entry, cur.entries.size()));
		// Single-entry tabs (intro pages) are opened directly, so there's no list to go Back to.
		if (cur.entries.size() > 1) {
			addRenderableWidget(Button.builder(Component.translatable("gui.back"), p -> {
				entry = -1;
				page = 0;
				rebuildWidgets();
			}).bounds(contentX, contentTop, 60, TAB_H).build());
		}

		flowTop = contentTop + TAB_H + 8;          // below the Back button + entry title row
		int navRow = TAB_H + 2;
		int flowBottom = contentBottom - navRow;
		flowPages = paginate(buildRows(e), Math.max(ROW, flowBottom - flowTop));
		page = clamp(page, flowPages.size());

		if (flowPages.size() > 1) {
			int navY = contentBottom - TAB_H;
			addNav(contentX, navY, page > 0, page < flowPages.size() - 1,
					() -> { page = Math.max(0, page - 1); rebuildWidgets(); },
					() -> { page = Math.min(flowPages.size() - 1, page + 1); rebuildWidgets(); });
		}
	}

	private void addNav(int x, int y, boolean prevOn, boolean nextOn, Runnable prev, Runnable next) {
		Button p = Button.builder(Component.literal("◀"), b -> prev.run()).bounds(x, y, 40, TAB_H).build();
		p.active = prevOn;
		addRenderableWidget(p);
		Button n = Button.builder(Component.literal("▶"), b -> next.run()).bounds(contentRight - 40, y, 40, TAB_H).build();
		n.active = nextOn;
		addRenderableWidget(n);
	}

	private void selectTab(int i) {
		tab = i;
		// A single-entry tab (the intro tabs) opens its one entry directly; multi-entry tabs show a list.
		entry = book.tabs.get(i).entries.size() == 1 ? 0 : -1;
		page = 0;
		listOffset = 0;
		rebuildWidgets();
	}

	private void openEntry(int i) {
		entry = i;
		page = 0;
		rebuildWidgets();
	}

	// ── Content reflow ────────────────────────────────────────────────────────────────────────────

	private enum Kind { HEADER, LINE, GAP, RECIPE }

	private static final class Row {
		final Kind kind;
		final FormattedCharSequence seq; // LINE / HEADER
		final int color;
		final Page recipe;               // RECIPE
		final int height;

		Row(Kind kind, FormattedCharSequence seq, int color, Page recipe, int height) {
			this.kind = kind;
			this.seq = seq;
			this.color = color;
			this.recipe = recipe;
			this.height = height;
		}
	}

	/** Flatten an entry's JSON pages into a linear list of drawable rows. */
	private List<Row> buildRows(Entry e) {
		List<Row> rows = new ArrayList<>();
		int lh = this.font.lineHeight;
		for (Page p : e.pages) {
			switch (p.type) {
				case "stats" -> {
					for (String line : p.lines) {
						rows.add(line(Component.literal(line).getVisualOrderText(), GuiStyle.TEXT, lh));
					}
				}
				case "recipe" -> {
					if (!p.recipeType.isEmpty()) {
						rows.add(new Row(Kind.HEADER, Component.literal(p.recipeType).getVisualOrderText(),
								GuiStyle.TEXT_DIM, null, lh + 2));
					}
					rows.add(new Row(Kind.RECIPE, null, 0, p, 3 * CELL + 4));
				}
				default -> {
					if (!p.title.isEmpty() && !p.title.equals(e.title)) {
						rows.add(new Row(Kind.HEADER, Component.literal(p.title).getVisualOrderText(),
								GuiStyle.TEXT, null, lh + 3));
					}
					for (String para : p.text.split("\n\n")) {
						if (para.isBlank()) {
							continue;
						}
						for (FormattedCharSequence l : this.font.split(Component.literal(para), contentW)) {
							rows.add(line(l, GuiStyle.TEXT, lh));
						}
						rows.add(new Row(Kind.GAP, null, 0, null, 4)); // paragraph spacing
					}
				}
			}
			rows.add(new Row(Kind.GAP, null, 0, null, 6)); // between JSON pages
		}
		return rows;
	}

	private Row line(FormattedCharSequence seq, int color, int h) {
		return new Row(Kind.LINE, seq, color, null, h);
	}

	/** Greedily pack rows into screen-pages that each fit {@code maxHeight}. */
	private List<List<Row>> paginate(List<Row> rows, int maxHeight) {
		List<List<Row>> pages = new ArrayList<>();
		List<Row> curPage = new ArrayList<>();
		int h = 0;
		for (Row r : rows) {
			if (r.kind == Kind.GAP && curPage.isEmpty()) {
				continue; // no leading gaps on a page
			}
			if (h + r.height > maxHeight && !curPage.isEmpty()) {
				pages.add(curPage);
				curPage = new ArrayList<>();
				h = 0;
				if (r.kind == Kind.GAP) {
					continue;
				}
			}
			curPage.add(r);
			h += r.height;
		}
		if (!curPage.isEmpty()) {
			pages.add(curPage);
		}
		return pages.isEmpty() ? List.of(List.of()) : pages;
	}

	// ── Rendering ─────────────────────────────────────────────────────────────────────────────────

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		drawPanel(g, panelX, panelY, panelW, panelH);
		// Dark header bar with a light, readable title; a thin recess line separates it from the body.
		g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + HEADER, HEADER_BG);
		g.fill(panelX + 1, panelY + HEADER, panelX + panelW - 1, panelY + HEADER + 1, GuiStyle.PANEL_LO);
		g.centeredText(this.font, this.title, panelX + panelW / 2, panelY + 7, TITLE_COLOR);

		if (book.tabs.isEmpty()) {
			g.text(this.font, Component.literal("The guide has no content yet."),
					contentX, contentTop, GuiStyle.TEXT_DIM, false);
			return;
		}

		g.fill(contentX - 6, contentTop, contentX - 5, contentBottom, GuiStyle.PANEL_LO);
		Tab cur = book.tabs.get(tab);
		if (entry < 0) {
			for (int[] ic : listIcons) {
				drawIcon(g, cur.entries.get(ic[0]).icon, contentX, ic[1]);
			}
		} else {
			drawEntry(g, cur);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick); // draws the widgets (buttons)
		// A knowledge-book icon on top of the (icon-only) Wiki button — reads as "open external guide".
		g.item(new ItemStack(Items.KNOWLEDGE_BOOK), wikiIconX + 3, panelY + 3);
	}

	private void openWiki() {
		String url = wikiUrl();
		this.minecraft.setScreenAndShow(new ConfirmLinkScreen(confirmed -> {
			if (confirmed) {
				Util.getPlatform().openUri(url);
			}
			this.minecraft.setScreenAndShow(this);
		}, url, true));
	}

	/** Locale-aware wiki landing page (ru for Russian clients, en otherwise). */
	private String wikiUrl() {
		String lang = this.minecraft.options.languageCode;
		String seg = (lang != null && lang.startsWith("ru")) ? "ru" : "en";
		return WIKI_BASE + seg + "/index.html";
	}

	private void drawEntry(GuiGraphicsExtractor g, Tab cur) {
		Entry e = cur.entries.get(clamp(entry, cur.entries.size()));
		int titleY = contentTop + 2;
		// Leave room for the Back button only on multi-entry tabs; intro pages start at the left edge.
		int titleX = cur.entries.size() > 1 ? contentX + 66 : contentX;
		drawIcon(g, e.icon, titleX, titleY - 2);
		g.text(this.font, Component.literal(e.title), titleX + ICON + 4, titleY, GuiStyle.COPPER, false);
		g.fill(contentX, titleY + 15, contentRight, titleY + 16, GuiStyle.PANEL_LO); // accent under the title (moved +2px)

		List<Row> rows = flowPages.get(clamp(page, flowPages.size()));
		int y = flowTop;
		for (Row r : rows) {
			switch (r.kind) {
				case LINE, HEADER -> g.text(this.font, r.seq, contentX, y, r.color, false);
				case RECIPE -> drawRecipe(g, r.recipe, contentX, y);
				case GAP -> { }
			}
			y += r.height;
		}

		if (flowPages.size() > 1) {
			g.centeredText(this.font, Component.literal((page + 1) + " / " + flowPages.size()),
					(contentX + contentRight) / 2, contentBottom - TAB_H + 5, GuiStyle.TEXT_DIM);
		}
	}

	private void drawRecipe(GuiGraphicsExtractor g, Page p, int x, int y) {
		for (int i = 0; i < 9 && i < p.grid.size(); i++) {
			int cx = x + (i % 3) * CELL;
			int cy = y + (i / 3) * CELL;
			GuiStyle.slot(g, cx, cy);
			ItemStack st = stackFromId(p.grid.get(i));
			if (!st.isEmpty()) {
				g.item(st, cx + 1, cy + 1);
			}
		}
		int arrowX = x + 3 * CELL + 6;
		int midY = y + CELL;
		g.text(this.font, Component.literal("→"), arrowX, midY + 4, GuiStyle.TEXT);
		int rx = arrowX + 16;
		GuiStyle.slot(g, rx, midY);
		ItemStack result = stackFromId(p.resultId);
		if (!result.isEmpty()) {
			result.setCount(Math.max(1, p.resultCount));
			g.item(result, rx + 1, midY + 1);
			g.itemDecorations(this.font, result, rx + 1, midY + 1);
		}
	}

	private void drawIcon(GuiGraphicsExtractor g, String id, int x, int y) {
		ItemStack st = stackFromId(id);
		if (!st.isEmpty()) {
			g.item(st, x, y);
		}
	}

	/** Panel face with a bevel border, without GuiStyle's fixed copper header strip (we draw our own). */
	private static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, GuiStyle.PANEL);
		g.fill(x, y, x + w, y + 1, GuiStyle.PANEL_HI);
		g.fill(x, y, x + 1, y + h, GuiStyle.PANEL_HI);
		g.fill(x, y + h - 1, x + w, y + h, GuiStyle.PANEL_LO);
		g.fill(x + w - 1, y, x + w, y + h, GuiStyle.PANEL_LO);
	}

	/** Resolve an item id (or "" / unknown → EMPTY). Tag ids ("#...") are not shown as icons in v1. */
	private static ItemStack stackFromId(String id) {
		if (id == null || id.isEmpty() || id.startsWith("#")) {
			return ItemStack.EMPTY;
		}
		Identifier iid = Identifier.tryParse(id);
		if (iid == null) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(BuiltInRegistries.ITEM.getValue(iid));
	}

	private static int clamp(int v, int size) {
		return Math.max(0, Math.min(v, size - 1));
	}
}
