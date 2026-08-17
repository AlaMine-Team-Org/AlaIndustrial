package dev.alaindustrial.client.screen;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.menu.MachineMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
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
public final class StatsPanelController {

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
	private int panelDX;
	private int panelDY;
	private boolean dragging;
	private double grabX;
	private double grabY;
	private long tabPressUntil;
	private long closePressUntil;
	private boolean pendingClose;

	public StatsPanelController(MachineMenu menu, int leftPos, int topPos, int screenWidth, int screenHeight) {
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
}
