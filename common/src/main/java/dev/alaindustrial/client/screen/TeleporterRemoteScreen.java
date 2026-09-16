package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.hud.TeleportNotice;
import dev.alaindustrial.client.screen.tabs.SideTabStrip;
import dev.alaindustrial.client.screen.tabs.TabPage;
import dev.alaindustrial.client.screen.teleporter.LogTabPage;
import dev.alaindustrial.client.screen.teleporter.MapTabPage;
import dev.alaindustrial.client.screen.teleporter.RandomTabPage;
import dev.alaindustrial.client.screen.teleporter.StationsTabPage;
import dev.alaindustrial.menu.TeleporterRemoteMenu;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.TeleportRenamePayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * The remote's screen (MOD-093): a panel with tabs down its left side since MOD-628, in the reactor controller's style.
 *
 * <p><b>The screen owns the frame, the pages own the rest.</b> The panel, the tab strip, the selected tab's name and the
 * readiness chip are the same on every tab; everything under the header belongs to the selected {@link TabPage}.
 *
 * <p>The tabs shipped one release at a time (MOD-627): «Map» (MOD-629), «Random» (MOD-630), «Stations» (MOD-628) and
 * «Log» (MOD-631). The selected station is one for the whole screen — picking it on any tab picks it on all of them.
 *
 * <p>Nothing here decides anything: a click sends the server an index and the server re-reads the real remote.
 */
public class TeleporterRemoteScreen extends AbstractContainerScreen<TeleporterRemoteMenu> {

	public static final Identifier TEXTURE = Industrialization.id("textures/gui/container/teleporter_remote.png");
	public static final int TEX_SIZE = 256;

	/** Panel size; must match {@code tools/gen_teleporter_remote_gui.py}. */
	public static final int PANEL_W = 236;
	public static final int PANEL_H = 192;

	/** The strip starts below the panel's rounded corner — 3 px of corner, 3 px of air — not corner on corner. */
	private static final int TAB_TOP = 6;

	private static final int TITLE_X = 8;
	private static final int TITLE_Y = 6;
	private static final int TITLE_COLOUR = 0xFF404040;
	private static final int CHIP_RIGHT = 228;
	private static final int CHIP_Y = 4;
	private static final int CHIP_H = 11;
	private static final int CHIP_BACK = 0xFF2A2D33;

	/** The tab a player last had open this session, or -1 before the first opening. Client-only by construction. */
	private static int lastPage = -1;
	/** The station a player last picked, so the next opening lands on it. */
	private static int lastStation = -1;

	private final SideTabStrip tabs = new SideTabStrip(TAB_TOP);
	private final MapTabPage map = new MapTabPage(this);
	private final RandomTabPage random = new RandomTabPage(this);
	private final StationsTabPage stations = new StationsTabPage(this);
	private final LogTabPage log = new LogTabPage(this);
	private final List<TabPage> pages = List.of(map, random, stations, log);
	private int selected;
	/** Whether the opening tab has been chosen; {@code init} runs again on every resize and must not re-choose. */
	private boolean pageChosen;

	public TeleporterRemoteScreen(TeleporterRemoteMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PANEL_W, PANEL_H);
	}

	@Override
	protected void init() {
		super.init();
		// A refusal from a previous screen has nothing to say about this one.
		TeleportNotice.clear();
		for (TabPage page : pages) {
			page.init();
		}
		if (!pageChosen) {
			pageChosen = true;
			// The map first; the tab last used after that; the list when there is nothing to put on a map.
			selected = this.menu.points().isEmpty() ? pages.indexOf(stations)
					: lastPage >= 0 && lastPage < pages.size() ? lastPage : pages.indexOf(map);
		}
		showSelected();
	}

	private void showSelected() {
		for (int i = 0; i < pages.size(); i++) {
			pages.get(i).setShown(i == selected);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		for (TabPage page : pages) {
			page.tick();
		}
	}

	/** Only the open tab's name — this screen has no player inventory for vanilla's second label to name. */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, pages.get(selected).title(), TITLE_X, TITLE_Y, TITLE_COLOUR, false);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, PANEL_W, PANEL_H,
				TEX_SIZE, TEX_SIZE);
		tabs.draw(graphics, this.leftPos, this.topPos, pages, selected);
		drawChip(graphics);
		pages.get(selected).draw(graphics, mouseX, mouseY);
	}

	/**
	 * The selected station's readiness in one word, right-aligned in the header — the reactor chip's shape. On «Random» it
	 * is readiness for a random jump, which a station with no chip lacks however ready it is for a targeted one.
	 */
	private void drawChip(GuiGraphicsExtractor graphics) {
		StationsTabPage.Chip chip = pages.get(selected) == random ? RandomTabPage.chip(this.menu, this.minecraft)
				: StationsTabPage.chip(this.menu);
		if (chip == null) {
			return;
		}
		int right = this.leftPos + CHIP_RIGHT;
		int left = right - this.font.width(chip.label()) - 14;
		int top = this.topPos + CHIP_Y;
		graphics.fill(left, top, right, top + CHIP_H, CHIP_BACK);
		graphics.fill(left + 3, top + 3, left + 8, top + 8, chip.colour());
		graphics.text(this.font, chip.label(), left + 11, top + 2, chip.colour(), false);
	}

	// ── Input ────────────────────────────────────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int tab = tabs.tabAt(event.x(), event.y(), this.leftPos, this.topPos, pages.size());
			if (tab >= 0) {
				if (tab != selected) {
					playUi(SoundEvents.UI_BUTTON_CLICK.value());
				}
				selectPage(tab);
				return true;
			}
			if (pages.get(selected).mouseClicked(event)) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean handled = super.mouseReleased(event);
		pages.get(selected).mouseReleased(event);
		return handled;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (pages.get(selected).mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** As vanilla's anvil: Escape closes, and a name being typed keeps every other key, the inventory key included. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			this.minecraft.player.closeContainer();
			return true;
		}
		if (pages.get(selected) == stations && stations.keyPressed(event)) {
			return true;
		}
		if (pages.get(selected) == map && map.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int tab = tabs.tabAt(mouseX, mouseY, this.leftPos, this.topPos, pages.size());
		if (tab >= 0) {
			graphics.setTooltipForNextFrame(this.font, pages.get(tab).title(), mouseX, mouseY);
			return;
		}
		if (pages.get(selected).tooltip(graphics, mouseX, mouseY)) {
			return;
		}
		super.extractTooltip(graphics, mouseX, mouseY);
	}

	/**
	 * The strip sticks out to the left of the panel, where JEI and REI park their bookmarks. Not a {@code MachineScreen},
	 * so the viewers' handlers for those do not reach it; each viewer's plugin registers this screen by name.
	 */
	public List<net.minecraft.client.renderer.Rect2i> extraGuiAreas() {
		return List.of(tabs.area(this.leftPos, this.topPos, pages.size()));
	}

	private void selectPage(int index) {
		if (index < 0 || index >= pages.size() || index == selected) {
			return;
		}
		selected = index;
		lastPage = index;
		showSelected();
	}

	// ── What a page may use ─────────────────────────────────────────────────────────────────────────

	/** The «Map» tab, for a stand that drives it directly. */
	public MapTabPage mapPage() {
		return map;
	}

	/** The «Random» tab: what the map's «Random ›» opens, and what a stand photographs. */
	public RandomTabPage randomPage() {
		return random;
	}

	/** The «Stations» tab, for a stand that drives it directly. */
	public StationsTabPage stationsPage() {
		return stations;
	}

	/** The «Log» tab, for a stand that drives it directly. */
	public LogTabPage logPage() {
		return log;
	}

	/** Opens a tab as a click on it does — for a stand that photographs each tab. */
	public void showPage(TabPage page) {
		selectPage(pages.indexOf(page));
	}

	/** Picks a station for the whole screen, as a click on its row or its dot does. */
	public void selectStation(int index) {
		stations.select(index);
	}

	public void rememberStation(int index) {
		lastStation = index;
	}

	public int rememberedStation() {
		return lastStation;
	}

	public int left() {
		return this.leftPos;
	}

	public int top() {
		return this.topPos;
	}

	public Font font() {
		return this.font;
	}

	public Minecraft minecraftClient() {
		return this.minecraft;
	}

	public <T extends GuiEventListener & Renderable & NarratableEntry> T addPageWidget(T widget) {
		return addRenderableWidget(widget);
	}

	public void playUi(SoundEvent sound) {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
	}

	/** Vanilla's container-button packet — no mod networking for an action that is just two ints. */
	public void press(TeleporterRemoteMenu.Action action, int index) {
		this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
				TeleporterRemoteMenu.buttonId(action, index));
	}

	/** A raw container-button id — the log's "read up to" mark, which is not an {@link TeleporterRemoteMenu.Action}. */
	public void sendButton(int buttonId) {
		this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
	}

	/** Rename carries a string, so it is the one action that needs the mod's own C2S payload. */
	public void sendRename(int index, String name) {
		NetworkDispatcher.get().sendToServer(new TeleportRenamePayload(index, name));
	}
}
