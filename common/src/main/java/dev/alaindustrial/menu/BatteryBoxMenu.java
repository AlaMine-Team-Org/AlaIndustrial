package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.item.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the BatteryBox — pouch charge slot (MOD-052) + stored energy / charge level readout. */
public class BatteryBoxMenu extends MachineMenu {
	/** Charge slot position in the GUI (niche drawn by BatteryBoxScreen left of the battery). */
	public static final int CHARGE_SLOT_X = 21;
	public static final int CHARGE_SLOT_Y = 32;

	/** Server side. */
	public BatteryBoxMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.BATTERY_BOX_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.BATTERY_BOX.get());
	}

	/** Client side. */
	public BatteryBoxMenu(int syncId, Inventory playerInventory) {
		super(ModContent.BATTERY_BOX_MENU.get(), syncId, playerInventory, new SimpleContainer(1 + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(5), ContainerLevelAccess.NULL, ModContent.BATTERY_BOX.get());
	}

	@Override
	protected void addMachineSlots() {
		// Powered-item filter in BOTH mayPlace (client prediction) and the BE's canPlaceItem (server /
		// hoppers) — only the server check causes a visible item flicker (generator lava-slot lesson).
		addSlot(new Slot(machine, BatteryBoxBlockEntity.CHARGE_SLOT, CHARGE_SLOT_X, CHARGE_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return ItemEnergy.capacity(stack) > 0;
			}
		});
	}

	/** Charge level as a percentage of capacity. */
	public int getChargePercent() {
		int cap = getCapacity();
		return cap > 0 ? getEnergy() * 100 / cap : 0;
	}

	/** Per-tick output cap (EU/t) this BatteryBox can emit from its output face. */
	public int getOutputRate() {
		return data.get(4);
	}
}
