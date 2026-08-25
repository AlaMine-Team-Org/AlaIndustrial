package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.energy.DirectAdjacencyDistributor;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.CrystalBlankItem;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.menu.CreativeEnergySourceMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Creative Energy Source (MOD-479): a technical block that never runs out of EU, so a test stand
 * can be powered without a real generator becoming the bottleneck of the test. Not obtainable in
 * survival — no recipe exists; it comes from the creative tab or {@code /give}.
 *
 * <h2>How "infinite" is implemented, and why not any other way</h2>
 *
 * <p>The buffer is an ordinary, finite {@link dev.alaindustrial.core.energy.EnergyBuffer} that is
 * <b>topped up to exactly {@link #getOutputLimit()} at the start of every tick</b>. That single line
 * does three jobs at once, which is why it beats the obvious alternatives:
 *
 * <ul>
 *   <li><b>It makes the source inexhaustible</b> without ever handing the network an unbounded number.
 *       {@code EnergyNetwork} estimates supply with {@code extract(Long.MAX_VALUE)} and <em>sums</em> the
 *       answers across producers; a source that answered "as much as you like" would overflow that sum
 *       into the negative with two of these blocks on one network, and the resulting bogus storage budget
 *       would drain every Battery Box on the line into the cables.</li>
 *   <li><b>It is the per-tick output cap, for free.</b> The limit cannot be enforced in
 *       {@code maxExtract} (final, and a per-<em>call</em> ceiling anyway) nor by counting inside
 *       {@code extract} (the network calls it in rolled-back simulations too). Holding only
 *       {@code outputLimit} EU at a time means the cabled pull and the six single-face direct pushes
 *       share one budget: whoever gets there first spends it, and the total for the tick is the number
 *       the player set — not six times it.</li>
 *   <li><b>It keeps the network's BFS fields stable.</b> A normal generator whose buffer empties and
 *       refills flips the live-endpoint set and makes the network walk the whole cable graph; this one
 *       always reports supply, so that set never changes.</li>
 * </ul>
 *
 * <p>The top-up goes through {@code produceInternal}/{@code drainInternal} — never {@code insert}, never
 * {@code setAmountUntracked}: those two keep the buffer's settled baseline in step, so a commit later in
 * the tick cannot read the internal refill as delivery into the grid.
 *
 * <h2>Switched off means invisible, not merely idle</h2>
 *
 * <p>{@link #energyRoleForFace} returns {@link EnergyRole#NONE} while disabled, so the block leaves the
 * network's producer list entirely. Leaving the face {@code OUT} with zero output would make it an
 * "idle producer" — live by role, dead by output — which seeds a distance of 1 into the network's flow
 * field and freezes the fill front for everyone else (the MOD-318 defect).
 *
 * <h2>Charge slot</h2>
 *
 * <p>Slot 0 accepts any powered item and fills it at the item's own intake rate. Deliberately
 * <em>outside</em> the output limit: the limit is about what the block puts on the grid, and a QA tool
 * that charges a drill at one third the speed because the grid test wanted 32 EU/t would be a trap. The
 * slot is GUI-only — hoppers and pipes are refused on every face — and, critically, <b>the tick never
 * writes an item into it</b>. It only edits the charge of the stack the player put there. A slot whose
 * contents are a projection of block state is what turned a single missing debit into an endless
 * duplication in MOD-492; there is nothing to debit here, but the shape of that bug is worth not
 * rebuilding.
 */
public class CreativeEnergySourceBlockEntity extends MachineBlockEntity implements MenuProvider {

	/** Slot 0 — any powered item dropped in here fills up. */
	public static final int CHARGE_SLOT = 0;
	/** Machine slots; no upgrade panel is appended (see {@link #hasUpgradePanel()}). */
	public static final int MACHINE_SLOTS = 1;

	/**
	 * Ceiling of the output setting, in EU/tick. Not a balance number and therefore deliberately not a
	 * {@code Config} key: it is the range of an instrument, not a property of the mod's economy.
	 *
	 * <p>Worth knowing while reading the GUI: no cable in the mod carries more than 512 EU/t
	 * ({@link dev.alaindustrial.core.energy.CableType} electrum), so settings above that only ever
	 * arrive when the block sits flush against the machine. The screen says so, because otherwise the
	 * upper half of the range reads as broken.
	 */
	public static final int MAX_OUTPUT = 8192;

	/** Output on placement — the MV preset, enough to drive early machines without swamping a test. */
	public static final int DEFAULT_OUTPUT = 128;

	/** Sync channel: 1 when the source is switched on. */
	public static final int DATA_ENABLED = MachineBlockEntity.DATA_COUNT;
	/** Sync channel: current output limit in EU/tick. Fits a signed short — {@link #MAX_OUTPUT} is 8192. */
	public static final int DATA_OUTPUT = MachineBlockEntity.DATA_COUNT + 1;

	/**
	 * Six-wide data. Both indices are written as offsets from the base constant rather than as literals
	 * so they move by themselves if the machine family ever grows a channel — the collision MOD-458 hit
	 * with a hard-coded 4 was invisible to every gate.
	 */
	public static final int DATA_COUNT = MachineBlockEntity.DATA_COUNT + 2;

	private boolean enabled = true;
	private int outputLimit = DEFAULT_OUTPUT;

	private final ContainerData sourceData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_ENABLED -> enabled ? 1 : 0;
				case DATA_OUTPUT -> outputLimit;
				default -> dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case DATA_ENABLED -> enabled = value != 0;
				case DATA_OUTPUT -> outputLimit = clampOutput(value);
				default -> dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public CreativeEnergySourceBlockEntity(BlockPos pos, BlockState state) {
		// Capacity == MAX_OUTPUT, so the energy bar reads as "how much of the instrument's range is
		// selected" rather than as a fuel gauge that is always full. maxInsert is 0: this block never
		// accepts energy, or the network would read its free space as machine demand and start
		// emptying the line's Battery Boxes into it.
		super(ModContent.CREATIVE_ENERGY_SOURCE_BE.get(), pos, state, EnergyTier.HV, MACHINE_SLOTS,
				MAX_OUTPUT, 0L, MAX_OUTPUT);
	}

	// --- settings ------------------------------------------------------------------------------

	public boolean isEnabled() {
		return enabled;
	}

	public int getOutputLimit() {
		return outputLimit;
	}

	/** Clamp, don't reject: this is an instrument, and a value off the end of its scale means "the end". */
	public static int clampOutput(int requested) {
		return Math.max(0, Math.min(MAX_OUTPUT, requested));
	}

	/**
	 * Flip the source on or off. Changing this changes the face roles, so the network's cached endpoint
	 * lists have to be told; {@code wake()} plus the neighbour update below is what makes the topology
	 * re-read them on the next tick instead of ticking a stale list.
	 */
	public void setEnabled(boolean value) {
		if (enabled == value) {
			return;
		}
		enabled = value;
		if (!enabled) {
			// Hand back what the buffer is holding: a disabled source must not leave a last packet
			// lying in the buffer for a lookup that ignores face roles. The zero-output path in
			// onServerTick does the same, for the same reason.
			energy.drainInternal(energy.getAmount());
		}
		onSettingChanged();
	}

	public void setOutputLimit(int requested) {
		int clamped = clampOutput(requested);
		if (outputLimit == clamped) {
			return;
		}
		outputLimit = clamped;
		if (clamped == 0) {
			// Crossing to zero retracts the face role (see energyRoleForFace), so the buffer must not keep
			// holding a packet that a face-blind lookup could still drain.
			energy.drainInternal(energy.getAmount());
		}
		onSettingChanged();
	}

	private void onSettingChanged() {
		setChanged();
		wake();
		syncBlockEntityToClient();
		if (level != null && !level.isClientSide()) {
			updateLit(enabled);
			// The energy role of every face just changed; neighbouring cables cache the endpoint set.
			level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
		}
	}

	// --- tick ----------------------------------------------------------------------------------

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		if (!enabled || outputLimit <= 0) {
			// Nothing to top up and nothing to push: a switched-off instrument — or one dialled to zero,
			// which is the same thing to the grid — should cost the server nothing. The charge slot is
			// idle too, which is the honest reading of "off".
			if (energy.getAmount() > 0) {
				energy.drainInternal(energy.getAmount());
			}
			return IDLE_SLEEP_TICKS;
		}
		refillToLimit();
		chargeItem();
		DirectAdjacencyDistributor.distribute(level, pos, this, true);
		// A source never sleeps: a neighbour may appear with no event that would wake it.
		return 0;
	}

	/**
	 * Bring the buffer to exactly {@link #getOutputLimit()}. Both directions matter: up because that is
	 * what makes the source inexhaustible, and down because lowering the setting must take effect on the
	 * next tick rather than after the old, larger charge has been drained away.
	 */
	private void refillToLimit() {
		long target = outputLimit;
		long current = energy.getAmount();
		if (current < target) {
			energy.produceInternal(target - current);
		} else if (current > target) {
			energy.drainInternal(current - target);
		}
	}

	/**
	 * Fill the powered item in the charge slot at the rate the item itself accepts. All arithmetic goes
	 * through the stack-aware helpers, because charge lives in a component and a stack of batteries
	 * costs its count times as much to fill.
	 *
	 * <p>A full item costs one comparison and no write at all: {@code stackAdd} returns 0, and the early
	 * return keeps the component untouched. Writing it anyway would re-send the slot to every viewer
	 * twenty times a second — the defect MOD-406 was opened for.
	 */
	private void chargeItem() {
		ItemStack target = getItem(CHARGE_SLOT);
		if (target.isEmpty()) {
			return;
		}
		long rate = ItemEnergy.inputRate(target) * target.getCount();
		long budget = Math.min(ItemEnergy.stackRoom(target), rate);
		if (budget <= 0) {
			return;
		}
		if (ItemEnergy.stackAdd(target, budget) > 0) {
			// MOD-504: the creative source finishes a blank like any other charger.
			ItemStack finished = CrystalBlankItem.promote(target);
			if (!finished.isEmpty()) {
				setItem(CHARGE_SLOT, finished);
			}
			setChanged();
		}
	}

	// --- energy faces --------------------------------------------------------------------------

	/**
	 * Emit on every face while the source is actually supplying, and vanish from the network entirely
	 * otherwise. There is no FACING on this block, so no face is reserved as a front.
	 *
	 * <p>"Actually supplying" is deliberately wider than the switch: an output of zero counts as off too.
	 * The network classifies producers by whether a face CAN extract, not by whether anything came out
	 * (see {@code EnergyTopologyCache}), so a face left OUT at zero output is the "idle producer" this
	 * class set out not to be — a block the network wakes for, ticks a dry run for, and gets nothing
	 * from. Retracting the role at zero keeps the two ways of switching it off indistinguishable to the
	 * grid, which is what a player sliding the output to the bottom expects.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return enabled && outputLimit > 0 ? EnergyRole.OUT : EnergyRole.NONE;
	}

	// --- inventory -----------------------------------------------------------------------------

	/** No overclocker can make an infinite source faster; a panel that does nothing is a lie. */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	/** A creative instrument earns nobody statistics — see the task's decision 11. */
	@Override
	public boolean tracksOwner() {
		return false;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == CHARGE_SLOT && ItemEnergy.capacity(stack) > 0;
	}

	/**
	 * GUI-only slot: every side-qualified request is refused, in both directions. Two separate answers
	 * are needed, and both are needed on both loaders: NeoForge's item capability routes through
	 * {@code canPlaceItemThroughFace}, while Fabric's global {@code ItemStorage.SIDED} fallback wraps any
	 * {@code Container} at all and asks {@code getSlotsForFace} first. Returning an empty slot array is
	 * the answer that closes both, and it also covers the trap of a block with no FACING: the base class
	 * would otherwise expose slot 0 on all six sides, including the one a hopper sits under.
	 *
	 * <p><b>Not</b> "closed to everything", and the distinction is worth keeping honest: a capability
	 * asked for with no side at all bypasses these three methods on both loaders and falls back to the
	 * plain {@code Container}. That hole is the mod's, not this block's — every machine has it — so it
	 * is recorded here rather than papered over locally.
	 */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return new int[0];
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}

	// --- sync + persistence --------------------------------------------------------------------

	@Override
	public ContainerData getDataAccess() {
		return sourceData;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean("Enabled", enabled);
		output.putInt("Output", outputLimit);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		enabled = input.getBooleanOr("Enabled", true);
		outputLimit = clampOutput(input.getIntOr("Output", DEFAULT_OUTPUT));
	}

	// --- menu ----------------------------------------------------------------------------------

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.creative_energy_source");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new CreativeEnergySourceMenu(syncId, playerInventory, this,
				ContainerLevelAccess.create(level, getBlockPos()));
	}
}
