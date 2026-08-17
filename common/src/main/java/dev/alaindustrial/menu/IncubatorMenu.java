package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.IncubatorMode;
import dev.alaindustrial.block.entity.IncubatorStatus;
import dev.alaindustrial.item.misc.MutationGrades;
import dev.alaindustrial.registry.ModCriteria;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for the incubator (MOD-118): chip, fuel, input, result and ash.
 *
 * <p>Every slot filter is written twice on purpose — here for the client's prediction (so a stack
 * does not flicker into a slot that will bounce back) and in the block entity's
 * {@code canPlaceItem} for the server and for hoppers.
 */
public class IncubatorMenu extends MachineMenu {

	@Nullable
	private final IncubatorBlockEntity incubator;

	/** Server side. */
	public IncubatorMenu(int syncId, Inventory playerInventory, IncubatorBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.INCUBATOR_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.INCUBATOR.get());
		this.incubator = be;
	}

	/** Client side — the data width must match the block entity's channel count exactly. */
	public IncubatorMenu(int syncId, Inventory playerInventory) {
		super(ModContent.INCUBATOR_MENU.get(), syncId, playerInventory,
				new SimpleContainer(IncubatorBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(IncubatorBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL, ModContent.INCUBATOR.get());
		this.incubator = null;
	}

	/**
	 * Slot layout of the hand-drawn atlas: chip, input and result across the top row with the progress
	 * arrow between input and result, fuel and ash below them. The gap between the two rows is left
	 * clear for the status line, and the energy bar runs down the left edge.
	 */
	@Override
	protected void addMachineSlots() {
		Container machineContainer = this.machine;
		addSlot(new Slot(machineContainer, IncubatorBlockEntity.CHIP_SLOT, 36, 20) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return getItem().isEmpty() && IncubatorMode.forChip(stack) != null;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addSlot(new Slot(machineContainer, IncubatorBlockEntity.FUEL_SLOT, 36, 62) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ModTags.Items.INCUBATOR_FUEL);
			}
		});
		addSlot(new Slot(machineContainer, IncubatorBlockEntity.INPUT_SLOT, 87, 20));
		addSlot(new Slot(machineContainer, IncubatorBlockEntity.OUTPUT_SLOT, 144, 20) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			/**
			 * Collecting the result is what the player is credited for. The machine finishing a cycle
			 * has no player attached — a block entity does not know who built it — so this is the only
			 * honest place to fire the criterion.
			 */
			@Override
			public void onTake(Player player, ItemStack taken) {
				if (player instanceof ServerPlayer server && !taken.isEmpty()) {
					ModCriteria.MUTATION_COMPLETED.get()
							.trigger(server, taken, MutationGrades.get(taken));
				}
				super.onTake(player, taken);
			}
		});
		addSlot(new OutputSlot(machineContainer, IncubatorBlockEntity.ASH_SLOT, 144, 62));
	}

	// The atlas is 14 pixels taller than the standard machine GUI — the extra height is the band that
	// keeps the status line clear of the slots — so the player's inventory sits that much lower.
	@Override
	protected int playerInventoryY() {
		return 98;
	}

	@Override
	protected int hotbarY() {
		return 156;
	}

	/** The mode the inserted chip selects, or {@code null} when the chip slot is empty. */
	@Nullable
	public IncubatorMode getMode() {
		return IncubatorMode.byDataValue(data.get(IncubatorBlockEntity.DATA_MODE));
	}

	/** Remaining irradiation attempts on the loaded ingot (0..mutationAttemptsPerIngot). */
	public int getCharge() {
		return data.get(IncubatorBlockEntity.DATA_CHARGE);
	}

	/** Whether the dome is in place; without it the machine refuses to run. */
	public boolean isFormed() {
		return data.get(IncubatorBlockEntity.DATA_FORMED) != 0;
	}

	/** Why the machine is idle, as diagnosed server-side (MOD-234) — the screen's status line. */
	public IncubatorStatus getStatus() {
		return IncubatorStatus.byOrdinal(data.get(IncubatorBlockEntity.DATA_STATUS));
	}
}
