package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.SettlementTier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the settlement core (MOD-831) — a readout, not a container.
 *
 * <p>No slots and no player inventory, the reactor controller's choice (MOD-618): the core holds nothing, so the
 * inventory rows would only be rows whose every click does nothing. Every channel is computed on the server
 * ({@link SettlementCoreBlockEntity.Channel}), the owner relation per viewer — the server knows who is looking,
 * the client does not know who owns the town. Claiming the town happens on the server when the screen of a lit
 * core is opened ({@link SettlementCoreBlockEntity#createMenu}). The one thing to press is "show borders"
 * (MOD-843), answered with a personal border payload; which borders a player sees is client state.
 */
public class SettlementCoreMenu extends MachineMenu {

	/** The menu-button id of "show borders" (MOD-843). */
	public static final int BUTTON_TOGGLE_BORDERS = 0;

	/** The core on the server side; null on the client, which has no block position. */
	@Nullable
	private final SettlementCoreBlockEntity core;

	/** Server side: the channels as THIS viewer sees them, so the owner row can say "you". */
	public SettlementCoreMenu(int syncId, Inventory playerInventory, SettlementCoreBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.SETTLEMENT_CORE_MENU.get(), syncId, playerInventory, be,
				be.viewerData(playerInventory.player.getUUID()), access, ModContent.SETTLEMENT_CORE.get());
		this.core = be;
	}

	/** Client side: no slots and no upgrade block, matching the {@code NoUpgradePanel} marker on the block entity. */
	public SettlementCoreMenu(int syncId, Inventory playerInventory) {
		super(ModContent.SETTLEMENT_CORE_MENU.get(), syncId, playerInventory,
				clientStub(0, SettlementCoreBlockEntity.DATA_COUNT), ModContent.SETTLEMENT_CORE.get());
		this.core = null;
	}

	/** "Show borders": answer the asking player, and only them, with the town's territory (MOD-843). */
	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId != BUTTON_TOGGLE_BORDERS || core == null || !(player instanceof ServerPlayer serverPlayer)) {
			return super.clickMenuButton(player, buttonId);
		}
		core.sendBorder(serverPlayer, true);
		return true;
	}

	/**
	 * Closes the screen of a player who is a stranger to the core's town (R98): the server answers no click or button
	 * of a menu that is not valid, and closes it on the next tick. Client side the core is unknown and the base
	 * decides.
	 */
	@Override
	public boolean stillValid(Player player) {
		return super.stillValid(player) && (core == null || core.admits(player));
	}

	/** None — see the class doc. */
	@Override
	protected void addMachineSlots() {
	}

	/** No player inventory (MOD-618's reasoning): the core holds nothing to trade with. */
	@Override
	protected boolean hasPlayerInventory() {
		return false;
	}

	/** Nothing to move: no slots on either side. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	/** Whether the core is lit and its town holds its territory. */
	public boolean isActive() {
		return channel(SettlementCoreBlockEntity.Channel.ACTIVE) != 0;
	}

	/** Whether this is a second core of a town whose core stands elsewhere: dark, founding no town (R43). */
	public boolean isMisplaced() {
		return channel(SettlementCoreBlockEntity.Channel.MISPLACED) == SettlementCoreBlockEntity.PLACE_SECOND;
	}

	/** Whether this is a player's core outside every territory, of no town: it works only in a town hall (R53). */
	public boolean isUnbound() {
		return channel(SettlementCoreBlockEntity.Channel.MISPLACED) == SettlementCoreBlockEntity.PLACE_NONE;
	}

	public SettlementTier getTier() {
		return SettlementTier.byOrdinal(channel(SettlementCoreBlockEntity.Channel.TIER));
	}

	/** The territory's width along X in blocks (MOD-848: a structure's town stretches over its buildings). */
	public int getTerritoryWidth() {
		return channel(SettlementCoreBlockEntity.Channel.TERRITORY_X);
	}

	/** The territory's depth along Z in blocks. */
	public int getTerritoryDepth() {
		return channel(SettlementCoreBlockEntity.Channel.TERRITORY_Z);
	}

	public int getHouseLimit() {
		return channel(SettlementCoreBlockEntity.Channel.HOUSE_LIMIT);
	}

	public int getWorkerLimit() {
		return channel(SettlementCoreBlockEntity.Channel.WORKER_LIMIT);
	}

	/** EU per tick the core draws at its tier while lit. */
	public int getUpkeep() {
		return channel(SettlementCoreBlockEntity.Channel.UPKEEP);
	}

	/**
	 * The core this menu was opened on — server side only; null on the client. A roster request takes its core from
	 * here (MOD-854), so the client never names a position the server would have to trust.
	 */
	@Nullable
	public SettlementCoreBlockEntity core() {
		return core;
	}

	/** {@code SettlementCoreBlockEntity.OWNER_NONE}, {@code OWNER_VIEWER} or {@code OWNER_OTHER}. */
	public int getOwnerRelation() {
		return channel(SettlementCoreBlockEntity.Channel.OWNER);
	}
}
