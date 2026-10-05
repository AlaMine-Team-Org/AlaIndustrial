package dev.alaindustrial.compat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

/**
 * Opening an external link from a screen, whose API differs between the Minecraft lines (MOD-703,
 * ADR-036). Every line has a twin of this class with the same signature; only the body differs.
 *
 * <p><b>This twin: Minecraft 26.2</b>: the link opens through the per-OS {@code Util.getPlatform()}
 * handler, and the opener and the confirmation screen take the URL as a {@code String}.
 */
public final class Links {

	private Links() {
	}

	/**
	 * Asks the player to confirm {@code url}, opens it in the system browser on yes, and returns to
	 * {@code returnTo} either way.
	 */
	public static void confirmAndOpen(Minecraft minecraft, Screen returnTo, String url) {
		minecraft.setScreenAndShow(new ConfirmLinkScreen(confirmed -> {
			if (confirmed) {
				Util.getPlatform().openUri(url);
			}
			minecraft.setScreenAndShow(returnTo);
		}, url, true));
	}
}
