package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.RepellerDomePayload;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Menu of the Mob Repeller family (MOD-278): the vessel slot plus the standard energy readout. One
 * class serves all three tiers — the tier differences (menu type, block for the valid-range check,
 * slot acceptance) ride in through the constructor / the block entity, so the screen logic cannot
 * drift between tiers.
 *
 * <p>No upgrade panel ({@code hasUpgradePanel() == false} on the block entity): an overclocker or a
 * mute chip in a field block would be a dead promise, same reasoning as the Energy Condenser
 * (MOD-393). The client-side dummy is sized to the machine slot alone accordingly.
 *
 * <p>The dome toggle button is NOT here: showing the personal radius dome is a client-only affair
 * (see {@code RepellerDomeClientState}) — the screen flips local state directly, no menu channel.
 */
public class MobRepellerMenu extends MachineMenu {

	/**
	 * Vessel slot. These coordinates are not a layout choice — they are read off the GUI atlas: the
	 * slot well is drawn at (85, 35) there, and a slot whose menu position disagrees with its painted
	 * frame puts the hover highlight and the ghost hint beside the hole they belong in.
	 */
	public static final int VESSEL_SLOT_X = 85;
	public static final int VESSEL_SLOT_Y = 35;

	/** Button id of the "show my radius" toggle (vanilla container-button channel). */
	public static final int BUTTON_TOGGLE_DOME = 0;

	/** The block entity on the server; null on the client (which has only the dummy container). */
	private final MobRepellerBlockEntity repeller;

	/** Server side — the tier subclass supplies its own menu type and block. */
	protected MobRepellerMenu(MenuType<? extends MobRepellerMenu> type, int syncId, Inventory playerInventory,
			MobRepellerBlockEntity be, ContainerLevelAccess access, Block block) {
		super(type, syncId, playerInventory, be, be.getDataAccess(), access, block);
		this.repeller = be;
	}

	/** Client side: dummy container of exactly the machine's one slot, no upgrade block. */
	protected MobRepellerMenu(MenuType<? extends MobRepellerMenu> type, int syncId, Inventory playerInventory,
			Block block) {
		super(type, syncId, playerInventory, new SimpleContainer(1),
				new SimpleContainerData(MachineBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL, block);
		this.repeller = null;
	}

	/** Server side, LV. */
	public MobRepellerMenu(int syncId, Inventory playerInventory, MobRepellerBlockEntity be,
			ContainerLevelAccess access) {
		this(ModContent.MOB_REPELLER_MENU.get(), syncId, playerInventory, be, access,
				ModContent.MOB_REPELLER.get());
	}

	/** Client side, LV — referenced by the {@code ContentManifest.MENUS} entry. */
	public MobRepellerMenu(int syncId, Inventory playerInventory) {
		this(ModContent.MOB_REPELLER_MENU.get(), syncId, playerInventory, ModContent.MOB_REPELLER.get());
	}

	/** No panel: see the class doc. */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	/**
	 * The dome button: answer the asking player — and only them — with this block's position and its
	 * server-authoritative radius; their client toggles the dome from that
	 * ({@code RepellerDomeRenderer}). The server keeps no per-player view state, so nothing here can
	 * leak between players or survive a disconnect.
	 */
	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId != BUTTON_TOGGLE_DOME || repeller == null
				|| !(player instanceof ServerPlayer serverPlayer)
				|| !(repeller.getLevel() instanceof ServerLevel serverLevel)) {
			return false;
		}
		NetworkDispatcher.get().sendToPlayer(serverPlayer, new RepellerDomePayload(
				serverLevel.dimension(), repeller.getBlockPos(), repeller.fieldRange(), true));
		return true;
	}

	@Override
	protected void addMachineSlots() {
		addSlot(new Slot(machine, MobRepellerBlockEntity.VESSEL_SLOT, VESSEL_SLOT_X, VESSEL_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				// Server: the block entity's full-vessel gate; client dummy: accept nothing rather
				// than lie (the real verdict arrives with the server's slot sync).
				return machine instanceof Container c && c.canPlaceItem(MobRepellerBlockEntity.VESSEL_SLOT, stack);
			}

			@Override
			public int getMaxStackSize(ItemStack stack) {
				return 1;
			}
		});
	}
}
