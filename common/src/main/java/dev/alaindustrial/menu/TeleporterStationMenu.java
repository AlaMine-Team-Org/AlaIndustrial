package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The station's screen (MOD-093): how full the jump fund is, whose station it is, and the
 * private/public switch.
 *
 * <p>The station has no slots — it is a fund, not a machine — so this menu carries only synced
 * numbers. Note what is <em>not</em> sent raw: the buffer is 500 000 EU, and a vanilla
 * {@code DataSlot} serialises as a 16-bit short, so anything above 32 767 arrives negative
 * client-side. That bug already bit this mod once (the solar panel's evolution bar). The fill
 * fraction therefore crosses the wire as permille (0–1000), exactly like
 * {@code SolarPanelBlockEntity} does, and the raw EU stays server-side.
 */
public class TeleporterStationMenu extends AbstractContainerMenu {
	/** Toggle privacy. Only this one button exists, so the id needs no encoding. */
	public static final int BUTTON_TOGGLE_PRIVACY = 0;

	/** Fill of the jump fund, 0..1000 — see the class note on why this is not raw EU. */
	public static final int DATA_ENERGY_PERMILLE = 0;
	/** 1 = private, 0 = public. */
	public static final int DATA_PRIVATE = 1;
	/** 1 = the viewing player owns this station (the toggle is theirs to press). */
	public static final int DATA_IS_OWNER = 2;
	public static final int DATA_SIZE = 3;

	/** Player-inventory grid, matching the texture's slot frames. */
	private static final int INV_X = 8, INV_Y = 105, HOTBAR_Y = 163;

	private final ContainerData data;
	private final ContainerLevelAccess access;
	@Nullable
	private final TeleporterBlockEntity station;

	/** Server side: the real station. */
	public TeleporterStationMenu(int syncId, Inventory playerInventory, TeleporterBlockEntity station,
			ContainerLevelAccess access) {
		super(ModContent.TELEPORTER_STATION_MENU.get(), syncId);
		this.station = station;
		this.access = access;
		this.data = station.stationData(playerInventory.player.getUUID());
		addDataSlots(data);
		addPlayerInventory(playerInventory);
	}

	/** Client side: vanilla fills the data slots from the server's copy. */
	public TeleporterStationMenu(int syncId, Inventory playerInventory) {
		super(ModContent.TELEPORTER_STATION_MENU.get(), syncId);
		this.station = null;
		this.access = ContainerLevelAccess.NULL;
		this.data = new SimpleContainerData(DATA_SIZE);
		addDataSlots(data);
		addPlayerInventory(playerInventory);
	}

	/**
	 * The player's own inventory — the station itself still has no slots of its own.
	 *
	 * <p>Coordinates are read off the texture's slot grid (borders on the 18px step at x=7, rows at
	 * y=104/122/140 and the hotbar at y=162); the item area sits 1px inside each frame, hence 8 and
	 * 105/123/141/163.
	 */
	private void addPlayerInventory(Inventory inventory) {
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
		}
	}

	/** Jump-fund fill as permille (0..1000) — what the bar draws. */
	public int getEnergyPermille() {
		return data.get(DATA_ENERGY_PERMILLE);
	}

	public boolean isPrivate() {
		return data.get(DATA_PRIVATE) != 0;
	}

	/** True when the viewing player may work the toggle. */
	public boolean isOwner() {
		return data.get(DATA_IS_OWNER) != 0;
	}

	/**
	 * The privacy toggle. Ownership is re-checked here, on the server, from the block entity — the
	 * {@code DATA_IS_OWNER} flag only greys the button out on the client, and a client that ignores
	 * it gets nowhere.
	 */
	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId != BUTTON_TOGGLE_PRIVACY || station == null || !(player instanceof ServerPlayer)) {
			return false;
		}
		if (!station.isOwner(player.getUUID())) {
			return false;
		}
		station.setPrivate(!station.isPrivate());
		return true;
	}

	/**
	 * The station owns no slots, so there is nowhere for a shift-click to move an item <em>to</em>.
	 * Returning EMPTY leaves the stack where it is — the alternative, calling through to a machine
	 * helper that assumes machine slots, would eat items.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return AbstractContainerMenu.stillValid(access, player, ModContent.TELEPORTER.get());
	}
}
