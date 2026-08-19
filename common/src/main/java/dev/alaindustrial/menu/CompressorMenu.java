package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.AbstractProcessingMachineBlockEntity;
import dev.alaindustrial.block.entity.ProcessingMachineStatus;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/** Menu for the LV compressor (input slot + result-only output slot). */
public class CompressorMenu extends MachineMenu {
	/** Server side. */
	public CompressorMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.COMPRESSOR_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.COMPRESSOR.get());
	}

	/** Client side. */
	public CompressorMenu(int syncId, Inventory playerInventory) {
		super(ModContent.COMPRESSOR_MENU.get(), syncId, playerInventory,
				new SimpleContainer(AbstractProcessingMachineBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(AbstractProcessingMachineBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL, ModContent.COMPRESSOR.get());
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, 0, 56, 35));
		// Output slot: result only, no manual insertion (spec).
		addSlot(new OutputSlot(machine, 1, 117, 35));
	}
	/** Why the machine is idle (MOD-458), read from synced data — works on both sides. */
	public ProcessingMachineStatus getStatus() {
		return ProcessingMachineStatus.byOrdinal(data.get(AbstractProcessingMachineBlockEntity.DATA_STATUS));
	}
}
