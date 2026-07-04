package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModMenus;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the LV compressor (input slot + result-only output slot). */
public class CompressorMenu extends MachineMenu {
	/** Server side. */
	public CompressorMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModMenus.COMPRESSOR, syncId, playerInventory, be, be.getDataAccess(), access, ModBlocks.COMPRESSOR);
	}

	/** Client side. */
	public CompressorMenu(int syncId, Inventory playerInventory) {
		super(ModMenus.COMPRESSOR, syncId, playerInventory, new SimpleContainer(2),
				new SimpleContainerData(4), ContainerLevelAccess.NULL, ModBlocks.COMPRESSOR);
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, 0, 56, 35));
		// Output slot: result only, no manual insertion (spec).
		addSlot(new Slot(machine, 1, 117, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
	}
}
