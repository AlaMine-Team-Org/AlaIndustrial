package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;

/** Menu for the pump: fluid-bucket input + empty-bucket output. Mirrors the geothermal generator menu,
 * but the client stub uses a 7-wide {@code ContainerData} to match the pump's tank-level sync channels. */
public class PumpMenu extends MachineMenu {
	/** Server side. */
	public PumpMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.PUMP_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.PUMP.get());
	}

	/** Client side. */
	public PumpMenu(int syncId, Inventory playerInventory) {
		// SimpleContainerData width must match PumpBlockEntity.getDataAccess() (7: 4 base + tank permille/denominator/fluid-id).
		super(ModContent.PUMP_MENU.get(), syncId, playerInventory, new SimpleContainer(4 + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(7), ContainerLevelAccess.NULL, ModContent.PUMP.get());
	}

	@Override
	protected void addMachineSlots() {
		// 2×2 grid matching the pump.png texture (frame 176×166), aligned to the slot-recess borders:
		//   top row (y=23)   = fill the tank:  full-bucket in → empty-bucket out
		//   bottom row (y=47) = drain the tank: empty-bucket in → full-bucket out
		addSlot(new Slot(machine, 0, 60, 23));   // FILL_INPUT  (top-left)
		addSlot(new Slot(machine, 1, 98, 23));   // FILL_OUTPUT (top-right)
		addSlot(new Slot(machine, 2, 98, 47));   // DRAIN_INPUT (bottom-right)
		addSlot(new Slot(machine, 3, 60, 47));   // DRAIN_OUTPUT (bottom-left)
	}

	/** Tank fill permille (0..1000) — sync channel 4. */
	public int getFluidPermille() {
		return data.get(4);
	}

	/** Permille denominator (1000) — sync channel 5. */
	public int getFluidPermilleMax() {
		return data.get(5);
	}

	/** Fluid-type id (0 empty, 1 lava, 2 water) — sync channel 6. */
	public int getFluidId() {
		return data.get(6);
	}
}
