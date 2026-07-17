package dev.alaindustrial.core.neoforge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Transactional NeoForge item-capability view of a vanilla {@link Container}.
 *
 * <p>MOD-104 uses 26.2's {@link ResourceHandler} API instead of the removed legacy item handler.
 * The vanilla container itself does not implement that interface, so this adapter enlists a full
 * inventory snapshot before mutation and restores it when the enclosing transaction aborts.
 * {@link WorldlyContainer} slot and face rules are preserved; plain containers expose all slots.
 */
public final class ContainerAsItemResourceHandler extends SnapshotJournal<List<ItemStack>>
		implements ResourceHandler<ItemResource> {
	private final Container container;
	private final Direction side;
	private final int[] slots;

	private ContainerAsItemResourceHandler(Container container, Direction side) {
		this.container = container;
		this.side = side;
		this.slots = container instanceof WorldlyContainer worldly && side != null
				? worldly.getSlotsForFace(side) : allSlots(container.getContainerSize());
	}

	public static ResourceHandler<ItemResource> of(Container container, Direction side) {
		return container == null ? null : new ContainerAsItemResourceHandler(container, side);
	}

	@Override public int size() { return slots.length; }

	@Override public ItemResource getResource(int index) { return ItemResource.of(stack(index)); }

	@Override public long getAmountAsLong(int index) { return stack(index).getCount(); }

	@Override
	public long getCapacityAsLong(int index, ItemResource resource) {
		checkIndex(index);
		return Math.min(container.getMaxStackSize(), resource.getMaxStackSize());
	}

	@Override
	public boolean isValid(int index, ItemResource resource) {
		if (resource.isEmpty()) return false;
		int slot = slot(index);
		return canInsert(slot, resource.toStack(1));
	}

	@Override
	public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
		TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
		int slot = slot(index);
		ItemStack candidate = resource.toStack(1);
		if (!canInsert(slot, candidate)) return 0;
		ItemStack existing = container.getItem(slot);
		if (!existing.isEmpty() && !resource.matches(existing)) return 0;
		int capacity = Math.min(container.getMaxStackSize(candidate), resource.getMaxStackSize());
		int inserted = Math.min(amount, capacity - existing.getCount());
		if (inserted <= 0) return 0;
		updateSnapshots(transaction);
		if (existing.isEmpty()) {
			container.setItem(slot, resource.toStack(inserted));
		} else {
			existing.grow(inserted);
			container.setChanged();
		}
		return inserted;
	}

	@Override
	public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
		TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
		int slot = slot(index);
		ItemStack existing = container.getItem(slot);
		if (existing.isEmpty() || !resource.matches(existing) || !canExtract(slot, existing)) return 0;
		int extracted = Math.min(amount, existing.getCount());
		if (extracted <= 0) return 0;
		updateSnapshots(transaction);
		container.removeItem(slot, extracted);
		return extracted;
	}

	@Override
	protected List<ItemStack> createSnapshot() {
		List<ItemStack> copy = new ArrayList<>(container.getContainerSize());
		for (int i = 0; i < container.getContainerSize(); i++) copy.add(container.getItem(i).copy());
		return copy;
	}

	@Override
	protected void revertToSnapshot(List<ItemStack> snapshot) {
		for (int i = 0; i < snapshot.size(); i++) container.setItem(i, snapshot.get(i).copy());
	}

	private boolean canInsert(int slot, ItemStack stack) {
		return container.canPlaceItem(slot, stack)
				&& (!(container instanceof WorldlyContainer worldly) || side == null
						|| worldly.canPlaceItemThroughFace(slot, stack, side));
	}

	private boolean canExtract(int slot, ItemStack stack) {
		return !(container instanceof WorldlyContainer worldly) || side == null
				|| worldly.canTakeItemThroughFace(slot, stack, side);
	}

	private ItemStack stack(int index) { return container.getItem(slot(index)); }

	private int slot(int index) {
		checkIndex(index);
		return slots[index];
	}

	private void checkIndex(int index) {
		if (index < 0 || index >= slots.length) throw new IndexOutOfBoundsException("item slot " + index);
	}

	private static int[] allSlots(int size) {
		int[] result = new int[size];
		for (int i = 0; i < size; i++) result[i] = i;
		return result;
	}
}
