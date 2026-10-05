package dev.alaindustrial.compat.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/**
 * Keyboard access whose API differs between the Minecraft lines (MOD-703, ADR-036). Every line has a twin
 * of this class with the same signatures; only the bodies differ. Key mappings and the tooltip's Shift
 * poll call these, so their sources are the same on every line. The key codes themselves are always
 * {@code InputConstants.KEY_*}: each line compiles its own values in.
 *
 * <p><b>This twin: Minecraft 26.2</b>, still on GLFW. The keyboard input type is
 * {@code InputConstants.Type.KEYSYM} and its values are GLFW key codes ({@code InputConstants.KEY_H} is
 * {@code GLFW_KEY_H}, 72). Polling a key goes through the game window's GLFW handle, which does not exist
 * before the window does.
 */
public final class Keyboard {

	private Keyboard() {
	}

	/** The input type a keyboard {@code KeyMapping} default is declared in. 26.2: {@code KEYSYM}. */
	public static InputConstants.Type keyMappingType() {
		return InputConstants.Type.KEYSYM;
	}

	/** Whether {@code key} ({@code InputConstants.KEY_*}) is held right now; {@code false} before a window. */
	public static boolean isDown(int key) {
		Minecraft minecraft = Minecraft.getInstance();
		Window window = minecraft == null ? null : minecraft.getWindow();
		return window != null && InputConstants.isKeyDown(window, key);
	}
}
