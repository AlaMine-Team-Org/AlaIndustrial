package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.QuarryControllerBlockEntity;
import dev.alaindustrial.block.entity.QuarryControllerBlockEntity.Channel;
import dev.alaindustrial.block.entity.quarry.QuarryWorkStatus;
import dev.alaindustrial.core.structure.QuarryFrameScan;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The quarry controller's window (MOD-951): the drill bit's slot, the 27-slot buffer the haul goes into, and the
 * channels the screen writes the work from — the cycle's state, the frame's first broken rule, the layer and how far
 * it is done. The panel is taller than a machine's (222 pixels) to hold the buffer above the player's inventory.
 */
public class QuarryControllerMenu extends MachineMenu {

	public static final int BIT_SLOT_X = 32;
	public static final int BIT_SLOT_Y = 41;
	public static final int BUFFER_X = 8;
	public static final int BUFFER_Y = 72;
	public static final int INVENTORY_Y = 140;
	public static final int HOTBAR_Y = 198;
	public static final int IMAGE_HEIGHT = 222;

	public QuarryControllerMenu(int syncId, Inventory playerInventory, QuarryControllerBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.QUARRY_CONTROLLER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.QUARRY_CONTROLLER.get());
	}

	public QuarryControllerMenu(int syncId, Inventory playerInventory) {
		super(ModContent.QUARRY_CONTROLLER_MENU.get(), syncId, playerInventory,
				clientStub(QuarryControllerBlockEntity.SLOT_COUNT, QuarryControllerBlockEntity.DATA_COUNT),
				ModContent.QUARRY_CONTROLLER.get());
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, QuarryControllerBlockEntity.BIT_SLOT, BIT_SLOT_X, BIT_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine instanceof QuarryControllerBlockEntity be
						? be.canPlaceItem(QuarryControllerBlockEntity.BIT_SLOT, stack)
						: stack.is(ModContent.QUARRY_DRILL_BIT.get());
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int slot = QuarryControllerBlockEntity.BUFFER_START + row * 9 + col;
				addSlot(new Slot(machine, slot, BUFFER_X + col * 18, BUFFER_Y + row * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return false; // the buffer is output only
					}
				});
			}
		}
	}

	@Override
	protected int playerInventoryY() {
		return INVENTORY_Y;
	}

	@Override
	protected int hotbarY() {
		return HOTBAR_Y;
	}

	public QuarryWorkStatus workStatus() {
		return QuarryWorkStatus.byOrdinal(channel(Channel.STATUS));
	}

	public QuarryFrameScan.Status frameStatus() {
		int ordinal = channel(Channel.FRAME_STATUS);
		QuarryFrameScan.Status[] all = QuarryFrameScan.Status.values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : QuarryFrameScan.Status.UNSCANNED;
	}

	public int layer() {
		return channel(Channel.LAYER);
	}

	public int layers() {
		return channel(Channel.LAYERS);
	}

	public int layerPermille() {
		return channel(Channel.LAYER_PERMILLE);
	}
}
