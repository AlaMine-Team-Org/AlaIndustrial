package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.LightningRodGeneratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the lightning rod generator (MOD-386): one conductor-tip slot, a capacitor gauge and a
 * status line. Nothing is processed here — the tip is a consumable component the player crafts and
 * installs, in the same shape as the wind mill's rotor slot, not an input to a recipe.
 */
public class LightningRodGeneratorMenu extends MachineMenu {
	/** Server side. */
	public LightningRodGeneratorMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.LIGHTNING_ROD_GENERATOR_MENU.get(), syncId, playerInventory, be, be.getDataAccess(),
				access, ModContent.LIGHTNING_ROD_GENERATOR.get());
	}

	/** Client side. */
	public LightningRodGeneratorMenu(int syncId, Inventory playerInventory) {
		super(ModContent.LIGHTNING_ROD_GENERATOR_MENU.get(), syncId, playerInventory,
				new SimpleContainer(LightningRodGeneratorBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(LightningRodGeneratorBlockEntity.DATA_COUNT),
				ContainerLevelAccess.NULL, ModContent.LIGHTNING_ROD_GENERATOR.get());
	}

	@Override
	protected void addMachineSlots() {
		// (80, 19) is read off the shipped atlas, not chosen here: the hand-drawn frame paints the
		// well there, and MachineScreen.ghostHint positions its hint from THIS slot. When the two
		// disagree the hint floats beside an empty well, which is exactly what the first playtest saw.
		addSlot(new Slot(machine, LightningRodGeneratorBlockEntity.TIP_SLOT, 80, 19) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(LightningRodGeneratorBlockEntity.TIP_SLOT, stack);
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
	}

	/** Capacitor charge in permille (0..1000) — the gauge the screen paints. */
	public int getCapacitorPermille() {
		return getProgress();
	}

	/** Status code; see the {@code MODE_*} constants on the block entity. */
	public int getMode() {
		return data.get(LightningRodGeneratorBlockEntity.MODE_CHANNEL);
	}

	/**
	 * Effective output rate (EU/t) — what the buffer actually gains while it has room, i.e. after
	 * {@link dev.alaindustrial.Config#globalEuRateMultiplier}. Rides its own channel because channel
	 * 2 carries the capacitor gauge here.
	 */
	public int getProductionRate() {
		return data.get(LightningRodGeneratorBlockEntity.RATE_CHANNEL);
	}
}
