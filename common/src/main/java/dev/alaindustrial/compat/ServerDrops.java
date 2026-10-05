package dev.alaindustrial.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Handing an item to a player from server code, whose API differs between the Minecraft lines (MOD-703,
 * ADR-036). Every line has a twin of this class with the same signatures; only the bodies differ. The
 * guide book, the commands, the fuel-rod assembly, the monitor panel, the stock display frame and the
 * pouch call these instead of naming their line's overload.
 *
 * <p><b>This twin: Minecraft 26.2</b>, whose drop and give-back calls take no prediction argument (26.3
 * added {@code Prediction}; its twin passes {@code SERVER_ONLY}).
 */
public final class ServerDrops {

	private ServerDrops() {
	}

	/** Drops {@code stack} at the player's feet (not thrown), as the server's own decision. */
	public static void drop(Player player, ItemStack stack) {
		player.drop(stack, false);
	}

	/** Puts {@code stack} back into the player's inventory, or drops what does not fit. */
	public static void placeBackInInventory(Player player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack);
	}
}
