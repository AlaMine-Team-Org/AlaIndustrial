package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.registry.ModContent;
import java.util.function.BooleanSupplier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Shared menu base for Industrialization machines. Adds the machine's slots (subclass-defined) plus the
 * player inventory, and binds the machine's {@link ContainerData} so energy + progress sync to
 * the client automatically. Subclasses provide a server constructor (real block entity) and a
 * client constructor (dummy container + data the vanilla sync fills in).
 */
public abstract class MachineMenu extends AbstractContainerMenu {
	/** Upgrade slots appended after the machine slots (MOD-080); mirrors {@link MachineBlockEntity#UPGRADE_SLOT_COUNT}. */
	public static final int UPGRADE_SLOT_COUNT = MachineBlockEntity.UPGRADE_SLOT_COUNT;

	// Upgrade-panel geometry, shared by the menu (slot hit-boxes) and the screen (panel blit). The panel
	// is drawn to the right of the 176-wide GUI; PANEL_X/Y are its top-left corner in screen space,
	// relative to leftPos/topPos. Slot item positions come from the 256×256 upgrades atlas (panel origin
	// 97,0): item xy = PANEL + (slotAtlas − panelOrigin) + 1 (the +1 steps inside the 18×18 slot frame).
	public static final int PANEL_X = 176 - 4;
	public static final int PANEL_Y = 4;

	/**
	 * The panel's docked left anchor, relative to {@code leftPos}. Default {@link #PANEL_X} fits the
	 * standard 176px frames; a wider GUI (the 200px distillation column, MOD-251) overrides this so
	 * the gear tab and panel dock to ITS right edge instead of floating inside the frame.
	 */
	public int panelAnchorX() {
		return PANEL_X;
	}

	/** Item (inner) x,y of upgrade slot {@code i}, relative to leftPos/topPos (anchor-aware). */
	private int[] upgradeSlotXY(int i) {
		int px = panelAnchorX();
		return switch (i) {
			case 0 -> new int[] { px + 17, PANEL_Y + 66 };   // LEFT (active, mute chip; atlas 113,65)
			case 1 -> new int[] { px + 71, PANEL_Y + 16 };   // TOP (locked; atlas 167,15)
			case 2 -> new int[] { px + 125, PANEL_Y + 66 };  // RIGHT (locked; atlas 221,65)
			default -> new int[] { px + 71, PANEL_Y + 115 }; // BOTTOM (locked; atlas 167,114)
		};
	}

	protected final Container machine;
	protected final ContainerData data;
	private final ContainerLevelAccess access;
	private final Block block;
	/** Client-only: whether the upgrade panel is expanded. The screen toggles it; the server ignores it. */
	private boolean panelOpen;

	protected MachineMenu(MenuType<?> type, int syncId, Inventory playerInventory,
			Container machine, ContainerData data, ContainerLevelAccess access, Block block) {
		super(type, syncId);
		this.machine = machine;
		this.data = data;
		this.access = access;
		this.block = block;
		addMachineSlots();
		addUpgradeSlots();
		addPlayerInventory(playerInventory);
		addDataSlots(data);
	}

	/** Add the machine's own slots (slot indices 0..machineSize-1). */
	protected abstract void addMachineSlots();

	/**
	 * Number of machine-specific slots (excludes upgrade slots). Derived from the container so it is the
	 * same client- and server-side: every {@code MachineMenu} container is sized {@code base + N} slots.
	 */
	protected final int baseSlotCount() {
		return machine.getContainerSize() - UPGRADE_SLOT_COUNT;
	}

	/**
	 * The menu slot bound to the machine container's slot {@code containerSlot} (the {@code *_SLOT}
	 * constants on the block entity), or {@code null} if this menu does not show it.
	 *
	 * <p><b>Why this exists instead of {@code slots.get(index)}.</b> The menu's slot list is in
	 * <em>insertion</em> order, which is not the container's slot order: {@code GalvanicBathMenu} adds
	 * the bucket pair before the fibre and silver slots, so {@code slots.get(FIBER_SLOT)} — index 0 —
	 * hands back the <em>fill</em> slot instead. Matching on {@link Slot#getContainerSlot()} asks the
	 * question the caller actually means, and the container identity check keeps a player-inventory
	 * slot (whose indices run 0..35 over a different container) from answering for a machine slot.
	 */
	public final Slot machineSlot(int containerSlot) {
		for (Slot slot : this.slots) {
			if (slot.container == machine && slot.getContainerSlot() == containerSlot) {
				return slot;
			}
		}
		return null;
	}

	/**
	 * Append the shared upgrade slots (MOD-080) at the tail of the container, laid out as a cross around
	 * the panel. Only slot 0 (LEFT) is active on the MVP panel and accepts a mute chip; slots 1–3 are
	 * present but never accept an item. All are hidden (inactive) while the panel is collapsed.
	 */
	private void addUpgradeSlots() {
		int start = baseSlotCount();
		if (start < 0) {
			return; // defensive: a container smaller than the upgrade block (should not happen)
		}
		for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
			boolean active = (i == MachineBlockEntity.ACTIVE_UPGRADE_INDEX);
			int[] xy = upgradeSlotXY(i);
			addSlot(new UpgradeSlot(machine, start + i, xy[0], xy[1],
					this::isPanelOpen, active));
		}
	}

	/**
	 * Move the upgrade slots by (dx, dy) from their docked positions (client-only, MOD-080 draggable
	 * panel). {@link Slot#x}/{@link Slot#y} are {@code final}, so each slot is rebuilt at the new
	 * position and swapped into {@link #slots} — the item lives in the container, not the slot, so
	 * nothing is lost. The server never calls this (it does not render).
	 */
	public void repositionUpgradeSlots(int dx, int dy) {
		int start = baseSlotCount();
		if (start < 0) {
			return;
		}
		for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
			int menuIndex = start + i;
			boolean active = (i == MachineBlockEntity.ACTIVE_UPGRADE_INDEX);
			int[] xy = upgradeSlotXY(i);
			UpgradeSlot moved = new UpgradeSlot(machine, menuIndex,
					xy[0] + dx, xy[1] + dy, this::isPanelOpen, active);
			moved.index = menuIndex;
			slots.set(menuIndex, moved);
		}
	}

	/** Client-only upgrade-panel state (see {@link #panelOpen}). */
	public boolean isPanelOpen() {
		return panelOpen;
	}

	/** Toggle the upgrade panel (screen-driven); returns the new state. */
	public boolean togglePanel() {
		panelOpen = !panelOpen;
		return panelOpen;
	}

	private void addPlayerInventory(Inventory inventory) {
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, 9 + row * 9 + col,
						playerInventoryX() + col * 18, playerInventoryY() + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, playerInventoryX() + col * 18, hotbarY()));
		}
	}

	protected int playerInventoryX() {
		return 8;
	}

	protected int playerInventoryY() {
		return 84;
	}

	protected int hotbarY() {
		return 142;
	}

	/**
	 * How many sync channels this menu is bound to. The <b>client</b> constructor of every machine menu
	 * sizes its {@code SimpleContainerData} from the block entity's {@code DATA_COUNT}, so this must equal
	 * {@code MachineBlockEntity#getDataAccess().getCount()} for the machine the menu belongs to; a stub
	 * narrower than the block entity throws when the screen reads the missing channel (the MOD-234 crash),
	 * a wider one silently reads stale zeros. Asserted for every menu by {@code MenuDataWidthScenarios}
	 * (MOD-235).
	 */
	public final int getDataChannelCount() {
		return data.getCount();
	}

	public int getEnergy() {
		return data.get(0);
	}

	public int getCapacity() {
		return data.get(1);
	}

	public int getProgress() {
		return data.get(2);
	}

	public int getMaxProgress() {
		return data.get(3);
	}

	/**
	 * Visual regression test helper — injects ContainerData without a server-side block entity.
	 * Only call this from client-game-test code; the values are overwritten on any real sync packet.
	 */
	public void injectTestData(int energy, int capacity, int progress, int maxProgress) {
		data.set(0, energy);
		data.set(1, capacity);
		data.set(2, progress);
		data.set(3, maxProgress);
	}

	/**
	 * Test-only companion to {@link #injectTestData}: sets one channel past the shared four, for the
	 * machines that add their own (the incubator's mode, charge and multiblock flag).
	 */
	public void injectTestChannel(int index, int value) {
		data.set(index, value);
	}

	@Override
	public boolean stillValid(Player player) {
		return AbstractContainerMenu.stillValid(access, player, block);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			result = stack.copy();
			// Menu slot layout: [0..base) machine, [base..base+N) upgrades, then 36 player-inventory slots.
			int base = baseSlotCount();
			int invStart = base + UPGRADE_SLOT_COUNT;
			int invEnd = invStart + 36;
			if (index < invStart) {
				// A machine or upgrade slot → dump into the player inventory.
				if (!moveItemStackTo(stack, invStart, invEnd, true)) {
					return ItemStack.EMPTY;
				}
			} else if (stack.is(ModContent.MUTE_CHIP.get())) {
				// A mute chip from the inventory shift-clicks straight into the active upgrade slot
				// (MOD-080): only that slot, only a mute chip, only one (the slot caps at 1). If it is
				// already occupied, leave the chips in the inventory — never spill into machine slots.
				int activeSlot = base + MachineBlockEntity.ACTIVE_UPGRADE_INDEX;
				if (!moveItemStackTo(stack, activeSlot, activeSlot + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveItemStackTo(stack, 0, base, false)) {
				// Any other inventory item → only the machine's base slots. Upgrade slots are never
				// auto-filled with non-chip items.
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return result;
	}

	/**
	 * An upgrade-panel slot (MOD-080). Hidden (inactive → not rendered, not hoverable, not clickable —
	 * vanilla {@code AbstractContainerScreen} gates all three on {@link Slot#isActive()}) while the panel
	 * is collapsed. Holds at most one item. The active slot accepts only a mute chip; locked slots accept
	 * nothing. Hoppers never reach these — the block entity omits them from {@code getSlotsForFace}.
	 */
	public static final class UpgradeSlot extends Slot {
		private final BooleanSupplier visible;
		private final boolean acceptsChip;

		public UpgradeSlot(Container container, int index, int x, int y, BooleanSupplier visible, boolean acceptsChip) {
			super(container, index, x, y);
			this.visible = visible;
			this.acceptsChip = acceptsChip;
		}

		@Override
		public boolean isActive() {
			return visible.getAsBoolean();
		}

		/** A locked slot (never accepts an item) — the MVP panel's slots 1–3, reserved for future upgrades. */
		public boolean isLocked() {
			return !acceptsChip;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return acceptsChip && stack.is(ModContent.MUTE_CHIP.get());
		}
	}
}
