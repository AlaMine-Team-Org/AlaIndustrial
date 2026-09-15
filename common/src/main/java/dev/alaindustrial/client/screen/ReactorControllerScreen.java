package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.screen.reactor.ConsoleTabPage;
import dev.alaindustrial.client.screen.reactor.CoolantTabPage;
import dev.alaindustrial.client.screen.reactor.LogTabPage;
import dev.alaindustrial.client.screen.reactor.ReactorConsole;
import dev.alaindustrial.client.screen.reactor.ReactorTabPage;
import dev.alaindustrial.client.screen.reactor.RoomTabPage;
import dev.alaindustrial.client.screen.reactor.StackGrid;
import dev.alaindustrial.client.screen.reactor.ZoneTabPage;
import dev.alaindustrial.client.screen.tabs.SideTabStrip;
import dev.alaindustrial.menu.ReactorControllerMenu;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Reactor Controller — a panel with tabs down its left side (MOD-617).
 *
 * <p><b>The screen owns the frame, the pages own the rest.</b> The panel, the tab strip, the status chip and
 * the accident countdown are the same on every tab, because the questions they answer — which tab am I on, is
 * the reactor all right, how long have I got — do not depend on which tab is open. Everything under the header
 * belongs to the selected {@link ReactorTabPage}.
 *
 * <p><b>The tabs are the shared {@link SideTabStrip}</b> (MOD-628), flush with the panel's top: this screen has
 * stood its first tab in the corner since it shipped.
 *
 * <p><b>No player inventory.</b> The controller holds nothing, so {@link ReactorControllerMenu} carries
 * no slots at all, and the panel is free for the reactor.
 */
public class ReactorControllerScreen extends MachineScreen<ReactorControllerMenu> {

	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/reactor_controller.png");

	/** Panel size; must match {@code tools/gen_reactor_controller_gui.py}. */
	public static final int IMAGE_WIDTH = 236;
	public static final int IMAGE_HEIGHT = 184;

	public static final int TITLE_X = 8;
	public static final int TITLE_Y = 6;
	public static final int CHIP_RIGHT = 228;
	public static final int CHIP_Y = 4;
	public static final int CHIP_H = 11;
	/** The accident countdown: a bar with no figure on it, under the header on every tab. */
	public static final int COUNTDOWN_Y = 17;
	public static final int COUNTDOWN_H = 2;

	/** The tabs, in the strip's order. */
	public static final int PAGE_CONSOLE = 0;
	public static final int PAGE_ROOM = 1;
	public static final int PAGE_ZONE = 2;
	public static final int PAGE_COOLANT = 3;
	public static final int PAGE_LOG = 4;

	private static final int TITLE_COLOUR = 0xFF404040;
	private static final int CHIP_BACK = 0xFF2A2D33;
	private static final int CHIP_ALARM_DIM = 0xFF7A2A22;
	private static final int COUNTDOWN_TRACK = 0xFF2A2D33;
	private static final int COUNTDOWN_FILL = 0xFFD63A2A;

	/**
	 * The tab a player last had open, for the next time they open a controller. Client-only by
	 * construction — this class never loads on a dedicated server.
	 */
	private static int lastPage;

	private final SideTabStrip tabs = new SideTabStrip(0);
	/** The grid of stacks the «Core» and «Coolant» tabs share, with the stack picked on either. */
	private final StackGrid stackGrid = new StackGrid(this);
	private final List<ReactorTabPage> pages;
	private int selected;
	/** Whether the opening tab has been decided — by the channels' first arrival or by the player. */
	private boolean pageSettled;

	public ReactorControllerScreen(ReactorControllerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.pages = List.of(new ConsoleTabPage(this), new RoomTabPage(this), new ZoneTabPage(this),
				new CoolantTabPage(this), new LogTabPage(this));
		this.selected = Math.min(lastPage, this.pages.size() - 1);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	/**
	 * No statistics tab. The controller has no upgrade panel, so the chip that feeds the statistics can
	 * never be fitted and the tab would only ever ask for one — and it sat on top of this screen's own tab
	 * strip, which is how the playtest found it.
	 */
	@Override
	protected boolean hasStatsTab() {
		return false;
	}

	@Override
	protected void init() {
		super.init();
		for (ReactorTabPage page : pages) {
			page.init();
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
		// The opening tab is decided before the pages tick (MOD-622): the «Log» tab marks what it shows as read, and on a
		// room that opens on «Room» it must not do so for the one tick it was still the remembered tab.
		settleOpeningPage();
		for (ReactorTabPage page : pages) {
			page.tick();
		}
	}

	/**
	 * Opens a room still being built on the «Room» tab (MOD-619) — the tab that says what to fix — unless the
	 * player has already picked one.
	 *
	 * <p>Decided on the first tick the server's channels have arrived, not in the constructor: a menu fresh off
	 * the wire reads all zeros, and zero is the ordinal of a sealed room. Arrival is told by the largest room the
	 * scan accepts, which the server always sends and which is never zero. The player's last tab is left alone:
	 * a room that needed building once does not make «Room» the tab they return to.
	 */
	private void settleOpeningPage() {
		if (pageSettled || this.menu.getRoomMaxInner() <= 0) {
			return;
		}
		pageSettled = true;
		ReactorConsole.Readout r = ConsoleTabPage.readout(this.menu);
		if (!r.formed() && !r.bare()) {
			selected = PAGE_ROOM;
			showSelected();
		}
	}

	/** Only the open tab's name. No block title and no "Inventory" — there is no inventory. */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, pages.get(selected).title(), TITLE_X, TITLE_Y, TITLE_COLOUR, false);
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);
		tabs.draw(graphics, this.leftPos, this.topPos, pages, selected);
		drawChip(graphics);
		drawCountdown(graphics);
		pages.get(selected).draw(graphics, mouseX, mouseY);
	}

	/** The reactor's state in one word, right-aligned in the header. An alarm blinks. */
	private void drawChip(GuiGraphicsExtractor graphics) {
		ReactorConsole.Verdict verdict = ReactorConsole.verdict(ConsoleTabPage.readout(this.menu));
		Component label = Component.translatable(verdict.translationKey());
		int right = this.leftPos + CHIP_RIGHT;
		int left = right - this.font.width(label) - 14;
		int top = this.topPos + CHIP_Y;
		int colour = toneColour(verdict.tone());
		if (verdict.tone() == ReactorConsole.Tone.ALARM && (System.currentTimeMillis() / 500L) % 2L == 1L) {
			colour = CHIP_ALARM_DIM;
		}
		graphics.fill(left, top, right, top + CHIP_H, CHIP_BACK);
		graphics.fill(left + 3, top + 3, left + 8, top + 8, colour);
		graphics.text(this.font, label, left + 11, top + 2, colour, false);
	}

	/**
	 * The accident countdown: a bar that empties, with no figure on it. Every accident rolls its own length
	 * so a player cannot learn it; printing the seconds would hand that knowledge straight back.
	 */
	private void drawCountdown(GuiGraphicsExtractor graphics) {
		int percent = Math.min(100, this.menu.getBlastPercent());
		if (percent <= 0) {
			return;
		}
		int left = this.leftPos + ConsoleTabPage.CONTENT_LEFT;
		int top = this.topPos + COUNTDOWN_Y;
		int width = ConsoleTabPage.CONTENT_RIGHT - ConsoleTabPage.CONTENT_LEFT;
		graphics.fill(left, top, left + width, top + COUNTDOWN_H, COUNTDOWN_TRACK);
		graphics.fill(left, top, left + width * percent / 100, top + COUNTDOWN_H, COUNTDOWN_FILL);
	}

	/** A tone's colour on the dark chip and advice box — the only two backgrounds it is drawn on. */
	public static int toneColour(ReactorConsole.Tone tone) {
		return switch (tone) {
			case GOOD -> 0xFF7FD08A;
			case WARN -> 0xFFE8B04A;
			case ALARM -> 0xFFFF6B5A;
			case IDLE -> 0xFFB9C0C7;
		};
	}

	// ── Input ────────────────────────────────────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Not under an open statistics panel: it can be dragged over the strip, and a click on it belongs to it.
		if (event.button() == 0 && !this.menu.isStatsPanelOpen()) {
			int tab = tabAt(event.x(), event.y());
			if (tab >= 0) {
				if (tab != selected) {
					this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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

	/** The wheel goes to the open tab first — the «Log» tab scrolls its list with it (MOD-622). */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (pages.get(selected).mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!this.menu.isStatsPanelOpen()) {
			int tab = tabAt(mouseX, mouseY);
			if (tab >= 0) {
				graphics.setTooltipForNextFrame(this.font, pages.get(tab).title(), mouseX, mouseY);
				return;
			}
			if (pages.get(selected).tooltip(graphics, mouseX, mouseY)) {
				return;
			}
		}
		super.extractTooltip(graphics, mouseX, mouseY);
	}

	/** The strip sticks out to the left of the frame, where JEI and REI park their bookmarks. */
	@Override
	public List<Rect2i> extraGuiAreas() {
		List<Rect2i> areas = super.extraGuiAreas();
		areas.add(tabs.area(this.leftPos, this.topPos, pages.size()));
		return areas;
	}

	private int tabAt(double mouseX, double mouseY) {
		return tabs.tabAt(mouseX, mouseY, this.leftPos, this.topPos, pages.size());
	}

	// ── What a page may use ─────────────────────────────────────────────────────────────────────────

	/** Opens a tab as the player's choice: it is remembered, and the opening tab is no longer decided for them. */
	public void selectPage(int index) {
		pageSettled = true;
		if (index < 0 || index >= pages.size() || index == selected) {
			return;
		}
		selected = index;
		lastPage = index;
		showSelected();
	}

	/** One of the tabs, for a stand that drives a page directly. */
	public ReactorTabPage page(int index) {
		return pages.get(index);
	}

	/** The open tab — {@link #PAGE_CONSOLE}, {@link #PAGE_ROOM}, {@link #PAGE_ZONE}, {@link #PAGE_COOLANT}, {@link #PAGE_LOG}. */
	public int selectedPage() {
		return selected;
	}

	/** Whether the opening tab has been decided — until then the open tab is not yet the one the player looks at. */
	public boolean openingPageSettled() {
		return pageSettled;
	}

	/** The grid of stacks, and the stack picked on it, that the «Core» and «Coolant» tabs share. */
	public StackGrid stackGrid() {
		return stackGrid;
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

	public <T extends GuiEventListener & Renderable & NarratableEntry> T addPageWidget(T widget) {
		return addRenderableWidget(widget);
	}

	/** {@link MachineScreen#drawFittedStatus}, for a page. */
	public void drawFitted(GuiGraphicsExtractor graphics, Component label, int y, int bandLeft, int bandRight,
			int colour) {
		drawFittedStatus(graphics, label, y, bandLeft, bandRight, colour);
	}

	/**
	 * Presses a container button on the server. The throttle rides this vanilla channel: the id is the
	 * requested depth in percent, and {@link ReactorControllerMenu#clickMenuButton} clamps nothing it did
	 * not send.
	 */
	public void sendButton(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}
}
