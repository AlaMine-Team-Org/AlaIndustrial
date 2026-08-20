package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The reactor controller's menu (MOD-468, stage 1) — a readout, not a workbench.
 *
 * <p>It carries no slots at all, following the two existing slotless machine menus (the charging
 * station and the electric heater): the controller is something the player reads, and giving it an
 * inventory would invite hoppers into a block that has nothing to hold.
 */
public class ReactorControllerMenu extends MachineMenu {

	/**
	 * Button ids for the control-rod throttle. The id IS the requested depth in percent, the same
	 * "the button id is the value" idiom the scrolling chests use for their top row — no payload of our
	 * own is needed to ask for a number the server is going to clamp anyway.
	 */
	public static final int BUTTON_DEPTH_BASE = 0;
	public static final int BUTTON_DEPTH_MAX = 100;

	/** Set on the server side only; the client stub has no block entity to act on. */
	@org.jspecify.annotations.Nullable
	private final ReactorControllerBlockEntity controller;

	/** Server side. */
	public ReactorControllerMenu(int syncId, Inventory playerInventory, MachineBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.REACTOR_CONTROLLER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(),
				access, ModContent.REACTOR_CONTROLLER.get());
		this.controller = be instanceof ReactorControllerBlockEntity c ? c : null;
	}

	/** Client side. The stub container is empty — no slots, and no upgrade block appended. */
	public ReactorControllerMenu(int syncId, Inventory playerInventory) {
		super(ModContent.REACTOR_CONTROLLER_MENU.get(), syncId, playerInventory, new SimpleContainer(0),
				new SimpleContainerData(ReactorControllerBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.REACTOR_CONTROLLER.get());
		this.controller = null;
	}

	/**
	 * Moves the control rods. The id is the requested depth in percent; anything outside 0…100 is
	 * ignored rather than clamped, because a value off that scale did not come from our screen.
	 */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (controller == null || id < BUTTON_DEPTH_BASE || id > BUTTON_DEPTH_MAX) {
			return false;
		}
		controller.setDepthPermille(id * 10);
		return true;
	}

	/**
	 * The panel is 16 px taller than the family default, so the player's inventory sits that much
	 * lower. The two numbers keep vanilla's own 58 px gap between the backpack and the hotbar — the
	 * art was drawn that way, and the slots are placed from here rather than read from the picture.
	 */
	@Override
	protected int playerInventoryY() {
		return 146;
	}

	@Override
	protected int hotbarY() {
		return 204;
	}

	/** None — see the class doc. */
	@Override
	protected void addMachineSlots() {
	}

	/**
	 * No upgrade panel. This must agree with {@link ReactorControllerBlockEntity#hasUpgradePanel()}:
	 * the slot indices are derived from it on both sides.
	 */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	/** Shift-clicking into a menu with no slots has nowhere to go; say so rather than misbehaving. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	public ReactorRoomStatus getStatus() {
		return ReactorRoomStatus.byOrdinal(data.get(ReactorControllerBlockEntity.DATA_STATUS));
	}

	/** Offset from the controller to the reported problem, in blocks (east/up/south positive). */
	public int getBreachDx() {
		return data.get(ReactorControllerBlockEntity.DATA_BREACH_DX);
	}

	public int getBreachDy() {
		return data.get(ReactorControllerBlockEntity.DATA_BREACH_DY);
	}

	public int getBreachDz() {
		return data.get(ReactorControllerBlockEntity.DATA_BREACH_DZ);
	}

	/** Interior extent measured by the last scan, in blocks; zero when the scan never got that far. */
	public int getSizeX() {
		return data.get(ReactorControllerBlockEntity.DATA_SIZE_X);
	}

	public int getSizeY() {
		return data.get(ReactorControllerBlockEntity.DATA_SIZE_Y);
	}

	public int getSizeZ() {
		return data.get(ReactorControllerBlockEntity.DATA_SIZE_Z);
	}

	/** Heat as a percentage of the scale — what the gauge fills to. */
	public int getHeatPercent() {
		return data.get(ReactorControllerBlockEntity.DATA_HEAT_PERCENT);
	}

	/** Rods burning across the whole room. */
	public int getRods() {
		return data.get(ReactorControllerBlockEntity.DATA_RODS);
	}

	/** Control-rod depth in percent — the throttle position. */
	public int getDepthPercent() {
		return data.get(ReactorControllerBlockEntity.DATA_DEPTH_PERCENT);
	}

	/** EU/t the last tick actually produced. */
	public int getOutput() {
		return data.get(ReactorControllerBlockEntity.DATA_OUTPUT);
	}

	/** Coolant left in the loop, 0…100. */
	public int getWaterPercent() {
		return data.get(ReactorControllerBlockEntity.DATA_WATER_PERCENT);
	}

	/** Coolant the reactor boiled last tick, in mB. */
	public int getWaterRate() {
		return data.get(ReactorControllerBlockEntity.DATA_WATER_RATE);
	}

	/** Why the reactor is idle, or {@code RUNNING} when it is not. */
	public dev.alaindustrial.block.entity.ReactorIdleReason getIdleReason() {
		return dev.alaindustrial.block.entity.ReactorIdleReason.byOrdinal(
				data.get(ReactorControllerBlockEntity.DATA_IDLE_REASON));
	}

	/** Charge in the reactor's own buffer, 0…100. */
	public int getStoredPercent() {
		return data.get(ReactorControllerBlockEntity.DATA_ENERGY_PERCENT);
	}

	/** Charge in EU, rounded to the nearest hundred — see the channel's note on 16-bit sync. */
	public int getStoredEu() {
		return data.get(ReactorControllerBlockEntity.DATA_ENERGY_HUNDREDS) * 100;
	}

	/** Steam waiting to be exhausted, 0…100. A full loop is a stalled one. */
	public int getSteamPercent() {
		return data.get(ReactorControllerBlockEntity.DATA_STEAM_PERCENT);
	}
}
