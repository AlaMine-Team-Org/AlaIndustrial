package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.FaceEnergyPort;
import dev.alaindustrial.core.MachineEnergyStorage;
import team.reborn.energy.api.EnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The spine of every Industrialization machine: an energy buffer, an item inventory, a generic
 * processing progress counter, server tick, persistence and live GUI sync.
 *
 * <p>To add a new machine, subclass this, pass slot count / tier / capacity / I-O limits to
 * the constructor, and implement {@link #serverTick}. The base handles energy storage,
 * inventory ({@link Container}), NBT persistence (via {@link ValueInput}/{@link ValueOutput}),
 * and the {@link #dataAccess} bridge that syncs energy + progress to an open screen.
 */
public abstract class MachineBlockEntity extends BlockEntity implements WorldlyContainer {
	/** Idle-sleep safety net (R-29): how long an idle machine skips its full tick before re-checking. */
	protected static final int IDLE_SLEEP_TICKS = 40;

	protected final MachineEnergyStorage energy;
	protected final NonNullList<ItemStack> items;
	protected final EnergyTier tier;
	protected int progress;
	protected int maxProgress;
	/** Ticks left to skip {@link #onServerTick}; reset to 0 by {@link #wake()} when external state changes. */
	private int sleepTicks;

	protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
			EnergyTier tier, int slots, long capacity, long maxInsert, long maxExtract) {
		super(type, pos, state);
		this.tier = tier;
		this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
		this.energy = new MachineEnergyStorage(capacity, maxInsert, maxExtract, this);
	}

	/**
	 * Server-side tick entry point (called from the block's ticker). Wraps {@link #onServerTick} with
	 * an idle-sleep gate (R-29): when a machine reports it has no work to do, it skips the next few
	 * ticks instead of re-running its full logic every tick. External changes — inventory mutation,
	 * energy delivery (via {@link MachineEnergyStorage#onFinalCommit}), or an explicit {@link #wake()}
	 * — reset the timer so a sleeping machine resumes on the very next tick. Generators, storage,
	 * cables and the pump return 0 (never sleep); processing machines return {@link #IDLE_SLEEP_TICKS}
	 * when idle.
	 */
	public final void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
		if (sleepTicks > 0) {
			sleepTicks--;
			return;
		}
		sleepTicks = Math.max(0, onServerTick(level, pos, state));
	}

	/**
	 * Per-tick machine logic. Returns the number of ticks the machine may sleep before its next full
	 * tick: 0 keeps it ticking every tick, a positive value lets the {@link #serverTick} gate skip
	 * that many ticks. Was {@code serverTick} before the idle-sleep gate (R-29) was introduced.
	 */
	protected abstract int onServerTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state);

	/** Wake a sleeping machine so its next {@link #serverTick} runs {@link #onServerTick} immediately. */
	public void wake() {
		sleepTicks = 0;
	}

	public MachineEnergyStorage getEnergyStorage() {
		return energy;
	}

	/**
	 * Energy role exposed on a given WORLD face (R-NRG-03). Default {@link EnergyRole#BOTH}
	 * (cables / symmetric storage); generators override to OUT, consumer machines to IN, and the
	 * BatteryBox to a mixed output-face layout.
	 */
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.BOTH;
	}

	/**
	 * Applies a block's working energy role to every face EXCEPT its {@link HorizontalMachineBlock#FACING}
	 * front, which is inert ({@link EnergyRole#NONE}) — no cable/hopper connects through it (R-NRG-03).
	 * Blocks without a FACING property keep the working role on all faces.
	 */
	protected EnergyRole facingAwareRole(Direction worldFace, EnergyRole workingRole) {
		BlockState st = getBlockState();
		if (st.hasProperty(HorizontalMachineBlock.FACING) && worldFace == st.getValue(HorizontalMachineBlock.FACING)) {
			return EnergyRole.NONE;
		}
		return workingRole;
	}

	/** The Energy API view for a face, enforcing its role; {@code null} means the face is inert. */
	public EnergyStorage energyPort(Direction worldFace) {
		EnergyRole role = energyRoleForFace(worldFace);
		return role == EnergyRole.NONE ? null : new FaceEnergyPort(energy, role);
	}

	/**
	 * True if this block is a pure energy <em>store</em> (e.g. BatteryBox) rather than a working machine.
	 * The {@link dev.alaindustrial.core.EnergyNetwork} serves working machines before storage sinks so
	 * a large buffer can't starve them, and never charges a sink from itself (MOD-009). Default false.
	 */
	public boolean isEnergyStorageSink() {
		return false;
	}

	public EnergyTier getTier() {
		return tier;
	}

	/** The energy/progress data bridge a {@code MachineMenu} binds for live GUI sync. */
	public ContainerData getDataAccess() {
		return dataAccess;
	}

	/** Mark dirty so the chunk saves; energy/progress reach an open screen via {@link #dataAccess}. */
	public void markDirtyAndSync() {
		setChanged();
	}

	/**
	 * Flip the block's {@code lit} state to match whether the machine is working, so it shows its
	 * active ("on") model. No-op for blocks without a {@code lit} blockstate (cables, solar panels).
	 */
	protected void updateLit(boolean working) {
		if (level == null || level.isClientSide()) {
			return;
		}
		BlockState state = getBlockState();
		if (state.hasProperty(BlockStateProperties.LIT)
				&& state.getValue(BlockStateProperties.LIT) != working) {
			level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, working), Block.UPDATE_CLIENTS);
		}
	}

	/** Index map for {@link #dataAccess}: 0 energy, 1 capacity, 2 progress, 3 maxProgress. */
	public final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> (int) Math.min(Integer.MAX_VALUE, energy.amount);
				case 1 -> (int) Math.min(Integer.MAX_VALUE, energy.getCapacity());
				case 2 -> progress;
				case 3 -> maxProgress;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> energy.amount = value;
				case 2 -> progress = value;
				case 3 -> maxProgress = value;
				default -> {
				}
			}
		}

		@Override
		public int getCount() {
			return 4;
		}
	};

	// --- persistence (26.2 ValueInput/ValueOutput) ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("Energy", energy.amount);
		output.putInt("Progress", progress);
		output.putInt("MaxProgress", maxProgress);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		energy.amount = input.getLongOr("Energy", 0L);
		progress = input.getIntOr("Progress", 0);
		maxProgress = input.getIntOr("MaxProgress", 0);
		items.clear();
		ContainerHelper.loadAllItems(input, items);
	}

	// --- Container over `items` ---

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
		if (!removed.isEmpty()) {
			setChanged();
			wake(); // output pulled / input taken — re-evaluate next tick (R-29)
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		wake();
		return ContainerHelper.takeItem(items, slot);
	}

	/**
	 * Whether swapping the input item mid-operation resets processing progress. Processing machines
	 * (macerator/furnace/compressor/extractor) override to {@code true} per spec (TC-MACH-001-FUN04):
	 * changing the input starts the new operation from zero. Generators/cables keep {@code false}.
	 */
	protected boolean resetProgressOnInputChange() {
		return false;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot == 0 && resetProgressOnInputChange() && !ItemStack.isSameItem(items.get(0), stack)) {
			progress = 0; // input item changed -> restart the operation (TC-MACH-001-FUN04)
		}
		items.set(slot, stack);
		setChanged();
		wake(); // new input / output change — re-evaluate next tick (R-29)
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
		wake();
	}

	// --- Sided automation (R-GUI-05/R-GUI-07): hoppers/pipes must respect slot roles ---

	/** Which slots are extractable by automation. Default: none (storage/generators keep their items). */
	protected boolean isOutputSlot(int slot) {
		return false;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		int[] slots = new int[items.size()];
		for (int i = 0; i < slots.length; i++) {
			slots[i] = i;
		}
		return slots;
	}

	/** Automation may insert only where manual placement is allowed (e.g. never the output slot). */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return canPlaceItem(slot, stack);
	}

	/** Automation may extract only from output slots — never pull unprocessed input or stored fuel. */
	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return isOutputSlot(slot);
	}
}
