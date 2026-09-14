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
 * inventory would invite hoppers into a block that has nothing to hold. Since MOD-618 it carries no
 * player inventory either — see {@link #hasPlayerInventory()}.
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
		this.viewer = playerInventory.player;
	}

	/** Client side. The stub container is empty — no slots, and no upgrade block appended. */
	public ReactorControllerMenu(int syncId, Inventory playerInventory) {
		super(ModContent.REACTOR_CONTROLLER_MENU.get(), syncId, playerInventory, new SimpleContainer(0),
				new SimpleContainerData(ReactorControllerBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.REACTOR_CONTROLLER.get());
		this.controller = null;
		this.viewer = playerInventory.player;
	}

	/**
	 * Ticks between two zone snapshots for one open screen (MOD-620). One second: rods wear slowly and water moves a
	 * little each tick. A column placed or broken shows once the room scan has seen it, on that scan's own timer.
	 */
	private static final int ZONE_SYNC_INTERVAL_TICKS = 20;

	/** The player this menu is open for — the address of the zone packet. */
	private final Player viewer;

	/** Server side: when the next zone snapshot is due, and whether it changed since the last one sent. */
	private final dev.alaindustrial.core.ThrottledSnapshot<dev.alaindustrial.network.ReactorZonePayload> zoneSync =
			new dev.alaindustrial.core.ThrottledSnapshot<>(ZONE_SYNC_INTERVAL_TICKS);

	/** Client side: the latest zone snapshot, or {@code null} before the first one lands. */
	private dev.alaindustrial.network.@org.jspecify.annotations.Nullable ReactorZonePayload zone;

	/**
	 * Pushes the core, stack by stack, to this screen's viewer (MOD-620) — at most once a second, and only when it
	 * changed. A closed screen sends nothing: vanilla calls this only for an open menu. The client menu has no
	 * controller behind it and falls through.
	 */
	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (controller == null || !(viewer instanceof net.minecraft.server.level.ServerPlayer player)
				|| !zoneSync.due()) {
			return;
		}
		dev.alaindustrial.network.ReactorZonePayload next = controller.zoneSnapshot(containerId);
		if (zoneSync.changed(next)) {
			dev.alaindustrial.network.NetworkDispatcher.get().sendToPlayer(player, next);
		}
	}

	/** Client side: accepts a zone snapshot addressed to THIS menu; one for another screen is dropped. */
	public void acceptZone(dev.alaindustrial.network.ReactorZonePayload payload) {
		if (payload.containerId() == containerId) {
			this.zone = payload;
		}
	}

	/** Client side: the latest zone snapshot, or {@code null} before the first one arrives. */
	public dev.alaindustrial.network.@org.jspecify.annotations.Nullable ReactorZonePayload zone() {
		return zone;
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
	 * No player inventory (MOD-618). The controller holds nothing, so the rows had nothing to trade with:
	 * they took the lower half of the old panel and every click on them did nothing. Removed rather than
	 * parked off the panel, because a parked slot is still an active one.
	 */
	@Override
	protected boolean hasPlayerInventory() {
		return false;
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

	/** Whether the last scan got far enough to measure the interior box — the rays found all six walls (MOD-619). */
	public boolean isBoxMeasured() {
		return getSizeX() > 0;
	}

	/** Offset from the controller to the interior's west edge (smallest X), in blocks; see {@link #isBoxMeasured}. */
	public int getBoxWest() {
		return data.get(ReactorControllerBlockEntity.DATA_BOX_WEST);
	}

	/** Offset from the controller to the interior's north edge (smallest Z), in blocks. */
	public int getBoxNorth() {
		return data.get(ReactorControllerBlockEntity.DATA_BOX_NORTH);
	}

	/** Smallest interior edge this server's scan accepts, in blocks. */
	public int getRoomMinInner() {
		return data.get(ReactorControllerBlockEntity.DATA_ROOM_MIN_INNER);
	}

	/** Largest interior edge this server's scan accepts, in blocks. */
	public int getRoomMaxInner() {
		return data.get(ReactorControllerBlockEntity.DATA_ROOM_MAX_INNER);
	}

	/** Largest share of the shell, in percent, this server's scan lets be glass. */
	public int getRoomMaxGlassPercent() {
		return data.get(ReactorControllerBlockEntity.DATA_ROOM_MAX_GLASS);
	}

	/** Every hole the last scan found in the shell; zero unless the verdict is a breach (MOD-619). */
	public int getHoleCount() {
		return data.get(ReactorControllerBlockEntity.DATA_HOLE_COUNT);
	}

	/** How many holes arrive by position — at most {@code RoomScan.MAX_LISTED_HOLES}. */
	public int getListedHoles() {
		return Math.max(0, Math.min(getHoleCount(), dev.alaindustrial.core.structure.RoomScan.MAX_LISTED_HOLES));
	}

	/** Offset from the controller to a listed hole, east/up/south positive. */
	public int getHoleDx(int index) {
		return data.get(ReactorControllerBlockEntity.DATA_HOLE_FIRST + 3 * index);
	}

	public int getHoleDy(int index) {
		return data.get(ReactorControllerBlockEntity.DATA_HOLE_FIRST + 3 * index + 1);
	}

	public int getHoleDz(int index) {
		return data.get(ReactorControllerBlockEntity.DATA_HOLE_FIRST + 3 * index + 2);
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

	/**
	 * How much of the accident countdown is left, 0…100; zero when none is running (MOD-471).
	 *
	 * <p>A share rather than the seconds on purpose — see the channel's own note. The panel draws a bar
	 * from this, and a bar is exactly as much as the player is meant to know.
	 */
	public int getBlastPercent() {
		return data.get(ReactorControllerBlockEntity.DATA_BLAST_PERCENT);
	}

	/** A bare core's instability, 0…100. Zero for a sealed room, which runs on heat instead. */
	public int getInstabilityPercent() {
		return data.get(ReactorControllerBlockEntity.DATA_INSTABILITY);
	}

	/** Share of the reaction's heat the water carried last tick, 0…100 — the share of power paid (MOD-623). */
	public int getCoolantSharePercent() {
		return data.get(ReactorControllerBlockEntity.DATA_COOLANT_SHARE);
	}

	/** Heat from which the reactor reports running hot, in percent — this server's setting (MOD-618). */
	public int getHeatWarnPercent() {
		return data.get(ReactorControllerBlockEntity.DATA_HEAT_WARN);
	}

	/** Heat from which a sealed room melts its contents, in percent — this server's setting (MOD-618). */
	public int getMeltdownStartPercent() {
		return data.get(ReactorControllerBlockEntity.DATA_HEAT_MELTDOWN);
	}

	/** Whether the room is melting its own contents right now (MOD-469). */
	public boolean isMeltingDown() {
		return data.get(ReactorControllerBlockEntity.DATA_MELTDOWN) != 0;
	}

	/**
	 * Whether this controller is running on racks it found in the open (MOD-469).
	 *
	 * <p><b>Derived rather than carried on a channel of its own.</b> The server sets the rod count from
	 * the bare sweep exactly when it is running bare, and from the room sweep exactly when it is not, so
	 * "not sealed and yet counting rods" already means bare and nothing else can. A second channel
	 * saying the same thing would be one more index to keep in step for no new information — and the
	 * distinction the panel actually needs is between a shell being BUILT (no racks in reach) and a
	 * reactor being RUN without one, which this answers exactly.
	 */
	public boolean isBare() {
		return getStatus() != dev.alaindustrial.block.entity.ReactorRoomStatus.FORMED && getRods() > 0;
	}
}
