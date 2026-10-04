package dev.alaindustrial.compat.client;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Keyboard access whose API differs between the Minecraft lines (MOD-703, ADR-036). Every line has a twin
 * of this class with the same signatures; only the bodies differ. Key mappings and the tooltip's Shift
 * poll call these, so their sources are the same on every line. The key codes themselves are always
 * {@code InputConstants.KEY_*}: each line compiles its own values in.
 *
 * <p><b>This twin: Minecraft 26.3</b>, which moved input from GLFW to SDL3. The keyboard input type is
 * {@code InputConstants.Type.KEYBOARD} (it was {@code KEYSYM}) and its values are SDL scancodes — the
 * same numbers vanilla's own {@code Options} passes. A player's own rebinding is unaffected:
 * {@code options.txt} stores the key <em>name</em> ({@code key.keyboard.h}), and 26.3 registers the same
 * names against the new values, so an existing file resolves to the same physical key. Polling a key
 * takes the scancode alone; there is no window handle to pass or to null-check.
 */
public final class Keyboard {

	private Keyboard() {
	}

	/** The input type a keyboard {@code KeyMapping} default is declared in. 26.3: {@code KEYBOARD}. */
	public static InputConstants.Type keyMappingType() {
		return InputConstants.Type.KEYBOARD;
	}

	/** Whether {@code key} ({@code InputConstants.KEY_*}) is held right now. */
	public static boolean isDown(int key) {
		return InputConstants.isKeyDown(key);
	}
}
