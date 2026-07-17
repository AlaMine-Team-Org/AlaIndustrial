package dev.alaindustrial.core.fabric;

import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidPort;
import dev.alaindustrial.item.ItemFluidBridge;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric implementation of the machine-slot fluid bridge (MOD-107): resolves the container item in a
 * machine slot through {@code FluidStorage.ITEM} and moves one bucket to/from a neutral {@link FluidPort},
 * leaving the swapped container in the machine's output slot.
 *
 * <p>Mirrors {@link FabricItemEnergyBridge} (MOD-084), with one structural difference: that bridge reaches a
 * player's inventory ({@code ContainerItemContext.ofPlayerSlot}), while a machine has its own
 * {@link Container}, wrapped here by {@code ContainerStorage} — the class Fabric used to call
 * {@code InventoryStorage} (renamed in 26.2; verified against the API jar, not from memory).
 */
public final class FabricItemFluidBridge implements ItemFluidBridge {

	@Override
	public long drainSlotIntoTank(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb) {
		return exchange(container, inSlot, outSlot, tank, maxMb, true);
	}

	@Override
	public long fillSlotFromTank(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb) {
		return exchange(container, inSlot, outSlot, tank, maxMb, false);
	}

	/**
	 * Whether {@code stack} speaks fluid at all. Uses a {@code withConstant} context on purpose: it never
	 * mutates the stack and needs no slot, which is exactly right for a yes/no question asked by
	 * {@code canPlaceItem} (which may be called for items that are nowhere near a slot yet).
	 */
	@Override
	public boolean isFluidContainer(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return ContainerItemContext.withConstant(stack).find(FluidStorage.ITEM) != null;
	}

	/**
	 * Move exactly {@code maxMb} between the slot's container item and the tank, or nothing at all.
	 *
	 * <p>Everything runs inside one {@link Transaction}: {@code StorageUtil.move} may transfer less than
	 * asked (a partial bucket), and the container swap — a capsule becoming an empty capsule — happens
	 * inside the item storage itself. Committing a short move would therefore either void the remainder or
	 * hand back a container that no longer matches its contents, so anything below the full amount aborts.
	 */
	private static long exchange(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb,
			boolean drain) {
		if (container.getItem(inSlot).isEmpty()) {
			return 0L;
		}
		ContainerStorage inventory = ContainerStorage.of(container, null);
		ContainerItemContext context =
				new OutputSlotContext(inventory.getSlot(inSlot), inventory.getSlot(outSlot));
		Storage<FluidVariant> item = context.find(FluidStorage.ITEM);
		if (item == null) {
			return 0L;
		}
		Storage<FluidVariant> tankStorage = TankAsFluidStorage.of(tank);
		// Fabric counts droplets, the mod counts millibuckets; TankAsFluidStorage converts on its own side,
		// so only the requested amount needs scaling here.
		long droplets = maxMb * FluidAmounts.FABRIC_DROPLETS_PER_MB;
		try (Transaction transaction = Transaction.openOuter()) {
			long moved = drain
					? StorageUtil.move(item, tankStorage, variant -> true, droplets, transaction)
					: StorageUtil.move(tankStorage, item, variant -> true, droplets, transaction);
			if (moved < droplets) {
				return 0L;
			}
			transaction.commit();
			return moved / FluidAmounts.FABRIC_DROPLETS_PER_MB;
		}
	}

	/**
	 * A context whose main slot is the machine's input and whose swapped container always lands in the
	 * output slot.
	 *
	 * <p>{@code ContainerItemContext.ofSingleSlot} cannot serve here: its {@code insertOverflow} returns 0,
	 * so the emptied container would have to fit back into the input slot or the exchange would silently
	 * fail. Fabric ships no factory that takes a fallback slot, and the interface is deliberately not
	 * {@code @ApiStatus.NonExtendable}, so a machine-shaped context is implemented here. {@code insert} is
	 * overridden too — the default tries the main slot first, which would drop the emptied container back
	 * into the input and leave the player's bucket looking stuck in the wrong half of the GUI.
	 */
	private record OutputSlotContext(SingleSlotStorage<ItemVariant> input, SingleSlotStorage<ItemVariant> output)
			implements ContainerItemContext {

		@Override
		public SingleSlotStorage<ItemVariant> getMainSlot() {
			return input;
		}

		@Override
		public long insert(ItemVariant variant, long maxAmount, TransactionContext transaction) {
			return output.insert(variant, maxAmount, transaction);
		}

		@Override
		public long insertOverflow(ItemVariant variant, long maxAmount, TransactionContext transaction) {
			return output.insert(variant, maxAmount, transaction);
		}

		@Override
		public List<SingleSlotStorage<ItemVariant>> getAdditionalSlots() {
			return List.of(output);
		}
	}
}
