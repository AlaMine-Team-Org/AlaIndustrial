package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.CreativeEnergySourceBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Creative Energy Source (MOD-479): the charge slot plus the three settings the screen
 * drives — the on/off switch, the output presets and the fine output slider.
 *
 * <p>All three travel on the vanilla container-button channel, so no payload of our own has to be
 * registered on either loader. The ids are laid out so the largest one stays small: the slider does not
 * send EU/tick, it sends a <b>step index</b> ({@link #OUTPUT_STEP} EU per step). Sending the raw value
 * would put ids up to 8192 on a channel whose shipped precedent in this mod reaches ~315, and the width
 * of that field is not something to find out in production.
 */
public class CreativeEnergySourceMenu extends MachineMenu {

	/**
	 * Panel height. 54 px taller than the family default: the art is hand-authored, and these numbers
	 * were measured off the PNG rather than agreed with it — the slot well, the slider groove and the
	 * inventory rows were all located by scanning the atlas for the vanilla slot grey.
	 */
	public static final int IMAGE_HEIGHT = 220;

	/** Charge slot, top right, on the switch's row and clear of every control below it. */
	public static final int CHARGE_SLOT_X = 142;
	public static final int CHARGE_SLOT_Y = 20;

	/** Toggle the source on or off. The server flips the current state; the client never sends a target. */
	public static final int BUTTON_TOGGLE = 0;
	/** Output presets, in the order the screen draws them: LV, MV, HV. */
	public static final int BUTTON_PRESET_LV = 1;
	public static final int BUTTON_PRESET_MV = 2;
	public static final int BUTTON_PRESET_HV = 3;

	/** Slider ids start here; the id is {@code OUTPUT_BASE + steps}. */
	public static final int OUTPUT_BASE = 16;
	/** EU/tick per slider step. Also the granularity the screen snaps the thumb to. */
	public static final int OUTPUT_STEP = 32;
	/** Highest step index the slider can send. */
	public static final int OUTPUT_MAX_STEP = CreativeEnergySourceBlockEntity.MAX_OUTPUT / OUTPUT_STEP;

	private final CreativeEnergySourceBlockEntity source;

	/** Server side. */
	public CreativeEnergySourceMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.CREATIVE_ENERGY_SOURCE_MENU.get(), syncId, playerInventory, be, be.getDataAccess(),
				access, ModContent.CREATIVE_ENERGY_SOURCE.get());
		this.source = be instanceof CreativeEnergySourceBlockEntity s ? s : null;
	}

	/** Client side. One machine slot and no upgrade block — the block has no upgrade panel. */
	public CreativeEnergySourceMenu(int syncId, Inventory playerInventory) {
		super(ModContent.CREATIVE_ENERGY_SOURCE_MENU.get(), syncId, playerInventory,
				new SimpleContainer(CreativeEnergySourceBlockEntity.MACHINE_SLOTS),
				new SimpleContainerData(CreativeEnergySourceBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.CREATIVE_ENERGY_SOURCE.get());
		this.source = null;
	}

	@Override
	protected void addMachineSlots() {
		// Powered-item filter in BOTH places: here for client prediction, and in the block entity's
		// canPlaceItem for the server. A server-only check is what makes an item visibly flicker back.
		addSlot(new Slot(machine, CreativeEnergySourceBlockEntity.CHARGE_SLOT, CHARGE_SLOT_X, CHARGE_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return ItemEnergy.capacity(stack) > 0;
			}
		});
	}

	/** The block has none — an overclocker cannot speed up an infinite source. */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	/**
	 * Applies a click from the screen. Every branch re-reads the block entity rather than trusting the
	 * client: an id is just a number on the wire, and this menu is reachable by anything that can open
	 * the block.
	 */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (source == null || !(player instanceof ServerPlayer)) {
			return false;
		}
		switch (id) {
			case BUTTON_TOGGLE -> {
				// The server flips its own state. Taking a target from the client instead would make two
				// fast clicks race and settle on whichever packet arrived last.
				source.setEnabled(!source.isEnabled());
				return true;
			}
			case BUTTON_PRESET_LV -> {
				source.setOutputLimit((int) EnergyTier.LV.maxVoltage());
				return true;
			}
			case BUTTON_PRESET_MV -> {
				source.setOutputLimit((int) EnergyTier.MV.maxVoltage());
				return true;
			}
			case BUTTON_PRESET_HV -> {
				source.setOutputLimit((int) EnergyTier.HV.maxVoltage());
				return true;
			}
			default -> {
				if (id < OUTPUT_BASE || id > OUTPUT_BASE + OUTPUT_MAX_STEP) {
					return false;
				}
				// Clamped, not rejected: the slider is an instrument, and running off the end of its
				// scale means the end of the scale. setOutputLimit clamps again on the block entity —
				// the authoritative bound lives with the state, not with the transport.
				source.setOutputLimit((id - OUTPUT_BASE) * OUTPUT_STEP);
				return true;
			}
		}
	}

	// --- readouts for the screen ---------------------------------------------------------------

	public boolean isSourceEnabled() {
		return data.get(CreativeEnergySourceBlockEntity.DATA_ENABLED) != 0;
	}

	/** Current output limit in EU/tick. */
	public int getOutputLimit() {
		return data.get(CreativeEnergySourceBlockEntity.DATA_OUTPUT);
	}

	@Override
	protected int playerInventoryY() {
		// Measured from the atlas: the first inventory row's slot grey starts at y=138. The pair below
		// keeps vanilla's own 58 px gap between the backpack and the hotbar, which the art also has.
		return 138;
	}

	@Override
	protected int hotbarY() {
		return 196;
	}
}
