package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import dev.alaindustrial.registry.ModContent;

/**
 * Menu for the Energy Condenser (MOD-393): one output slot plus the three readouts the decision rests
 * on — how full the bank is, which clot tier it is worth right now, and how far along it is toward the
 * next one.
 *
 * <p>The container has no upgrade slots ({@code hasUpgradePanel() == false} on the block entity), so
 * the client-side dummy is sized to the machine slots alone — sizing it like the other machines would
 * desync the slot indices the moment the player opens the screen.
 */
public class EnergyCondenserMenu extends MachineMenu {

	/** Output slot: dead centre of the ring gauge, so the gauge closes around what it produces. */
	public static final int OUTPUT_SLOT_X = 80;
	public static final int OUTPUT_SLOT_Y = 38;

	/**
	 * This screen is 20 px taller than a standard machine GUI, so the player's inventory moves down
	 * with it. The height buys two full text rows under the ring; the first version had room for one,
	 * and one row could not say both which tier the bank is worth AND how far it is from the next —
	 * which left the screen reading as "nothing is happening" while the bank was in fact climbing.
	 */
	public static final int IMAGE_HEIGHT = 190;
	private static final int INVENTORY_Y = 108;
	private static final int HOTBAR_Y = 166;

	/** Server side. */
	public EnergyCondenserMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerData data, ContainerLevelAccess access) {
		super(ModContent.ENERGY_CONDENSER_MENU.get(), syncId, playerInventory, be, data, access,
				ModContent.ENERGY_CONDENSER.get());
	}

	/** Client side: dummy container of exactly the machine's slots, no upgrade block. */
	public EnergyCondenserMenu(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, new SimpleContainer(1),
				new SimpleContainerData(EnergyCondenserBlockEntity.DATA_COUNT));
	}

	private EnergyCondenserMenu(int syncId, Inventory playerInventory, Container container, ContainerData data) {
		super(ModContent.ENERGY_CONDENSER_MENU.get(), syncId, playerInventory, container, data,
				ContainerLevelAccess.NULL, ModContent.ENERGY_CONDENSER.get());
	}

	/** No panel: an overclocker here would defeat the "sips surplus" design (see the block entity). */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	@Override
	protected void addMachineSlots() {
		// Take-only: the condenser's product appears here, nothing may be put in.
		addSlot(new Slot(machine, EnergyCondenserBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
	}

	@Override
	protected int playerInventoryY() {
		return INVENTORY_Y;
	}

	@Override
	protected int hotbarY() {
		return HOTBAR_Y;
	}

	/** Bank fill, 0..1000 — permille because the sync channel is 16-bit and the bank is millions. */
	public int fillPermille() {
		return data.get(EnergyCondenserBlockEntity.DATA_FILL_PERMILLE);
	}

	/** Banked EU, reassembled from the two channels it had to be split across to survive 16 bits. */
	public long bankedEu() {
		return (long) data.get(EnergyCondenserBlockEntity.DATA_BANK_HI)
				* EnergyCondenserBlockEntity.BANK_RADIX
				+ data.get(EnergyCondenserBlockEntity.DATA_BANK_LO);
	}

	/** EU the bank is climbing toward; 0 once the top tier is reached. */
	public long nextThresholdEu() {
		return data.get(EnergyCondenserBlockEntity.DATA_NEXT_THOUSANDS) * 1000L;
	}

	/** Clot tier the bank is worth right now: 0 when it is still below the first threshold. */
	public int currentTier() {
		return data.get(EnergyCondenserBlockEntity.DATA_TIER);
	}

	/** Progress toward the NEXT tier, 0..1000; 1000 once the top tier is reached. */
	public int nextTierPermille() {
		return data.get(EnergyCondenserBlockEntity.DATA_NEXT_PERMILLE);
	}
}
