package dev.alaindustrial.client.tooltip;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.compat.client.Keyboard;
import dev.alaindustrial.item.misc.TooltipKeys;

/**
 * Installs the client answer for {@link TooltipKeys} (MOD-108) — the "hold Shift for details" split in
 * item tooltips. Mirrors {@code MachineHumClientHook}: the client-only class lives here, both loaders'
 * client entrypoints call {@link #register()}, and a dedicated server never loads it.
 *
 * <p>Polls the keyboard directly ({@link Keyboard#isDown}) instead of the familiar
 * {@code Screen.hasShiftDown()}: that helper does not exist (verified against the client jar —
 * {@code hasShiftDown} is now a method on the keyboard <em>event</em>, and a tooltip has no event to
 * ask). Both Shift keys count, matching what a player expects from every other mod's tooltip.
 */
public final class TooltipKeysClientHook {

	public static void register() {
		TooltipKeys.CLIENT = TooltipKeysClientHook::shiftHeld;
	}

	private static boolean shiftHeld() {
		return Keyboard.isDown(InputConstants.KEY_LSHIFT) || Keyboard.isDown(InputConstants.KEY_RSHIFT);
	}

	private TooltipKeysClientHook() {}
}
