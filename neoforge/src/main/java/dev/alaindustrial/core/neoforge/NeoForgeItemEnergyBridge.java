package dev.alaindustrial.core.neoforge;

import com.google.common.primitives.Ints;
import dev.alaindustrial.item.ItemEnergyBridge;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * NeoForge implementation of the cross-mod item energy bridge (MOD-084): resolves a player inventory slot
 * through {@code Capabilities.Energy.ITEM} so the Energy Pack can charge other mods' powered items. The
 * neutral face of this is {@link ItemEnergyBridge}; the block-side counterpart is
 * {@link NeoForgeEnergyLookup}.
 *
 * <p>{@link ItemAccess#forPlayerSlot} indexes slots exactly like the vanilla {@code Inventory} the caller
 * walks, so the slot index passes straight through.
 */
public final class NeoForgeItemEnergyBridge implements ItemEnergyBridge {

	@Override
	public long chargeSlot(Player player, int slot, long maxEu) {
		if (maxEu <= 0) {
			return 0L;
		}
		ItemAccess access = ItemAccess.forPlayerSlot(player, slot);
		EnergyHandler target = access.getCapability(Capabilities.Energy.ITEM);
		if (target == null) {
			return 0L;
		}
		// EnergyHandler transfers in int; the pack's budget is a long. Saturating is safe — the budget is
		// a few hundred EU per step, orders of magnitude below the int ceiling.
		int budget = Ints.saturatedCast(maxEu);
		// The caller debits the pack only for what this commits, so the transfer needs its own transaction
		// rather than a simulate-then-repeat pass.
		try (Transaction transaction = Transaction.openRoot()) {
			int inserted = target.insert(budget, transaction);
			transaction.commit();
			return inserted;
		}
	}
}
