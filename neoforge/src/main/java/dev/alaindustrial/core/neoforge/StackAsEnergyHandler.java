package dev.alaindustrial.core.neoforge;

import dev.alaindustrial.item.ItemEnergy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * NeoForge item-side energy capability for the mod's powered items (MOD-084) — the item counterpart of
 * the block-side {@link BufferAsEnergyHandler}, and the NeoForge twin of Fabric's
 * {@code StackAsEnergyStorage}. Lets other mods' chargers push FE into a Battery Pouch, Energy Pack or
 * Electric Drill sitting in an inventory or machine slot.
 *
 * <p><b>Insert-only by design</b>, for the reasons documented on the Fabric twin: our items are charged
 * by foreign machines, never drained by them.
 *
 * <p><b>Not {@link ItemAccessEnergyHandler}.</b> NeoForge's ready-made handler stores charge in a
 * {@code DataComponentType<Integer>}, while the mod's shared {@code pouch_energy} component is a
 * {@code Long} — adopting it would mean migrating the component every existing world stores. This handler
 * follows the same structure (mutate a copy of the resource, then {@code exchange} it inside the
 * transaction) against {@link ItemEnergy} instead.
 *
 * <p>The write goes through {@link ItemEnergy#set} rather than touching the component directly: that is
 * where a pack's worn look is kept in sync with its charge, so a pack charged by a foreign machine still
 * lights up correctly.
 *
 * <p>Energy passes at 1 EU = 1 FE, the same identity rate the block side uses
 * ({@code EnergyUnits.UNITS_PER_EU}). Per-operation insertion is capped at the item's own
 * {@link ItemEnergy#inputRate}, the same ceiling the mod's Battery Box charge slot respects. The
 * {@code int} arithmetic of {@link EnergyHandler} is safe here: the largest buffer in the mod is the
 * pack's 20 000 EU.
 */
public final class StackAsEnergyHandler implements EnergyHandler {
	private final ItemAccess itemAccess;
	private final Item validItem;

	public StackAsEnergyHandler(ItemAccess itemAccess) {
		this.itemAccess = itemAccess;
		// Pin the item this handler was built for: the slot behind the access can change hands later, and
		// nothing may then be read from or written to a different item.
		this.validItem = itemAccess.getResource().getItem();
	}

	@Override
	public int insert(int amount, TransactionContext transaction) {
		TransferPreconditions.checkNonNegative(amount);
		// All three powered items are stacksTo(1); refusing anything else keeps per-count energy
		// arithmetic (and any dupe hiding in it) out of the picture.
		if (itemAccess.getAmount() != 1) {
			return 0;
		}
		ItemResource current = itemAccess.getResource();
		if (!current.is(validItem)) {
			return 0;
		}
		ItemStack updated = current.toStack();
		long stored = ItemEnergy.get(updated);
		long accepted = Math.min(Math.min(amount, ItemEnergy.inputRate(updated)),
				ItemEnergy.capacity(updated) - stored);
		if (accepted <= 0) {
			return 0;
		}
		ItemEnergy.set(updated, stored + accepted);
		if (itemAccess.exchange(ItemResource.of(updated), 1, transaction) != 1) {
			return 0;
		}
		return (int) accepted;
	}

	@Override
	public int extract(int amount, TransactionContext transaction) {
		return 0;
	}

	@Override
	public long getAmountAsLong() {
		ItemResource current = itemAccess.getResource();
		return current.is(validItem) ? ItemEnergy.get(current.toStack()) : 0L;
	}

	@Override
	public long getCapacityAsLong() {
		ItemResource current = itemAccess.getResource();
		return current.is(validItem) ? ItemEnergy.capacity(current.toStack()) : 0L;
	}
}
