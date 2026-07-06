package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModMenus;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Menu for the LV generator (one fuel slot). */
public class GeneratorMenu extends MachineMenu {
	/** Server side. */
	public GeneratorMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModMenus.GENERATOR, syncId, playerInventory, be, be.getDataAccess(), access, ModBlocks.GENERATOR);
	}

	/** Client side. */
	public GeneratorMenu(int syncId, Inventory playerInventory) {
		super(ModMenus.GENERATOR, syncId, playerInventory, new SimpleContainer(1),
				new net.minecraft.world.inventory.SimpleContainerData(4), ContainerLevelAccess.NULL, ModBlocks.GENERATOR);
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, 0, 79, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				// Explicit client-side check so the client rejects immediately without
				// optimistic placement → server correction → visible flicker.
				if (stack.is(Items.LAVA_BUCKET)) {
					return false;
				}
				return machine.canPlaceItem(0, stack);
			}
		});
	}
}
