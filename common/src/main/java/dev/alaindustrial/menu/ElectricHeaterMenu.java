package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ElectricHeaterBlockEntity;
import dev.alaindustrial.block.entity.ElectricHeaterStatus;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Electric Heater (MOD-418) — a readout, not a container.
 *
 * <p>The mod's second slotless machine menu, after the Charging Station. There is nothing to put into a
 * heater: it takes EU from a cable and gives heat upward, and it does both whether or not this window is
 * open. The screen exists because the block now has something worth reading — how far along its warm-up
 * is, therefore whether the machine above is getting x2 or x3, and what that is costing per tick.
 *
 * <p>Every channel is computed on the SERVER and shipped as a finished value. They cannot be derived
 * here: the rate depends on {@code Config} (never synced to clients) and on the chips in the machine
 * above (which the client has no reason to have loaded), so a client doing its own arithmetic would
 * answer from its local balance on any server that retuned one.
 */
public class ElectricHeaterMenu extends MachineMenu {

	/** Server side. */
	public ElectricHeaterMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.ELECTRIC_HEATER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.ELECTRIC_HEATER.get());
	}

	/**
	 * Client side. The dummy container is empty — exactly the heater's zero slots, with no upgrade block
	 * appended, matching {@link ElectricHeaterBlockEntity#hasUpgradePanel()}. Sizing it any wider would
	 * push {@code baseSlotCount()} out of step with the server and misalign every slot index.
	 */
	public ElectricHeaterMenu(int syncId, Inventory playerInventory) {
		super(ModContent.ELECTRIC_HEATER_MENU.get(), syncId, playerInventory, new SimpleContainer(0),
				new SimpleContainerData(ElectricHeaterBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.ELECTRIC_HEATER.get());
	}

	/** The heater has none — see the class doc. */
	@Override
	protected void addMachineSlots() {
	}

	/**
	 * No panel. Must answer the same as {@link ElectricHeaterBlockEntity#hasUpgradePanel()}: the slot
	 * indices are derived from it on both sides, and the block entity's answer also decides whether the
	 * heater has an inventory at all.
	 */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	/**
	 * Shift-click does nothing, said out loud — same reasoning as the Charging Station's: the inherited
	 * implementation happens to degenerate to {@code EMPTY} with no machine slots, but that is not
	 * something a reader should have to re-derive.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	/** Warm-up as a percentage, the number the screen prints and the thermometer fills to. */
	public int getWarmupPercent() {
		return data.get(ElectricHeaterBlockEntity.DATA_HEAT) / 10;
	}

	/** Warm-up in permille — the finer value, used for the gauge so it moves every few ticks. */
	public int getWarmupPermille() {
		return data.get(ElectricHeaterBlockEntity.DATA_HEAT);
	}

	/** EU per tick the heater bills while working, including the chips of the machine above. */
	public int getRateEuPerTick() {
		return data.get(ElectricHeaterBlockEntity.DATA_RATE);
	}

	public ElectricHeaterStatus getStatus() {
		return ElectricHeaterStatus.byOrdinal(data.get(ElectricHeaterBlockEntity.DATA_STATUS));
	}

	/**
	 * The output multiplier the machine above is getting right now, or {@code 0} when the heater is not
	 * supplying heat — drawn as a dash rather than as "x0", which would read as a measured value.
	 *
	 * <p>Only {@code HOT} yields a number, and that is the whole point of the gate: a warming heater
	 * supplies nothing, so the row must not promise a multiplier the machine above is not receiving. An
	 * earlier draft answered per the heater's own tier and would print "x3" the instant the thermometer
	 * filled, while the batch in flight was still being paid out at the tier it started on.
	 */
	public int getHeatMultiplier() {
		return getStatus() == ElectricHeaterStatus.HOT ? 3 : 0;
	}
}
