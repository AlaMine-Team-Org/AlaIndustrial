package dev.alaindustrial.client.screen;

import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.menu.MachineMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.Mth;

/**
 * Owns the drag-state + hit-testing for the floating upgrade panel overlay (MOD-080), extracted from
 * {@link MachineScreen} so the screen class is about the machine again (frame, energy bar, slots) and
 * the panel's input/positioning maths lives separately.
 *
 * <p><b>Why the screen passes {@code leftPos}/{@code topPos}/{@code screenWidth}/{@code screenHeight}
 * as ints.</b> Those fields are {@code protected} on {@code AbstractContainerScreen}, so a helper
 * class in this package cannot read them through a {@code screen.leftPos} reference — Java protected
 * access is package-or-subclass only, and this controller is neither. The screen (a subclass) reads
 * them and hands the live values to each geometry call. The alternative — making the controller an
 * inner class of MachineScreen — would re-couple it to the screen's generics and undo the split.
 *
 * <p>The rendering itself (the panel blit, the active-slot chip indicator, the close-X flash) stays
 * on {@link MachineScreen} because it is deeply intertwined with {@code GuiGraphics} and per-frame
 * slot iteration. The split is: controller answers "where is it / what is the user doing to it",
 * screen answers "given that, draw it".
 */
public final class UpgradePanelController {
	static final int ATLAS = 256;
	static final int BTN_U = 0, BTN_V = 0, BTN_W = 24, BTN_H = 34;
	static final int BTN_X = 176, BTN_Y = 0;
	static final int PANEL_U = 97, PANEL_V = 0, PANEL_W = 159, PANEL_H = 145;
	static final int CLOSE_U = 162, CLOSE_V = 60, CLOSE_W = 28, CLOSE_H = 28;
	static final int ACT_U = 0, ACT_V = 48, ACT_W = 7, ACT_H = 7;
	static final int ACTIVE_IND_DX = 4, ACTIVE_IND_DY = 52;
	static final int LOCK_TINT = 0x90101010;
	static final int HOVER_TINT = 0x80FFFFFF;
	static final int PRESS_DARKEN = 0x30000000;
	static final long PRESS_FLASH_MS = 90L;

	private final MachineMenu menu;
	private int panelDX;
	private int panelDY;
	private boolean draggingPanel;
	private double grabX;
	private double grabY;
	private long gearPressUntil;
	private long closePressUntil;
	private boolean pendingClose;

	public UpgradePanelController(MachineMenu menu, int leftPos, int topPos, int screenWidth, int screenHeight) {
		this.menu = menu;
		this.panelDX = Mth.clamp(AlaClientConfig.upgradePanelDX, minPanelDX(leftPos), maxPanelDX(leftPos, screenWidth));
		this.panelDY = Mth.clamp(AlaClientConfig.upgradePanelDY, minPanelDY(topPos), maxPanelDY(topPos, screenHeight));
		this.menu.repositionUpgradeSlots(this.panelDX, this.panelDY);
	}

	public int panelDX() { return panelDX; }
	public int panelDY() { return panelDY; }
	public long gearPressUntil() { return gearPressUntil; }
	public long closePressUntil() { return closePressUntil; }

	public void finishCloseIfReady() {
		if (this.pendingClose && System.currentTimeMillis() >= this.closePressUntil) {
			this.pendingClose = false;
			if (this.menu.isPanelOpen()) { this.menu.togglePanel(); }
		}
	}

	public void onGearClick() {
		this.gearPressUntil = System.currentTimeMillis() + PRESS_FLASH_MS;
		playClick();
		this.menu.togglePanel();
	}

	public void onCloseClick() {
		this.closePressUntil = System.currentTimeMillis() + PRESS_FLASH_MS;
		this.pendingClose = true;
		playClick();
	}

	private void playClick() {
		AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
	}

	public boolean dragging() { return draggingPanel; }

	public void beginDrag(double mouseX, double mouseY) {
		this.draggingPanel = true;
		this.grabX = mouseX - this.panelDX;
		this.grabY = mouseY - this.panelDY;
	}

	public void dragTo(double mouseX, double mouseY, int leftPos, int topPos, int screenWidth, int screenHeight) {
		int newDX = Mth.clamp((int) Math.round(mouseX - this.grabX), minPanelDX(leftPos), maxPanelDX(leftPos, screenWidth));
		int newDY = Mth.clamp((int) Math.round(mouseY - this.grabY), minPanelDY(topPos), maxPanelDY(topPos, screenHeight));
		if (newDX != this.panelDX || newDY != this.panelDY) {
			this.panelDX = newDX;
			this.panelDY = newDY;
			this.menu.repositionUpgradeSlots(this.panelDX, this.panelDY);
		}
	}

	public void endDrag() {
		this.draggingPanel = false;
		AlaClientConfig.savePanelPosition(this.panelDX, this.panelDY);
	}

	public int gearX(int leftPos) { return leftPos + BTN_X; }
	public int gearY(int topPos) { return topPos + BTN_Y; }

	public boolean isOverGear(double mx, double my, int leftPos, int topPos) {
		int bx = gearX(leftPos);
		int by = gearY(topPos);
		return mx >= bx && mx < bx + BTN_W && my >= by && my < by + BTN_H;
	}

	public boolean isOverClose(double mx, double my, int leftPos, int topPos) {
		int cx = closeX(leftPos);
		int cy = closeY(topPos);
		return mx >= cx && mx < cx + CLOSE_W && my >= cy && my < cy + CLOSE_H;
	}

	public boolean isOverPanel(double mx, double my, int leftPos, int topPos) {
		double px = leftPos + MachineMenu.PANEL_X + this.panelDX;
		double py = topPos + MachineMenu.PANEL_Y + this.panelDY;
		return mx >= px && mx < px + PANEL_W && my >= py && my < py + PANEL_H;
	}

	public int closeX(int leftPos) {
		return leftPos + MachineMenu.PANEL_X + this.panelDX + (CLOSE_U - PANEL_U);
	}

	public int closeY(int topPos) {
		return topPos + MachineMenu.PANEL_Y + this.panelDY + (CLOSE_V - PANEL_V);
	}

	public Rect2i gearArea(int leftPos, int topPos) {
		return new Rect2i(gearX(leftPos), gearY(topPos), BTN_W, BTN_H);
	}

	public Rect2i panelArea(int leftPos, int topPos) {
		return new Rect2i(leftPos + MachineMenu.PANEL_X + this.panelDX,
				topPos + MachineMenu.PANEL_Y + this.panelDY, PANEL_W, PANEL_H);
	}

	private int minPanelDX(int leftPos) { return -(leftPos + MachineMenu.PANEL_X); }
	private int maxPanelDX(int leftPos, int screenWidth) { return screenWidth - PANEL_W - (leftPos + MachineMenu.PANEL_X); }
	private int minPanelDY(int topPos) { return -(topPos + MachineMenu.PANEL_Y); }
	private int maxPanelDY(int topPos, int screenHeight) { return screenHeight - PANEL_H - (topPos + MachineMenu.PANEL_Y); }
}
