package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.SawmillMode;
import dev.alaindustrial.menu.SawmillMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Texture-backed screen for the LV sawmill (MOD-150/MOD-215). Draws its own GUI atlas — same frame
 * and energy bar as the other processing machines, but the progress sprite is a saw blade cutting
 * left-to-right ({@link ProgressMachineScreen}) — and adds a row of four {@link SawmillMode} buttons
 * below the slots. Clicking a button rides the vanilla container-button channel
 * ({@code handleInventoryButtonClick}) to switch the machine's mode; the active button is
 * highlighted, and each shows a ghost item + tooltip.
 *
 * <p>Layout (MOD-215, closing the MOD-150 open question): the input/saw/output row sits high in the
 * frame (slots at y=19, see {@link SawmillMenu#addMachineSlots}), which frees the band beneath it for
 * a centered row of mode buttons — they no longer compete with the slots for the top of the panel.
 */
public class SawmillScreen extends ProgressMachineScreen<SawmillMenu> {
	private static final Identifier TEXTURE = Industrialization.id("textures/gui/container/sawmill.png");

	// Saw-blade progress sprite: the atlas holds the lit version in the service area, the unlit track
	// is baked into the frame at the same size, on the slot row itself — between input and output.
	private static final ProgressSpec PROGRESS = new ProgressSpec(
			192, 1, 22, 12,  // sprite u/v/w/h
			82, 20,           // dest x/y in the 176×166 frame
			false);           // no min-1px

	// Four 18×18 mode buttons in a centered row below the slots (relative to leftPos/topPos).
	private static final int BUTTON_SIZE = 18;
	private static final int BUTTON_Y = 48;
	private static final int BUTTON_X0 = 52;

	/**
	 * The sawmill's status row sits ABOVE the mode buttons, not in the family's usual place.
	 *
	 * <p>{@link MachineScreen#STATUS_ROW_Y} is y=61, which on every other machine in the family is empty
	 * frame — here it is the middle of the four 18×18 mode buttons (y=48..65, x=52..123). The band this
	 * screen has instead is the gap between the slot row (which ends at y=35, the slots being higher on
	 * this frame than on the others) and the buttons.
	 */
	private static final int STATUS_Y = 38;

	private static final int COLOR_BG = 0xFF2B2B2B;
	private static final int COLOR_BG_ACTIVE = 0xFF5A4A21;
	private static final int COLOR_BORDER_ACTIVE = 0xFFFFC94A;
	private static final int COLOR_HOVER = 0x40FFFFFF;

	public SawmillScreen(SawmillMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PROGRESS);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	private static int buttonX(int ordinal) {
		return BUTTON_X0 + ordinal * BUTTON_SIZE;
	}

	/** Which mode button (if any) the given absolute screen point is over; null when none. */
	private SawmillMode buttonAt(double mx, double my) {
		for (SawmillMode m : SawmillMode.values()) {
			int bx = this.leftPos + buttonX(m.ordinal());
			int by = this.topPos + BUTTON_Y;
			if (mx >= bx && mx < bx + BUTTON_SIZE && my >= by && my < by + BUTTON_SIZE) {
				return m;
			}
		}
		return null;
	}

	/**
	 * Mode buttons are drawn in the FOREGROUND pass (not {@code drawMachineFrame}) because they render
	 * ghost item icons via {@code graphics.item(...)} — item rendering in this codebase only happens in
	 * the {@code extractContents} pass (see the upgrade panel in {@link MachineScreen}); drawing items in
	 * the background layer is not done anywhere and is unreliable. Called after {@code super} so the
	 * buttons sit on top of the frame; the upgrade panel/gear (also drawn by super) never overlap this
	 * row (they live in the top-right corner).
	 */
	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		drawProcessingStatus(graphics, this.menu.getStatus(), STATUS_Y);
		SawmillMode active = this.menu.getMode();
		for (SawmillMode m : SawmillMode.values()) {
			int bx = this.leftPos + buttonX(m.ordinal());
			int by = this.topPos + BUTTON_Y;
			boolean isActive = m == active;
			graphics.fill(bx, by, bx + BUTTON_SIZE, by + BUTTON_SIZE, isActive ? COLOR_BG_ACTIVE : COLOR_BG);
			if (isActive) {
				// 1px highlight frame around the selected mode.
				graphics.fill(bx, by, bx + BUTTON_SIZE, by + 1, COLOR_BORDER_ACTIVE);
				graphics.fill(bx, by + BUTTON_SIZE - 1, bx + BUTTON_SIZE, by + BUTTON_SIZE, COLOR_BORDER_ACTIVE);
				graphics.fill(bx, by, bx + 1, by + BUTTON_SIZE, COLOR_BORDER_ACTIVE);
				graphics.fill(bx + BUTTON_SIZE - 1, by, bx + BUTTON_SIZE, by + BUTTON_SIZE, COLOR_BORDER_ACTIVE);
			}
			// Hover tint BEFORE the icon so the item stays crisp on top (matches MachineScreen.drawPanel).
			if (mouseX >= bx && mouseX < bx + BUTTON_SIZE && mouseY >= by && mouseY < by + BUTTON_SIZE) {
				graphics.fill(bx, by, bx + BUTTON_SIZE, by + BUTTON_SIZE, COLOR_HOVER);
			}
			graphics.item(m.iconStack(), bx + 1, by + 1);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		SawmillMode hovered = buttonAt(mouseX, mouseY);
		if (hovered != null) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable(hovered.translationKey()), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Only claim a click for a mode button when the modal upgrade panel is closed — while it is open
		// MachineScreen.mouseClicked is modal over its footprint, so defer to super. (The default layout
		// never overlaps the panel, but the button layout is an open question; this keeps it safe if retuned.)
		if (event.button() == 0 && !this.menu.isPanelOpen()) {
			SawmillMode clicked = buttonAt(event.x(), event.y());
			if (clicked != null) {
				if (clicked != this.menu.getMode() && this.minecraft != null && this.minecraft.gameMode != null) {
					this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, clicked.ordinal());
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
