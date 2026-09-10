package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.DaylightSolarPanelBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the Daylight Solar Panel — one resonance-chip slot; energy, production, sky mode, evolution. */
public class DaylightSolarPanelMenu extends MachineMenu {
	/** Server side. */
	public DaylightSolarPanelMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.DAYLIGHT_SOLAR_PANEL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.DAYLIGHT_SOLAR_PANEL.get());
	}

	/** Client side. */
	public DaylightSolarPanelMenu(int syncId, Inventory playerInventory) {
		super(ModContent.DAYLIGHT_SOLAR_PANEL_MENU.get(), syncId, playerInventory,
				new SimpleContainer(DaylightSolarPanelBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(DaylightSolarPanelBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.DAYLIGHT_SOLAR_PANEL.get());
	}

	@Override
	protected void addMachineSlots() {
		// Resonance-chip slot (MOD-602); what may go in is the block entity's call.
		addSlot(new Slot(machine, DaylightSolarPanelBlockEntity.CHIP_SLOT, 149, 27) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(DaylightSolarPanelBlockEntity.CHIP_SLOT, stack);
			}

			// MOD-211: one chip at a time. The block entity's emptiness guard stops automation
			// stacking; a player dropping a whole held stack into an EMPTY slot is limited here.
			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
	}

	/** Current production rate (EU/t), carried on the progress channel. */
	public int getProductionRate() {
		return getProgress();
	}

	/** Sky mode: 0 inactive/night, 1 day clear, 2 day weather; carried on the maxProgress channel. */
	public int getMode() {
		return getMaxProgress();
	}

	/** Evolution progress on a permille scale (0..1000); ≥1 as soon as any progress accrues. */
	public int getEvolveProgress() {
		return data.get(4);
	}

	/** Evolution denominator (constant 1000) — permille scale, kept short-safe for DataSlot sync. */
	public int getEvolveMax() {
		return data.get(5);
	}
}
