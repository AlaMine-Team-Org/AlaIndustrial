package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.block.entity.GalvanicBathStatus;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Five-slot Galvanic Bath menu (MOD-127): fibre, silver dust, a bucket pair for the water tank, and
 * the flux-thread result.
 *
 * <p>{@code mayPlace} here is the client-side half of the slot rules; the server-side half is
 * {@code GalvanicBathBlockEntity.canPlaceItem}. Both are needed — this one refuses a wrong item under
 * the player's cursor, that one is what automation and the fluid-container exchange actually consult.
 */
public final class GalvanicBathMenu extends MachineMenu {
	public GalvanicBathMenu(int syncId, Inventory playerInventory, GalvanicBathBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.GALVANIC_BATH_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.GALVANIC_BATH.get());
	}

	public GalvanicBathMenu(int syncId, Inventory playerInventory) {
		super(ModContent.GALVANIC_BATH_MENU.get(), syncId, playerInventory,
				new SimpleContainer(GalvanicBathBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(GalvanicBathBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.GALVANIC_BATH.get());
	}

	@Override
	protected void addMachineSlots() {
		Container container = machine;
		// Layout follows the GUI art: buckets on the top row (in, then out), materials on the bottom
		// (fibre, then silver). The background hints in each empty slot say which is which, so the
		// coordinates here have to match that art or the picture would lie to the player.
		addSlot(new Slot(container, GalvanicBathBlockEntity.FILL_INPUT_SLOT, 40, 23) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return ItemFluidBridge.get().isFluidContainer(stack);
			}
		});
		addSlot(new Slot(container, GalvanicBathBlockEntity.FILL_OUTPUT_SLOT, 62, 23) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				// Machine-filled: the emptied bucket lands here through the exchange, not by hand.
				return false;
			}
		});
		addSlot(new Slot(container, GalvanicBathBlockEntity.FIBER_SLOT, 40, 47) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModTags.Items.FIBER);
			}
		});
		addSlot(new Slot(container, GalvanicBathBlockEntity.SILVER_SLOT, 62, 47) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModContent.SILVER_DUST.get());
			}
		});
		addSlot(new Slot(container, GalvanicBathBlockEntity.OUTPUT_SLOT, 117, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
	}


	/** Tank fill as a permille (0..1000) — the level travels scaled because the channel is a short. */
	public int getTankPermille() {
		return data.get(4);
	}

	/** Registry id of the fluid in the tank, or {@link GalvanicBathBlockEntity#FLUID_ID_NONE}. */
	public int getTankFluidId() {
		return data.get(5);
	}

	/** Why the machine is idle, for the screen's status line. */
	public GalvanicBathStatus getStatus() {
		return GalvanicBathStatus.byOrdinal(data.get(6));
	}
}
