package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
import dev.alaindustrial.block.entity.ThermalCentrifugeStatus;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;

/** Two-slot Thermal Centrifuge menu: dust in, shavings out, with a rotor-speed gauge. */
public final class ThermalCentrifugeMenu extends MachineMenu {
	public ThermalCentrifugeMenu(int syncId, Inventory inventory, ThermalCentrifugeBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.THERMAL_CENTRIFUGE_MENU.get(), syncId, inventory, be, be.getDataAccess(), access,
				ModContent.THERMAL_CENTRIFUGE.get());
	}

	public ThermalCentrifugeMenu(int syncId, Inventory inventory) {
		super(ModContent.THERMAL_CENTRIFUGE_MENU.get(), syncId, inventory,
				clientStub(ThermalCentrifugeBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT,
						ThermalCentrifugeBlockEntity.DATA_COUNT),
				ModContent.THERMAL_CENTRIFUGE.get());
	}

	@Override
	protected void addMachineSlots() {
		Container container = machine;
		addSlot(new Slot(container, ThermalCentrifugeBlockEntity.INPUT_SLOT, 56, 35));
		addSlot(new OutputSlot(container, ThermalCentrifugeBlockEntity.OUTPUT_SLOT, 117, 35));
	}

	/** Rotor speed as permille of a full spin-up, for the gauge. */
	public int getSpinPermille() {
		return channel(ThermalCentrifugeBlockEntity.Channel.SPIN);
	}

	public ThermalCentrifugeStatus getStatus() {
		return ThermalCentrifugeStatus.byOrdinal(channel(ThermalCentrifugeBlockEntity.Channel.STATUS));
	}
}
