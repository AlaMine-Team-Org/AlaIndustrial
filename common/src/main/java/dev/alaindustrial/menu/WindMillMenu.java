package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.WindMillBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the wind mill — two slots: a rotor slot (center, required for generation) and an evolution-chip
 * slot (right). Shows energy, production, mode and evolution progress (mirrors SolarPanelMenu).
 */
public class WindMillMenu extends MachineMenu {
	/** Server side. */
	public WindMillMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.WIND_MILL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.WIND_MILL.get());
	}

	/** Client side. */
	public WindMillMenu(int syncId, Inventory playerInventory) {
		super(ModContent.WIND_MILL_MENU.get(), syncId, playerInventory, new SimpleContainer(2),
				new SimpleContainerData(6), ContainerLevelAccess.NULL, ModContent.WIND_MILL.get());
	}

	@Override
	protected void addMachineSlots() {
		// Rotor slot (center) — only a windmill_rotor may be inserted, one at a time. The rotor is a
		// single installed component (spec: stack size 1); it may still stack to 64 in the player's
		// inventory for transport, but only one mounts into the mill.
		addSlot(new Slot(machine, WindMillBlockEntity.ROTOR_SLOT, 84, 23) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(WindMillBlockEntity.ROTOR_SLOT, stack);
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
		// Evolution-chip slot (right) — only altitude/storm chips may be inserted, one at a time.
		addSlot(new Slot(machine, WindMillBlockEntity.CHIP_SLOT, 149, 39) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(WindMillBlockEntity.CHIP_SLOT, stack);
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
	}

	@Override
	protected int playerInventoryY() {
		return 96;
	}

	@Override
	protected int hotbarY() {
		return 154;
	}

	/** Current production rate (EU/t), carried on the progress channel. */
	public int getProductionRate() {
		return getProgress();
	}

	/** Wind mode: 0 no rotor, 1 roofed, 2 calm, 3 breeze, 4 gale, 5 storm; carried on the maxProgress channel. */
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
	public void injectWindMillTestData(int energy, int capacity, int production, int mode,
			int evolveProgress, int evolveMax) {
		data.set(0, energy);
		data.set(1, capacity);
		data.set(2, production);
		data.set(3, mode);
		data.set(4, evolveProgress);
		data.set(5, evolveMax);
	}
}
