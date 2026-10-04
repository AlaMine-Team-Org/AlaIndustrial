package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.client.AlaClientConfig;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.menu.MachineMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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
 * <p>Since MOD-716 it is also the panel's {@link ScreenOverlay}: it draws the panel, the chips in it and
 * the gear, routes clicks on them and shows their tooltips — the screen only replays its overlay list.
 * The gear is drawn with the body, after the panel, because it sits in the panel's transparent corner;
 * as a click and tooltip target it is the handle.
 */
public final class UpgradePanelController implements ScreenOverlay {
	static final int ATLAS = 256;
	static final int BTN_U = 0, BTN_V = 0, BTN_W = 24, BTN_H = 34;
	static final int BTN_X = 176, BTN_Y = 4;
	static final int PANEL_U = 97, PANEL_V = 0, PANEL_W = 159, PANEL_H = 145;
	static final int CLOSE_U = 162, CLOSE_V = 60, CLOSE_W = 28, CLOSE_H = 28;
	static final int ACT_U = 0, ACT_V = 48, ACT_W = 7, ACT_H = 7;
	/**
	 * Panel-relative position of each slot's rivet indicator, in slot order (LEFT, TOP, RIGHT, BOTTOM).
	 * Was a single hardcoded point back when only slot 0 could hold anything; since MOD-392 all four
	 * slots work, so the lit rivet has to follow the slot that actually holds a chip rather than always
	 * lighting the left arm.
	 */
	static final int[][] IND_XY = { { 4, 52 }, { 96, 4 }, { 148, 52 }, { 96, 134 } };
	/** Greys out a reserved arm so it reads as "not yet", rather than as an arm that refuses your chip. */
	static final int LOCK_TINT = 0x90101010;
	static final int HOVER_TINT = 0x80FFFFFF;
	static final int PRESS_DARKEN = 0x30000000;
	static final long PRESS_FLASH_MS = 90L;

	private final MachineMenu menu;
	private final OverlayHost host;
	private int panelDX;
	private int panelDY;
	private boolean draggingPanel;
	private double grabX;
	private double grabY;
	private long gearPressUntil;
	private long closePressUntil;
	private boolean pendingClose;

	UpgradePanelController(OverlayHost host, MachineMenu menu, int leftPos, int topPos, int screenWidth,
			int screenHeight) {
		this.host = host;
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

	public int gearX(int leftPos) { return leftPos + menu.panelAnchorX() + (BTN_X - MachineMenu.PANEL_X); }
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
		double px = leftPos + menu.panelAnchorX() + this.panelDX;
		double py = topPos + MachineMenu.PANEL_Y + this.panelDY;
		return mx >= px && mx < px + PANEL_W && my >= py && my < py + PANEL_H;
	}

	public int closeX(int leftPos) {
		return leftPos + menu.panelAnchorX() + this.panelDX + (CLOSE_U - PANEL_U);
	}

	public int closeY(int topPos) {
		return topPos + MachineMenu.PANEL_Y + this.panelDY + (CLOSE_V - PANEL_V);
	}

	public Rect2i gearArea(int leftPos, int topPos) {
		return new Rect2i(gearX(leftPos), gearY(topPos), BTN_W, BTN_H);
	}

	public Rect2i panelArea(int leftPos, int topPos) {
		return new Rect2i(leftPos + menu.panelAnchorX() + this.panelDX,
				topPos + MachineMenu.PANEL_Y + this.panelDY, PANEL_W, PANEL_H);
	}

	private int minPanelDX(int leftPos) { return -(leftPos + menu.panelAnchorX()); }
	private int maxPanelDX(int leftPos, int screenWidth) { return screenWidth - PANEL_W - (leftPos + menu.panelAnchorX()); }
	private int minPanelDY(int topPos) { return -(topPos + MachineMenu.PANEL_Y); }
	private int maxPanelDY(int topPos, int screenHeight) { return screenHeight - PANEL_H - (topPos + MachineMenu.PANEL_Y); }

	// --- ScreenOverlay (MOD-716): the panel's drawing and input, moved from MachineScreen unchanged ---

	@Override
	public void beginFrame() {
		finishCloseIfReady();
	}

	/** The panel when open, then the gear on every machine that has a panel. */
	@Override
	public void drawBody(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.menu.hasUpgradePanel() && this.menu.isPanelOpen()) {
			drawPanel(graphics, mouseX, mouseY);
		}
		// The gear tab is drawn and clickable on every machine that HAS a panel. It sits at a fixed
		// position anchored to the screen (gearX/gearY take leftPos/topPos), tucked into the panel's
		// transparent top-left corner when open, so it never covers panel content. A machine that opted
		// out (MOD-393) shows no gear — a button that opens nothing reads as broken.
		if (this.menu.hasUpgradePanel()) {
			drawGear(graphics);
		}
	}

	private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// Anchor-aware (not the flat PANEL_X constant): a wider GUI overrides panelAnchorX(), and the
		// hit-boxes, slots and close button already follow it — the background used to be the one piece
		// that did not, so on the 200px distillation column the panel art sat 24px left of its own slots.
		int px = host.left() + this.menu.panelAnchorX() + panelDX();
		int py = host.top() + MachineMenu.PANEL_Y + panelDY();
		graphics.blit(RenderPipelines.GUI_TEXTURED, MachineScreen.UPGRADES_ATLAS, px, py,
				(float) PANEL_U, (float) PANEL_V, PANEL_W, PANEL_H, ATLAS, ATLAS);

		Slot hovered = upgradeSlotAt(mouseX, mouseY);
		int upgradeIndex = 0;
		for (Slot slot : this.menu.slots) {
			if (!(slot instanceof MachineMenu.UpgradeSlot up) || !up.isActive()) {
				continue;
			}
			int sx = host.left() + slot.x;
			int sy = host.top() + slot.y;
			ItemStack item = slot.getItem();
			if (up.isLocked()) {
				// Reserved for a MOD-286 module: dim it so the baked hint reads as "later", not "broken".
				graphics.fill(sx, sy, sx + 16, sy + 16, LOCK_TINT);
			}
			if (slot == hovered) {
				graphics.fill(sx, sy, sx + 16, sy + 16, HOVER_TINT);
			}
			if (!item.isEmpty()) {
				graphics.item(item, sx, sy);
				graphics.itemDecorations(host.font(), item, sx, sy, null);
				// Light THIS arm's rivet, not a fixed one: the slots are added in panel order, so the
				// running index maps straight onto IND_XY.
				if (upgradeIndex < IND_XY.length) {
					int[] ind = IND_XY[upgradeIndex];
					graphics.blit(RenderPipelines.GUI_TEXTURED, MachineScreen.UPGRADES_ATLAS,
							px + ind[0], py + ind[1], (float) ACT_U, (float) ACT_V, ACT_W, ACT_H, ATLAS, ATLAS);
				}
			}
			upgradeIndex++;
		}

		int cx = closeX(host.left());
		int cy = closeY(host.top());
		boolean flash = System.currentTimeMillis() < closePressUntil();
		int off = flash ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, MachineScreen.UPGRADES_ATLAS, cx + off, cy + off,
				(float) CLOSE_U, (float) CLOSE_V, CLOSE_W, CLOSE_H, ATLAS, ATLAS);
		if (flash) {
			graphics.fill(cx + off, cy + off, cx + off + CLOSE_W, cy + off + CLOSE_H, PRESS_DARKEN);
		}
	}

	private void drawGear(GuiGraphicsExtractor graphics) {
		int bx = gearX(host.left());
		int by = gearY(host.top());
		boolean flash = System.currentTimeMillis() < gearPressUntil();
		int off = flash ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, MachineScreen.UPGRADES_ATLAS, bx + off, by + off,
				(float) BTN_U, (float) BTN_V, BTN_W, BTN_H, ATLAS, ATLAS);
		if (flash) {
			graphics.fill(bx + off, by + off, bx + off + BTN_W, by + off + BTN_H, PRESS_DARKEN);
		}
	}

	/** The gear's tooltip: the panel's name, and with an overclocker fitted, what it does to the machine. */
	@Override
	public boolean handleTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!this.menu.hasUpgradePanel() || !isOverGear(mouseX, mouseY, host.left(), host.top())) {
			return false;
		}
		int chips = this.menu.installedOverclockerTier();
		if (chips <= 0) {
			graphics.setTooltipForNextFrame(host.font(), Component.translatable("gui.alaindustrial.upgrades"),
					mouseX, mouseY);
			return true;
		}
		// Relative multipliers only — never an absolute EU/t figure: the ratios are pure functions
		// of the chip tier. The two factors are the server's (ServerBalance, MOD-695).
		double speed = 1.0 / Math.pow(ServerBalance.overclockerSpeedFactor(), chips);
		double draw = Math.pow(ServerBalance.overclockerEuFactor(), chips);
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.addAll(host.font().split(Component.translatable("gui.alaindustrial.upgrades"), 200));
		lines.addAll(host.font().split(Component.translatable("gui.alaindustrial.upgrades.overclock",
				chips, String.format(Locale.ROOT, "%.2f", speed),
				String.format(Locale.ROOT, "%.0f", draw)).withStyle(ChatFormatting.GRAY), 200));
		graphics.setTooltipForNextFrame(host.font(), lines, mouseX, mouseY);
		return true;
	}

	/** Modal: over the open panel only the panel's own tooltips show, nothing from the GUI beneath it. */
	@Override
	public boolean bodyTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!coversPoint(mouseX, mouseY)) {
			return false;
		}
		Slot hovered = upgradeSlotAt(mouseX, mouseY);
		if (hovered != null && !hovered.getItem().isEmpty()) {
			ItemStack item = hovered.getItem();
			graphics.setTooltipForNextFrame(host.font(), host.containerTooltip(item), item.getTooltipImage(),
					mouseX, mouseY);
		}
		return true;
	}

	@Override
	public boolean coversPoint(double mouseX, double mouseY) {
		return this.menu.isPanelOpen() && isOverPanel(mouseX, mouseY, host.left(), host.top());
	}

	/** The gear always toggles the panel (it stays visible in the panel's transparent corner). */
	@Override
	public boolean clickHandle(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.menu.hasUpgradePanel()
				&& isOverGear(event.x(), event.y(), host.left(), host.top())) {
			onGearClick();
			return true;
		}
		return false;
	}

	/** The open panel is modal over its footprint: it consumes every click so nothing beneath reacts. */
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
		MachineMenu.UpgradeSlot slot = (btn == InputConstants.MOUSE_BUTTON_LEFT
				|| btn == InputConstants.MOUSE_BUTTON_RIGHT) ? upgradeSlotAt(event.x(), event.y()) : null;
		if (slot != null) {
			ContainerInput input = (btn == InputConstants.MOUSE_BUTTON_LEFT && event.hasShiftDown())
					? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
			host.clickSlot(slot, btn, input);
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

	@Override
	public boolean keepsClickInside(double mouseX, double mouseY) {
		return coversPoint(mouseX, mouseY);
	}

	@Override
	public void addExclusionAreas(List<Rect2i> areas) {
		areas.add(gearArea(host.left(), host.top()));
		if (this.menu.isPanelOpen()) {
			areas.add(panelArea(host.left(), host.top()));
		}
	}

	/** The active upgrade slot under the point, or null. */
	private MachineMenu.UpgradeSlot upgradeSlotAt(double mx, double my) {
		for (Slot slot : this.menu.slots) {
			if (slot instanceof MachineMenu.UpgradeSlot up && up.isActive()) {
				int sx = host.left() + slot.x;
				int sy = host.top() + slot.y;
				if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
					return up;
				}
			}
		}
		return null;
	}
}
