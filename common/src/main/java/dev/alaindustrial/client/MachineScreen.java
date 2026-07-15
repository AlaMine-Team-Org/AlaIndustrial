package dev.alaindustrial.client;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.MachineMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Shared base for every machine screen (MOD-080). It adds a gear tab at the GUI's right edge that
 * opens a draggable upgrade panel, plus a centre close-X. The panel is a <em>floating, modal window
 * drawn on top of the GUI content</em>: its background, slots and buttons are painted in an overlay
 * pass ({@link #extractContents} after {@code super}), the panel's slots are skipped in the normal
 * slot pass ({@link #extractSlot}), and while it is open every mouse interaction over its footprint is
 * consumed here so nothing beneath it (GUI slots, energy bars, recipe hooks) reacts. The gear tab
 * hides while the panel is open (close via the X).
 *
 * <p>Buttons and panel slots are drawn and hit-tested manually so they sit above the overlay; the gear
 * and X play the standard click sound and a press flash. {@code Slot.x/y} are final, so dragging
 * rebuilds the upgrade slots at new coordinates ({@link MachineMenu#repositionUpgradeSlots}); the
 * offset persists in {@link AlaClientConfig}. {@link #extraGuiAreas()} exposes the gear / panel
 * rectangles so recipe viewers (JEI/REI) keep their items clear of them.
 */
public class MachineScreen<T extends MachineMenu> extends AbstractContainerScreen<T> {

	protected static final Identifier UPGRADES_ATLAS =
			Industrialization.id("textures/gui/container/upgrades_tab_variants/upgrades_tab_small_01_gear.png");
	private static final int ATLAS = 256;
	private static final long PRESS_FLASH_MS = 90L;

	// Gear tab button: atlas region + docked placement (relative to leftPos/topPos).
	private static final int BTN_U = 0, BTN_V = 0, BTN_W = 24, BTN_H = 34;
	private static final int BTN_X = 176, BTN_Y = 0;

	// Upgrade panel (cross) atlas region. Screen origin comes from MachineMenu.PANEL_X/Y so panel slots
	// line up with the UpgradeSlot hit-boxes.
	private static final int PANEL_U = 97, PANEL_V = 0, PANEL_W = 159, PANEL_H = 145;

	// Centre "X" close button atlas region (the decorative X baked into the panel; re-blitted so it can
	// flash on press).
	private static final int CLOSE_U = 162, CLOSE_V = 60, CLOSE_W = 28, CLOSE_H = 28;

	// "Active" indicator: an orange rivet (atlas 0,48 7×7) blitted over the grey rivet on the active
	// slot's arm (panel-relative 4,52) while a chip is installed.
	private static final int ACT_U = 0, ACT_V = 48, ACT_W = 7, ACT_H = 7;
	private static final int ACTIVE_IND_DX = 4, ACTIVE_IND_DY = 52;

	private static final int LOCK_TINT = 0x90101010;
	private static final int HOVER_TINT = 0x80FFFFFF;
	private static final int PRESS_DARKEN = 0x30000000;

	private int panelDX;
	private int panelDY;
	private boolean draggingPanel;
	private double grabX;
	private double grabY;
	private long gearPressUntil;
	private long closePressUntil;
	private boolean pendingClose;

	public MachineScreen(T menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	/** For machines with a non-default GUI size (the windmills are 176×178). */
	public MachineScreen(T menu, Inventory inventory, Component title, int imageWidth, int imageHeight) {
		super(menu, inventory, title, imageWidth, imageHeight);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
		// Clamp the restored offset to this screen size — a panel dragged wide on a big screen must not
		// open off-screen on a smaller one / higher GUI scale.
		this.panelDX = Mth.clamp(AlaClientConfig.upgradePanelDX, minPanelDX(), maxPanelDX());
		this.panelDY = Mth.clamp(AlaClientConfig.upgradePanelDY, minPanelDY(), maxPanelDY());
		this.menu.repositionUpgradeSlots(this.panelDX, this.panelDY);
	}

	// --- Rendering: subclass frame in the background, upgrade panel as a top overlay ---

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		drawMachineFrame(graphics, mouseX, mouseY, partialTick);
	}

	/** Each machine screen draws its own frame + dynamic sprites here (was its {@code extractBackground} body). */
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		maybeFinishClose();
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		// Overlay pass — above the GUI's slots, items and labels.
		if (this.menu.isPanelOpen()) {
			drawPanel(graphics, mouseX, mouseY);
		}
		// The gear tab is always drawn and clickable. When open it sits in the panel's transparent
		// top-left corner (it follows the panel when dragged), so it never covers panel content.
		drawTabButton(graphics);
	}

	/** Skip the upgrade slots in the normal slot pass — they are painted in the panel overlay instead. */
	@Override
	protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
		if (slot instanceof MachineMenu.UpgradeSlot) {
			return;
		}
		super.extractSlot(graphics, slot, mouseX, mouseY);
	}

	private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int px = this.leftPos + MachineMenu.PANEL_X + this.panelDX;
		int py = this.topPos + MachineMenu.PANEL_Y + this.panelDY;
		graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, px, py, (float) PANEL_U, (float) PANEL_V,
				PANEL_W, PANEL_H, ATLAS, ATLAS);

		Slot hovered = upgradeSlotAt(mouseX, mouseY);
		boolean activeHasChip = false;
		for (Slot slot : this.menu.slots) {
			if (!(slot instanceof MachineMenu.UpgradeSlot up) || !up.isActive()) {
				continue;
			}
			int sx = this.leftPos + slot.x;
			int sy = this.topPos + slot.y;
			ItemStack item = slot.getItem();
			if (up.isLocked()) {
				graphics.fill(sx, sy, sx + 16, sy + 16, LOCK_TINT);
			} else if (!item.isEmpty()) {
				activeHasChip = true;
			}
			if (slot == hovered) {
				graphics.fill(sx, sy, sx + 16, sy + 16, HOVER_TINT);
			}
			if (!item.isEmpty()) {
				graphics.item(item, sx, sy);
				graphics.itemDecorations(this.font, item, sx, sy, null);
			}
		}
		// Active-slot indicator: swap the grey rivet for the orange one while a chip is installed.
		if (activeHasChip) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, px + ACTIVE_IND_DX, py + ACTIVE_IND_DY,
					(float) ACT_U, (float) ACT_V, ACT_W, ACT_H, ATLAS, ATLAS);
		}

		int cx = closeX();
		int cy = closeY();
		boolean flash = System.currentTimeMillis() < this.closePressUntil;
		int off = flash ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, cx + off, cy + off, (float) CLOSE_U,
				(float) CLOSE_V, CLOSE_W, CLOSE_H, ATLAS, ATLAS);
		if (flash) {
			graphics.fill(cx + off, cy + off, cx + off + CLOSE_W, cy + off + CLOSE_H, PRESS_DARKEN);
		}
	}

	private void drawTabButton(GuiGraphicsExtractor graphics) {
		int bx = gearX();
		int by = gearY();
		boolean flash = System.currentTimeMillis() < this.gearPressUntil;
		int off = flash ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, bx + off, by + off, (float) BTN_U,
				(float) BTN_V, BTN_W, BTN_H, ATLAS, ATLAS);
		if (flash) {
			graphics.fill(bx + off, by + off, bx + off + BTN_W, by + off + BTN_H, PRESS_DARKEN);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (isOverGear(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("gui.alaindustrial.upgrades"),
					mouseX, mouseY);
			return;
		}
		if (this.menu.isPanelOpen() && isOverPanel(mouseX, mouseY)) {
			// Modal: show only the panel's own tooltips, nothing from the GUI beneath it.
			Slot hovered = upgradeSlotAt(mouseX, mouseY);
			if (hovered != null && !hovered.getItem().isEmpty()) {
				ItemStack item = hovered.getItem();
				graphics.setTooltipForNextFrame(this.font, getTooltipFromContainerItem(item),
						item.getTooltipImage(), mouseX, mouseY);
			}
			return;
		}
		super.extractTooltip(graphics, mouseX, mouseY);
	}

	/** Suppress the machine's bar tooltips (energy/fluid) when the mouse is over the open panel. */
	@Override
	protected boolean isHovering(int left, int top, int w, int h, double mx, double my) {
		if (this.menu.isPanelOpen() && isOverPanel(mx, my)) {
			return false;
		}
		return super.isHovering(left, top, w, h, mx, my);
	}

	// --- Recipe-viewer exclusion (MOD-080): absolute screen rects the viewers must keep clear ---

	/** The gear tab (always) and the open panel (dynamic, drag-aware) as absolute screen rectangles. */
	public List<Rect2i> extraGuiAreas() {
		List<Rect2i> areas = new ArrayList<>(2);
		areas.add(new Rect2i(gearX(), gearY(), BTN_W, BTN_H));
		if (this.menu.isPanelOpen()) {
			areas.add(new Rect2i(this.leftPos + MachineMenu.PANEL_X + this.panelDX,
					this.topPos + MachineMenu.PANEL_Y + this.panelDY, PANEL_W, PANEL_H));
		}
		return areas;
	}

	// --- Input: gear, modal panel (buttons, slots, drag), click routing ---

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int btn = event.button();
		// The gear always toggles the panel (it stays visible in the panel's transparent corner).
		if (btn == 0 && isOverGear(event.x(), event.y())) {
			onGearClick();
			return true;
		}
		// The open panel is modal over its footprint: consume every click so nothing beneath reacts.
		if (this.menu.isPanelOpen() && isOverPanel(event.x(), event.y())) {
			if (btn == 0 && isOverClose(event.x(), event.y())) {
				onCloseClick();
				return true;
			}
			MachineMenu.UpgradeSlot slot = (btn == 0 || btn == 1) ? upgradeSlotAt(event.x(), event.y()) : null;
			if (slot != null) {
				ContainerInput input = (btn == 0 && event.hasShiftDown())
						? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
				this.slotClicked(slot, slot.index, btn, input);
				return true;
			}
			if (btn == 0) {
				this.draggingPanel = true;
				this.grabX = event.x() - this.panelDX;
				this.grabY = event.y() - this.panelDY;
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.draggingPanel && event.button() == 0) {
			int newDX = Mth.clamp((int) Math.round(event.x() - this.grabX), minPanelDX(), maxPanelDX());
			int newDY = Mth.clamp((int) Math.round(event.y() - this.grabY), minPanelDY(), maxPanelDY());
			if (newDX != this.panelDX || newDY != this.panelDY) {
				this.panelDX = newDX;
				this.panelDY = newDY;
				this.menu.repositionUpgradeSlots(this.panelDX, this.panelDY);
			}
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.draggingPanel && event.button() == 0) {
			this.draggingPanel = false;
			AlaClientConfig.savePanelPosition(this.panelDX, this.panelDY);
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop) {
		if (this.menu.isPanelOpen() && isOverPanel(mx, my)) {
			return false;
		}
		return super.hasClickedOutside(mx, my, guiLeft, guiTop);
	}

	private void onGearClick() {
		this.gearPressUntil = System.currentTimeMillis() + PRESS_FLASH_MS;
		playClick();
		this.menu.togglePanel();
	}

	private void onCloseClick() {
		this.closePressUntil = System.currentTimeMillis() + PRESS_FLASH_MS;
		this.pendingClose = true; // close after the press flash has shown (see maybeFinishClose)
		playClick();
	}

	private void maybeFinishClose() {
		if (this.pendingClose && System.currentTimeMillis() >= this.closePressUntil) {
			this.pendingClose = false;
			if (this.menu.isPanelOpen()) {
				this.menu.togglePanel();
			}
		}
	}

	private void playClick() {
		AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
	}

	private MachineMenu.UpgradeSlot upgradeSlotAt(double mx, double my) {
		for (Slot slot : this.menu.slots) {
			if (slot instanceof MachineMenu.UpgradeSlot up && up.isActive()) {
				int sx = this.leftPos + slot.x;
				int sy = this.topPos + slot.y;
				if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
					return up;
				}
			}
		}
		return null;
	}

	private boolean isOverGear(double mx, double my) {
		int bx = gearX();
		int by = gearY();
		return mx >= bx && mx < bx + BTN_W && my >= by && my < by + BTN_H;
	}

	/**
	 * Gear tab position — fixed at the GUI's right edge, always visible and clickable. It does NOT move
	 * with the panel: the panel is dragged independently, the tab stays put at the machine's right side.
	 */
	private int gearX() {
		return this.leftPos + BTN_X;
	}

	private int gearY() {
		return this.topPos + BTN_Y;
	}

	private boolean isOverClose(double mx, double my) {
		int cx = closeX();
		int cy = closeY();
		return mx >= cx && mx < cx + CLOSE_W && my >= cy && my < cy + CLOSE_H;
	}

	private boolean isOverPanel(double mx, double my) {
		double px = this.leftPos + MachineMenu.PANEL_X + this.panelDX;
		double py = this.topPos + MachineMenu.PANEL_Y + this.panelDY;
		return mx >= px && mx < px + PANEL_W && my >= py && my < py + PANEL_H;
	}

	private int closeX() {
		return this.leftPos + MachineMenu.PANEL_X + this.panelDX + (CLOSE_U - PANEL_U);
	}

	private int closeY() {
		return this.topPos + MachineMenu.PANEL_Y + this.panelDY + (CLOSE_V - PANEL_V);
	}

	private int minPanelDX() {
		return -(this.leftPos + MachineMenu.PANEL_X);
	}

	private int maxPanelDX() {
		return this.width - PANEL_W - (this.leftPos + MachineMenu.PANEL_X);
	}

	private int minPanelDY() {
		return -(this.topPos + MachineMenu.PANEL_Y);
	}

	private int maxPanelDY() {
		return this.height - PANEL_H - (this.topPos + MachineMenu.PANEL_Y);
	}
}
