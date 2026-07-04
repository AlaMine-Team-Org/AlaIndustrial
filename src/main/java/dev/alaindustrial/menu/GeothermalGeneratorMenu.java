package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModMenus;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;

/** Menu for the geothermal generator: lava-bucket input + empty-bucket output. */
public class GeothermalGeneratorMenu extends MachineMenu {
	/** Server side. */
	public GeothermalGeneratorMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModMenus.GEOTHERMAL_GENERATOR, syncId, playerInventory, be, be.getDataAccess(), access,
				ModBlocks.GEOTHERMAL_GENERATOR);
	}

	/** Client side. */
	public GeothermalGeneratorMenu(int syncId, Inventory playerInventory) {
		super(ModMenus.GEOTHERMAL_GENERATOR, syncId, playerInventory, new SimpleContainer(2),
				new net.minecraft.world.inventory.SimpleContainerData(4), ContainerLevelAccess.NULL,
				ModBlocks.GEOTHERMAL_GENERATOR);
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, 0, 60, 34));   // lava bucket in
		addSlot(new Slot(machine, 1, 98, 34));   // empty bucket out
	}
}
