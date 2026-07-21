package dev.alaindustrial.core.neoforge;

import com.google.common.primitives.Ints;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.item.ItemFluidBridge;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * NeoForge implementation of the machine-slot fluid bridge (MOD-107): resolves the container item in a
 * machine slot through {@code Capabilities.Fluid.ITEM} and moves one bucket to/from a neutral
 * {@link FluidPort}, leaving the swapped container in the machine's output slot.
 *
 * <p>Same shape as {@link NeoForgeItemEnergyBridge} (MOD-084) — capability off an {@link ItemAccess}, one
 * root transaction — with two differences: the access comes from the machine's own container rather than a
 * player slot, and the swapped container has to be relocated to the output slot afterwards.
 *
 * <p><b>Not</b> {@code IFluidHandlerItem} / {@code neoforge.fluids.FluidUtil}: both are deprecated for
 * removal in 26.2 (verified in the NeoForge sources, not assumed), in favour of this ResourceHandler API.
 */
public final class NeoForgeItemFluidBridge implements ItemFluidBridge {

	@Override
	public long drainSlotIntoTank(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb) {
		return exchange(container, inSlot, outSlot, tank, maxMb, true);
	}

	@Override
	public long fillSlotFromTank(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb) {
		return exchange(container, inSlot, outSlot, tank, maxMb, false);
	}

	/**
	 * Whether {@code stack} speaks fluid at all. {@code ItemAccess.forStack} never changes the underlying
	 * item, which is exactly right for a yes/no question from {@code canPlaceItem} — it must not mutate the
	 * stack it is asked about.
	 */
	@Override
	public boolean isFluidContainer(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return ItemAccess.forStack(stack).getCapability(Capabilities.Fluid.ITEM) != null;
	}

	/**
	 * Move exactly {@code maxMb} between the slot's container item and the tank, or nothing at all.
	 *
	 * <p>One root transaction covers the fluid move <em>and</em> the container relocation, so a tank that
	 * cannot take the whole bucket, or an output slot that cannot take the emptied container, rolls the
	 * whole thing back — the player never loses a capsule or half a bucket.
	 */
	private static long exchange(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb,
			boolean drain) {
		if (container.getItem(inSlot).isEmpty()) {
			return 0L;
		}
		ResourceHandler<ItemResource> inventory = VanillaContainerWrapper.of(container);
		// Strict: overflow must never spill into an unrelated machine slot — this bridge decides where the
		// swapped container goes (outSlot, below). oneByOne so a stack of capsules is handled one per call,
		// matching the pump's bucket cadence.
		ItemAccess input = ItemAccess.forHandlerIndexStrict(inventory, inSlot).oneByOne();
		ResourceHandler<FluidResource> item = input.getCapability(Capabilities.Fluid.ITEM);
		if (item == null) {
			return 0L;
		}
		ResourceHandler<FluidResource> tankHandler = TankAsResourceHandler.of(tank);
		// The mod's millibuckets and NeoForge's fluid amounts share the same scale (FluidType.BUCKET_VOLUME
		// is 1000), so the amount passes through unscaled; only the long→int narrowing is needed.
		int amount = Ints.saturatedCast(maxMb);
		try (Transaction transaction = Transaction.openRoot()) {
			int moved = drain
					? ResourceHandlerUtil.move(item, tankHandler, resource -> true, amount, transaction)
					: ResourceHandlerUtil.move(tankHandler, item, resource -> true, amount, transaction);
			if (moved != amount) {
				return 0L;
			}
			// The item handler swapped the container in place (a filled capsule became an empty one); move
			// that swapped container out of the input slot so the player sees it where a bucket would land.
			ItemResource swapped = input.getResource();
			if (!swapped.isEmpty()) {
				if (input.extract(swapped, 1, transaction) != 1) {
					return 0L;
				}
				if (inventory.insert(outSlot, swapped, 1, transaction) != 1) {
					return 0L;
				}
			}
			transaction.commit();
			return moved;
		}
	}
}
