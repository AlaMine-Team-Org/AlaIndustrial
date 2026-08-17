package dev.alaindustrial.menu;

import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.item.misc.OverclockerChipItem;
import dev.alaindustrial.network.MachineStatsPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModTags;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
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
import org.jspecify.annotations.Nullable;

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
	/** The viewer. Needed to address the statistics packet (MOD-125); vanilla's menu keeps no player. */
	private final Player player;
	/** Client-only: whether the upgrade panel is expanded. The screen toggles it; the server ignores it. */
	private boolean panelOpen;
	/** Client-only: whether the statistics panel is expanded (MOD-125). Docks opposite the upgrade panel. */
	private boolean statsPanelOpen;

	protected MachineMenu(MenuType<?> type, int syncId, Inventory playerInventory,
			Container machine, ContainerData data, ContainerLevelAccess access, Block block) {
		super(type, syncId);
		this.machine = machine;
		this.data = data;
		this.access = access;
		this.block = block;
		this.player = playerInventory.player;
		addMachineSlots();
		addUpgradeSlots();
		addPlayerInventory(playerInventory);
		addDataSlots(data);
	}

	/** Add the machine's own slots (slot indices 0..machineSize-1). */
	protected abstract void addMachineSlots();

	/**
	 * Number of machine-specific slots (excludes upgrade slots). Derived from the container so it is the
	 * same client- and server-side: a {@code MachineMenu} container is sized {@code base + N} slots.
	 *
	 * <p>A machine may opt out of the panel entirely ({@code hasUpgradePanel() == false} — the Energy
	 * Condenser, MOD-393); then the container holds only its own slots and there is nothing to subtract.
	 * Which case applies is read from {@link #hasUpgradePanel()} rather than guessed from the size, so
	 * both sides agree even though the client is backed by a dummy container.
	 */
	protected final int baseSlotCount() {
		return hasUpgradePanel() ? machine.getContainerSize() - UPGRADE_SLOT_COUNT : machine.getContainerSize();
	}

	/**
	 * Whether this menu shows the upgrade panel. Default true; a menu whose block entity opted out of
	 * the panel overrides it. Must answer identically on client and server — the slot indices depend
	 * on it.
	 */
	public boolean hasUpgradePanel() {
		return true;
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
	 * the panel. Each arm is TYPED ({@link UpgradeSlotKind}): the left one takes the mute chip, the top
	 * one an overclocker, the other two are reserved for the modules of MOD-286. The atlas carries a hint
	 * glyph per arm, so the layout explains itself without text — and a typed arm caps stacking for free
	 * (one overclocker per machine, because there is one arm that takes one). All are hidden (inactive)
	 * while the panel is collapsed.
	 */
	private void addUpgradeSlots() {
		if (!hasUpgradePanel()) {
			return; // this machine opted out of the panel (MOD-393)
		}
		int start = baseSlotCount();
		if (start < 0) {
			return; // defensive: a container smaller than the upgrade block (should not happen)
		}
		for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
			int[] xy = upgradeSlotXY(i);
			addSlot(new UpgradeSlot(machine, start + i, xy[0], xy[1],
					this::isPanelOpen, upgradeSlotKind(i)));
		}
	}

	/**
	 * The kind of panel arm {@code i} on THIS machine.
	 *
	 * <p>Both places that build the arms go through here, and that is the point: the panel formula
	 * already existed twice (docked and dragged), and the last time only one copy was fixed the slots
	 * worked until the player dragged the panel and then stopped. One method, one answer.
	 *
	 * <p>The only per-machine difference is arm 1: a machine the overclocker does nothing to gets a
	 * locked arm instead of one that accepts a chip and ignores it. Decided from the BLOCK, so the
	 * client — which has no block entity behind this menu — reaches the same answer as the server and
	 * the chip does not snap back after appearing to fit.
	 */
	protected UpgradeSlotKind upgradeSlotKind(int i) {
		UpgradeSlotKind kind = UpgradeSlotKind.byIndex(i);
		if (kind == UpgradeSlotKind.OVERCLOCK && !ContentManifest.isOverclockable(block)) {
			return UpgradeSlotKind.OVERCLOCK_UNSUPPORTED;
		}
		return kind;
	}

	/**
	 * Move the upgrade slots by (dx, dy) from their docked positions (client-only, MOD-080 draggable
	 * panel). {@link Slot#x}/{@link Slot#y} are {@code final}, so each slot is rebuilt at the new
	 * position and swapped into {@link #slots} — the item lives in the container, not the slot, so
	 * nothing is lost. The server never calls this (it does not render).
	 */
	public void repositionUpgradeSlots(int dx, int dy) {
		if (!hasUpgradePanel()) {
			// This machine opted out of the panel (MOD-393). Without the guard the method would build
			// UpgradeSlots at menu indices base..base+3 and splice them over the PLAYER-inventory slots
			// that live there instead — each bound to a machine container that has no such slot. The
			// first ClientboundContainerSetContent then writes past the end of it and the client dies.
			// The screen calls this on init to restore a dragged panel position, so it is not a rare path.
			return;
		}
		int start = baseSlotCount();
		if (start < 0) {
			return;
		}
		for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
			int menuIndex = start + i;
			int[] xy = upgradeSlotXY(i);
			UpgradeSlot moved = new UpgradeSlot(machine, menuIndex,
					xy[0] + dx, xy[1] + dy, this::isPanelOpen, upgradeSlotKind(i));
			moved.index = menuIndex;
			slots.set(menuIndex, moved);
		}
	}

	/** Client-only upgrade-panel state (see {@link #panelOpen}). */
	public boolean isPanelOpen() {
		return panelOpen;
	}

	/**
	 * Tier of the overclocker chip sitting in the panel, or 0 (MOD-393). Read from the container rather
	 * than the block entity so it answers the same on both sides: client-side the container is the
	 * menu's dummy, which vanilla keeps in sync with the real inventory — the block entity itself is not
	 * reachable here.
	 *
	 * <p>This is what the player has INSTALLED, which is what the gear tooltip reports. What actually
	 * takes effect is additionally capped by the machine's voltage tier
	 * ({@link MachineBlockEntity#overclockerCap()}) and lives server-side.
	 */
	public int installedOverclockerTier() {
		int start = baseSlotCount();
		if (start < 0) {
			return 0;
		}
		int tier = 0;
		for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
			tier = Math.max(tier, OverclockerChipItem.tierOf(machine.getItem(start + i)));
		}
		return tier;
	}

	/** Toggle the upgrade panel (screen-driven); returns the new state. */
	public boolean togglePanel() {
		panelOpen = !panelOpen;
		return panelOpen;
	}

	// --- Statistics panel (MOD-125) ---------------------------------------------------------------

	/** Ticks between two statistics packets for one open screen. Two seconds: fast enough to read as live. */
	private static final int STATS_SYNC_INTERVAL_TICKS = 40;

	/** Client-only statistics-panel state (see {@link #statsPanelOpen}). */
	public boolean isStatsPanelOpen() {
		return statsPanelOpen;
	}

	/** Toggle the statistics panel (screen-driven); returns the new state. */
	public boolean toggleStatsPanel() {
		statsPanelOpen = !statsPanelOpen;
		return statsPanelOpen;
	}

	/** Server-side: ticks since the last statistics packet went out to this viewer. */
	private int ticksSinceStatsSync;

	/** Server-side: {@code energyGenerated + energyConsumed} at the last packet, to derive the window rate. */
	private long lastThroughputSample = -1L;

	/** Server-side: the snapshot last sent to this viewer, so an unchanged one can be skipped. */
	private @Nullable MachineStatsPayload lastSentStats;

	/** Client-side: the most recent snapshot, or {@code null} until the first packet lands. */
	private @Nullable MachineStatsPayload stats;

	/** Client-side: {@code System.currentTimeMillis()} when {@link #stats} last changed. */
	private long statsReceivedAt;

	/**
	 * Push this viewer's statistics snapshot, throttled.
	 *
	 * <p>Riding {@code broadcastChanges} is what keeps the promise "no open GUI, no traffic": vanilla calls
	 * it once per tick for an OPEN menu and stops the moment the screen closes, so nothing here has to scan
	 * the player list or ask a block who is watching it. The counter is per-menu, i.e. per viewer, and dies
	 * with the screen.
	 *
	 * <p>The {@code instanceof} is both the block-entity lookup and the side guard: server-side
	 * {@code machine} IS the block entity ({@code MachineBlockEntity implements WorldlyContainer}), while a
	 * client menu is backed by a {@code SimpleContainer} and falls through.
	 */
	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (++ticksSinceStatsSync < STATS_SYNC_INTERVAL_TICKS) {
			return;
		}
		ticksSinceStatsSync = 0;
		if (!(machine instanceof MachineBlockEntity be) || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		// No chip, no telemetry — not even the packet. This is what makes the feature free for a base
		// that has not opted into it: an un-instrumented machine never builds a snapshot at all.
		if (!be.hasStatsChip()) {
			lastSentStats = null;
			return;
		}
		MachineStatsPayload next = snapshot(be);
		// Silence when nothing changed. An idle machine skips its tick, so none of its numbers move, and
		// re-sending an identical snapshot every two seconds would be pure noise — this is the idle-sleep
		// rule falling out of the data instead of needing a timer of its own. It also means the packet
		// only ever costs anything on machines that are actually doing something.
		//
		// The client reads the resulting silence as "idle" (see statsAreStale), which is why the first
		// snapshot after a quiet spell must still go out: it is what turns the label back to "updating".
		if (next.equals(lastSentStats)) {
			return;
		}
		lastSentStats = next;
		NetworkDispatcher.get().sendToPlayer(serverPlayer, next);
	}

	/**
	 * Build the snapshot, deriving the window rate from the career totals rather than sampling the tick.
	 *
	 * <p>Throughput (produced + consumed) is monotonic, so the difference between two packets divided by the
	 * interval is the true average over that window — including the ticks the block spent asleep, which a
	 * per-tick sample would simply miss. The first packet has no previous sample and falls back to the
	 * block's instantaneous rate rather than reporting a bogus average over an unknown span.
	 */
	private MachineStatsPayload snapshot(MachineBlockEntity be) {
		long throughput = be.getEnergyStorage().getTotalEnergyGenerated()
				+ be.getEnergyStorage().getTotalEnergyConsumed();
		int windowRate;
		if (lastThroughputSample < 0) {
			windowRate = be.currentEuRate();
		} else {
			windowRate = (int) Math.min(Integer.MAX_VALUE,
					Math.max(0L, throughput - lastThroughputSample) / STATS_SYNC_INTERVAL_TICKS);
		}
		lastThroughputSample = throughput;
		return new MachineStatsPayload(containerId, be.activeTicks(),
				be.getEnergyStorage().getTotalEnergyIn(), be.getEnergyStorage().getTotalEnergyOut(),
				be.getEnergyStorage().getTotalEnergyGenerated(), be.getEnergyStorage().getTotalEnergyConsumed(),
				windowRate, be.peakEuRate(), be.countDirectConnections(), be.totalItemsProcessed());
	}

	/** Client-side: accept a snapshot addressed to THIS menu. A stale packet for another screen is dropped. */
	public void acceptStats(MachineStatsPayload payload) {
		if (payload.containerId() != containerId) {
			return;
		}
		this.stats = payload;
		this.statsReceivedAt = System.currentTimeMillis();
	}

	/** Client-side: the latest snapshot, or {@code null} before the first packet arrives. */
	public @Nullable MachineStatsPayload stats() {
		return stats;
	}

	/**
	 * Whether a statistics chip sits in the panel, answered from the CONTAINER rather than the block
	 * entity so the client can ask it too — vanilla keeps the menu's dummy container in sync with the
	 * real inventory, while the block entity is server-only. Same trick {@link #installedOverclockerTier}
	 * uses, and for the same reason.
	 */
	public boolean hasStatsChipInPanel() {
		if (!hasUpgradePanel()) {
			return false;
		}
		int start = baseSlotCount();
		if (start < 0) {
			return false;
		}
		for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
			if (machine.getItem(start + i).is(ModTags.Items.UPGRADE_STATS)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Client-side: whether the machine has gone quiet.
	 *
	 * <p>Derived from packet silence rather than carried as a flag, because silence is exactly what an idle
	 * machine produces: an asleep block skips its tick, so its numbers stop changing and the panel would
	 * otherwise look frozen — indistinguishable from a bug. Two and a half intervals of nothing is the
	 * threshold, wide enough that a single late packet does not blink the label on.
	 */
	public boolean statsAreStale() {
		return stats != null && System.currentTimeMillis() - statsReceivedAt > STATS_SYNC_INTERVAL_TICKS * 50L * 5 / 2;
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
			// Menu slot layout: [0..base) machine, [base..base+N) upgrades (absent when the machine opted
			// out of the panel), then 36 player-inventory slots.
			int base = baseSlotCount();
			int invStart = base + (hasUpgradePanel() ? UPGRADE_SLOT_COUNT : 0);
			int invEnd = invStart + 36;
			if (index < invStart) {
				// A machine or upgrade slot → dump into the player inventory.
				if (!moveItemStackTo(stack, invStart, invEnd, true)) {
					return ItemStack.EMPTY;
				}
			} else if (stack.is(ModTags.Items.MACHINE_UPGRADE)) {
				// An upgrade chip from the inventory shift-clicks into the panel (MOD-080, widened by
				// MOD-392 from "the one active slot" to all four). If every slot is full, leave the chips
				// in the inventory — never spill into machine slots.
				if (!moveItemStackTo(stack, base, invStart, false)) {
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
	 * is collapsed. Accepts anything tagged {@link ModTags.Items#MACHINE_UPGRADE}. Hoppers never reach
	 * these — the block entity omits them from {@code getSlotsForFace}.
	 */
	public static final class UpgradeSlot extends Slot {
		private final BooleanSupplier visible;
		private final UpgradeSlotKind kind;

		public UpgradeSlot(Container container, int index, int x, int y, BooleanSupplier visible,
				UpgradeSlotKind kind) {
			super(container, index, x, y);
			this.visible = visible;
			this.kind = kind;
		}

		@Override
		public boolean isActive() {
			return visible.getAsBoolean();
		}

		/** Which upgrade this arm of the panel takes; the screen draws its hint and lock from this. */
		public UpgradeSlotKind kind() {
			return kind;
		}

		/** A reserved arm — visible, greyed out, accepts nothing until MOD-286 fills it. */
		public boolean isLocked() {
			return !kind.isOpen();
		}

		/**
		 * One module per arm, always. Tiers of the overclocker are three distinct items rather than a
		 * stack, so "how much speed" is read off the item itself instead of a count in a slot.
		 */
		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return kind.accepts(stack);
		}
	}
}
