package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.item.fluid.ItemFluidBridge;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Two-slot Sprinkler menu (MOD-525): a container pair for filling the tank by hand, and the tank
 * gauge itself.
 *
 * <p>The smallest machine menu in the mod, and deliberately so — the block has one number worth
 * showing. It exists because "how much is left" is the question a player asks of a sprinkler in the
 * middle of a field, and a chat line answers it once while a gauge answers it continuously.
 *
 * <p>No energy bar: this machine takes no EU at all, and an always-empty bar would read as a fault.
 */
public final class SprinklerMenu extends MachineMenu {
	public SprinklerMenu(int syncId, Inventory playerInventory, SprinklerBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.SPRINKLER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.SPRINKLER.get());
	}

	public SprinklerMenu(int syncId, Inventory playerInventory) {
		// No upgrade slots on this machine, so the client stub's container is the machine slots alone.
		super(ModContent.SPRINKLER_MENU.get(), syncId, playerInventory,
				new SimpleContainer(SprinklerBlockEntity.SLOT_COUNT),
				new SimpleContainerData(SprinklerBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.SPRINKLER.get());
	}

	/**
	 * No upgrade panel — the block entity opted out, and the menu has to say the same thing.
	 *
	 * <p>Both sides derive slot indices from this flag: left at the default {@code true}, the base
	 * class computes {@code containerSize - 4} for a two-slot container and gets −2, so every index
	 * after the machine's own slots is wrong and the player inventory lands in the wrong place. The
	 * Energy Condenser hit this first (MOD-393) and its menu carries the same override for the same
	 * reason.
	 */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	@Override
	protected void addMachineSlots() {
		Container container = machine;
		// Beside the gauge: what you pour in, and the empty container that comes back out.
		addSlot(new Slot(container, SprinklerBlockEntity.FILL_INPUT_SLOT, 80, 26) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return ItemFluidBridge.get().isFluidContainer(stack);
			}
		});
		addSlot(new OutputSlot(container, SprinklerBlockEntity.FILL_OUTPUT_SLOT, 80, 50));
	}

	/** Tank level as a permille (0..1000) — scaled because the sync channel is a signed short. */
	public int getSolutionPermille() {
		return data.get(SprinklerBlockEntity.CH_SOLUTION_PERMILLE);
	}

	/** Registry id of the fluid in the tank, or {@link SprinklerBlockEntity#FLUID_ID_NONE}. */
	public int getSolutionFluidId() {
		return data.get(SprinklerBlockEntity.CH_SOLUTION_FLUID_ID);
	}
}
