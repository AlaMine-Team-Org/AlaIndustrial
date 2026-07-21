package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.menu.MachineMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Shared base for every machine screen. Owns the GUI rendering hooks ({@link #drawMachineFrame},
 * energy bar, slot overlay for the upgrade slots) and delegates the floating upgrade panel
 * (MOD-080) — its drag state, hit-testing and geometry — to an {@link UpgradePanelController}.
 *
 * <p><b>Why the controller split.</b> Before this refactor the screen was ~470 lines mixing three
 * concerns: machine rendering (frame + energy bar), slot rendering, and the panel's input + drag +
 * positioning logic. The panel code was the largest of the three (~250 lines) and unrelated to the
 * machine's own look — every edit there meant navigating interleaved rendering constants and drag
 * state. Pulling the state + hit-tests into {@link UpgradePanelController} makes this file about the
 * machine again; the controller owns "where is the panel / what is the user doing to it", and the
 * screen owns "given that, draw the frame and paint the panel overlay".
 *
 * <p>The rendering of the panel itself stays here (see {@link #drawPanel}) because it is deeply
 * intertwined with {@link GuiGraphicsExtractor} and per-frame slot iteration — pulling it out would
 * mean passing the graphics + slot list back into the controller and duplicating the slot-paint loop.
 *
 * <p>The controller does NOT do click routing itself — it answers geometry and drag state; the
 * screen's {@code mouseClicked} keeps the close-X / slot / drag dispatch inline so {@link #slotClicked}
 * stays callable without re-exports.
 */
public abstract class MachineScreen<T extends MachineMenu> extends AbstractContainerScreen<T> {

	/**
	 * Side of every machine GUI atlas — the visible imageWidth × imageHeight region sits at the
	 * top-left of a 256 × 256 PNG. Declared once here so each concrete screen no longer duplicates
	 * its own private {@code TEX_SIZE = 256} constant. {@link ProgressMachineScreen} and all 12
	 * direct subclasses previously re-declared this; they now read this inherited constant.
	 */
	protected static final int TEX_SIZE = 256;

	protected static final Identifier UPGRADES_ATLAS =
			Industrialization.id("textures/gui/container/upgrades_tab_variants/upgrades_tab_small_01_gear.png");

	private UpgradePanelController panel;

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
		// (Re)build the panel controller — re-clamps the persisted offset to this screen size on resize.
		this.panel = new UpgradePanelController(this.menu, this.leftPos, this.topPos, this.width, this.height);
	}

	/** The controller (null before {@link #init()}). Used by subclasses only in rare override cases. */
	protected UpgradePanelController panelController() {
		return panel;
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

	/**
	 * The 256 × 256 GUI atlas PNG for this machine. Each concrete subclass implements this with a
	 * one-line {@code Industrialization.id("textures/gui/container/<name>.png")} — replacing the
	 * private {@code TEXTURE} constant every screen used to redeclare. Read by {@link #blitStaticFrame}
	 * and by {@link #renderEnergyBar} (via the subclass's drawMachineFrame call site).
	 */
	protected abstract Identifier texture();

	/**
	 * Standard opening blit — the visible {@code imageWidth × imageHeight} region at the top-left of
	 * the {@value #TEX_SIZE} × {@value #TEX_SIZE} atlas. Call once at the top of {@link #drawMachineFrame}
	 * for machines whose atlas has an opaque interior. Skip for {@code BatteryBoxScreen} — its atlas
	 * has a transparent interior, so the frame must be blitted <em>after</em> the orange fill.
	 */
	protected void blitStaticFrame(GuiGraphicsExtractor graphics) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture(),
				this.leftPos, this.topPos, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, TEX_SIZE, TEX_SIZE);
	}

	/**
	 * Vertical energy-bar geometry shared by every machine screen with a bottom-up orange fill (the only
	 * outlier is {@code BatteryBoxScreen}, which is horizontal). Holds the four numbers that differ per
	 * machine: the bar's on-screen anchor, its UV anchor in the GUI atlas, and its inner size. Width and
	 * height stay constant across machines (10×44); only the anchors move (left bar X=17 vs right bar
	 * X=149 for Pump/GeothermalGenerator, UV-top 0 for most vs 48 for the taller windmill GUIs).
	 */
	public record EnergyBarSpec(int barX, int barBottom, int uvX, int uvTop) {
		/** The default left-side bar shared by most machines (furnace, macerator, generators, solar). */
		public static final EnergyBarSpec LEFT = new EnergyBarSpec(17, 64, 176, 0);
		/** The right-side bar used by the Pump and the Geothermal Generator (their left slot is a fluid/lava gauge). */
		public static final EnergyBarSpec RIGHT = new EnergyBarSpec(149, 64, 176, 0);
		/** The left bar offset into the windmill atlas (taller 178-tall GUI → service fill starts at UV 48). */
		public static final EnergyBarSpec LEFT_WINDMILL = new EnergyBarSpec(17, 76, 176, 48);

		/** Bar inner size — constant across every machine (the orange segmented fill is a 10×44 sprite). */
		public static final int WIDTH = 10;
		public static final int HEIGHT = 44;
	}

	/**
	 * Draw the bottom-up energy fill and return the computed fill height so the caller can pair it with
	 * {@link #renderEnergyTooltip}. Replaces the copy-pasted 7-line block that lived in every screen.
	 * Call from {@link #drawMachineFrame} after the static frame is blitted. Reads {@link #texture()}
	 * internally so callers no longer pass the atlas explicitly.
	 */
	protected int renderEnergyBar(GuiGraphicsExtractor graphics, EnergyBarSpec spec) {
		int capacity = this.menu.getCapacity();
		int energy = this.menu.getEnergy();
		int eFill = capacity > 0 ? (int) ((long) energy * EnergyBarSpec.HEIGHT / capacity) : 0;
		if (eFill > 0) {
			int x = this.leftPos;
			int y = this.topPos;
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture(),
					x + spec.barX(), y + spec.barBottom() - eFill,
					spec.uvX(), spec.uvTop() + (EnergyBarSpec.HEIGHT - eFill),
					EnergyBarSpec.WIDTH, eFill, TEX_SIZE, TEX_SIZE);
		}
		return eFill;
	}

	/**
	 * Hover tooltip "X / max EU" (lang key {@code gui.alaindustrial.energy}) over the bar interior.
	 * Mirrors the per-screen {@code isHovering(...)} block that every bar screen duplicated. Call from
	 * an {@code extractTooltip} override after {@code super}.
	 */
	protected void renderEnergyTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, EnergyBarSpec spec) {
		if (this.isHovering(spec.barX(), spec.barBottom() - EnergyBarSpec.HEIGHT,
				EnergyBarSpec.WIDTH, EnergyBarSpec.HEIGHT, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font,
					Component.translatable("gui.alaindustrial.energy", this.menu.getEnergy(), this.menu.getCapacity()),
					mouseX, mouseY);
		}
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		panel.finishCloseIfReady();
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		// Overlay pass — above the GUI's slots, items and labels.
		if (this.menu.isPanelOpen()) {
			drawPanel(graphics, mouseX, mouseY);
		}
		// The gear tab is always drawn and clickable. It sits at a fixed position anchored to the
		// screen (panel.gearX/gearY take leftPos/topPos), tucked into the panel's transparent top-left
		// corner when open, so it never covers panel content.
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
		int px = this.leftPos + MachineMenu.PANEL_X + panel.panelDX();
		int py = this.topPos + MachineMenu.PANEL_Y + panel.panelDY();
		graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, px, py,
				(float) UpgradePanelController.PANEL_U, (float) UpgradePanelController.PANEL_V,
				UpgradePanelController.PANEL_W, UpgradePanelController.PANEL_H,
				UpgradePanelController.ATLAS, UpgradePanelController.ATLAS);

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
				graphics.fill(sx, sy, sx + 16, sy + 16, UpgradePanelController.LOCK_TINT);
			} else if (!item.isEmpty()) {
				activeHasChip = true;
			}
			if (slot == hovered) {
				graphics.fill(sx, sy, sx + 16, sy + 16, UpgradePanelController.HOVER_TINT);
			}
			if (!item.isEmpty()) {
				graphics.item(item, sx, sy);
				graphics.itemDecorations(this.font, item, sx, sy, null);
			}
		}
		// Active-slot indicator: swap the grey rivet for the orange one while a chip is installed.
		if (activeHasChip) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS,
					px + UpgradePanelController.ACTIVE_IND_DX, py + UpgradePanelController.ACTIVE_IND_DY,
					(float) UpgradePanelController.ACT_U, (float) UpgradePanelController.ACT_V,
					UpgradePanelController.ACT_W, UpgradePanelController.ACT_H,
					UpgradePanelController.ATLAS, UpgradePanelController.ATLAS);
		}

		int cx = panel.closeX(this.leftPos);
		int cy = panel.closeY(this.topPos);
		boolean flash = System.currentTimeMillis() < panel.closePressUntil();
		int off = flash ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, cx + off, cy + off,
				(float) UpgradePanelController.CLOSE_U, (float) UpgradePanelController.CLOSE_V,
				UpgradePanelController.CLOSE_W, UpgradePanelController.CLOSE_H,
				UpgradePanelController.ATLAS, UpgradePanelController.ATLAS);
		if (flash) {
			graphics.fill(cx + off, cy + off, cx + off + UpgradePanelController.CLOSE_W,
					cy + off + UpgradePanelController.CLOSE_H, UpgradePanelController.PRESS_DARKEN);
		}
	}

	private void drawTabButton(GuiGraphicsExtractor graphics) {
		int bx = panel.gearX(this.leftPos);
		int by = panel.gearY(this.topPos);
		boolean flash = System.currentTimeMillis() < panel.gearPressUntil();
		int off = flash ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, UPGRADES_ATLAS, bx + off, by + off,
				(float) UpgradePanelController.BTN_U, (float) UpgradePanelController.BTN_V,
				UpgradePanelController.BTN_W, UpgradePanelController.BTN_H,
				UpgradePanelController.ATLAS, UpgradePanelController.ATLAS);
		if (flash) {
			graphics.fill(bx + off, by + off, bx + off + UpgradePanelController.BTN_W,
					by + off + UpgradePanelController.BTN_H, UpgradePanelController.PRESS_DARKEN);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (panel.isOverGear(mouseX, mouseY, this.leftPos, this.topPos)) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("gui.alaindustrial.upgrades"),
					mouseX, mouseY);
			return;
		}
		if (this.menu.isPanelOpen() && panel.isOverPanel(mouseX, mouseY, this.leftPos, this.topPos)) {
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
		if (this.menu.isPanelOpen() && panel.isOverPanel(mx, my, this.leftPos, this.topPos)) {
			return false;
		}
		return super.isHovering(left, top, w, h, mx, my);
	}

	// --- Recipe-viewer exclusion (MOD-080): absolute screen rects the viewers must keep clear ---

	/** The gear tab (always) and the open panel (dynamic, drag-aware) as absolute screen rectangles. */
	public List<Rect2i> extraGuiAreas() {
		List<Rect2i> areas = new ArrayList<>(2);
		areas.add(panel.gearArea(this.leftPos, this.topPos));
		if (this.menu.isPanelOpen()) {
			areas.add(panel.panelArea(this.leftPos, this.topPos));
		}
		return areas;
	}

	// --- Input: gear, modal panel (buttons, slots, drag), click routing ---

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int btn = event.button();
		// The gear always toggles the panel (it stays visible in the panel's transparent corner).
		if (btn == 0 && panel.isOverGear(event.x(), event.y(), this.leftPos, this.topPos)) {
			panel.onGearClick();
			return true;
		}
		// The open panel is modal over its footprint: consume every click so nothing beneath reacts.
		if (this.menu.isPanelOpen() && panel.isOverPanel(event.x(), event.y(), this.leftPos, this.topPos)) {
			if (btn == 0 && panel.isOverClose(event.x(), event.y(), this.leftPos, this.topPos)) {
				panel.onCloseClick();
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
				panel.beginDrag(event.x(), event.y());
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (panel.dragging() && event.button() == 0) {
			panel.dragTo(event.x(), event.y(), this.leftPos, this.topPos, this.width, this.height);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (panel.dragging() && event.button() == 0) {
			panel.endDrag();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop) {
		if (this.menu.isPanelOpen() && panel.isOverPanel(mx, my, this.leftPos, this.topPos)) {
			return false;
		}
		return super.hasClickedOutside(mx, my, guiLeft, guiTop);
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
}
