package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.StormWindMillBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the storm wind mill (T2, LV): one rotor slot, no evolution chip slot. */
public class StormWindMillMenu extends MachineMenu implements WindMillReadout {
	/** Server side. */
	public StormWindMillMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.STORM_WIND_MILL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.STORM_WIND_MILL.get());
	}

	/** Client side. */
	public StormWindMillMenu(int syncId, Inventory playerInventory) {
		super(ModContent.STORM_WIND_MILL_MENU.get(), syncId, playerInventory,
				new SimpleContainer(StormWindMillBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(StormWindMillBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.STORM_WIND_MILL.get());
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, StormWindMillBlockEntity.ROTOR_SLOT, 84, 23) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(StormWindMillBlockEntity.ROTOR_SLOT, stack);
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
	}

	@Override
	protected int playerInventoryY() {
		return 96;
	}

	@Override
	protected int hotbarY() {
		return 154;
	}

	/**
	 * Effective production rate (EU/t) — the rate the buffer gains while it has room, i.e. after
	 * {@link dev.alaindustrial.Config#globalEuRateMultiplier}. It rides its own channel rather than the
	 * progress channel, which stays the mechanical rate that drives the rotor's spin speed (MOD-356).
	 */
	@Override
	public int getProductionRate() {
		return data.get(StormWindMillBlockEntity.RATE_CHANNEL);
	}

	/** Wind mode: 0 no rotor, 1 roofed, 2 calm, 3 breeze, 4 gale, 5 storm. */
	@Override
	public int getMode() {
		return getMaxProgress();
	}
}
