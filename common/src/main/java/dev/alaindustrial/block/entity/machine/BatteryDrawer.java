package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * The battery drawer (MOD-679): one slot at the very end of a battery-fed machine's inventory whose item
 * the machine drains into its own buffer.
 *
 * <p>One component of the machine base (MOD-712, BE-1). The slot itself stays in the machine's inventory —
 * part of its save, at the index the slot layout gives it — so the drawer reads it through the list it was
 * handed; whether the machine has a drawer at all is the layout's answer.
 */
public final class BatteryDrawer {

	private final SlotLayout layout;
	private final List<ItemStack> items;
	private final EnergyBuffer energy;
	private final EnergyTier tier;

	/**
	 * @param layout where the drawer slot sits, if there is one
	 * @param items the machine's whole inventory
	 * @param energy the machine's own buffer, the one the drawer fills
	 * @param tier the machine's tier, whose voltage bounds one tick's transfer
	 */
	public BatteryDrawer(SlotLayout layout, List<ItemStack> items, EnergyBuffer energy, EnergyTier tier) {
		this.layout = layout;
		this.items = items;
		this.energy = energy;
		this.tier = tier;
	}

	/**
	 * Drain the drawer's item into the buffer, at most one tier-voltage packet per tick — the most a cable
	 * of this machine's tier could deliver in the same tick, so a battery is a portable wire, not a faster
	 * one. Bounded by the room left too, so a full machine leaves the battery alone.
	 *
	 * @return whether any energy moved — the machine then marks itself changed
	 */
	public boolean drain() {
		if (!layout.batteryDrawer()) {
			return false;
		}
		ItemStack source = items.get(layout.batterySlot());
		if (source.isEmpty()) {
			return false;
		}
		long room = energy.getCapacity() - energy.getAmount();
		if (room <= 0) {
			return false;
		}
		long moved = ItemEnergy.discharge(source, Math.min(room, tier.maxVoltage()));
		if (moved <= 0) {
			return false;
		}
		energy.receiveInternal(moved);
		return true;
	}
}
