package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.UpgradeTableBlockEntity;
import dev.alaindustrial.core.machine.ToolUpgradeStatus;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Upgrade Table (MOD-482): the module on the left, the tool being upgraded on the right,
 * the progress arrow pointing from one into the other.
 *
 * <p><b>The tool sits in the "output" position on purpose.</b> Fitting an upgrade produces no new
 * item — the drill that goes in is the drill that comes out — so the right-hand slot is both where the
 * player puts it and where automation collects it once the table is done
 * ({@code UpgradeTableBlockEntity#isOutputSlot}). The arrow then reads correctly: the module on the
 * left is consumed INTO the tool on the right. Same layout, and the same reason, as the Component
 * Repair Bench.
 */
public class UpgradeTableMenu extends MachineMenu {

	public UpgradeTableMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.UPGRADE_TABLE_MENU.get(), syncId, playerInventory, be, be.getDataAccess(),
				access, ModContent.UPGRADE_TABLE.get());
	}

	public UpgradeTableMenu(int syncId, Inventory playerInventory) {
		super(ModContent.UPGRADE_TABLE_MENU.get(), syncId, playerInventory,
				new SimpleContainer(UpgradeTableBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(UpgradeTableBlockEntity.DATA_COUNT),
				ContainerLevelAccess.NULL, ModContent.UPGRADE_TABLE.get());
	}

	/**
	 * Slots are added in container-index order (tool, then module) so the menu's slot list keeps lining
	 * up with the block entity's inventory; their on-screen positions are chosen independently and put
	 * the module on the left.
	 */
	@Override
	protected void addMachineSlots() {
		// The tool being upgraded — right-hand position, see the class javadoc.
		addSlot(new Slot(machine, UpgradeTableBlockEntity.TOOL_SLOT, 117, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(UpgradeTableBlockEntity.TOOL_SLOT, stack);
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
		// The module — left-hand "input" position, feeds the arrow.
		addSlot(new Slot(machine, UpgradeTableBlockEntity.MODULE_SLOT, 56, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(UpgradeTableBlockEntity.MODULE_SLOT, stack);
			}
		});
	}

	/** The table's current {@link ToolUpgradeStatus}, for the screen's status line. */
	public ToolUpgradeStatus getStatus() {
		return ToolUpgradeStatus.byCode(data.get(UpgradeTableBlockEntity.STATUS_CHANNEL));
	}
}
