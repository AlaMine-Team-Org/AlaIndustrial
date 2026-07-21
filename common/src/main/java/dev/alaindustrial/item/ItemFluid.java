package dev.alaindustrial.item;

import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Item-side fluid store for the Vacuum Capsule (MOD-063) — the fluid counterpart of {@link ItemEnergy}.
 * The held fluid lives in the {@code alaindustrial:capsule_fluid} data component ({@code Holder<Fluid>});
 * this helper owns the read/write rules so item code never touches the component directly.
 *
 * <p>Conventions mirror {@link ItemEnergy}:
 * <ul>
 * <li>An absent component reads as {@link Fluids#EMPTY}, and writing empty removes the component — an
 * empty capsule and a freshly crafted one are component-identical (no "same-looking but unequal" stacks).</li>
 * <li>The stored value is the fluid's built-in registry holder, whose identity is stable per fluid, so
 * two capsules of the same fluid carry an equal component and stack together.</li>
 * </ul>
 */
public final class ItemFluid {
	private ItemFluid() {
	}

	/** The capacity of one capsule: exactly one bucket. Amount is implicit (a capsule is all-or-nothing). */
	public static long capacityMillibuckets() {
		return dev.alaindustrial.core.fluid.FluidAmounts.BUCKET;
	}

	/** The fluid this stack holds, or {@link Fluids#EMPTY} if the component is absent. */
	public static Fluid get(ItemStack stack) {
		Holder<Fluid> holder = stack.get(ModDataComponents.CAPSULE_FLUID.get());
		return holder == null ? Fluids.EMPTY : holder.value();
	}

	/** The fluid as a neutral {@link FluidHolder}, for the fluid core (empty holder if absent). */
	public static FluidHolder holder(ItemStack stack) {
		return FluidHolder.of(get(stack));
	}

	/** Whether this stack carries no fluid (absent component). */
	public static boolean isEmpty(ItemStack stack) {
		return get(stack) == Fluids.EMPTY;
	}

	/**
	 * Store {@code fluid} on the stack; {@code null}/{@link Fluids#EMPTY} removes the component. Uses the
	 * fluid's built-in registry holder ({@code builtInRegistryHolder()} — deprecated but the intended way
	 * to obtain the stable per-fluid holder for storage) so equal fluids yield an equal component value.
	 */
	@SuppressWarnings("deprecation")
	public static void set(ItemStack stack, Fluid fluid) {
		if (fluid == null || fluid == Fluids.EMPTY) {
			stack.remove(ModDataComponents.CAPSULE_FLUID.get());
		} else {
			stack.set(ModDataComponents.CAPSULE_FLUID.get(), fluid.builtInRegistryHolder());
		}
	}
}
