package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity.Channel;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import dev.alaindustrial.settlement.drone.DispatcherStatus;
import dev.alaindustrial.settlement.drone.DispatcherView;
import dev.alaindustrial.settlement.drone.RoadWork;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the town dispatcher (MOD-858; owner's round 1): three sheet slots and the plan slot in a row with the
 * «assemble» button (shown on the screen's «Task» tab), the 27 buffer slots (its «Buffer» tab), the player's inventory
 * and the channels of {@link Channel}. Every slot is the town owner's (R95): for anybody else none takes or gives an
 * item — the server asks the block entity, a client the viewer's {@link Channel#CONTROL} channel.
 */
public class TownDispatcherMenu extends MachineMenu {

	/**
	 * The window (owner's round 1: bigger, the tabs down its left edge; round 2: wider, so the texts fit; round 3,
	 * MOD-873: no taller than 240, the height of a 1280 x 720 screen at interface scale 3).
	 */
	public static final int IMAGE_WIDTH = 256;
	public static final int IMAGE_HEIGHT = 240;
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
	public static final int INVENTORY_Y = 158;
	public static final int HOTBAR_Y = 216;

	/** Button id of «assemble the plan». */
	public static final int BUTTON_ASSEMBLE = 0;
	/** Button id of «start the construction» on the «Task» tab. */
	public static final int BUTTON_START = 1;
	/**
	 * Button ids that tell the server the «Materials» tab opened or closed (owner's round 2 of 0.1.219, R2-1): while it
	 * is open, a shift-click in the inventory puts only what the stage still asks for into the buffer.
	 */
	public static final int BUTTON_MATERIALS_SHOWN = 2;
	public static final int BUTTON_MATERIALS_HIDDEN = 3;
	/**
	 * The «Orders» tab's presses (round 2 of 0.1.219, Q3): {@code BUTTON_ORDER_BASE + building * 4 + press}, the press
	 * a {@link TownDispatcherBlockEntity.OrderPress} ordinal.
	 */
	public static final int BUTTON_ORDER_BASE = 100;

	/** The button id of {@code press} on the order of building {@code building}. */
	public static int orderButton(int building, TownDispatcherBlockEntity.OrderPress press) {
		return BUTTON_ORDER_BASE + building * TownDispatcherBlockEntity.OrderPress.values().length + press.ordinal();
	}

	/** Server side: the dispatcher; null on the client. */
	@Nullable
	private final TownDispatcherBlockEntity dispatcher;
	/** Whether the plan row (the «Task» tab) and the buffer slots (the «Buffer» tab) are shown. Client side only. */
	private boolean planShown = true;
	private boolean bufferShown;
	/** Whether the «Materials» tab is open: the server learns it by button, the client from its screen. */
	private boolean materialsShown;
	/** The current stage's bill: the dispatcher's view on the server, the screen's synced view on the client. */
	private Supplier<List<DispatcherView.BillLine>> bill = List::of;
	/** The player looking at the menu. */
	private final Player viewer;

	/** Server side. */
	public TownDispatcherMenu(int syncId, Inventory playerInventory, TownDispatcherBlockEntity be, ContainerData data,
			ContainerLevelAccess access) {
		super(ModContent.TOWN_DISPATCHER_MENU.get(), syncId, playerInventory, be, data, access,
				ModContent.TOWN_DISPATCHER.get());
		this.dispatcher = be;
		this.viewer = playerInventory.player;
		this.bill = () -> be.view().bill();
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
		if (buttonId == BUTTON_MATERIALS_SHOWN || buttonId == BUTTON_MATERIALS_HIDDEN) {
			materialsShown = buttonId == BUTTON_MATERIALS_SHOWN;
			return true;
		}
		if (buttonId >= BUTTON_ORDER_BASE && dispatcher != null && player instanceof ServerPlayer) {
			TownDispatcherBlockEntity.OrderPress[] presses = TownDispatcherBlockEntity.OrderPress.values();
			int relative = buttonId - BUTTON_ORDER_BASE;
			dispatcher.pressOrder(player, relative / presses.length, presses[relative % presses.length]);
			return true;
		}
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

	/**
	 * Shift-click with the «Materials» tab open (owner's round 2 of 0.1.219, R2-1): a stack from the inventory puts
	 * into the buffer only what the current stage still asks for — its need less what the buffer already holds — and
	 * the rest stays with the player; an item the stage does not ask for stays put. Other tabs and the dispatcher's own
	 * slots keep the usual shift-click. {@link #moveItemStackTo} still asks every slot's {@code mayPlace}.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		int invStart = baseSlotCount() + (hasUpgradePanel() ? UPGRADE_SLOT_COUNT : 0);
		if (!materialsShown || index < invStart || index >= invStart + 36) {
			return super.quickMoveStack(player, index);
		}
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		int wanted = stillWanted(stack);
		if (wanted <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack part = stack.copyWithCount(Math.min(wanted, stack.getCount()));
		moveItemStackTo(part, TownDispatcherBlockEntity.BUFFER_FIRST, TownDispatcherBlockEntity.SLOT_COUNT, false);
		int moved = Math.min(wanted, stack.getCount()) - part.getCount();
		if (moved <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack result = stack.copy();
		stack.shrink(moved);
		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return result;
	}

	/** How many more of {@code stack}'s item the bill asks for than the buffer slots hold now; 0 if none. */
	public int stillWanted(ItemStack stack) {
		int wanted = 0;
		for (DispatcherView.BillLine line : bill.get()) {
			if (!answers(line.item(), stack)) {
				continue;
			}
			int held = 0;
			for (int i = TownDispatcherBlockEntity.BUFFER_FIRST; i < TownDispatcherBlockEntity.SLOT_COUNT; i++) {
				ItemStack inBuffer = slots.get(i).getItem();
				if (!inBuffer.isEmpty() && answers(line.item(), inBuffer)) {
					held += inBuffer.getCount();
				}
			}
			wanted += Math.max(0, line.need() - held);
		}
		return wanted;
	}

	/** Whether {@code stack} counts toward a bill line of {@code item}: the fill line takes any earth or stone. */
	private static boolean answers(String item, ItemStack stack) {
		if (item.equals(RoadWork.FILL_ITEM)) {
			return stack.is(ModTags.Items.TOWN_SITE_FILL) && stack.getItem() instanceof BlockItem;
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item);
	}

	/** Client side: whether the screen's «Materials» tab is open; the screen tells the server by button. */
	public void showMaterials(boolean shown) {
		this.materialsShown = shown;
	}

	/** Client side: where the bill comes from — the screen's synced view of the dispatcher. */
	public void billSource(Supplier<List<DispatcherView.BillLine>> source) {
		this.bill = source;
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
