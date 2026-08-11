package dev.alaindustrial.client.screen;

import dev.alaindustrial.menu.AbstractScrollingChestMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Shared drawing for every scrolling chest-style window: the modular warehouse (MOD-287, two sizes —
 * 3 rows in a 176x166 panel for a lone module, 6 rows in a 195x220 panel for anything larger) and
 * the double chest (MOD-391, always the 6-row scrolling panel). Since MOD-391 the menu bound is the
 * scrolling base rather than the warehouse menu, so both families share this one screen.
 *
 * <h2>Why the window stops at six rows</h2>
 *
 * <p>A window of twelve rows is 328 px of panel, and the player photographed it running off the top
 * AND the bottom of the screen with the hotbar clipped. The 108-slot cap had been chosen on the
 * assumption that 108 slots fit one screen; the assumption was never checked against the scale the
 * game picks. {@code Window.calculateScale} raises the GUI scale while
 * {@code height / (scale + 1) >= 240}, so at the default "auto" the usable height is 240 px on 1440p
 * and 4K, 256 on 1366x768, 270 on 1080p — never more than about 270 on any common resolution. Six
 * rows come to 220 px: exactly the vanilla double chest, the tallest container window the game itself
 * ships. The rows past the sixth are reached with the scrollbar on the right.
 *
 * <p>The bar is drawn for every 6-row window, greyed out when the warehouse has nothing to scroll to,
 * rather than appearing and vanishing between cluster sizes — a control that comes and goes is harder
 * to read than one that is visibly inactive.
 *
 * @param <M> the concrete menu size this screen is paired with
 */
public abstract class AbstractStorageScreen<M extends AbstractScrollingChestMenu> extends AbstractContainerScreen<M> {
	/** Frame + player inventory + hotbar around the storage rows (see {@code AbstractChestMenu}). */
	private static final int HEIGHT_BASE = 112;
	private static final int ROW_HEIGHT = 18;
	/** Panel width without a scrollbar, and with one — the two atlases, in the same order. */
	protected static final int IMAGE_WIDTH = 176;
	protected static final int IMAGE_WIDTH_SCROLL = 195;
	private static final int TEX_W = 256;
	private static final int TEX_H = 512;
	/** Gap between the last storage row and the "Inventory" label, matching the chest screens. */
	private static final int INV_LABEL_GAP = 11;

	// Scrollbar geometry — must match tools/gen_storage_gui.py, which draws the groove and the thumb.
	private static final int GRID_TOP_Y = 18;
	private static final int GROOVE_X = 177;
	private static final int GROOVE_W = 14;
	private static final int THUMB_X = GROOVE_X + 1;
	private static final int THUMB_W = 12;
	private static final int THUMB_H = 15;
	private static final int THUMB_U = 196;
	private static final int THUMB_DISABLED_U = THUMB_U + THUMB_W + 2;
	private static final int THUMB_V = 0;

	private final Identifier texture;
	private final boolean scrollable;
	/**
	 * The row the thumb is drawn at. Kept locally rather than read back from the menu so dragging
	 * tracks the mouse instead of the network: the server is still the authority on what the slots
	 * hold, and it clamps the same way, so the two cannot disagree about where the window ends.
	 */
	private int scrollRow;
	private boolean dragging;

	protected AbstractStorageScreen(M menu, Inventory playerInventory, Component title, int rows,
			int imageWidth, Identifier texture) {
		// 4-arg constructor pins the (final) imageWidth/imageHeight before anything renders.
		super(menu, playerInventory, title, imageWidth, panelHeight(rows));
		// Passed in as a whole literal rather than built from `rows`: the asset verifier resolves
		// texture paths statically and cannot follow a concatenation.
		this.texture = texture;
		this.scrollable = imageWidth == IMAGE_WIDTH_SCROLL;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = playerInvTopY(rows) - INV_LABEL_GAP;
	}

	/** Total panel height: 166 for 3 rows, 220 for 6. */
	protected static int panelHeight(int rows) {
		return rows * ROW_HEIGHT + HEIGHT_BASE;
	}

	/** Y of the player inventory's first row — mirrors the layout maths in {@code AbstractChestMenu}. */
	private static int playerInvTopY(int rows) {
		return 18 + rows * ROW_HEIGHT + 13;
	}

	@Override
	public void init() {
		super.init();
		// Left-align the title like every other Ala Industrial screen (default centres it).
		this.titleLabelX = 8;
		this.scrollRow = this.menu.getTopRow();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, this.leftPos, this.topPos, 0.0F, 0.0F,
				this.imageWidth, this.imageHeight, TEX_W, TEX_H);
		if (scrollable) {
			int u = this.menu.isScrollable() ? THUMB_U : THUMB_DISABLED_U;
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
					this.leftPos + THUMB_X, this.topPos + GRID_TOP_Y + thumbOffset(),
					(float) u, (float) THUMB_V, THUMB_W, THUMB_H, TEX_W, TEX_H);
		}
	}

	/** Travel of the thumb inside the groove, in pixels. */
	private int thumbTravel() {
		return this.menu.getRows() * ROW_HEIGHT - THUMB_H;
	}

	private int thumbOffset() {
		int max = this.menu.getMaxTopRow();
		return max <= 0 ? 0 : Math.round((float) thumbTravel() * scrollRow / max);
	}

	/** Scroll to {@code row}, clamped, and tell the server — it owns what the slots then show. */
	private void scrollTo(int row) {
		int clamped = Math.max(0, Math.min(row, this.menu.getMaxTopRow()));
		if (clamped == scrollRow) {
			return;
		}
		scrollRow = clamped;
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, clamped);
		}
	}

	/** The row the thumb's centre would be at if it sat at {@code mouseY}. */
	private int rowAtMouse(double mouseY) {
		int travel = thumbTravel();
		if (travel <= 0) {
			return 0;
		}
		double offset = mouseY - (this.topPos + GRID_TOP_Y) - THUMB_H / 2.0;
		return (int) Math.round(offset / travel * this.menu.getMaxTopRow());
	}

	private boolean overScrollbar(double mouseX, double mouseY) {
		return scrollable
				&& mouseX >= this.leftPos + GROOVE_X && mouseX < this.leftPos + GROOVE_X + GROOVE_W
				&& mouseY >= this.topPos + GRID_TOP_Y
				&& mouseY < this.topPos + GRID_TOP_Y + this.menu.getRows() * ROW_HEIGHT;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.menu.isScrollable() && scrollY != 0) {
			scrollTo(scrollRow - (int) Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && overScrollbar(event.x(), event.y())) {
			if (this.menu.isScrollable()) {
				dragging = true;
				scrollTo(rowAtMouse(event.y()));
			}
			// Consumed either way: a click on an inactive bar must not fall through to the slot behind.
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging && event.button() == 0) {
			scrollTo(rowAtMouse(event.y()));
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && event.button() == 0) {
			dragging = false;
			return true;
		}
		return super.mouseReleased(event);
	}
}
