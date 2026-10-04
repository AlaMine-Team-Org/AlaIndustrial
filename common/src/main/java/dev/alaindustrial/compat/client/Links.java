package dev.alaindustrial.compat.client;

import com.mojang.blaze3d.Blaze3D;
import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opening an external link from a screen, whose API differs between the Minecraft lines (MOD-703,
 * ADR-036). Every line has a twin of this class with the same signature; only the body differs.
 *
 * <p><b>This twin: Minecraft 26.3</b>, which opens links through SDL ({@code Blaze3D.openUri}) rather
 * than through a per-OS {@code Util.getPlatform()} handler, and whose opener and confirmation screen
 * take a {@link URI} instead of a {@code String}.
 */
public final class Links {

	private Links() {
	}

	/**
	 * Asks the player to confirm {@code url}, opens it in the system browser on yes, and returns to
	 * {@code returnTo} either way.
	 */
	public static void confirmAndOpen(Minecraft minecraft, Screen returnTo, String url) {
		URI uri = URI.create(url);
		minecraft.setScreenAndShow(new ConfirmLinkScreen(confirmed -> {
			if (confirmed) {
				Blaze3D.openUri(uri);
			}
			minecraft.setScreenAndShow(returnTo);
		}, uri, true));
	}
}
