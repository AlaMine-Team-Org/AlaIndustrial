package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnStatus;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import dev.alaindustrial.registry.ModContent;

/**
 * Menu for the Distillation Column (MOD-251): three container pairs mirroring the tower's port
 * layout — oil (fill) on the left, diesel (drain) top-right, fuel oil (drain) bottom-right — and a
 * 12-channel data bridge (three tank permilles + three fluid ids + status + heat) sized from
 * {@link DistillationColumnBlockEntity#DATA_COUNT} on both sides (MOD-235).
 */
public class DistillationColumnMenu extends MachineMenu {
	/** Server side. */
	public DistillationColumnMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.DISTILLATION_COLUMN_MENU.get(), syncId, playerInventory, be, be.getDataAccess(),
				access, ModContent.DISTILLATION_COLUMN.get());
	}

	/** Client side. */
	public DistillationColumnMenu(int syncId, Inventory playerInventory) {
		super(ModContent.DISTILLATION_COLUMN_MENU.get(), syncId, playerInventory,
				clientStub(DistillationColumnBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT,
						DistillationColumnBlockEntity.DATA_COUNT),
				ModContent.DISTILLATION_COLUMN.get());
	}

	@Override
	protected void addMachineSlots() {
		// Left column: oil intake pair beside the oil gauge (full container in, emptied out below).
		addSlot(new Slot(machine, DistillationColumnBlockEntity.OIL_FILL_INPUT_SLOT, 30, 28));
		addSlot(new MachineFilledSlot(machine, DistillationColumnBlockEntity.OIL_FILL_OUTPUT_SLOT, 30, 62));
		// Right column, top: diesel drain pair (empty container in above, filled diesel out below),
		// stacked vertically between the diesel gauge and the energy bar.
		addSlot(new Slot(machine, DistillationColumnBlockEntity.DIESEL_DRAIN_INPUT_SLOT, 122, 24));
		addSlot(new MachineFilledSlot(machine, DistillationColumnBlockEntity.DIESEL_DRAIN_OUTPUT_SLOT, 122, 42));
		// Right column, bottom: fuel-oil drain pair.
		addSlot(new Slot(machine, DistillationColumnBlockEntity.FUEL_OIL_DRAIN_INPUT_SLOT, 122, 62));
		addSlot(new MachineFilledSlot(machine, DistillationColumnBlockEntity.FUEL_OIL_DRAIN_OUTPUT_SLOT, 122, 80));
	}

	/** 200×200 GUI (round 2b): the tower schematic and gauges got room to breathe. */
	@Override
	public int panelAnchorX() {
		return 200 - 4; // dock the upgrades gear/panel to THIS frame's right edge, not the 176px default
	}

	@Override
	protected int playerInventoryX() {
		return 20;
	}

	@Override
	protected int playerInventoryY() {
		return 118;
	}

	@Override
	protected int hotbarY() {
		return 176;
	}

	/** A machine-filled slot: the exchange puts the container here, the player only takes it. */
	private static final class MachineFilledSlot extends Slot {
		MachineFilledSlot(net.minecraft.world.Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}

	public int getOilPermille() {
		return channel(DistillationColumnBlockEntity.Channel.OIL_PERMILLE);
	}

	public int getDieselPermille() {
		return channel(DistillationColumnBlockEntity.Channel.DIESEL_PERMILLE);
	}

	public int getFuelOilPermille() {
		return channel(DistillationColumnBlockEntity.Channel.FUEL_OIL_PERMILLE);
	}

	public int getOilFluidId() {
		return channel(DistillationColumnBlockEntity.Channel.OIL_FLUID_ID);
	}

	public int getDieselFluidId() {
		return channel(DistillationColumnBlockEntity.Channel.DIESEL_FLUID_ID);
	}

	public int getFuelOilFluidId() {
		return channel(DistillationColumnBlockEntity.Channel.FUEL_OIL_FLUID_ID);
	}

	/** The synced idle diagnosis for the status line. */
	public DistillationColumnStatus getStatus() {
		return DistillationColumnStatus.byOrdinal(channel(DistillationColumnBlockEntity.Channel.STATUS));
	}

	/** Warm-up as permille of the configured warm-up window (0 cold .. 1000 hot). */
	public int getHeatPermille() {
		return channel(DistillationColumnBlockEntity.Channel.HEAT_PERMILLE);
	}

	/** Coke fouling 0..{@link DistillationColumnBlockEntity#FOULING_MAX} (round 2). */
	public int getFouling() {
		return channel(DistillationColumnBlockEntity.Channel.FOULING);
	}

	/** Whether a Rectification Section stands on the real tower (round 2). */
	public boolean hasSection() {
		return channel(DistillationColumnBlockEntity.Channel.SECTION) != 0;
	}
}
