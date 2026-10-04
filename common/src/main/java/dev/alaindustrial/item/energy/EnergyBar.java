package dev.alaindustrial.item.energy;

import dev.alaindustrial.core.energy.EnergyTier;
import net.minecraft.world.item.ItemStack;

/**
 * The charge bar of every powered item, written once (MOD-707). Each item delegates
 * {@code getBarWidth} / {@code getBarColor} here, so a change to the bar is an edit of this file rather
 * than of thirteen copies. {@code isBarVisible} stays with the item: the battery hides its bar when empty,
 * every other powered item always shows it.
 *
 * <p>{@code maxWidth} is the vanilla bar width ({@code Item.MAX_BAR_WIDTH}), passed in by the item.
 */
public final class EnergyBar {
	private EnergyBar() {
	}

	/** Bar width for the item's charge against its own capacity; 0 for an item without a buffer. */
	public static int width(ItemStack stack, int maxWidth) {
		long capacity = ItemEnergy.capacity(stack);
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.min(maxWidth, maxWidth * ItemEnergy.get(stack) / capacity);
	}

	/** Bar colour of an item of voltage tier {@code tier} (LV for everything but the crystal blanks). */
	public static int color(EnergyTier tier) {
		return tier.color();
	}
}
