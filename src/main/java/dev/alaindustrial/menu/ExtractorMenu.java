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

/** Menu for the LV extractor (input slot + result-only output slot). */
public class ExtractorMenu extends MachineMenu {
	/** Server side. */
	public ExtractorMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModMenus.EXTRACTOR, syncId, playerInventory, be, be.getDataAccess(), access, ModBlocks.EXTRACTOR);
	}

	/** Client side. */
	public ExtractorMenu(int syncId, Inventory playerInventory) {
		super(ModMenus.EXTRACTOR, syncId, playerInventory, new SimpleContainer(2),
				new SimpleContainerData(4), ContainerLevelAccess.NULL, ModBlocks.EXTRACTOR);
	}

	@Override
	protected void addMachineSlots() {
		// Slot positions match the extractor.png atlas: input left (56,35), output right (117,35),
		// flanking the central progress arrow (input → arrow → output, all on one row).
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
