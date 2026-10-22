package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity.Channel;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.settlement.drone.DispatcherStatus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the town dispatcher (MOD-858; owner's round 1): three sheet slots and the plan slot in a row with the
 * «assemble» button (shown on the screen's «Task» tab), the 27 buffer slots (its «Buffer» tab), the player's inventory
 * and the channels of {@link Channel}. Every slot is the town owner's (R95): for anybody else none takes or gives an
 * item — the server asks the block entity, a client the viewer's {@link Channel#CONTROL} channel.
 */
public class TownDispatcherMenu extends MachineMenu {

	/** The window (owner's round 1: bigger, the tabs down its left edge; round 2: wider, so the texts fit). */
	public static final int IMAGE_WIDTH = 256;
	public static final int IMAGE_HEIGHT = 250;
	/** Item position of the first sheet slot on the «Task» tab; the next two every {@link #SLOT_STEP} to the right. */
	public static final int SHEETS_X = 14;
	public static final int ROW_Y = 34;
	public static final int SLOT_STEP = 18;
	/** Item position of the plan slot, right of the «assemble» button. */
	public static final int PLAN_X = 152;
	/** Item position of the first buffer slot on the «Buffer» tab: three rows of nine, centred. */
	public static final int BUFFER_X = 47;
	public static final int BUFFER_Y = 34;
	/** The player's inventory: centred under the panel. */
	public static final int INVENTORY_X = 47;
	public static final int INVENTORY_Y = 168;
	public static final int HOTBAR_Y = 226;

	/** Button id of «assemble the plan». */
	public static final int BUTTON_ASSEMBLE = 0;
	/** Button id of «start the construction» on the «Task» tab. */
	public static final int BUTTON_START = 1;

	/** Server side: the dispatcher; null on the client. */
	@Nullable
	private final TownDispatcherBlockEntity dispatcher;
	/** Whether the plan row (the «Task» tab) and the buffer slots (the «Buffer» tab) are shown. Client side only. */
	private boolean planShown = true;
	private boolean bufferShown;
	/** The player looking at the menu. */
	private final Player viewer;

	/** Server side. */
	public TownDispatcherMenu(int syncId, Inventory playerInventory, TownDispatcherBlockEntity be, ContainerData data,
			ContainerLevelAccess access) {
		super(ModContent.TOWN_DISPATCHER_MENU.get(), syncId, playerInventory, be, data, access,
				ModContent.TOWN_DISPATCHER.get());
		this.dispatcher = be;
		this.viewer = playerInventory.player;
	}

	/** Client side. */
	public TownDispatcherMenu(int syncId, Inventory playerInventory) {
		super(ModContent.TOWN_DISPATCHER_MENU.get(), syncId, playerInventory,
				clientStub(TownDispatcherBlockEntity.SLOT_COUNT, TownDispatcherBlockEntity.DATA_COUNT),
				ModContent.TOWN_DISPATCHER.get());
		this.dispatcher = null;
		this.viewer = playerInventory.player;
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if ((buttonId != BUTTON_ASSEMBLE && buttonId != BUTTON_START) || dispatcher == null
				|| !(player instanceof ServerPlayer)) {
			return super.clickMenuButton(player, buttonId);
		}
		if (buttonId == BUTTON_ASSEMBLE) {
			dispatcher.assemble(player);
		} else {
			dispatcher.startConstruction(player);
		}
		return true;
	}

	/** Closes the screen of a stranger to the dispatcher's town (R98); see {@code SettlementCoreMenu#stillValid}. */
	@Override
	public boolean stillValid(Player player) {
		return super.stillValid(player) && (dispatcher == null || dispatcher.admits(player));
	}

	@Override
	protected void addMachineSlots() {
		Container container = machine;
		for (int i = 0; i < TownDispatcherBlockEntity.SHEET_SLOTS; i++) {
			addSlot(new OwnerSlot(container, TownDispatcherBlockEntity.SHEET_SLOT_FIRST + i,
					SHEETS_X + i * SLOT_STEP, ROW_Y, false));
		}
		addSlot(new OwnerSlot(container, TownDispatcherBlockEntity.PLAN_SLOT, PLAN_X, ROW_Y, false));
		for (int i = 0; i < TownDispatcherBlockEntity.BUFFER_SIZE; i++) {
			addSlot(new OwnerSlot(container, TownDispatcherBlockEntity.BUFFER_FIRST + i,
					BUFFER_X + (i % 9) * SLOT_STEP, BUFFER_Y + (i / 9) * SLOT_STEP, true));
		}
	}

	@Override
	protected int playerInventoryX() {
		return INVENTORY_X;
	}

	@Override
	protected int playerInventoryY() {
		return INVENTORY_Y;
	}

	@Override
	protected int hotbarY() {
		return HOTBAR_Y;
	}

	/** Whether {@code player} may take and put here (R95). */
	public boolean mayControl(Player player) {
		return dispatcher != null ? dispatcher.mayControl(player) : channel(Channel.CONTROL) != 0;
	}

	/** Client side: the screen shows the plan row on its «Task» tab and the buffer slots on its «Buffer» tab. */
	public void showSlots(boolean plan, boolean buffer) {
		this.planShown = plan;
		this.bufferShown = buffer;
	}

	public DispatcherStatus getStatus() {
		return DispatcherStatus.byOrdinal(channel(Channel.STATUS));
	}

	public int getNestLimit() {
		return channel(Channel.NEST_LIMIT);
	}

	public int getBusyNests() {
		return channel(Channel.BUSY_NESTS);
	}

	/** Nests with a drone over the town hall that work, at most the limit. */
	public int getReadyNests() {
		return channel(Channel.READY_NESTS);
	}

	/** The last assembly's refusal: {@link TownDispatcherBlockEntity#REASON_NONE}, a reason's ordinal + 1, … */
	public int getReason() {
		return channel(Channel.REASON);
	}

	/** The missing sheet numbers of the last refusal, one bit each. */
	public int getMissing() {
		return channel(Channel.MISSING);
	}

	/** Whether the viewer controls the dispatcher. */
	public boolean viewerControls() {
		return channel(Channel.CONTROL) != 0;
	}

	/** A slot only the town's owner fills and empties; a buffer slot hides with the «Buffer» tab. */
	private final class OwnerSlot extends Slot {
		private final boolean bufferSlot;

		OwnerSlot(Container container, int slot, int x, int y, boolean bufferSlot) {
			super(container, slot, x, y);
			this.bufferSlot = bufferSlot;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return mayControl(viewer) && TownDispatcherBlockEntity.fitsByHand(getContainerSlot(), stack);
		}

		@Override
		public boolean mayPickup(Player who) {
			return mayControl(who);
		}

		@Override
		public boolean isActive() {
			return dispatcher != null || (bufferSlot ? bufferShown : planShown);
		}
	}
}
