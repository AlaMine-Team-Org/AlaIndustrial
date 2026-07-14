package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Neutral EU buffer for items (MOD-052) — the item-side counterpart of the block-side energy core,
 * and the shared foundation for future powered items (electric tools / armor,
 * {@code docs/FUTURE_CONTENT.md}). Charge lives in the {@code alaindustrial:pouch_energy} data
 * component ({@code Long}); this helper owns the read/write/clamp rules so items never touch the
 * component directly.
 *
 * <p>Conventions:
 * <ul>
 * <li>An absent component reads as 0 EU, and writing 0 removes the component — a drained item and a
 * freshly crafted one are component-identical (no "same-looking but unequal" stacks).</li>
 * <li>Values are clamped to {@code [0, capacity(stack)]} on every write.</li>
 * <li>{@link #capacity} resolves per item type; non-powered items report 0 and ignore writes.</li>
 * </ul>
 */
public final class ItemEnergy {
	private ItemEnergy() {
	}

	/** Max EU the item can hold; 0 for items without an energy buffer. */
	public static long capacity(ItemStack stack) {
		if (stack.getItem() instanceof PouchItem) {
			return Config.lvPouchBuffer;
		}
		if (stack.getItem() instanceof EnergyPackItem) {
			return Config.energyPackBuffer;
		}
		return 0L;
	}

	/**
	 * Max EU/tick this item accepts while sitting in a charge slot. A charger caps its transfer at
	 * {@code min(its own tier ceiling, inputRate(stack))} — the item's own ceiling, so a small pouch
	 * cannot be force-fed at a big charger's rate. 0 for items without a buffer.
	 */
	public static long inputRate(ItemStack stack) {
		if (stack.getItem() instanceof PouchItem) {
			return EnergyTier.LV.maxVoltage();
		}
		if (stack.getItem() instanceof EnergyPackItem) {
			return Config.energyPackInputRate;
		}
		return 0L;
	}

	/** Stored EU (absent component = 0), clamped to the item's capacity. */
	public static long get(ItemStack stack) {
		Long value = stack.get(ModDataComponents.POUCH_ENERGY.get());
		if (value == null) {
			return 0L;
		}
		return Math.max(0L, Math.min(value, capacity(stack)));
	}

	/** Store {@code eu} clamped to {@code [0, capacity]}; 0 removes the component. */
	public static void set(ItemStack stack, long eu) {
		long clamped = Math.max(0L, Math.min(eu, capacity(stack)));
		if (clamped == 0L) {
			stack.remove(ModDataComponents.POUCH_ENERGY.get());
		} else {
			stack.set(ModDataComponents.POUCH_ENERGY.get(), clamped);
		}
		if (stack.getItem() instanceof EnergyPackItem) {
			// The pack looks different when dead (red light, pale cells), and the worn model is chosen by
			// its EQUIPPABLE asset — so the visual follows the charge from the one place charge changes.
			EnergyPackItem.refreshWornAsset(stack, clamped);
		}
	}

	/** Adjust stored EU by {@code delta} (may be negative); result is clamped. */
	public static void add(ItemStack stack, long delta) {
		set(stack, get(stack) + delta);
	}

	/** Free space in the buffer: {@code capacity - stored}. */
	public static long room(ItemStack stack) {
		return capacity(stack) - get(stack);
	}
}
