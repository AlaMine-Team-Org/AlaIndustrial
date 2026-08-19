package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.AbstractProcessingMachineBlockEntity;
import dev.alaindustrial.block.entity.ProcessingMachineStatus;
import dev.alaindustrial.block.entity.SawmillBlockEntity;
import dev.alaindustrial.block.entity.SawmillMode;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * Menu for the LV sawmill (MOD-150) — one input slot + a result-only output slot, plus a mode selector.
 * The four cutting modes ({@link SawmillMode}) are switched with GUI buttons that ride the vanilla
 * {@link #clickMenuButton} channel (no custom packet, works on both loaders): the button id is the
 * target mode ordinal. The active mode is read back for the screen from the machine's 6-wide
 * {@link net.minecraft.world.inventory.ContainerData} (index {@link SawmillBlockEntity#DATA_MODE}).
 */
public class SawmillMenu extends MachineMenu {
	/** Server side — carries a typed block-entity ref so {@link #clickMenuButton} can set the mode. */
	@Nullable
	private final SawmillBlockEntity sawmill;

	/** Server side. */
	public SawmillMenu(int syncId, Inventory playerInventory, SawmillBlockEntity be, ContainerLevelAccess access) {
		super(ModContent.SAWMILL_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access, ModContent.SAWMILL.get());
		this.sawmill = be;
	}

	/** Client side — 6-wide data (the family's four + status, plus mode) matches the server bridge. */
	public SawmillMenu(int syncId, Inventory playerInventory) {
		super(ModContent.SAWMILL_MENU.get(), syncId, playerInventory,
				new SimpleContainer(AbstractProcessingMachineBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(SawmillBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL, ModContent.SAWMILL.get());
		this.sawmill = null;
	}

	@Override
	protected void addMachineSlots() {
		// Slot positions aligned to the sawmill's own GUI atlas: the input/saw/output row sits high in
		// the frame so the band underneath is free for the four mode buttons (see SawmillScreen).
		addSlot(new Slot(machine, 0, 55, 19));
		// Output slot: result only, no manual insertion (spec).
		addSlot(new OutputSlot(machine, 1, 116, 19));
	}

	/** The active cutting mode, read from synced data (works on both sides). */
	public SawmillMode getMode() {
		return SawmillMode.byOrdinal(data.get(SawmillBlockEntity.DATA_MODE));
	}

	/**
	 * Handle a mode-button press: {@code buttonId} is the target {@link SawmillMode} ordinal. Server-only
	 * and validated here — a client that forges a button id gets nowhere. The screen sends this via
	 * {@code minecraft.gameMode.handleInventoryButtonClick}.
	 */
	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (sawmill == null || !(player instanceof ServerPlayer)
				|| buttonId < 0 || buttonId >= SawmillMode.values().length) {
			return false;
		}
		sawmill.setMode(SawmillMode.byOrdinal(buttonId));
		return true;
	}
	/** Why the machine is idle (MOD-458), read from synced data — works on both sides. */
	public ProcessingMachineStatus getStatus() {
		return ProcessingMachineStatus.byOrdinal(data.get(AbstractProcessingMachineBlockEntity.DATA_STATUS));
	}
}
