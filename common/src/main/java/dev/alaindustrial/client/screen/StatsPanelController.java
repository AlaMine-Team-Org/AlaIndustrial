package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.menu.MachineMenu;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * State, geometry and drag handling for the statistics panel (MOD-125) — the mirror image of
 * {@link UpgradePanelController}.
 *
 * <p><b>It docks on the LEFT.</b> The upgrade panel owns the right side, and two docks on the same edge
 * meant the statistics tab printed over the upgrade panel's art and the panel's own text. Mirroring the
 * dock removes the overlap by construction instead of by draw-order tricks, and gives the player a stable
 * mental map: gear right, chart left.
 *
 * <p>Draggable and persisted, like the upgrade panel, so a player who prefers it elsewhere is not
 * fighting the layout every time a screen opens.
 */
public final class StatsPanelController implements ScreenOverlay {

	/**
	 * Atlas region of the chart tab. Two variants sit side by side: the right-facing one at u=0 (unused
	 * while the dock is on the left, kept because the mirrored art is generated from it) and the
	 * left-facing one at u=24, whose lip points away from the GUI the way the gear's does on its side.
	 */
	static final int TAB_U = 24, TAB_V = 150, TAB_W = 24, TAB_H = 34;

	/** Panel box, same footprint as the upgrade panel so both docks read as one system. */
	static final int PANEL_W = 159, PANEL_H = 145;

	/** Docked top edge, level with the gear tab on the other side. */
	private static final int DOCK_Y = MachineMenu.PANEL_Y;

	private final MachineMenu menu;
	private final OverlayHost host;
	private int panelDX;
	private int panelDY;
	private boolean dragging;
	private double grabX;
	private double grabY;
	private long tabPressUntil;
	private long closePressUntil;
	private boolean pendingClose;

	StatsPanelController(OverlayHost host, MachineMenu menu, int leftPos, int topPos, int screenWidth,
			int screenHeight) {
		this.host = host;
		this.menu = menu;
		this.panelDX = Mth.clamp(AlaClientConfig.statsPanelDX, minDX(leftPos), maxDX(leftPos, screenWidth));
		this.panelDY = Mth.clamp(AlaClientConfig.statsPanelDY, minDY(topPos), maxDY(topPos, screenHeight));
	}

	public int panelDX() {
		return panelDX;
	}

	public int panelDY() {
		return panelDY;
	}

	public long tabPressUntil() {
		return tabPressUntil;
	}

	public long closePressUntil() {
		return closePressUntil;
	}

	/** Complete a close whose button flash has finished, so the click is seen before the panel vanishes. */
	public void finishCloseIfReady() {
		if (this.pendingClose && System.currentTimeMillis() >= this.closePressUntil) {
			this.pendingClose = false;
			if (this.menu.isStatsPanelOpen()) {
				this.menu.toggleStatsPanel();
			}
		}
	}

	public void onTabClick() {
		this.tabPressUntil = System.currentTimeMillis() + UpgradePanelController.PRESS_FLASH_MS;
		AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
		this.menu.toggleStatsPanel();
	}

	public void onCloseClick() {
		this.closePressUntil = System.currentTimeMillis() + UpgradePanelController.PRESS_FLASH_MS;
		this.pendingClose = true;
		AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
	}

	// --- geometry -------------------------------------------------------------------------------

	/**
	 * Docked panel left edge: to the left of the GUI, with the tab's width reserved between them.
	 *
	 * <p>Reserving that strip is what keeps the tab clickable while the panel is open — the panel stops
	 * exactly where the tab begins instead of covering it. On a narrow window the panel can still be
	 * pushed over the tab; it is drawn after the tab precisely so it hides it cleanly in that case rather
	 * than half-covering the artwork.
	 */
	private int dockX(int leftPos) {
		return leftPos - PANEL_W - TAB_W;
	}

	public int panelX(int leftPos) {
		return dockX(leftPos) + panelDX;
	}

	public int panelY(int topPos) {
		return topPos + DOCK_Y + panelDY;
	}

	/**
	 * Tab position — anchored to the screen, NOT to the dragged panel.
	 *
	 * <p>Same rule the gear follows: the button that opens a panel has to stay where the player last saw
	 * it, or dragging the panel off to a corner would take its own handle with it.
	 */
	public int tabX(int leftPos) {
		return leftPos - TAB_W;
	}

	public int tabY(int topPos) {
		return topPos + DOCK_Y;
	}

	public boolean isOverTab(double mx, double my, int leftPos, int topPos) {
		int bx = tabX(leftPos);
		int by = tabY(topPos);
		return mx >= bx && mx < bx + TAB_W && my >= by && my < by + TAB_H;
	}

	public boolean isOverPanel(double mx, double my, int leftPos, int topPos) {
		int px = panelX(leftPos);
		int py = panelY(topPos);
		return mx >= px && mx < px + PANEL_W && my >= py && my < py + PANEL_H;
	}

	/**
	 * Close button, tucked into the panel's top-right corner.
	 *
	 * <p>These are the plate's own coordinates in the panel texture, and the hit box below is the whole
	 * plate rather than a smaller square inside it: the two used to be derived separately, so the visible
	 * button and the clickable area did not line up.
	 */
	static final int CLOSE_X = PANEL_W - 13, CLOSE_Y = 4, CLOSE_SIZE = 10;

	public int closeX(int leftPos) {
		return panelX(leftPos) + CLOSE_X;
	}

	public int closeY(int topPos) {
		return panelY(topPos) + CLOSE_Y;
	}

	public boolean isOverClose(double mx, double my, int leftPos, int topPos) {
		int cx = closeX(leftPos);
		int cy = closeY(topPos);
		return mx >= cx && mx < cx + CLOSE_SIZE && my >= cy && my < cy + CLOSE_SIZE;
	}

	// --- drag -----------------------------------------------------------------------------------

	public boolean dragging() {
		return dragging;
	}

	public void beginDrag(double mouseX, double mouseY) {
		this.dragging = true;
		this.grabX = mouseX - this.panelDX;
		this.grabY = mouseY - this.panelDY;
	}

	public void dragTo(double mouseX, double mouseY, int leftPos, int topPos, int screenWidth, int screenHeight) {
		this.panelDX = Mth.clamp((int) Math.round(mouseX - this.grabX), minDX(leftPos), maxDX(leftPos, screenWidth));
		this.panelDY = Mth.clamp((int) Math.round(mouseY - this.grabY), minDY(topPos), maxDY(topPos, screenHeight));
	}

	public void endDrag() {
		this.dragging = false;
		AlaClientConfig.saveStatsPanelPosition(this.panelDX, this.panelDY);
	}

	// Clamps keep the panel inside the window: a panel dragged off-screen cannot be dragged back.
	private int minDX(int leftPos) {
		return -dockX(leftPos);
	}

	private int maxDX(int leftPos, int screenWidth) {
		return screenWidth - PANEL_W - dockX(leftPos);
	}

	private int minDY(int topPos) {
		return -(topPos + DOCK_Y);
	}

	private int maxDY(int topPos, int screenHeight) {
		return screenHeight - PANEL_H - (topPos + DOCK_Y);
	}

	/** Screen area the tab occupies — handed to the recipe viewer so its overlay steps aside. */
	public Rect2i tabArea(int leftPos, int topPos) {
		return new Rect2i(tabX(leftPos), tabY(topPos), TAB_W, TAB_H);
	}

	/** Screen area the open panel occupies — same purpose as {@link #tabArea}. */
	public Rect2i panelArea(int leftPos, int topPos) {
		return new Rect2i(panelX(leftPos), panelY(topPos), PANEL_W, PANEL_H);
	}

	// --- ScreenOverlay (MOD-716): the panel's drawing and input, moved from MachineScreen unchanged ---

	@Override
	public void beginFrame() {
		finishCloseIfReady();
	}

	/** The tab, on every machine that shows one ({@link MachineScreen#hasStatsTab()}). */
	@Override
	public void drawHandle(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!host.hasStatsTab()) {
			return;
		}
		int bx = tabX(host.left());
		int by = tabY(host.top());
		boolean flash = System.currentTimeMillis() < tabPressUntil();
		int off = flash ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, MachineScreen.UPGRADES_ATLAS, bx + off, by + off,
				(float) TAB_U, (float) TAB_V, TAB_W, TAB_H,
				UpgradePanelController.ATLAS, UpgradePanelController.ATLAS);
		if (flash) {
			graphics.fill(bx + off, by + off, bx + off + TAB_W, by + off + TAB_H, UpgradePanelController.PRESS_DARKEN);
		}
	}

	@Override
	public void drawBody(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.menu.isStatsPanelOpen()) {
			drawPanel(graphics, mouseX, mouseY);
		}
	}

	/**
	 * Paint the statistics panel.
	 *
	 * <p>Blitted from its own texture: the frame is artwork, and artwork belongs in a PNG an artist can open.
	 * Everything below only writes text into it.
	 */
	private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int px = panelX(host.left());
		int py = panelY(host.top());
		int pw = PANEL_W;
		int ph = PANEL_H;
		Font font = host.font();

		graphics.blit(RenderPipelines.GUI_TEXTURED, MachineScreen.STATS_PANEL_TEXTURE, px, py,
				0.0F, 0.0F, pw, ph, MachineScreen.TEX_SIZE, MachineScreen.TEX_SIZE);

		graphics.text(font, Component.translatable("gui.alaindustrial.stats.title"),
				px + 8, py + 7, GuiStyle.TEXT, false);

		// The × itself lives in the panel texture, centred there by construction. Only the press flash is
		// drawn here — the font's "x" glyph sat low and left of the plate, which is what showed in game.
		if (System.currentTimeMillis() < closePressUntil()) {
			int cx = closeX(host.left());
			int cy = closeY(host.top());
			graphics.fill(cx, cy, cx + CLOSE_SIZE, cy + CLOSE_SIZE, UpgradePanelController.PRESS_DARKEN);
		}

		new StatsPanelReadout(host.font(), this.menu, graphics, mouseX, mouseY).draw(px, py);
	}

	@Override
	public boolean handleTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (host.hasStatsTab() && isOverTab(mouseX, mouseY, host.left(), host.top())) {
			graphics.setTooltipForNextFrame(host.font(),
					Component.translatable("gui.alaindustrial.stats.title"), mouseX, mouseY);
			return true;
		}
		return false;
	}

	/**
	 * Modal over its own footprint: the row tooltips are emitted while drawing, and nothing from the GUI
	 * beneath may show through.
	 */
	@Override
	public boolean bodyTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		return coversPoint(mouseX, mouseY);
	}

	@Override
	public boolean coversPoint(double mouseX, double mouseY) {
		return this.menu.isStatsPanelOpen() && isOverPanel(mouseX, mouseY, host.left(), host.top());
	}

	/** MOD-125: the statistics tab, on every machine that shows one. */
	@Override
	public boolean clickHandle(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && host.hasStatsTab()
				&& isOverTab(event.x(), event.y(), host.left(), host.top())) {
			onTabClick();
			return true;
		}
		return false;
	}

	/**
	 * Modal over its footprint, like the upgrade panel, but with nothing to click inside it except the close
	 * button — a readout takes no input, so the whole surface is a drag handle.
	 */
	@Override
	public boolean clickBody(MouseButtonEvent event) {
		if (!coversPoint(event.x(), event.y())) {
			return false;
		}
		int btn = event.button();
		if (btn == InputConstants.MOUSE_BUTTON_LEFT && isOverClose(event.x(), event.y(), host.left(), host.top())) {
			onCloseClick();
			return true;
		}
		if (btn == InputConstants.MOUSE_BUTTON_LEFT) {
			beginDrag(event.x(), event.y());
		}
		return true;
	}

	@Override
	public boolean drag(MouseButtonEvent event) {
		if (dragging() && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragTo(event.x(), event.y(), host.left(), host.top(), host.screenWidth(), host.screenHeight());
			return true;
		}
		return false;
	}

	@Override
	public boolean release(MouseButtonEvent event) {
		if (dragging() && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			endDrag();
			return true;
		}
		return false;
	}

	/**
	 * The statistics dock is declared with the tab (MOD-125): both stick out of the frame, exactly where
	 * REI/JEI park their item list.
	 */
	@Override
	public void addExclusionAreas(List<Rect2i> areas) {
		if (host.hasStatsTab()) {
			areas.add(tabArea(host.left(), host.top()));
		}
		if (this.menu.isStatsPanelOpen()) {
			areas.add(panelArea(host.left(), host.top()));
		}
	}
}
