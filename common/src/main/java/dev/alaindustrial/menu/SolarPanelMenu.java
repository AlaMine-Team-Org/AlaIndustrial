package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the solar panel — one evolution-chip slot; shows energy, production, sky mode, evolution. */
public class SolarPanelMenu extends MachineMenu {
	/** Server side. */
	public SolarPanelMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.SOLAR_PANEL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.SOLAR_PANEL.get());
	}

	/** Client side. */
	public SolarPanelMenu(int syncId, Inventory playerInventory) {
		super(ModContent.SOLAR_PANEL_MENU.get(), syncId, playerInventory, new SimpleContainer(1 + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(6), ContainerLevelAccess.NULL, ModContent.SOLAR_PANEL.get());
	}

	@Override
	protected void addMachineSlots() {
		// Evolution-chip slot; only chips may be inserted (delegated to the block entity).
		addSlot(new Slot(machine, 0, 149, 27) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(0, stack);
			}

			// MOD-211: one chip at a time, mirroring WindMillMenu. The block entity's emptiness guard stops
			// automation stacking, but a player can drop a whole held stack into an EMPTY slot in one click
			// — that path is limited here, and only here.
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

	/** Sky mode: 0 night, 1 day, 2 weather; carried on the maxProgress channel. */
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

	/**
	 * Visual regression test helper — injects all six ContainerData fields without a server-side
	 * block entity. Only call this from client-game-test code.
	 */
	public void injectSolarTestData(int energy, int capacity, int production, int mode,
			int evolveProgress, int evolveMax) {
		data.set(0, energy);
		data.set(1, capacity);
		data.set(2, production);
		data.set(3, mode);
		data.set(4, evolveProgress);
		data.set(5, evolveMax);
	}
}
