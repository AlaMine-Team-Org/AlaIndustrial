package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.CesuBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Reinforced Energy Storage — charge slot, discharge slot and the stored-energy readout.
 * Two slots instead of the Battery Box's one; they sit either side of the battery gauge so the
 * direction of travel is obvious: charge on the left, discharge on the right.
 */
public class CesuMenu extends MachineMenu {
	/** Charge slot — left of the battery, same position as the Battery Box's slot. */
	public static final int CHARGE_SLOT_X = 21;
	public static final int CHARGE_SLOT_Y = 32;
	/** Discharge slot — right of the battery gauge (which ends at x=124), inside the 176px face. */
	public static final int DISCHARGE_SLOT_X = 139;
	public static final int DISCHARGE_SLOT_Y = 32;

	/** Server side. */
	public CesuMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.CESU_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.CESU.get());
	}

	/** Client side. */
	public CesuMenu(int syncId, Inventory playerInventory) {
		super(ModContent.CESU_MENU.get(), syncId, playerInventory, new SimpleContainer(2 + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(CesuBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL, ModContent.CESU.get());
	}

	@Override
	protected void addMachineSlots() {
		// Powered-item filter in BOTH mayPlace (client prediction) and the BE's canPlaceItem (server /
		// hoppers) — only the server check causes a visible item flicker (generator lava-slot lesson).
		addSlot(new Slot(machine, CesuBlockEntity.CHARGE_SLOT, CHARGE_SLOT_X, CHARGE_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return ItemEnergy.capacity(stack) > 0;
			}
		});
		addSlot(new Slot(machine, CesuBlockEntity.DISCHARGE_SLOT, DISCHARGE_SLOT_X, DISCHARGE_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return ItemEnergy.capacity(stack) > 0;
			}
		});
	}

	/**
	 * Stored EU. Overrides the base accessor because this buffer does not fit in a {@code DataSlot}'s
	 * 16-bit short: channel 0 carries the raw value and would arrive negative, so the number is read
	 * from the scaled channel instead (see {@link CesuBlockEntity#SYNC_SCALE}).
	 */
	@Override
	public int getEnergy() {
		return data.get(CesuBlockEntity.DATA_ENERGY_SCALED) * CesuBlockEntity.SYNC_SCALE;
	}

	/** Capacity — scaled for the same reason as {@link #getEnergy()}. */
	@Override
	public int getCapacity() {
		return data.get(CesuBlockEntity.DATA_CAPACITY_SCALED) * CesuBlockEntity.SYNC_SCALE;
	}

	/**
	 * Charge level as a percentage of capacity. Computed from the scaled channels directly rather than
	 * from {@link #getEnergy()}/{@link #getCapacity()} so the two ×100 factors cancel instead of
	 * risking an overflow in the multiplication.
	 */
	public int getChargePercent() {
		int cap = data.get(CesuBlockEntity.DATA_CAPACITY_SCALED);
		return cap > 0 ? data.get(CesuBlockEntity.DATA_ENERGY_SCALED) * 100 / cap : 0;
	}

	/** Per-tick output cap (EU/t) this store can emit from its output face. */
	public int getOutputRate() {
		return data.get(4);
	}
}
