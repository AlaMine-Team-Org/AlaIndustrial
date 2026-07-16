package dev.alaindustrial.core.fabric;

import dev.alaindustrial.item.ItemEnergyBridge;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.entity.player.Player;
import team.reborn.energy.api.EnergyStorage;

/**
 * Fabric implementation of the cross-mod item energy bridge (MOD-084): resolves a player inventory slot
 * through Team Reborn's {@code EnergyStorage.ITEM} lookup so the Energy Pack can charge other mods'
 * powered items. The neutral face of this is {@link ItemEnergyBridge}; the block-side counterpart is
 * {@link FabricEnergyLookup}.
 *
 * <p>{@link PlayerInventoryStorage} indexes slots exactly like the vanilla {@code Inventory} the caller
 * walks, so the slot index passes straight through.
 */
public final class FabricItemEnergyBridge implements ItemEnergyBridge {

	@Override
	public long chargeSlot(Player player, int slot, long maxEu) {
		if (maxEu <= 0) {
			return 0L;
		}
		ContainerItemContext context =
				ContainerItemContext.ofPlayerSlot(player, PlayerInventoryStorage.of(player).getSlot(slot));
		EnergyStorage target = context.find(EnergyStorage.ITEM);
		if (target == null || !target.supportsInsertion()) {
			return 0L;
		}
		// The caller debits the pack only for what this commits, so the transfer needs its own transaction
		// rather than a simulate-then-repeat pass.
		try (Transaction transaction = Transaction.openOuter()) {
			long inserted = target.insert(maxEu, transaction);
			transaction.commit();
			return inserted;
		}
	}
}
