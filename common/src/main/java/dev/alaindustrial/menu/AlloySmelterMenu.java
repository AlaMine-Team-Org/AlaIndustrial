package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.AlloySmelterBlockEntity;
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
 * Four-slot Alloy Smelter menu: three interchangeable component slots in a column, one result slot.
 *
 * <p>The three input slots are deliberately identical — no slot is "the copper slot". Each defers to
 * {@link AlloySmelterBlockEntity#canPlaceItem}, which is where both the "is this a component at all"
 * test and the anti-deadlock duplicate rule live, so a manual drag and a hopper push are judged by
 * exactly the same rule.
 */
public final class AlloySmelterMenu extends MachineMenu {

	/** The three interchangeable input slots, in GUI order. */
	private static final int[] INPUT_SLOTS = {
			AlloySmelterBlockEntity.INPUT_SLOT_0,
			AlloySmelterBlockEntity.INPUT_SLOT_1,
			AlloySmelterBlockEntity.INPUT_SLOT_2};

	/** Server side. */
	public AlloySmelterMenu(int syncId, Inventory playerInventory, AlloySmelterBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.ALLOY_SMELTER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.ALLOY_SMELTER.get());
	}

	/** Client side. */
	public AlloySmelterMenu(int syncId, Inventory playerInventory) {
		super(ModContent.ALLOY_SMELTER_MENU.get(), syncId, playerInventory,
				new SimpleContainer(AlloySmelterBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(dev.alaindustrial.block.entity.MachineBlockEntity.DATA_COUNT),
				ContainerLevelAccess.NULL, ModContent.ALLOY_SMELTER.get());
	}

	@Override
	protected void addMachineSlots() {
		Container container = machine;
		// Slot art positions must match the GUI atlas exactly — see textures/gui/container/alloy_smelter.png.
		addSlot(new ComponentSlot(container, AlloySmelterBlockEntity.INPUT_SLOT_0, 44, 17));
		addSlot(new ComponentSlot(container, AlloySmelterBlockEntity.INPUT_SLOT_1, 44, 35));
		addSlot(new ComponentSlot(container, AlloySmelterBlockEntity.INPUT_SLOT_2, 44, 53));
		addSlot(new OutputSlot(container, AlloySmelterBlockEntity.OUTPUT_SLOT, 120, 35));
	}

	/**
	 * An input slot carrying the component rules on BOTH sides.
	 *
	 * <p>Delegating straight to {@code container.canPlaceItem} is not enough: client-side the container
	 * is the {@link SimpleContainer} built by the client constructor above, and its {@code canPlaceItem}
	 * always says yes. The client would then predict a placement the server rejects, and the stack would
	 * visibly drop into the slot and jump back — the flicker every other menu in this mod avoids by
	 * duplicating its filter here (see {@code BatteryBoxMenu}, {@code GalvanicBathMenu}).
	 *
	 * <p>The duplicate rule is reproduced exactly, because the client knows the neighbouring slots. The
	 * "is this a component of some recipe" half cannot be: recipes live on the server. It is approximated
	 * client-side by the conventional ingot tag — every alloy component is an ingot — so at worst a
	 * player dragging an ingot no recipe wants still sees the old flicker, while every other rejection is
	 * now predicted correctly. The server remains the authority in {@code AlloySmelterBlockEntity}.
	 */
	private static final class ComponentSlot extends Slot {
		private ComponentSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (stack.isEmpty()) {
				return false;
			}
			for (int other : INPUT_SLOTS) {
				if (other != this.getContainerSlot()
						&& ItemStack.isSameItem(this.container.getItem(other), stack)) {
					return false;
				}
			}
			// Server-side the machine has the real answer (which recipes exist right now); client-side
			// fall back to "it is at least an ingot".
			return this.container.canPlaceItem(this.getContainerSlot(), stack)
					&& (this.container instanceof SimpleContainer ? stack.is(ModTags.Items.C_INGOTS) : true);
		}
	}
}
