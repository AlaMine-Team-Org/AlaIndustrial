package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.SolarPanelBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
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
		super(ModContent.SOLAR_PANEL_MENU.get(), syncId, playerInventory,
				clientStub(SolarPanelBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT, SolarPanelBlockEntity.DATA_COUNT),
				ModContent.SOLAR_PANEL.get());
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
		return channel(SolarPanelBlockEntity.Channel.EVOLVE_PERMILLE);
	}

	/** Evolution denominator (constant 1000) — permille scale, kept short-safe for DataSlot sync. */
	public int getEvolveMax() {
		return channel(SolarPanelBlockEntity.Channel.EVOLVE_MAX);
	}

	/**
	 * Visual regression test helper — injects all six ContainerData fields without a server-side
	 * block entity. Only call this from client-game-test code.
	 */
	public void injectSolarTestData(int energy, int capacity, int production, int mode,
			int evolveProgress, int evolveMax) {
		data.set(SolarPanelBlockEntity.Channel.ENERGY.ordinal(), energy);
		data.set(SolarPanelBlockEntity.Channel.CAPACITY.ordinal(), capacity);
		data.set(SolarPanelBlockEntity.Channel.PROGRESS.ordinal(), production);
		data.set(SolarPanelBlockEntity.Channel.MAX_PROGRESS.ordinal(), mode);
		data.set(SolarPanelBlockEntity.Channel.EVOLVE_PERMILLE.ordinal(), evolveProgress);
		data.set(SolarPanelBlockEntity.Channel.EVOLVE_MAX.ordinal(), evolveMax);
	}
}
