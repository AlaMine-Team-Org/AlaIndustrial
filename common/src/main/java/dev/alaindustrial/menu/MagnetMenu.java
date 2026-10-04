package dev.alaindustrial.menu;

import dev.alaindustrial.item.module.ItemModules;
import dev.alaindustrial.item.module.ModuleHost;
import dev.alaindustrial.item.tool.MagnetFilter;
import dev.alaindustrial.item.tool.MagnetFilterModuleItem;
import dev.alaindustrial.item.tool.MagnetItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The electromagnet's own screen (MOD-592): charge, the on/off switch, the module slots and — when a
 * filter module is fitted — its sixteen sample cells, over the player's inventory.
 *
 * <h2>Where the magnet is</h2>
 * Nothing travels in the open packet (that needs a different API on each loader): both sides find the
 * magnet in the player's hand the same way ({@link #heldMagnetSlot}) and hold on to that inventory slot
 * for the life of the screen. The magnet itself is locked in place while the screen is open — it cannot
 * be picked up, thrown, swapped with a number key or with the off-hand key — because the module slots
 * write into that very stack, and a magnet moved from under them would either lose its modules or
 * duplicate them.
 *
 * <h2>Module slots</h2>
 * Real slots over a small container that is written back onto the magnet ({@link ItemModules}) on every
 * change. There are always {@link #MAX_MODULE_SLOTS} of them so slot indices never depend on the grade;
 * the ones the grade does not have are inactive, which hides them from clicks, from shift-click and
 * from vanilla's slot rendering.
 *
 * <h2>Filter cells</h2>
 * Ghost slots: they show the samples of the fitted filter and never hold an item. A click on one is not
 * a slot click — the screen turns it into a container button and the server reads the item off the
 * cursor (the Assembler's pattern grid does the same). Recipe viewers drop a sample through
 * {@code MagnetFilterSamplePayload}, which ends in {@link #setCell}.
 */
public class MagnetMenu extends AbstractContainerMenu {

	/** Module slots the menu always has; the grade decides how many are active. */
	public static final int MAX_MODULE_SLOTS = 3;

	// --- layout, in screen pixels; the atlas (tools/gen_magnet_gui.py) is drawn to the same numbers ---
	public static final int MODULE_X = 26;
	public static final int MODULE_Y = 18;
	public static final int CELL_X = 52;
	public static final int CELL_Y = 18;
	public static final int CELL_COLUMNS = 4;
	public static final int STRIDE = 18;
	public static final int INV_X = 8;
	public static final int INV_Y = 104;
	public static final int HOTBAR_Y = 162;

	// --- slot index ranges ---
	public static final int MODULE_SLOT_START = 0;
	public static final int PLAYER_SLOT_START = MODULE_SLOT_START + MAX_MODULE_SLOTS;
	public static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 36;
	public static final int CELL_SLOT_START = PLAYER_SLOT_END;

	// --- container buttons. Part of the wire format: append, never renumber. ---
	/** {@code BUTTON_CELL + i}: set cell {@code i} from the cursor; an empty cursor clears it. */
	public static final int BUTTON_CELL = 0;
	/** {@code BUTTON_CELL_TAG + i}: in category mode, move cell {@code i} to its item's next tag. */
	public static final int BUTTON_CELL_TAG = 32;
	public static final int BUTTON_POWER = 64;
	public static final int BUTTON_LIST_MODE = 65;
	public static final int BUTTON_MATCH = 66;

	private final Player player;
	/** Inventory slot of the magnet this screen controls, or -1 when none was in hand. */
	private final int magnetSlot;
	private final SimpleContainer modules = new SimpleContainer(MAX_MODULE_SLOTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			writeBack();
		}
	};
	/** Guards the write-back while the container is being filled from the stack. */
	private boolean loading;

	public MagnetMenu(int syncId, Inventory inventory) {
		super(ModContent.MAGNET_MENU.get(), syncId);
		this.player = inventory.player;
		this.magnetSlot = heldMagnetSlot(player);

		loading = true;
		ItemModules fitted = ItemModules.of(host());
		for (int i = 0; i < MAX_MODULE_SLOTS; i++) {
			modules.setItem(i, fitted.get(i));
		}
		loading = false;

		for (int i = 0; i < MAX_MODULE_SLOTS; i++) {
			addSlot(new ModuleSlot(this, i, MODULE_X, MODULE_Y + i * STRIDE));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addPlayerSlot(inventory, col + row * 9 + 9, INV_X + col * STRIDE, INV_Y + row * STRIDE);
			}
		}
		for (int col = 0; col < 9; col++) {
			addPlayerSlot(inventory, col, INV_X + col * STRIDE, HOTBAR_Y);
		}
		Container cells = new CellView(this);
		for (int i = 0; i < MagnetFilter.CELLS; i++) {
			addSlot(new CellSlot(this, cells, i,
					CELL_X + (i % CELL_COLUMNS) * STRIDE, CELL_Y + (i / CELL_COLUMNS) * STRIDE));
		}
	}

	private void addPlayerSlot(Inventory inventory, int index, int x, int y) {
		addSlot(index == magnetSlot ? new LockedSlot(inventory, index, x, y) : new Slot(inventory, index, x, y));
	}

	/**
	 * Inventory slot of the magnet in the player's hand: the main hand first, then the off-hand, or -1.
	 * The same rule the right-click follows, so the screen always shows the magnet that opened it.
	 */
	public static int heldMagnetSlot(Player player) {
		Inventory inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		if (inventory.getItem(selected).getItem() instanceof MagnetItem) {
			return selected;
		}
		return inventory.getItem(Inventory.SLOT_OFFHAND).getItem() instanceof MagnetItem ? Inventory.SLOT_OFFHAND : -1;
	}

	/** The magnet this screen controls — the live stack — or empty once it is gone. */
	public ItemStack host() {
		if (magnetSlot < 0) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = player.getInventory().getItem(magnetSlot);
		return stack.getItem() instanceof MagnetItem ? stack : ItemStack.EMPTY;
	}

	/** How many module slots the magnet has (0 when it is gone). */
	public int activeModuleSlots() {
		ItemStack host = host();
		return host.getItem() instanceof ModuleHost h ? Math.min(MAX_MODULE_SLOTS, h.moduleSlots(host)) : 0;
	}

	/** Copy the module container onto the magnet. Every change goes through here, at once. */
	private void writeBack() {
		ItemStack host = host();
		if (loading || host.isEmpty()) {
			return;
		}
		ItemModules next = ItemModules.EMPTY;
		for (int i = 0; i < MAX_MODULE_SLOTS; i++) {
			next = next.with(i, modules.getItem(i));
		}
		ItemModules.set(host, next);
		// The off-hand is not one of this menu's slots; the player's own menu carries it to the client.
		if (magnetSlot == Inventory.SLOT_OFFHAND && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.inventoryMenu.broadcastChanges();
		}
	}

	// --- the filter ---------------------------------------------------------------------------

	/** Module-slot index of the fitted filter, or -1. */
	public int filterSlot() {
		for (int i = 0; i < activeModuleSlots(); i++) {
			if (modules.getItem(i).getItem() instanceof MagnetFilterModuleItem) {
				return i;
			}
		}
		return -1;
	}

	/** The fitted filter's settings, or {@code null} when no filter is fitted. */
	@Nullable
	public MagnetFilter filter() {
		int slot = filterSlot();
		return slot < 0 ? null : MagnetFilter.of(modules.getItem(slot));
	}

	private void editFilter(java.util.function.UnaryOperator<MagnetFilter> edit) {
		int slot = filterSlot();
		if (slot < 0) {
			return;
		}
		ItemStack module = modules.getItem(slot);
		MagnetFilter.set(module, edit.apply(MagnetFilter.of(module)));
		modules.setChanged();
	}

	/** Put {@code sample} into cell {@code cell} (empty clears it). The recipe viewers end up here too. */
	public void setCell(int cell, ItemStack sample) {
		if (cell >= 0 && cell < MagnetFilter.CELLS) {
			editFilter(f -> f.withCell(cell, sample));
		}
	}

	/** Same, by item id — what a recipe viewer's drop sends. An unknown id is ignored. */
	public void setCell(int cell, String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		@Nullable Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		if (item != null) {
			setCell(cell, new ItemStack(item));
		}
	}

	@Override
	public boolean clickMenuButton(Player clicker, int buttonId) {
		if (host().isEmpty()) {
			return false;
		}
		if (buttonId >= BUTTON_CELL && buttonId < BUTTON_CELL + MagnetFilter.CELLS) {
			setCell(buttonId - BUTTON_CELL, getCarried());
			return true;
		}
		if (buttonId >= BUTTON_CELL_TAG && buttonId < BUTTON_CELL_TAG + MagnetFilter.CELLS) {
			MagnetFilter filter = filter();
			if (filter != null && filter.match() == MagnetFilter.Match.TAG) {
				int cell = buttonId - BUTTON_CELL_TAG;
				editFilter(f -> f.withNextTag(cell));
			}
			return true;
		}
		switch (buttonId) {
			case BUTTON_POWER -> {
				ItemStack host = host();
				boolean on = !MagnetItem.isEnabled(host);
				MagnetItem.setEnabled(host, on);
				clicker.level().playSound(null, clicker.blockPosition(),
						on ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
						SoundSource.PLAYERS, 0.7f, on ? 1.15f : 0.9f);
				if (magnetSlot == Inventory.SLOT_OFFHAND && clicker instanceof ServerPlayer serverPlayer) {
					serverPlayer.inventoryMenu.broadcastChanges();
				}
			}
			case BUTTON_LIST_MODE -> editFilter(f -> f.withAllowList(!f.allowList()));
			case BUTTON_MATCH -> editFilter(f -> f.withMatch(f.match().next()));
			default -> {
				return false;
			}
		}
		return true;
	}

	// --- clicks: the magnet stays put ---------------------------------------------------------

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
		if (input == ContainerInput.SWAP && swapTouchesMagnet(slotId, button)) {
			return;
		}
		if (slotId >= CELL_SLOT_START && slotId < CELL_SLOT_START + MagnetFilter.CELLS) {
			// Cells are edited only through container buttons; a slot click on one does nothing.
			return;
		}
		super.clicked(slotId, button, input, clicker);
	}

	/** A number-key or off-hand-key swap that would move the magnet in or out of its slot. */
	private boolean swapTouchesMagnet(int slotId, int button) {
		if (magnetSlot < 0) {
			return false;
		}
		if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof LockedSlot) {
			return true;
		}
		// button is the hotbar index (0-8), or 40 for the off-hand key.
		return button == magnetSlot || (button == Inventory.SLOT_OFFHAND && magnetSlot == Inventory.SLOT_OFFHAND);
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return !(slot instanceof CellSlot) && !(slot instanceof LockedSlot) && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return !(slot instanceof CellSlot) && !(slot instanceof LockedSlot) && super.canDragTo(slot);
	}

	@Override
	public ItemStack quickMoveStack(Player clicker, int index) {
		if (index < 0 || index >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(index);
		if (!slot.hasItem() || !slot.mayPickup(clicker) || slot instanceof CellSlot) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < PLAYER_SLOT_START) {
			if (!moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, MODULE_SLOT_START, PLAYER_SLOT_START, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public boolean stillValid(Player clicker) {
		return !host().isEmpty();
	}

	// --- slots --------------------------------------------------------------------------------

	/** A module slot: takes what the magnet accepts, one per slot, only while the magnet is there. */
	public static final class ModuleSlot extends Slot {
		private final MagnetMenu menu;

		ModuleSlot(MagnetMenu menu, int index, int x, int y) {
			super(menu.modules, index, x, y);
			this.menu = menu;
		}

		@Override
		public boolean isActive() {
			return getContainerSlot() < menu.activeModuleSlots();
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			ItemStack host = menu.host();
			if (!isActive() || !(host.getItem() instanceof ModuleHost h)) {
				return false;
			}
			ItemModules fitted = ItemModules.EMPTY;
			for (int i = 0; i < MAX_MODULE_SLOTS; i++) {
				fitted = fitted.with(i, menu.modules.getItem(i));
			}
			return h.acceptsModule(host, fitted, getContainerSlot(), stack);
		}

		@Override
		public boolean mayPickup(Player player) {
			return !menu.host().isEmpty();
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}

	/** The magnet's own inventory slot: shown, but it cannot be taken, replaced or swapped away. */
	public static final class LockedSlot extends Slot {
		LockedSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}

	/** A filter sample cell: shows a copy, never holds an item, hidden while no filter is fitted. */
	public static final class CellSlot extends Slot {
		private final MagnetMenu menu;

		CellSlot(MagnetMenu menu, Container cells, int index, int x, int y) {
			super(cells, index, x, y);
			this.menu = menu;
		}

		@Override
		public boolean isActive() {
			return menu.filterSlot() >= 0;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}
	}

	/** Read-only view of the fitted filter's cells, as one-item stacks. */
	private static final class CellView extends SimpleContainer {
		private final MagnetMenu menu;

		CellView(MagnetMenu menu) {
			super(MagnetFilter.CELLS);
			this.menu = menu;
		}

		@Override
		public ItemStack getItem(int index) {
			MagnetFilter filter = menu.filter();
			return filter == null ? ItemStack.EMPTY : filter.cell(index).stack();
		}

		@Override
		public void setItem(int index, ItemStack stack) {
			// The server's copy of a cell arrives here on the client; the cell is derived from the
			// filter module's own stack, which arrives with it, so there is nothing to store.
		}

		@Override
		public ItemStack removeItem(int index, int count) {
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItemNoUpdate(int index) {
			return ItemStack.EMPTY;
		}
	}
}
