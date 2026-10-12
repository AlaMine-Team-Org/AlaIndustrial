package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.client.render.ZonePreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The "show my reach" button (MOD-278, shared since MOD-802): a square EYE with no caption that toggles the
 * personal zone preview of the block whose screen is open ({@link ZonePreviewRenderer}). An eye reads the same
 * in every language, so the button needs no translation and cannot overflow its box; the tooltip names it.
 *
 * <p>The click rides the vanilla container-button channel ({@code handleInventoryButtonClick}): the menu
 * answers with a personal {@code ZonePreviewPayload} carrying the server's own zone. The button's lit state
 * comes from the renderer, which learns which block the open screen belongs to when the screen opens.
 *
 * @param x        left edge, relative to the screen frame
 * @param y        top edge, relative to the screen frame
 * @param buttonId the menu-button id the owning menu answers with the zone
 * @param showKey  tooltip while the zone is hidden
 * @param hideKey  tooltip while the zone is shown
 */
public record ZonePreviewButton(int x, int y, int buttonId, String showKey, String hideKey) {

	public static final int SIZE = 22;

	// Off: dark, quiet. On: the teal the zone itself is drawn in, so the button and the thing it controls are
	// visibly the same feature.
	private static final int OFF_BG = 0xFF2B2B2B;
	private static final int OFF_EDGE = 0xFF565B63;
	private static final int OFF_LABEL = 0xFF9AA0A6;
	private static final int ON_BG = 0xFF14484B;
	private static final int ON_EDGE = 0xFF4FD8DE;
	private static final int ON_LABEL = 0xFFC7FBFF;
	private static final int HOVER = 0x30FFFFFF;

	/** Whether the zone of the block whose screen is open is shown. */
	public boolean shown() {
		return ZonePreviewRenderer.isShownForOpenScreen();
	}

	public boolean isOver(int leftPos, int topPos, double mx, double my) {
		int bx = leftPos + x;
		int by = topPos + y;
		return mx >= bx && mx < bx + SIZE && my >= by && my < by + SIZE;
	}

	public void draw(GuiGraphicsExtractor graphics, int leftPos, int topPos, int mouseX, int mouseY) {
		int bx = leftPos + x;
		int by = topPos + y;
		boolean on = shown();
		int edge = on ? ON_EDGE : OFF_EDGE;
		graphics.fill(bx, by, bx + SIZE, by + SIZE, on ? ON_BG : OFF_BG);
		graphics.fill(bx, by, bx + SIZE, by + 1, edge);
		graphics.fill(bx, by + SIZE - 1, bx + SIZE, by + SIZE, edge);
		graphics.fill(bx, by, bx + 1, by + SIZE, edge);
		graphics.fill(bx + SIZE - 1, by, bx + SIZE, by + SIZE, edge);
		if (isOver(leftPos, topPos, mouseX, mouseY)) {
			graphics.fill(bx, by, bx + SIZE, by + SIZE, HOVER);
		}
		drawEye(graphics, bx + SIZE / 2, by + SIZE / 2, on ? ON_LABEL : OFF_LABEL, on);
	}

	/**
	 * The eye glyph, drawn from filled rects rather than a texture so it lives with the button and needs no
	 * atlas coordinates. Open eye = the zone is shown; struck through = it is hidden.
	 */
	private static void drawEye(GuiGraphicsExtractor graphics, int cx, int cy, int colour, boolean on) {
		// Lid: two stepped rows above and below, so the shape reads as an eye and not as a box.
		graphics.fill(cx - 7, cy - 1, cx + 7, cy, colour);
		graphics.fill(cx - 7, cy + 1, cx + 7, cy + 2, colour);
		graphics.fill(cx - 5, cy - 3, cx + 5, cy - 2, colour);
		graphics.fill(cx - 5, cy + 2, cx + 5, cy + 4, colour);
		graphics.fill(cx - 2, cy - 4, cx + 2, cy - 3, colour);
		graphics.fill(cx - 2, cy + 4, cx + 2, cy + 5, colour);
		if (on) {
			graphics.fill(cx - 2, cy - 2, cx + 2, cy + 3, ON_EDGE);
		} else {
			// Struck through: the plain "hidden" language, drawn as a stepped diagonal.
			for (int i = -6; i <= 6; i++) {
				graphics.fill(cx + i, cy + 3 - i / 2, cx + i + 1, cy + 4 - i / 2, colour);
			}
		}
	}

	/** The tooltip, when the mouse is over the button. */
	public void tooltip(GuiGraphicsExtractor graphics, Font font, int leftPos, int topPos, int mouseX, int mouseY) {
		if (isOver(leftPos, topPos, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font, Component.translatable(shown() ? hideKey : showKey), mouseX, mouseY);
		}
	}

	/** Handle a click; returns whether it was this button's. */
	public boolean clicked(Minecraft minecraft, int containerId, int leftPos, int topPos, MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !isOver(leftPos, topPos, event.x(), event.y())
				|| minecraft == null || minecraft.gameMode == null) {
			return false;
		}
		minecraft.gameMode.handleInventoryButtonClick(containerId, buttonId);
		// A click needs to be heard as well as seen — the same UI click the teleporter's toggle uses.
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, shown() ? 0.9F : 1.1F));
		return true;
	}
}
