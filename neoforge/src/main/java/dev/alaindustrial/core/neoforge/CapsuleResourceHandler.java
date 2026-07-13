package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.item.ItemFluid;
import dev.alaindustrial.registry.ModContent;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * NeoForge item-side fluid capability for the Vacuum Capsule (MOD-063) — the item counterpart of the
 * block-side {@link TankAsResourceHandler}. Swaps between the empty and filled capsule items one bucket
 * at a time, so other mods' pipes/tanks can fill or drain a capsule sitting in a slot.
 *
 * <p>Ported from the vanilla {@code BucketResourceHandler} shape (verified 26.2): a single-slot
 * {@link ItemAccessResourceHandler} whose {@link #update} is strictly all-or-nothing — a whole bucket
 * ({@link FluidType#BUCKET_VOLUME} = 1000 mB, the NeoForge item-capability unit) or nothing. Emptying maps
 * to a component-free empty capsule (so it stacks with crafted ones); filling maps to a filled capsule
 * carrying the new fluid in its {@code capsule_fluid} component.
 */
public final class CapsuleResourceHandler extends ItemAccessResourceHandler<FluidResource> {

	public CapsuleResourceHandler(ItemAccess itemAccess) {
		super(itemAccess, 1);
	}

	private static Fluid fluidOf(ItemResource resource) {
		if (resource.is(ModContent.FILLED_VACUUM_CAPSULE.get())) {
			return ItemFluid.get(resource.toStack());
		}
		return Fluids.EMPTY;
	}

	@Override
	protected FluidResource getResourceFrom(ItemResource accessResource, int index) {
		Fluid fluid = fluidOf(accessResource);
		return fluid == Fluids.EMPTY ? FluidResource.EMPTY : FluidResource.of(fluid);
	}

	@Override
	protected int getAmountFrom(ItemResource accessResource, int index) {
		return getResourceFrom(accessResource, index).isEmpty() ? 0 : FluidType.BUCKET_VOLUME;
	}

	@Override
	protected ItemResource update(ItemResource accessResource, int index, FluidResource newResource, int newAmount) {
		if (newAmount == 0) {
			return ItemResource.of(ModContent.VACUUM_CAPSULE.get());
		}
		if (newAmount != FluidType.BUCKET_VOLUME) {
			return ItemResource.EMPTY;
		}
		ItemStack filled = new ItemStack(ModContent.FILLED_VACUUM_CAPSULE.get());
		ItemFluid.set(filled, newResource.getFluid());
		return ItemResource.of(filled);
	}

	@Override
	protected int getCapacity(int index, FluidResource resource) {
		Objects.checkIndex(index, size());
		return FluidType.BUCKET_VOLUME;
	}
}
