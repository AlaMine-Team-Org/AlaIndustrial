package dev.alaindustrial.core.fabric;

import dev.alaindustrial.item.ItemEnergy;
import dev.alaindustrial.registry.ModItems;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.DelegatingEnergyStorage;

/**
 * Fabric item-side energy capability for the mod's powered items (MOD-084) — the item counterpart of the
 * block-side {@link PortAsEnergyStorage}. Lets other mods' chargers (Powah's Player Transmitter, a
 * TechReborn charge slot, ...) push energy into a Battery Pouch, Energy Pack or Electric Drill sitting in
 * an inventory or machine slot.
 *
 * <p><b>Insert-only by design.</b> Foreign machines can fill our items; they cannot pull EU back out. The
 * pack hands its charge out itself ({@code EnergyPackItem.chargeStep}), the pouch and drill are
 * consumers, so an extraction path would add nothing a player wants and would open the ping-pong loop
 * MOD-065 recorded (TechReborn #2297) to foreign chargers as well.
 *
 * <p><b>Not {@code SimpleEnergyItem}.</b> That interface would provide a storage for free, but it stores
 * charge in Team Reborn's own {@code EnergyStorage.ENERGY_COMPONENT} — and its impl reaches for that
 * component through {@code static} helpers, which no override can redirect. Adopting it would strand the
 * charge of every pack in every existing world. So the storage is written against our own
 * {@code pouch_energy} component through {@link ItemEnergy}, following the structure Team Reborn uses in
 * {@code SimpleItemEnergyStorageImpl}: mutate a copy of the stack, then exchange the slot's variant for it
 * inside the transaction.
 *
 * <p>Energy passes at 1 EU = 1 Team Reborn unit, the same identity rate the block side uses
 * ({@code EnergyUnits.UNITS_PER_EU}). Per-operation insertion is capped at the item's own
 * {@link ItemEnergy#inputRate} — the same ceiling the mod's own Battery Box charge slot respects, so a
 * foreign charger cannot force-feed a pouch faster than the mod's balance allows.
 */
public final class StackAsEnergyStorage implements EnergyStorage {
	private final ContainerItemContext context;
	private final Item validItem;

	private StackAsEnergyStorage(ContainerItemContext context) {
		this.context = context;
		this.validItem = context.getItemVariant().getItem();
	}

	/**
	 * Register the capability for every powered item of the mod. Called once from the Fabric entrypoint at
	 * mod init.
	 *
	 * <p>{@code ITEM.registerForItems} is used here rather than the capsule's {@code combinedItemApiProvider}:
	 * that helper is a fluid-specific convenience with no energy equivalent — Team Reborn documents
	 * {@code EnergyStorage.ITEM} as the item path.
	 */
	public static void register() {
		EnergyStorage.ITEM.registerForItems((stack, context) -> {
			// The context is a view of a slot, not of a stack: the item there can change between
			// operations. Pin the item this storage was created for and let DelegatingEnergyStorage
			// re-check it before every call (Team Reborn's documented requirement for item storages).
			Item startingItem = context.getItemVariant().getItem();
			return new DelegatingEnergyStorage(new StackAsEnergyStorage(context),
					() -> context.getItemVariant().isOf(startingItem) && context.getAmount() > 0);
		}, ModItems.BATTERY_POUCH, ModItems.ENERGY_PACK, ModItems.ELECTRIC_DRILL);
	}

	@Override
	public boolean supportsInsertion() {
		return true;
	}

	@Override
	public long insert(long maxAmount, TransactionContext transaction) {
		StoragePreconditions.notNegative(maxAmount);
		// All three powered items are stacksTo(1). Refusing anything else keeps the "energy per item"
		// arithmetic (and any dupe it could hide) out of the picture entirely.
		if (context.getAmount() != 1) {
			return 0L;
		}
		ItemVariant currentVariant = context.getItemVariant();
		if (!currentVariant.isOf(validItem)) {
			return 0L;
		}
		ItemStack updated = currentVariant.toStack();
		long current = ItemEnergy.get(updated);
		long accepted = Math.min(Math.min(maxAmount, ItemEnergy.inputRate(updated)),
				ItemEnergy.capacity(updated) - current);
		if (accepted <= 0) {
			return 0L;
		}
		ItemEnergy.set(updated, current + accepted);
		try (Transaction nested = transaction.openNested()) {
			if (context.extract(currentVariant, 1, nested) == 1
					&& context.insert(ItemVariant.of(updated), 1, nested) == 1) {
				nested.commit();
				return accepted;
			}
		}
		return 0L;
	}

	@Override
	public boolean supportsExtraction() {
		return false;
	}

	@Override
	public long extract(long maxAmount, TransactionContext transaction) {
		return 0L;
	}

	@Override
	public long getAmount() {
		return ItemEnergy.get(context.getItemVariant().toStack());
	}

	@Override
	public long getCapacity() {
		return ItemEnergy.capacity(context.getItemVariant().toStack());
	}
}
