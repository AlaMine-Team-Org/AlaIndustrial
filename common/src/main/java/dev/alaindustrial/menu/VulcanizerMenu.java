package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.VulcanizerBlockEntity;
import dev.alaindustrial.block.entity.VulcanizerStatus;
import dev.alaindustrial.core.heat.HeatSource;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Three-slot Vulcanizer menu: raw rubber, sulfur dust, result. */
public final class VulcanizerMenu extends MachineMenu {
	public VulcanizerMenu(int syncId, Inventory inventory, VulcanizerBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.VULCANIZER_MENU.get(), syncId, inventory, be, be.getDataAccess(), access,
				ModContent.VULCANIZER.get());
	}

	public VulcanizerMenu(int syncId, Inventory inventory) {
		super(ModContent.VULCANIZER_MENU.get(), syncId, inventory,
				new SimpleContainer(VulcanizerBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(VulcanizerBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.VULCANIZER.get());
	}

	@Override
	protected void addMachineSlots() {
		Container container = machine;
		addSlot(new Slot(container, VulcanizerBlockEntity.RAW_RUBBER_SLOT, 45, 26) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModContent.RAW_RUBBER.get());
			}
		});
		addSlot(new Slot(container, VulcanizerBlockEntity.SULFUR_SLOT, 45, 49) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModContent.SULFUR_DUST.get());
			}
		});
		addSlot(new Slot(container, VulcanizerBlockEntity.OUTPUT_SLOT, 119, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
	}

	public HeatSource getHeatSource() {
		return HeatSource.byOrdinal(data.get(4));
	}

	public VulcanizerStatus getStatus() {
		return VulcanizerStatus.byOrdinal(data.get(5));
	}
}
