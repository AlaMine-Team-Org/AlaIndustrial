package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.PolymerizerBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Polymerizer: a filled-container slot feeding the tank, the emptied container below it, and
 * the result slot. The client stub is sized from {@link PolymerizerBlockEntity#DATA_COUNT} — six channels,
 * because the machine adds the tank level and its fluid type to the shared four (MOD-235).
 */
public class PolymerizerMenu extends MachineMenu {
	/** Server side. */
	public PolymerizerMenu(int syncId, Inventory playerInventory, MachineBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.POLYMERIZER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.POLYMERIZER.get());
	}

	/** Client side. */
	public PolymerizerMenu(int syncId, Inventory playerInventory) {
		super(ModContent.POLYMERIZER_MENU.get(), syncId, playerInventory, new SimpleContainer(3 + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(PolymerizerBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.POLYMERIZER.get());
	}

	@Override
	protected void addMachineSlots() {
		// Positions match the polymerizer.png atlas: the tank gauge occupies the left edge, the two
		// container slots stack beside it (full bucket in at the top, emptied one below), and the result
		// sits at the far end of the progress arrow.
		addSlot(new Slot(machine, PolymerizerBlockEntity.FILL_INPUT_SLOT, 44, 23));
		addSlot(new Slot(machine, PolymerizerBlockEntity.FILL_OUTPUT_SLOT, 44, 47) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false; // the emptied container lands here; nothing is placed by hand
			}
		});
		addSlot(new Slot(machine, PolymerizerBlockEntity.OUTPUT_SLOT, 117, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false; // result only, no manual insertion (spec)
			}
		});
	}

	/** Tank fill permille (0..1000) — sync channel 4. */
	public int getFluidPermille() {
		return data.get(4);
	}

	/**
	 * The tank fluid's {@code BuiltInRegistries.FLUID} registry id (channel 5), or
	 * {@link PolymerizerBlockEntity#FLUID_ID_NONE} when empty. The tank accepts any {@code c:oil} fluid, so
	 * the screen resolves the texture and name from this rather than assuming our own oil.
	 */
	public int getFluidRegistryId() {
		return data.get(5);
	}
}
