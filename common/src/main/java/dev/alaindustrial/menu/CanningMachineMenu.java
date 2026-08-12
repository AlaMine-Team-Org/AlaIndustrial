package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.CanningMachineBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.food.CanningRules;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Canning Machine window: food in, empty cans in, rations out (MOD-383). */
public final class CanningMachineMenu extends MachineMenu {
	public CanningMachineMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.CANNING_MACHINE_MENU.get(), syncId, playerInventory, be, be.getDataAccess(),
				access, ModContent.CANNING_MACHINE.get());
	}

	public CanningMachineMenu(int syncId, Inventory playerInventory) {
		super(ModContent.CANNING_MACHINE_MENU.get(), syncId, playerInventory,
				new SimpleContainer(CanningMachineBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(CanningMachineBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.CANNING_MACHINE.get());
	}

	@Override
	protected void addMachineSlots() {
		Container container = machine;
		// The same predicate the block entity enforces for hoppers and pipes. Both layers are needed:
		// this one keeps the player from placing by hand, the block entity's canPlaceItem keeps
		// automation out, and neither covers the other.
		addSlot(new Slot(container, CanningMachineBlockEntity.FOOD_SLOT, 46, 19) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return CanningRules.isCannable(stack);
			}
		});
		addSlot(new Slot(container, CanningMachineBlockEntity.CAN_SLOT, 46, 42) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModContent.EMPTY_CAN.get());
			}
		});
		addSlot(new Slot(container, CanningMachineBlockEntity.OUTPUT_SLOT, 120, 28) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
	}

	/** Banked food value in tenths. */
	public int getFoodBuffer() {
		return data.get(4);
	}

	/** Food value one ration costs, in tenths — synced so the readout cannot drift from the server. */
	public int getValuePerRation() {
		return data.get(5);
	}
}
