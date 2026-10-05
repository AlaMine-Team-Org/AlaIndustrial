package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
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

	/** Click area of the recipe viewers (MOD-716): exactly the progress sprite. */
	public static final GuiRect PROGRESS_AREA = PROGRESS.area();
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

	/** Atlas, energy bar and progress sprite: the whole declared frame (MOD-716, CLI-3). */
	private static final MachineLayout LAYOUT = MachineLayout.of(TEXTURE, EnergyBarSpec.LEFT)
			.withProgress(PROGRESS);

	public SawmillScreen(SawmillMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, LAYOUT);
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
	 * the background layer is not done anywhere and is unreliable. Drawn under every overlay: both panels
	 * can be dragged across this row and then cover it.
	 *
	 * <p>A button answers a click, a tooltip and the hover tint by one rule, {@link #frameAcceptsInput}
	 * (MOD-738): while it is false the button shows neither the tint nor the tooltip, since a click there
	 * would not switch the mode. The tint asks {@link #litModeAt}, the same question the tooltip asks.
	 */
	@Override
	protected void drawUnderPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		drawProcessingStatus(graphics, this.menu.getStatus(), STATUS_Y);
		SawmillMode active = this.menu.getMode();
		SawmillMode lit = litModeAt(mouseX, mouseY);
		for (SawmillMode m : SawmillMode.values()) {
			int bx = this.leftPos + buttonX(m.ordinal());
			int by = this.topPos + BUTTON_Y;
			boolean isActive = m == active;
			graphics.fill(bx, by, bx + BUTTON_SIZE, by + BUTTON_SIZE,
					isActive ? GuiStyle.BUTTON_ACTIVE : GuiStyle.BUTTON);
			if (isActive) {
				// 1px highlight frame around the selected mode.
				graphics.fill(bx, by, bx + BUTTON_SIZE, by + 1, GuiStyle.BUTTON_ACTIVE_EDGE);
				graphics.fill(bx, by + BUTTON_SIZE - 1, bx + BUTTON_SIZE, by + BUTTON_SIZE,
						GuiStyle.BUTTON_ACTIVE_EDGE);
				graphics.fill(bx, by, bx + 1, by + BUTTON_SIZE, GuiStyle.BUTTON_ACTIVE_EDGE);
				graphics.fill(bx + BUTTON_SIZE - 1, by, bx + BUTTON_SIZE, by + BUTTON_SIZE,
						GuiStyle.BUTTON_ACTIVE_EDGE);
			}
			// Hover tint BEFORE the icon so the item stays crisp on top (matches MachineScreen.drawPanel).
			if (m == lit) {
				graphics.fill(bx, by, bx + BUTTON_SIZE, by + BUTTON_SIZE, GuiStyle.HOVER_WASH);
			}
			graphics.item(m.iconStack(), bx + 1, by + 1);
		}
	}

	/**
	 * The mode button lit by the hover tint at the point, or {@code null} when no mode button is there or the
	 * screen is deaf to its own controls ({@link #frameAcceptsInput} — the rule a click obeys, MOD-738). Public as
	 * the seam the L3 stand checks; {@link #drawUnderPanels} tints exactly this button and
	 * {@link #modeTooltipAt} names it.
	 */
	public SawmillMode litModeAt(double mouseX, double mouseY) {
		return frameAcceptsInput(mouseX, mouseY) ? buttonAt(mouseX, mouseY) : null;
	}

	/**
	 * The mode tooltip at the point: the name of {@link #litModeAt}, or {@code null} where no button is lit.
	 * Public as the seam the L3 stand checks; {@link #extractTooltip} only hands its answer on.
	 */
	public Component modeTooltipAt(double mouseX, double mouseY) {
		SawmillMode lit = litModeAt(mouseX, mouseY);
		return lit == null ? null : Component.translatable(lit.translationKey());
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		Component tip = modeTooltipAt(mouseX, mouseY);
		if (tip != null) {
			graphics.setTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Only claim a click for a mode button when frameAcceptsInput lets it through: the buttons are deaf while
		// the upgrade panel is open anywhere — it can be dragged over this row — and under the open statistics
		// panel (MOD-693); otherwise defer to super. The tooltip and the hover tint obey the same rule (MOD-738).
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && frameAcceptsInput(event.x(), event.y())) {
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
