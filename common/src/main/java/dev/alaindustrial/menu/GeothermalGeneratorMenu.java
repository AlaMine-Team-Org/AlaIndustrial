package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;

/** Menu for the geothermal generator: lava-bucket input + empty-bucket output. */
public class GeothermalGeneratorMenu extends MachineMenu {
	/** Server side. */
	public GeothermalGeneratorMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.GEOTHERMAL_GENERATOR_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.GEOTHERMAL_GENERATOR.get());
	}

	/** Client side. */
	public GeothermalGeneratorMenu(int syncId, Inventory playerInventory) {
		super(ModContent.GEOTHERMAL_GENERATOR_MENU.get(), syncId, playerInventory, new SimpleContainer(2 + UPGRADE_SLOT_COUNT),
				new net.minecraft.world.inventory.SimpleContainerData(4), ContainerLevelAccess.NULL,
				ModContent.GEOTHERMAL_GENERATOR.get());
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, 0, 60, 34));   // lava bucket in
		addSlot(new Slot(machine, 1, 98, 34));   // empty bucket out
	}
}
