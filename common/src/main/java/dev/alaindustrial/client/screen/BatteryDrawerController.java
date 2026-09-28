package dev.alaindustrial.client.screen;

import dev.alaindustrial.Industrialization;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Geometry of the battery drawer (MOD-679): the bolt key beside the energy bar and the drawer that slides
 * out from behind the frame on the bar's side.
 *
 * <p><b>Placed from the energy bar, not per screen.</b> Every machine screen already names its bar through
 * {@link MachineScreen.EnergyBarSpec} when it draws it; the screen hands that spec here, and the key and
 * drawer follow it — left of a left bar, right of a right one. Thirty screens therefore needed no edit,
 * and a screen that moves its bar moves its key with it.
 *
 * <p>All positions are relative to {@code leftPos}/{@code topPos}, like vanilla slot coordinates.
 */
public final class BatteryDrawerController {
	static final Identifier ATLAS = Industrialization.id("textures/gui/container/battery_drawer.png");
	static final int ATLAS_W = 128, ATLAS_H = 64;

	/** The key: three 10×12 states side by side at the top of the atlas (off, hover, on). */
	static final int KEY_W = 10, KEY_H = 12, KEY_V = 0;
	static final int KEY_U_OFF = 0, KEY_U_HOVER = 10, KEY_U_ON = 20;
	/** The drawer, one per side: its chevrons point into the machine. The slot frame is part of the art. */
	static final int DRAWER_W = 38, DRAWER_H = 28, DRAWER_V = 16;
	static final int DRAWER_U_LEFT = 0, DRAWER_U_RIGHT = 40;
	/** Top-left of the slot frame inside the drawer art, per side. */
	private static final int SLOT_FRAME_X_LEFT = 5, SLOT_FRAME_X_RIGHT = 15, SLOT_FRAME_Y = 5;
	/** Gap between the key and the bar's outer frame (the frame is 2px wider than the bar's inner fill). */
	private static final int KEY_GAP = 13;
	/** How far the drawer tucks under the GUI frame, so it reads as sliding out from behind it. */
	private static final int TUCK = 4;

	/**
	 * The last drawer state the player chose, kept for the session: a player who works off batteries opens
	 * it once, not on every machine they walk up to. Not persisted to disk on purpose; a fresh session
	 * starts with the tidy, closed frame.
	 */
	static boolean rememberedOpen;

	private MachineScreen.@Nullable EnergyBarSpec bar;
	private int imageWidth;

	/**
	 * Record the bar the screen just drew. Returns whether the geometry changed, which is the screen's cue
	 * to move the drawer slot.
	 */
	boolean track(MachineScreen.EnergyBarSpec spec, int imageWidth) {
		if (spec.equals(this.bar) && imageWidth == this.imageWidth) {
			return false;
		}
		this.bar = spec;
		this.imageWidth = imageWidth;
		return true;
	}

	/** Whether the screen has drawn its energy bar yet — until then there is nowhere to put the key. */
	boolean placed() {
		return bar != null;
	}

	/** The key and the drawer sit on the side of the frame the bar is closer to. */
	boolean rightSide() {
		return bar != null && bar.barX() > imageWidth / 2;
	}

	int keyX() {
		return rightSide() ? bar.barX() + KEY_GAP : bar.barX() - KEY_GAP;
	}

	/** Bottom-aligned with the bar's outer frame, which ends one pixel below {@code barBottom}. */
	int keyY() {
		return bar.barBottom() + 2 - KEY_H;
	}

	int drawerX() {
		return rightSide() ? imageWidth - TUCK : TUCK - DRAWER_W;
	}

	/** The drawer's bottom edge lines up with the key's. */
	int drawerY() {
		return keyY() + KEY_H - DRAWER_H;
	}

	int drawerU() {
		return rightSide() ? DRAWER_U_RIGHT : DRAWER_U_LEFT;
	}

	/** Item position of the drawer slot (one pixel inside the frame, as vanilla slots are). */
	int slotItemX() {
		return drawerX() + (rightSide() ? SLOT_FRAME_X_RIGHT : SLOT_FRAME_X_LEFT) + 1;
	}

	int slotItemY() {
		return drawerY() + SLOT_FRAME_Y + 1;
	}

	boolean isOverKey(double mx, double my, int leftPos, int topPos) {
		if (!placed()) {
			return false;
		}
		int x = leftPos + keyX();
		int y = topPos + keyY();
		return mx >= x && mx < x + KEY_W && my >= y && my < y + KEY_H;
	}

	boolean isOverDrawer(double mx, double my, int leftPos, int topPos) {
		if (!placed()) {
			return false;
		}
		int x = leftPos + drawerX();
		int y = topPos + drawerY();
		return mx >= x && mx < x + DRAWER_W && my >= y && my < y + DRAWER_H;
	}

	Rect2i drawerArea(int leftPos, int topPos) {
		return new Rect2i(leftPos + drawerX(), topPos + drawerY(), DRAWER_W, DRAWER_H);
	}
}
