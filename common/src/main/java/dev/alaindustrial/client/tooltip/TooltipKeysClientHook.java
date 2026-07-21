package dev.alaindustrial.client.tooltip;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.item.TooltipKeys;
import net.minecraft.client.Minecraft;

/**
 * Installs the client answer for {@link TooltipKeys} (MOD-108) — the "hold Shift for details" split in
 * item tooltips. Mirrors {@code MachineHumClientHook}: the client-only class lives here, both loaders'
 * client entrypoints call {@link #register()}, and a dedicated server never loads it.
 *
 * <p>Polls the window directly instead of the familiar {@code Screen.hasShiftDown()}: that helper does
 * not exist in 26.2 (verified against the client jar — {@code hasShiftDown} is now a method on the
 * keyboard <em>event</em>, and a tooltip has no event to ask). Both Shift keys count, matching what a
 * player expects from every other mod's tooltip.
 */
public final class TooltipKeysClientHook {

	public static void register() {
		TooltipKeys.CLIENT = TooltipKeysClientHook::shiftHeld;
	}

	private static boolean shiftHeld() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LSHIFT)
				|| InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_RSHIFT);
	}

	private TooltipKeysClientHook() {}
}
