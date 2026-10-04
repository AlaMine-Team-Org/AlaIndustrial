package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ChargePadBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Charging Station (MOD-416) — a readout, not a container.
 *
 * <p><b>The only slotless machine menu in the mod.</b> There is nothing to put into a floor plate: the
 * station charges what the player is wearing and carrying, and it does that whether or not this window
 * is open. The screen exists so a player who wants the numbers can have them; the plate's own job needs
 * no clicks at all.
 *
 * <p>The three readout channels are computed on the SERVER and shipped as finished values. They cannot
 * be derived here: {@code ItemEnergy.capacity} reads the config, and the mod never syncs the config to
 * clients, so a client doing the arithmetic would answer from its own local balance on any server that
 * retuned one.
 */
public class ChargePadMenu extends MachineMenu {

	/** Server side. */
	public ChargePadMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.CHARGE_PAD_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.CHARGE_PAD.get());
	}

	/**
	 * Client side. The dummy container is empty — exactly the station's zero slots, with no upgrade block
	 * appended, matching the {@code NoUpgradePanel} marker on {@link ChargePadBlockEntity}. Sizing it any
	 * wider would push {@code baseSlotCount()} out of step with the server and misalign every slot index.
	 */
	public ChargePadMenu(int syncId, Inventory playerInventory) {
		super(ModContent.CHARGE_PAD_MENU.get(), syncId, playerInventory,
				clientStub(0, ChargePadBlockEntity.DATA_COUNT), ModContent.CHARGE_PAD.get());
	}

	/** The station has none — see the class doc. */
	@Override
	protected void addMachineSlots() {
	}


	/**
	 * Shift-click does nothing, said out loud.
	 *
	 * <p>The inherited implementation already happens to return {@code EMPTY} here — with no machine
	 * slots it ends up asking {@code moveItemStackTo} to move a stack into the empty range 0..0 — but
	 * "correct because the arithmetic degenerates" is not something a reader should have to re-derive,
	 * and it would stop being true the moment the base class grew a different fallback.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	/** Station charge as a percentage of its buffer. */
	public int getChargePercent() {
		int cap = getCapacity();
		return cap > 0 ? getEnergy() * 100 / cap : 0;
	}

	/** EU per tick currently flowing into whoever stands on the plate. */
	public int getRateEuPerTick() {
		return channel(ChargePadBlockEntity.Channel.RATE);
	}

	/** How many carried items are taking charge right now. */
	public int getItemsCharging() {
		return channel(ChargePadBlockEntity.Channel.ITEMS);
	}

	/** Seconds until the visitor's gear is full, or 0 when there is nothing to report. */
	public int getEtaSeconds() {
		return channel(ChargePadBlockEntity.Channel.ETA);
	}

	/**
	 * Whether the station is serving anyone this moment — the switch between real numbers and a dash.
	 *
	 * <p>Keyed on the delivery rate rather than on the item count: a visitor whose gear is already full
	 * is standing there being served nothing, and reporting "0 items, 0 EU/t" as live data would be a
	 * more confident claim than the station can make.
	 */
	public boolean isServing() {
		return getRateEuPerTick() > 0;
	}
}
