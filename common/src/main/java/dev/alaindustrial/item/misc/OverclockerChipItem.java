package dev.alaindustrial.item.misc;

import net.minecraft.world.item.ItemStack;

/**
 * An overclocker chip of a fixed tier (MOD-393). Three separate items rather than one stacking chip:
 * the tier is then visible on the item itself — its icon, its name, its recipe — instead of having to
 * be counted inside a slot, and each tier can be gated behind a different energy clot.
 *
 * <p>The tier a machine actually runs at is still clamped by what its voltage tier can feed
 * ({@code MachineBlockEntity.overclockerCap()}), so a chip too strong for the machine simply does not
 * fit its panel.
 */
public class OverclockerChipItem extends HintItem {

	private final int tier;

	public OverclockerChipItem(Properties properties, int tier, String... hintKeys) {
		super(properties, hintKeys);
		this.tier = tier;
	}

	/** Steps of overclocking this chip grants: 1, 2 or 3. */
	public int tier() {
		return tier;
	}

	/** The tier of the chip in {@code stack}, or 0 when it is not an overclocker at all. */
	public static int tierOf(ItemStack stack) {
		return stack.getItem() instanceof OverclockerChipItem chip ? chip.tier() : 0;
	}
}
