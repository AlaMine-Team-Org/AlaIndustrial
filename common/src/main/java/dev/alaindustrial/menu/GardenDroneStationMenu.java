package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStatus;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Menu for the Garden Drone Station (MOD-277): seeds, fertilizer, and four output slots.
 *
 * <p>Each slot filter is written twice on purpose — here for the client's prediction (so a stack
 * does not flicker into a slot that would bounce straight back) and in the block entity's
 * {@code canPlaceItem} for the server and for hoppers.
 */
public class GardenDroneStationMenu extends MachineMenu {

	/** Server side. */
	public GardenDroneStationMenu(int syncId, Inventory playerInventory,
			GardenDroneStationBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.GARDEN_DRONE_STATION_MENU.get(), syncId, playerInventory, be, be.getDataAccess(),
				access, ModContent.GARDEN_DRONE_STATION.get());
	}

	/** Client side — the data width must match the block entity's channel count exactly (MOD-235). */
	public GardenDroneStationMenu(int syncId, Inventory playerInventory) {
		super(ModContent.GARDEN_DRONE_STATION_MENU.get(), syncId, playerInventory,
				new SimpleContainer(GardenDroneStationBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(GardenDroneStationBlockEntity.DATA_COUNT),
				ContainerLevelAccess.NULL, ModContent.GARDEN_DRONE_STATION.get());
	}

	/**
	 * Consumables down the left, harvest in a 2×2 block on the right — the two halves the player
	 * interacts with differently (one is refilled, one is emptied), so they are not interleaved.
	 */
	@Override
	protected void addMachineSlots() {
		Container machineContainer = this.machine;
		addSlot(new Slot(machineContainer, GardenDroneStationBlockEntity.SEED_SLOT, 44, 24) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof BlockItem;
			}
		});
		addSlot(new Slot(machineContainer, GardenDroneStationBlockEntity.FERTILIZER_SLOT, 44, 48) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Items.BONE_MEAL);
			}
		});
		// The hoe the drone tills with — a tool, so it sits with the other things the player refits.
		addSlot(new Slot(machineContainer, GardenDroneStationBlockEntity.HOE_SLOT, 80, 48) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return getItem().isEmpty() && stack.is(ItemTags.HOES);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		// The drone's own bay, set apart from the consumables: it is installed once, not refilled.
		addSlot(new Slot(machineContainer, GardenDroneStationBlockEntity.DRONE_SLOT, 80, 24) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return getItem().isEmpty() && stack.is(ModContent.GARDEN_DRONE.get());
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		for (int i = 0; i < GardenDroneStationBlockEntity.OUTPUT_SLOT_COUNT; i++) {
			int x = 116 + (i % 2) * 18;
			int y = 24 + (i / 2) * 18;
			addSlot(new Slot(machineContainer, GardenDroneStationBlockEntity.OUTPUT_SLOT_START + i, x, y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false; // the station fills these; nothing goes in by hand or by hopper
				}
			});
		}
	}

	// The atlas is 14 pixels taller than the machine standard (the status band), so the player's
	// inventory sits that much lower — same offsets the incubator uses.
	@Override
	protected int playerInventoryY() {
		return 98;
	}

	@Override
	protected int hotbarY() {
		return 156;
	}

	/** Why the station is idle, as diagnosed server-side — the screen's status line. */
	public GardenDroneStatus getStatus() {
		return GardenDroneStatus.byOrdinal(data.get(GardenDroneStationBlockEntity.DATA_STATUS));
	}
}
