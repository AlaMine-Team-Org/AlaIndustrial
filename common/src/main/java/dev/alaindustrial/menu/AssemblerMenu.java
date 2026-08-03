package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.AssemblerBlockEntity;
import dev.alaindustrial.block.entity.AssemblerBlockEntity.AssemblerStatus;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.assembler.BlueprintPattern;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menu for the Assembler (MOD-275). One block, one window, <b>two tabs</b>: "Work" runs the machine
 * (blueprint queue, output area, energy, progress, status, and a read-only picture of what the active
 * blueprint makes) and "Record" authors a blueprint (the nine-cell ghost grid, the result preview, the
 * blank blueprint and the "Write" button).
 *
 * <p><b>A view switch, not a mode switch.</b> The machine keeps crafting while the player is on the
 * recording tab; nothing here touches the crafting cycle. A global mode that halted production while
 * recording was considered and rejected — see the task log.
 *
 * <p><b>The ghost cells are real menu slots on purpose.</b> A client menu is built from
 * {@code (syncId, Inventory)} alone — no block position — so it can neither read the block entity nor
 * be handed a payload without inventing a packet. Binding the grid as ordinary slots hands the job to
 * the vanilla container sync, which ships whatever a slot returns; the slots then refuse both
 * placement and pickup, so the player can never treat them as storage.
 *
 * <p><b>How a ghost gets filled.</b> Clicking a cell rides the vanilla container-button channel
 * ({@link #clickMenuButton}, the same one {@link SawmillMenu} uses for its mode buttons) with the cell
 * index as the button id. The item comes from {@link #getCarried()} — the stack on the player's
 * cursor, which the server already tracks — so nothing about the choice travels in a packet of ours.
 * Button {@link #BUTTON_WRITE} is "Write", {@link #BUTTON_TAB_BASE} + tab switches the view, and
 * {@link #BUTTON_SUBSTITUTE_BASE} + queue slot flips that blueprint's ingredient substitution.
 *
 * <p><b>Slot order.</b> {@link MachineMenu}'s constructor fixes the first three groups — machine
 * slots, upgrades, player inventory — so the ghosts are appended after it returns rather than from
 * {@code addMachineSlots}. Two reasons: the pattern container is not assignable before {@code super(…)}
 * runs, and inserting the ghosts in the middle would break the base class's index arithmetic for every
 * group after them. The layout is therefore {@code [0,3)} blueprints, {@code [3,9)} output, {@code 9}
 * the blank blueprint, {@code [10,14)} upgrades, {@code [14,50)} player inventory, {@code [50,59)}
 * ghost grid, {@code 59} result preview.
 */
public class AssemblerMenu extends MachineMenu {
	/** Tab 0 — the running machine. The tab a freshly opened window shows, on both sides. */
	public static final int TAB_WORK = 0;
	/** Tab 1 — authoring a blueprint. */
	public static final int TAB_RECORD = 1;
	/** Slots that belong to neither tab and are therefore always reachable (inventory, upgrades). */
	private static final int TAB_ANY = -1;

	/** Button ids {@code [0, 9)} write ghost cell {@code i} from the carried stack. */
	public static final int BUTTON_CELL_BASE = 0;
	/** Button id that stamps the grid onto the blank blueprint. */
	public static final int BUTTON_WRITE = AssemblerBlockEntity.PATTERN_GRID_SIZE;
	/** Button ids {@code BUTTON_TAB_BASE + tab} switch the view to {@code tab}. */
	public static final int BUTTON_TAB_BASE = BUTTON_WRITE + 1;
	/** How many tabs the window has; sizes the id block reserved for {@link #BUTTON_TAB_BASE}. */
	private static final int TAB_COUNT = 2;
	/**
	 * Button ids {@code [BUTTON_SUBSTITUTE_BASE, +BLUEPRINT_SLOT_COUNT)} flip ingredient substitution
	 * on the blueprint in queue slot {@code id - BUTTON_SUBSTITUTE_BASE}. Per blueprint rather than
	 * per machine: the flag rides the blueprint, so moving it to another assembler keeps the setting.
	 *
	 * <p>Placed after the tab block, not at {@code BUTTON_WRITE + 1}: the tab ids already own that
	 * slice, and the substitution range is tested with {@code >=}, so an overlap would silently make
	 * "switch to Record" also toggle a blueprint's substitution.
	 */
	public static final int BUTTON_SUBSTITUTE_BASE = BUTTON_TAB_BASE + TAB_COUNT;

	/** Menu index one past the last machine slot (blueprints + output + the blank). */
	private static final int MACHINE_SLOT_END = AssemblerBlockEntity.SLOT_COUNT;
	private static final int UPGRADE_SLOT_START = MACHINE_SLOT_END;
	private static final int PLAYER_SLOT_START = UPGRADE_SLOT_START + UPGRADE_SLOT_COUNT;
	private static final int PLAYER_SLOT_COUNT = 36;
	/** Menu index of the first ghost-grid slot; the nine cells run from here, row-major. */
	public static final int GRID_SLOT_START = PLAYER_SLOT_START + PLAYER_SLOT_COUNT;
	/** Menu index of the result-preview slot. */
	public static final int RESULT_SLOT_INDEX = GRID_SLOT_START + AssemblerBlockEntity.PATTERN_GRID_SIZE;

	// --- Slot geometry, measured off the generated atlases (tools/gen_assembler_gui.py) ---
	// Work tab.
	private static final int QUEUE_X = 25, QUEUE_Y = 37;
	private static final int OUTPUT_X = 131, OUTPUT_Y = 37, OUTPUT_COLS = 2;
	// Record tab.
	private static final int GRID_X = 25, GRID_Y = 37;
	private static final int RESULT_X = 111, RESULT_Y = 55;
	private static final int BLANK_X = 151, BLANK_Y = 55;
	private static final int SLOT_PITCH = 18;

	@Nullable
	private final AssemblerBlockEntity assembler;
	private final Container patternContainer;

	/**
	 * Which tab each menu slot belongs to, or {@link #TAB_ANY}. Built once the slot list is complete,
	 * because it is indexed the same way {@link #slots} is.
	 */
	private int[] slotTab = new int[0];

	/**
	 * The tab this window is showing.
	 *
	 * <p><b>Menu state, and it genuinely has to exist on the server too.</b> It is not machine state —
	 * it is never persisted, never a {@code ContainerData} channel and never on the block entity, so two
	 * players at the same assembler can be on different tabs. But the server needs its own copy,
	 * because {@link Slot#isActive()} is checked <em>only</em> on the client: verified against 26.2
	 * bytecode, {@code AbstractContainerScreen} calls it in three places (the slot render pass, the
	 * per-slot paint and {@code getHoveredSlot}) and {@code AbstractContainerMenu} calls it nowhere at
	 * all — there is not one reference to it in the whole of {@code minecraft-common.jar}. Hiding a slot
	 * therefore stops the client drawing and targeting it, and leaves the server perfectly willing to
	 * accept a click on it. Mirroring the tab here is what lets {@link #clicked} refuse those clicks.
	 * The client tells the server through the same ordered button channel the rest of this menu uses.
	 */
	private int activeTab = TAB_WORK;

	/** Server side — carries the typed block entity so {@link #clickMenuButton} can act on it. */
	public AssemblerMenu(int syncId, Inventory playerInventory, AssemblerBlockEntity be,
			ContainerLevelAccess access) {
		super(ModContent.ASSEMBLER_MENU.get(), syncId, playerInventory, be, be.getDataAccess(), access,
				ModContent.ASSEMBLER.get());
		this.assembler = be;
		this.patternContainer = be.getPatternContainer();
		addPatternSlots();
		assignTabs();
	}

	/**
	 * Client side — the data width comes from the block entity's {@code DATA_COUNT}, the one source of
	 * it (MOD-235); a narrower stub throws in the render thread the moment the screen reads the missing
	 * channel.
	 */
	public AssemblerMenu(int syncId, Inventory playerInventory) {
		super(ModContent.ASSEMBLER_MENU.get(), syncId, playerInventory,
				new SimpleContainer(AssemblerBlockEntity.SLOT_COUNT + UPGRADE_SLOT_COUNT),
				new SimpleContainerData(AssemblerBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL,
				ModContent.ASSEMBLER.get());
		this.assembler = null;
		this.patternContainer = new SimpleContainer(AssemblerBlockEntity.PATTERN_SLOT_COUNT);
		addPatternSlots();
		assignTabs();
	}

	@Override
	protected void addMachineSlots() {
		// Blueprint queue (Work tab): one blueprint per slot, laid out as a column beside the energy bar.
		for (int i = 0; i < AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT; i++) {
			addSlot(new TabSlot(machine, AssemblerBlockEntity.BLUEPRINT_SLOT_START + i,
					QUEUE_X, QUEUE_Y + i * SLOT_PITCH, TAB_WORK, true));
		}
		// Output area (Work tab): the machine fills it, the player empties it.
		for (int i = 0; i < AssemblerBlockEntity.OUTPUT_SLOT_COUNT; i++) {
			addSlot(new TabSlot(machine, AssemblerBlockEntity.OUTPUT_SLOT_START + i,
					OUTPUT_X + (i % OUTPUT_COLS) * SLOT_PITCH, OUTPUT_Y + (i / OUTPUT_COLS) * SLOT_PITCH,
					TAB_WORK, false));
		}
		// The blank blueprint (Record tab): the one slot on the recording side the player fills.
		addSlot(new TabSlot(machine, AssemblerBlockEntity.BLANK_SLOT, BLANK_X, BLANK_Y, TAB_RECORD, true));
	}

	/**
	 * A machine slot that belongs to one tab.
	 *
	 * <p>{@link #isActive()} is the client half of hiding it — it keeps the slot out of the render pass
	 * and out of {@code getHoveredSlot}. It is <b>only</b> the client half: the server never consults it
	 * (see {@link #activeTab}), which is what {@link #clicked} is for. Both halves are needed and neither
	 * substitutes for the other — an L3 screenshot gate caught the first draft of this class, which had
	 * the server guard and no {@code isActive}, still painting the queue's blueprints onto the recording
	 * tab.
	 *
	 * @param acceptsBlueprint whether the player may put a blueprint in (the output area may not be filled)
	 */
	private final class TabSlot extends Slot {
		private final int tab;
		private final boolean acceptsBlueprint;

		private TabSlot(Container container, int index, int x, int y, int tab, boolean acceptsBlueprint) {
			super(container, index, x, y);
			this.tab = tab;
			this.acceptsBlueprint = acceptsBlueprint;
		}

		@Override
		public boolean isActive() {
			return activeTab == tab;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return acceptsBlueprint && stack.is(ModContent.ASSEMBLY_BLUEPRINT.get());
		}

		@Override
		public int getMaxStackSize() {
			// One blueprint per slot; capping at one is what keeps "Write" unambiguous. The output area
			// is not a blueprint slot and keeps the ordinary stack limit.
			return acceptsBlueprint ? 1 : super.getMaxStackSize();
		}
	}

	/** The nine ghost cells and the result preview, appended after the base class has laid out its groups. */
	private void addPatternSlots() {
		for (int i = 0; i < AssemblerBlockEntity.PATTERN_GRID_SIZE; i++) {
			addSlot(new GhostSlot(patternContainer, i,
					GRID_X + (i % 3) * SLOT_PITCH, GRID_Y + (i / 3) * SLOT_PITCH, this::isRecordTab));
		}
		addSlot(new GhostSlot(patternContainer, AssemblerBlockEntity.PATTERN_RESULT_SLOT,
				RESULT_X, RESULT_Y, this::isRecordTab));
	}

	// --- Tabs ---

	/**
	 * Tag every slot with the tab it belongs to. Ranges rather than per-slot subclasses: the queue, the
	 * output and the blank are otherwise ordinary slots, and a marker interface on each would say the
	 * same thing in four more places.
	 */
	private void assignTabs() {
		slotTab = new int[slots.size()];
		java.util.Arrays.fill(slotTab, TAB_ANY);
		for (int i = 0; i < AssemblerBlockEntity.BLANK_SLOT; i++) {
			slotTab[i] = TAB_WORK;                       // the queue and the output area
		}
		slotTab[AssemblerBlockEntity.BLANK_SLOT] = TAB_RECORD;
		for (int i = GRID_SLOT_START; i <= RESULT_SLOT_INDEX; i++) {
			slotTab[i] = TAB_RECORD;                     // the ghost grid and its preview
		}
		// Upgrade slots and the player inventory stay TAB_ANY: the upgrade panel is a floating overlay
		// with its own show/hide, and the player's own inventory is never hidden by a tab.
	}

	/** The tab this window is showing. */
	public int getActiveTab() {
		return activeTab;
	}

	private boolean isRecordTab() {
		return activeTab == TAB_RECORD;
	}

	/**
	 * Switch the view. Called on the client by the screen and on the server by
	 * {@link #clickMenuButton}; both sides must run it, or the guard below would refuse the very clicks
	 * the player can see.
	 */
	public void setActiveTab(int tab) {
		this.activeTab = tab == TAB_RECORD ? TAB_RECORD : TAB_WORK;
	}

	/** Whether slot {@code index} belongs to the tab that is currently hidden. */
	private boolean isHidden(int index) {
		if (index < 0 || index >= slotTab.length) {
			return false;
		}
		int tab = slotTab[index];
		return tab != TAB_ANY && tab != activeTab;
	}

	private boolean isHidden(Slot slot) {
		return isHidden(slot.index);
	}

	/**
	 * The server-side half of hiding a tab.
	 *
	 * <p>{@code Slot#isActive} keeps the hidden slots off the screen and out of the client's hit test;
	 * this keeps them out of reach of anything the client sends anyway. Every click path — pickup,
	 * quick-move, swap, throw, and each step of a quick-craft drag — arrives through this one method
	 * ({@code doClick} is {@code private} in 26.2 and cannot be overridden), so refusing here closes all
	 * of them at once.
	 *
	 * <p>The refusal resyncs rather than staying silent: a click on a hidden slot only happens when the
	 * client's idea of the tab has diverged from the server's, or when it is not a stock client at all,
	 * and in both cases the honest answer is to re-send the truth.
	 */
	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		if (isHidden(slotId)) {
			broadcastFullState();
			return;
		}
		super.clicked(slotId, button, input, player);
	}

	/** Double-click "collect all" must not sweep items out of, or into, a tab that is not on screen. */
	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return !isHidden(slot) && super.canTakeItemForPickAll(stack, slot);
	}

	/** Same for the drag path's per-slot test, which vanilla consults outside {@link #clicked}. */
	@Override
	public boolean canDragTo(Slot slot) {
		return !isHidden(slot) && super.canDragTo(slot);
	}

	// --- The taller frame: two tabs and their content band need the room ---

	@Override
	protected int playerInventoryY() {
		return 130;
	}

	@Override
	protected int hotbarY() {
		return 188;
	}

	// --- Read-only projections for the screen ---

	/** Which blueprint slot the machine is working from, or {@code -1} when the queue is empty. */
	public int getActiveBlueprintSlot() {
		return data.get(4);
	}

	/** Why the machine is idle (or {@link AssemblerStatus#READY} when it is not). */
	public AssemblerStatus getStatus() {
		return AssemblerStatus.byOrdinal(data.get(5));
	}

	/** What the authoring grid currently resolves to; empty when it resolves to nothing. */
	public ItemStack getPatternResult() {
		return patternContainer.getItem(AssemblerBlockEntity.PATTERN_RESULT_SLOT);
	}

	/** Whether queue slot {@code queueSlot} holds a blueprint with a layout written on it. */
	public boolean isRecordedBlueprint(int queueSlot) {
		if (queueSlot < 0 || queueSlot >= AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT) {
			return false;
		}
		return AssemblyBlueprintItem.isRecorded(
				machine.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + queueSlot));
	}

	/** The first queue slot holding a recorded blueprint, or {@code -1} when the queue holds none. */
	public int firstRecordedBlueprintSlot() {
		for (int i = 0; i < AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT; i++) {
			if (isRecordedBlueprint(i)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The layout the Work tab should draw: what the machine is making, straight off the blueprint in the
	 * queue.
	 *
	 * <p><b>The authoring grid is not consulted, and that is the tab split.</b> Before the tabs, one
	 * screen carried both the player's editable grid and this picture, so the projection had to arbitrate
	 * between them — the player's own layout won as soon as they touched a cell, and the machine's
	 * disappeared out from under them. With the two on separate tabs there is nothing to arbitrate: the
	 * Record tab always shows the player's grid, and this always shows the machine's blueprint.
	 *
	 * <p>{@code preferred} is the queue slot the caller would rather see (the screen passes the slot the
	 * machine is working from). It is honoured when it actually holds a recorded blueprint, and
	 * otherwise the first recorded blueprint stands in, so an idle machine still shows what it is about
	 * to make rather than an empty frame.
	 *
	 * <p>A pure function of the menu's synced state, deliberately: it is what the headless test asserts,
	 * and it means the screen owns no copy of the layout that could go stale.
	 */
	public BlueprintView blueprintView(int preferred) {
		int slot = isRecordedBlueprint(preferred) ? preferred : firstRecordedBlueprintSlot();
		if (slot < 0) {
			return BlueprintView.NONE;
		}
		ItemStack blueprint = machine.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + slot);
		BlueprintPattern recorded = AssemblyBlueprintItem.patternOf(blueprint);
		List<ItemStack> cells = new ArrayList<>(AssemblerBlockEntity.PATTERN_GRID_SIZE);
		for (int i = 0; i < AssemblerBlockEntity.PATTERN_GRID_SIZE; i++) {
			cells.add(recorded.cell(i));
		}
		return new BlueprintView(slot, List.copyOf(cells), AssemblyBlueprintItem.resultOf(blueprint));
	}

	/**
	 * A queued blueprint's layout for the Work tab to draw: nine cells and what they make.
	 *
	 * @param blueprintSlot the queue slot this came out of, or {@code -1} when the queue holds none
	 * @param cells nine stacks, row-major, empties included
	 * @param result what the layout makes, empty when unknown or when it makes nothing
	 */
	public record BlueprintView(int blueprintSlot, List<ItemStack> cells, ItemStack result) {
		/** Nothing to show: the queue holds no recorded blueprint. */
		public static final BlueprintView NONE = new BlueprintView(-1,
				java.util.Collections.nCopies(AssemblerBlockEntity.PATTERN_GRID_SIZE, ItemStack.EMPTY),
				ItemStack.EMPTY);

		/** Whether there is a blueprint behind this view at all. */
		public boolean isPresent() {
			return blueprintSlot >= 0;
		}
	}

	/** Whether "Write" would do anything; server truth on the server, synced slots on the client. */
	public boolean canWrite() {
		if (assembler != null) {
			return assembler.canWrite();
		}
		// Client: the same predicate over the two things the vanilla sync ships — the resolved preview
		// and the blank slot's content. The count>1 fallback is not modelled here; it needs a pipe to
		// reach and the server refuses the write anyway.
		ItemStack blank = machine.getItem(AssemblerBlockEntity.BLANK_SLOT);
		return !getPatternResult().isEmpty()
				&& blank.is(ModContent.ASSEMBLY_BLUEPRINT.get())
				&& !AssemblyBlueprintItem.isRecorded(blank);
	}

	/**
	 * Tab switches, ghost-cell writes and the "Write" press. Server-only and validated here: a client
	 * that forges a button id gets nowhere, and the item that lands in a cell is the one the
	 * <em>server</em> believes is on the cursor, never one the client named.
	 *
	 * <p>The recording actions additionally require the recording tab to be the one on screen — the same
	 * rule {@link #clicked} applies to slots, for the same reason.
	 */
	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (!(player instanceof ServerPlayer)) {
			return false;
		}
		if (buttonId == BUTTON_TAB_BASE + TAB_WORK || buttonId == BUTTON_TAB_BASE + TAB_RECORD) {
			setActiveTab(buttonId - BUTTON_TAB_BASE);
			return true;
		}
		if (assembler == null || activeTab != TAB_RECORD) {
			return false;
		}
		if (buttonId == BUTTON_WRITE) {
			return assembler.writePattern();
		}
		if (buttonId >= BUTTON_CELL_BASE
				&& buttonId < BUTTON_CELL_BASE + AssemblerBlockEntity.PATTERN_GRID_SIZE) {
			assembler.setPatternCell(buttonId - BUTTON_CELL_BASE, getCarried());
			return true;
		}
		if (buttonId >= BUTTON_SUBSTITUTE_BASE
				&& buttonId < BUTTON_SUBSTITUTE_BASE + AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT) {
			return assembler.toggleSubstitution(buttonId - BUTTON_SUBSTITUTE_BASE);
		}
		return false;
	}

	/** Whether the blueprint in queue slot {@code queueSlot} is set to substitute ingredients. */
	public boolean substitutes(int queueSlot) {
		if (!isRecordedBlueprint(queueSlot)) {
			return false;
		}
		return AssemblyBlueprintItem.substitutes(
				machine.getItem(AssemblerBlockEntity.BLUEPRINT_SLOT_START + queueSlot));
	}

	/**
	 * Shift-click routing. Written out rather than inherited because the ghost slots live past the
	 * player inventory, which the base class's arithmetic treats as the last group — inheriting it
	 * would let a shift-click push items into the ghost grid, where they would be neither stored nor
	 * recoverable.
	 *
	 * <p>Tab-aware in both directions: a shift-click never moves anything out of a hidden slot (that is
	 * already covered by {@link #clicked}, but this method is {@code public} and reachable on its own),
	 * and never moves anything <em>into</em> one — a blueprint shift-clicked from the inventory joins
	 * the queue only while the queue is on screen, and the blank slot only while it is.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem() || slot instanceof GhostSlot || isHidden(index)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack result = stack.copy();
		int invEnd = PLAYER_SLOT_START + PLAYER_SLOT_COUNT;
		if (index < PLAYER_SLOT_START) {
			// A machine or upgrade slot → the player's inventory.
			if (!moveItemStackTo(stack, PLAYER_SLOT_START, invEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(ModContent.MUTE_CHIP.get())) {
			int activeSlot = UPGRADE_SLOT_START + MachineBlockEntity.ACTIVE_UPGRADE_INDEX;
			if (!moveItemStackTo(stack, activeSlot, activeSlot + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(ModContent.ASSEMBLY_BLUEPRINT.get())) {
			// A blueprint goes where the player can see it going: the queue on the Work tab, the blank
			// slot on the Record tab. Nothing else may enter a machine slot from the inventory — the
			// output area is machine-fill only and the ghosts are not storage.
			if (!moveItemStackTo(stack, blueprintTargetStart(), blueprintTargetEnd(), false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return result;
	}

	private int blueprintTargetStart() {
		return activeTab == TAB_RECORD
				? AssemblerBlockEntity.BLANK_SLOT : AssemblerBlockEntity.BLUEPRINT_SLOT_START;
	}

	private int blueprintTargetEnd() {
		return activeTab == TAB_RECORD
				? AssemblerBlockEntity.BLANK_SLOT + 1 : AssemblerBlockEntity.OUTPUT_SLOT_START;
	}

	/**
	 * A cell of the authoring grid, or the result preview. Shown, never touched: it refuses placement
	 * and pickup, so every vanilla click path — pickup, quick-move, swap, drag, throw — is a no-op on
	 * it regardless of what the client sends. Its content is written only by
	 * {@link AssemblerBlockEntity#setPatternCell}, through the button channel.
	 */
	public static final class GhostSlot extends Slot {
		private final java.util.function.BooleanSupplier visible;

		public GhostSlot(Container container, int index, int x, int y,
				java.util.function.BooleanSupplier visible) {
			super(container, index, x, y);
			this.visible = visible;
		}

		@Override
		public boolean isActive() {
			return visible.getAsBoolean();
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
}
