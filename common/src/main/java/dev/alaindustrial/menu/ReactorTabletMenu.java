package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.reactor.ReactorChannels;
import dev.alaindustrial.block.entity.reactor.ReactorTabletLink;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.misc.ReactorTabletItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The reactor controller's panel seen through a tier-1 tablet (MOD-941): every tab the controller shows, nothing it
 * lets the player change.
 *
 * <p><b>The same panel, not a copy.</b> The numbers are the controller's own channels, forwarded by
 * {@link LinkedData} while the tablet hears the reactor and zeros while it does not; the zone and log snapshots ride
 * {@link ReactorControllerMenu#broadcastChanges} exactly as at the controller. One more channel,
 * {@link #LINK_CHANNEL}, carries the {@link ReactorTabletLink.State}: anything but {@code LINKED} is drawn as
 * interference with the reason over it.
 *
 * <p><b>The link is asked every tick.</b> Walking out of range turns the panel to interference and walking back
 * restores it without reopening. The screen closes only when the tablet leaves the player's hands.
 *
 * <p><b>View-only, enforced by the server.</b> The screen hides the throttle and STOP, and {@link #clickMenuButton}
 * refuses the depth ids whatever a client sends; marking the log read is allowed. Remote control is a later tier,
 * with its own guard against bypassing the reactor's danger (MOD-947).
 */
public class ReactorTabletMenu extends ReactorControllerMenu {

	/** The link state's channel: one past the controller's own channels, so theirs keep their indices. */
	public static final int LINK_CHANNEL = ReactorChannels.COUNT;

	/** Channels this menu syncs: the controller's, then the link state. */
	public static final int DATA_COUNT = ReactorChannels.COUNT + 1;

	/** Ticks between two charge debits: one batched debit a second rather than a component write every tick. */
	private static final int SPEND_INTERVAL_TICKS = 20;

	/** Server side only; the client's stub holds the synced values. */
	private final @Nullable LinkedData linked;

	/** The player holding the tablet — the one whose hands {@link #stillValid} and the link look in. */
	private final Player holder;

	private int ticksSinceSpend;

	/** Client side. */
	public ReactorTabletMenu(int syncId, Inventory playerInventory) {
		super(ModContent.REACTOR_TABLET_MENU.get(), syncId, playerInventory, new SimpleContainerData(DATA_COUNT));
		this.linked = null;
		this.holder = playerInventory.player;
	}

	/** Server side: opened by the tablet; the first tick decides what it shows. */
	public static ReactorTabletMenu open(int syncId, Inventory playerInventory) {
		return new ReactorTabletMenu(syncId, playerInventory, new LinkedData());
	}

	private ReactorTabletMenu(int syncId, Inventory playerInventory, LinkedData data) {
		super(ModContent.REACTOR_TABLET_MENU.get(), syncId, playerInventory, data);
		this.linked = data;
		this.holder = playerInventory.player;
		resolveLink(holder);
	}

	@Override
	public boolean isViewOnly() {
		return true;
	}

	/** The link state as the screen reads it. */
	public ReactorTabletLink.State linkState() {
		return ReactorTabletLink.State.byOrdinal(data.get(LINK_CHANNEL));
	}

	/** Whether the panel shows the reactor right now; otherwise it shows interference and {@link #linkState}. */
	public boolean isLinked() {
		return linkState() == ReactorTabletLink.State.LINKED;
	}

	@Override
	public void broadcastChanges() {
		if (linked != null) {
			resolveLink(holder);
			spendCharge(holder);
		}
		super.broadcastChanges();
	}

	/** Asks the link and attaches the controller it names, or none. */
	private void resolveLink(Player player) {
		if (linked == null) {
			return;
		}
		ItemStack tablet = ReactorTabletItem.heldTablet(player);
		ReactorTabletLink.Result result = tablet.isEmpty()
				? new ReactorTabletLink.Result(ReactorTabletLink.State.UNBOUND, null)
				: ReactorTabletLink.resolve(player, tablet);
		ReactorControllerBlockEntity controller = result.state() == ReactorTabletLink.State.LINKED
				? result.controller() : null;
		linked.source = controller == null ? null : controller.getDataAccess();
		linked.link = result.state().ordinal();
		attach(controller);
	}

	/**
	 * Bills the tablet for the time its screen has been open, once a second: more while the reactor it shows runs
	 * hot. A flat tablet spends nothing more — it already shows "discharged".
	 */
	private void spendCharge(Player player) {
		if (++ticksSinceSpend < SPEND_INTERVAL_TICKS) {
			return;
		}
		ticksSinceSpend = 0;
		ItemStack tablet = ReactorTabletItem.heldTablet(player);
		if (tablet.isEmpty() || ItemEnergy.get(tablet) <= 0) {
			return;
		}
		boolean hot = linked != null && linked.hot;
		long perTick = hot ? ToolConfig.reactorTabletAlarmEuPerTick : ToolConfig.reactorTabletEuPerTick;
		ItemEnergy.spend(tablet, Math.min(ItemEnergy.get(tablet), perTick * SPEND_INTERVAL_TICKS), player);
	}

	@Override
	protected void attach(@Nullable ReactorControllerBlockEntity controller) {
		super.attach(controller);
		if (linked != null) {
			linked.hot = controller != null && controller.isRunningHot();
		}
	}

	/** Only the log's "seen" mark: the depth ids are refused here, whatever the client sends. */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id < BUTTON_LOG_SEEN_BASE) {
			return false;
		}
		return super.clickMenuButton(player, id);
	}

	/** Open while a tablet is in either hand; a lost link is interference, not a reason to close. */
	@Override
	public boolean stillValid(Player player) {
		return !ReactorTabletItem.heldTablet(player).isEmpty();
	}

	/** The controller's channels while linked, zeros otherwise; the link state on {@link #LINK_CHANNEL}. */
	private static final class LinkedData implements ContainerData {
		private @Nullable ContainerData source;
		private int link = ReactorTabletLink.State.UNBOUND.ordinal();
		private boolean hot;

		@Override
		public int get(int index) {
			if (index == LINK_CHANNEL) {
				return link;
			}
			ContainerData from = source;
			return from == null || index >= from.getCount() ? 0 : from.get(index);
		}

		/** The server never takes values from the client; nothing to set. */
		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	}
}
