package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ComponentRepairBenchBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.machine.RepairStatus;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Component Repair Bench (MOD-384): the grade's material on the left, the rotor/wheel
 * being repaired on the right, progress arrow between them pointing into the component.
 *
 * <p><b>The component sits in the "output" position on purpose.</b> A repair produces no new item —
 * the component that goes in is the one that comes out — so the right-hand slot is both where the
 * player puts it and where automation collects it once the bench is done
 * ({@code ComponentRepairBenchBlockEntity#isOutputSlot}). The arrow then reads correctly: the plate on
 * the left is consumed INTO the component on the right. Unlike every other machine's right slot this
 * one therefore accepts manual placement.
 */
public class ComponentRepairBenchMenu extends MachineMenu {

	public ComponentRepairBenchMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.COMPONENT_REPAIR_BENCH_MENU.get(), syncId, playerInventory, be, be.getDataAccess(),
				access, ModContent.COMPONENT_REPAIR_BENCH.get());
	}

	public ComponentRepairBenchMenu(int syncId, Inventory playerInventory) {
		super(ModContent.COMPONENT_REPAIR_BENCH_MENU.get(), syncId, playerInventory,
				new SimpleContainer(2 + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(ComponentRepairBenchBlockEntity.DATA_COUNT),
				ContainerLevelAccess.NULL, ModContent.COMPONENT_REPAIR_BENCH.get());
	}

	/**
	 * Slots are added in container-index order (target, then material) so the menu's slot list keeps
	 * lining up with the block entity's inventory; their on-screen positions are chosen independently
	 * and put the material on the left.
	 */
	@Override
	protected void addMachineSlots() {
		// Component being repaired — right-hand position, see the class javadoc.
		addSlot(new Slot(machine, ComponentRepairBenchBlockEntity.TARGET_SLOT, 117, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(ComponentRepairBenchBlockEntity.TARGET_SLOT, stack);
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
		// Repair material — left-hand "input" position, feeds the arrow.
		addSlot(new Slot(machine, ComponentRepairBenchBlockEntity.MATERIAL_SLOT, 56, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(ComponentRepairBenchBlockEntity.MATERIAL_SLOT, stack);
			}
		});
	}

	/** The bench's current {@link RepairStatus}, for the screen's status line. */
	public RepairStatus getStatus() {
		return RepairStatus.byCode(data.get(ComponentRepairBenchBlockEntity.STATUS_CHANNEL));
	}

	/**
	 * EU one repair of the loaded grade costs, 0 with nothing loaded.
	 *
	 * <p>These three come off sync channels rather than out of {@link dev.alaindustrial.Config}
	 * because the client's config is its own file: on a dedicated server with retuned balance, a
	 * locally-read number would print a confident lie. The block entity publishes them each tick.
	 */
	public int getRepairEuCost() {
		return data.get(ComponentRepairBenchBlockEntity.COST_CHANNEL);
	}

	/** Percent of the original durability ceiling one repair burns. */
	public int getDecayPercent() {
		return data.get(ComponentRepairBenchBlockEntity.DECAY_CHANNEL);
	}

	/** How many repairs one component is ever allowed. */
	public int getMaxRepairs() {
		return data.get(ComponentRepairBenchBlockEntity.MAX_REPAIRS_CHANNEL);
	}
}
