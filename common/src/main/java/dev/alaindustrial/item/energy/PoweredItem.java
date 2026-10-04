package dev.alaindustrial.item.energy;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * An item that owns an EU buffer (MOD-707). {@link ItemEnergy} asks the item itself for its capacity and
 * intake instead of walking an {@code instanceof} ladder, and both loaders derive the item energy
 * capability from this interface ({@code ItemCapabilityRoster.energyItems()}), so a new powered item is
 * one class implementing this interface plus its {@code ContentManifest.ITEMS} entry — {@code ItemEnergy}
 * and the loader entry points are not touched.
 *
 * <p>A subclass inherits the answers of its parent and overrides only what differs (the netherite drill
 * overrides {@link #energyCapacity} alone), so correctness no longer depends on branch order.
 *
 * <p>Implementing this interface is a promise of a positive buffer: the gametest guard
 * {@code PoweredItemCatalog} checks every item of the roster for {@code capacity > 0}, independently.
 */
public interface PoweredItem {

	/** Max EU one item holds; read live, so a config reload is honoured. */
	long energyCapacity(ItemStack stack);

	/** Max EU/tick the item accepts in a charge slot or from a foreign charger. */
	long energyInputRate(ItemStack stack);

	/**
	 * Called by {@link ItemEnergy#set} after every write of the charge, with the clamped value — the one
	 * place charge changes, so a model, attribute or flag that follows the charge is refreshed here.
	 */
	default void onChargeChanged(ItemStack stack, long charge) {
	}

	/**
	 * This item's hover tooltip as a powered tool (MOD-716, ADR-040), or null when its tooltip is written
	 * elsewhere (the pouches and the battery have their own). Runs on the client: numbers are read through
	 * {@code ServerBalance}.
	 */
	default @Nullable PoweredToolTooltip toolTooltip() {
		return null;
	}
}
