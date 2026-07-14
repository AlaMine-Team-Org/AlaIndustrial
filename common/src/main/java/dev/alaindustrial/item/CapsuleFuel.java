package dev.alaindustrial.item;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

/**
 * Shared rule for treating a lava-filled Vacuum Capsule as furnace fuel, on par with a vanilla lava bucket
 * (MOD-077). The furnace's own {@code FuelValues} is keyed purely by item type, so it cannot tell a
 * lava capsule from a water one — the loader-neutral mixins ({@code FuelValuesMixin},
 * {@code FurnaceFuelSlotMixin}) consult this stack-aware predicate instead, and the empty-capsule remainder
 * is set via the item's {@code craftRemainder} property.
 *
 * <p>Only lava capsules are fuel; a capsule holding any other fluid is not, which is why the check is on the
 * stored fluid component rather than the item alone.
 */
public final class CapsuleFuel {
	private CapsuleFuel() {
	}

	/** Whether {@code stack} is a Vacuum Capsule currently holding lava. */
	public static boolean isLavaCapsule(ItemStack stack) {
		return stack.is(ModContent.FILLED_VACUUM_CAPSULE.get()) && ItemFluid.get(stack) == Fluids.LAVA;
	}
}
