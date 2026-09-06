package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.RecyclerBlockEntity;
import dev.alaindustrial.block.entity.RecyclerStatus;
import dev.alaindustrial.core.waste.WasteFraction;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Recycler (MOD-145): waste in, blades below it, briquettes and ash out on the right.
 *
 * <p>The three fraction readouts are the machine's teaching surface — the player is supposed to notice
 * that a lopsided batch pays ballast — so they are read here and drawn with words by the screen, never
 * baked into the texture.
 */
public class RecyclerMenu extends MachineMenu {
	// Geometry mirrored from tools/gen_recycler_assets.py — the artwork is the source of truth.
	public static final int INPUT_X = 44;
	public static final int INPUT_Y = 24;
	public static final int BLADE_X = 44;
	public static final int BLADE_Y = 52;
	public static final int SLAG_X = 134;
	public static final int SLAG_Y = 24;
	public static final int ASH_X = 134;
	public static final int ASH_Y = 52;
	/** The frame is taller than the standard 166 to keep the gauges clear of the slots and the title. */
	public static final int PLAYER_INV_Y = 140;
	public static final int HOTBAR_Y = 198;

	/** Server side. */
	public RecyclerMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.RECYCLER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.RECYCLER.get());
	}

	/** Client side. */
	public RecyclerMenu(int syncId, Inventory playerInventory) {
		super(ModContent.RECYCLER_MENU.get(), syncId, playerInventory,
				new SimpleContainer(RecyclerBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(RecyclerBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.RECYCLER.get());
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, RecyclerBlockEntity.INPUT_SLOT, INPUT_X, INPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(RecyclerBlockEntity.INPUT_SLOT, stack);
			}
		});
		// Blades: one at a time, wear shows as the vanilla durability bar right in the slot.
		addSlot(new Slot(machine, RecyclerBlockEntity.BLADE_SLOT, BLADE_X, BLADE_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(RecyclerBlockEntity.BLADE_SLOT, stack);
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
		addSlot(new OutputSlot(machine, RecyclerBlockEntity.SLAG_SLOT, SLAG_X, SLAG_Y));
		addSlot(new OutputSlot(machine, RecyclerBlockEntity.ASH_SLOT, ASH_X, ASH_Y));
	}

	@Override
	protected int playerInventoryY() {
		return PLAYER_INV_Y;
	}

	@Override
	protected int hotbarY() {
		return HOTBAR_Y;
	}

	/** Mass collected by the batch so far, in slag units. */
	public int batchMass() {
		return data.get(RecyclerBlockEntity.DATA_BATCH_MASS);
	}

	/** Mass held by one graded fraction. */
	public int fractionMass(WasteFraction fraction) {
		return switch (fraction) {
			case MINERAL -> data.get(RecyclerBlockEntity.DATA_MINERAL);
			case METAL -> data.get(RecyclerBlockEntity.DATA_METAL);
			case COMBUSTIBLE -> data.get(RecyclerBlockEntity.DATA_COMBUSTIBLE);
			case OTHER -> 0;
		};
	}

	/** Ash sitting in the bin. */
	public int ashCount() {
		return data.get(RecyclerBlockEntity.DATA_ASH);
	}

	/** Why the machine is idle — works on both sides, read from synced data. */
	public RecyclerStatus getStatus() {
		return RecyclerStatus.byOrdinal(data.get(RecyclerBlockEntity.DATA_STATUS));
	}
}
