package dev.alaindustrial.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Industrialization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
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
 *
 * <p>Since MOD-716 it is also the drawer's {@link ScreenOverlay}: the drawer behind the frame, the key on it,
 * the key's tooltip and click, and the open drawer as a place a click may land without dropping the stack.
 */
public final class BatteryDrawerController implements ScreenOverlay {
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

	// --- ScreenOverlay (MOD-716): moved from MachineScreen unchanged ---

	private final OverlayHost host;

	/**
	 * Whether the drawer art is shown this frame, decided before the frame is drawn and kept for the key:
	 * the frame's energy bar may place the drawer for the first time, and the key follows on the next frame.
	 */
	private boolean shownThisFrame;

	BatteryDrawerController(OverlayHost host) {
		this.host = host;
	}

	/** The drawer goes down BEFORE the frame, so the frame's edge covers its inner end (MOD-679). */
	@Override
	public void drawBehindFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		shownThisFrame = host.menu().hasBatteryDrawer() && placed();
		if (shownThisFrame && host.menu().isBatteryDrawerOpen()) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS, host.left() + drawerX(), host.top() + drawerY(),
					(float) drawerU(), (float) DRAWER_V, DRAWER_W, DRAWER_H, ATLAS_W, ATLAS_H);
		}
	}

	/** The key, on the frame next to the energy bar. */
	@Override
	public void drawOnFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!shownThisFrame) {
			return;
		}
		int u = host.menu().isBatteryDrawerOpen() ? KEY_U_ON
				: isOverKey(mouseX, mouseY, host.left(), host.top()) ? KEY_U_HOVER : KEY_U_OFF;
		graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS, host.left() + keyX(), host.top() + keyY(),
				(float) u, (float) KEY_V, KEY_W, KEY_H, ATLAS_W, ATLAS_H);
	}

	@Override
	public boolean handleTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!host.menu().hasBatteryDrawer() || !isOverKey(mouseX, mouseY, host.left(), host.top())) {
			return false;
		}
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.addAll(host.font().split(Component.translatable("gui.alaindustrial.battery_drawer"), 200));
		lines.addAll(host.font().split(Component.translatable("gui.alaindustrial.battery_drawer.hint")
				.withStyle(ChatFormatting.GRAY), 200));
		graphics.setTooltipForNextFrame(host.font(), lines, mouseX, mouseY);
		return true;
	}

	/** The key sits inside the frame, where nothing else answers a click. */
	@Override
	public boolean clickHandle(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && host.menu().hasBatteryDrawer()
				&& isOverKey(event.x(), event.y(), host.left(), host.top())) {
			host.toggleBatteryDrawer();
			return true;
		}
		return false;
	}

	/** The open drawer sticks out of the frame; a click on it is not a click that drops the held stack. */
	@Override
	public boolean keepsClickInside(double mouseX, double mouseY) {
		return host.menu().isBatteryDrawerOpen() && isOverDrawer(mouseX, mouseY, host.left(), host.top());
	}

	@Override
	public void addExclusionAreas(List<Rect2i> areas) {
		if (host.menu().hasBatteryDrawer() && host.menu().isBatteryDrawerOpen() && placed()) {
			areas.add(drawerArea(host.left(), host.top()));
		}
	}
}
