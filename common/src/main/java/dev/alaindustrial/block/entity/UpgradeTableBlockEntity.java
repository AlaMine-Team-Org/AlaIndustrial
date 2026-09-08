package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.UpgradeTableBlock;
import dev.alaindustrial.block.WorkstationPart;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.machine.ToolUpgradeStatus;
import dev.alaindustrial.item.tool.DrillUpgrades;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import dev.alaindustrial.menu.UpgradeTableMenu;
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
import org.jspecify.annotations.Nullable;

/**
 * The Upgrade Table (MOD-482): a tool goes in, a module is spent, and the tool comes back out as
 * itself — with one more permanent upgrade written onto it.
 *
 * <h2>Why this is not a processing machine</h2>
 * {@code AbstractProcessingMachineBlockEntity} is built on "input → recipe → a NEW stack in the
 * output slot", and a recipe cannot express what this table does: {@code AlaProcessingRecipe.assemble}
 * ignores its input entirely and builds the result from a static template, so any recipe-shaped answer
 * would hand back a fresh drill and throw away the charge, the enchantments and the Silk Touch mode the
 * player's drill was carrying. The table therefore mutates the components of the very stack in the
 * slot, exactly as the Component Repair Bench does, and needs neither a {@code RecipeType} nor a single
 * line of recipe JSON.
 *
 * <h2>Where the machine lives</h2>
 * The table is two blocks tall, but only the lower half is a machine: it owns the buffer, the slots and
 * the menu, and only its state gets a block entity at all (see {@code UpgradeTableBlock.newBlockEntity}).
 * The upper half is scenery that mirrors the lit state.
 */
public class UpgradeTableBlockEntity extends MachineBlockEntity
		implements MenuProvider, Overclockable {

	/** The tool being upgraded. Stays in place across the whole operation and leaves as itself. */
	public static final int TOOL_SLOT = 0;
	/** The module. Exactly one is consumed per completed installation. */
	public static final int MODULE_SLOT = 1;

	/** Machine-slot count — the client menu stub sizes its container from this (MOD-439). */
	public static final int SLOT_COUNT = 2;

	/** Five-wide data: the shared base 0..3 plus the status. Hides {@link MachineBlockEntity#DATA_COUNT}. */
	public static final int DATA_COUNT = 5;
	/** Channel carrying the {@link ToolUpgradeStatus} code the screen turns into its status line. */
	public static final int STATUS_CHANNEL = 4;

	/**
	 * Current status. Transient: recomputed every server tick and delivered to an open screen through
	 * the menu's {@link ContainerData}, so it is never serialised and never read off a client block
	 * entity.
	 */
	private ToolUpgradeStatus status = ToolUpgradeStatus.NO_TOOL;

	/** The shared eight-step tick loop (MOD-557). */
	private final ProcessingCycle cycle = new ProcessingCycle(this);

	public UpgradeTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.UPGRADE_TABLE_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.upgradeTableDuration);
	}

	/** The table's own tariff: fitting an upgrade is heavier work than smelting an ingot. */
	@Override
	public int baseEuPerTick() {
		return Config.upgradeTableEuPerTick;
	}

	/**
	 * Which upgrade a module installs, or {@code null} if the stack is not a module at all.
	 *
	 * <p>An exhaustive mapping in one place, so a module added later has exactly one thing to update.
	 * It is deliberately item identity rather than a tag: a tag would say "this is a module" without
	 * saying WHICH upgrade it fits, and the mapping has to exist regardless.
	 */
	@Nullable
	public static String upgradeOf(ItemStack module) {
		if (module.is(ModContent.DRILL_COLUMN_MODULE.get())) {
			return DrillUpgrades.COLUMN_BORE;
		}
		return null;
	}

	/** Whether this table can do anything at all with {@code stack} in the tool slot. */
	public static boolean upgradeable(ItemStack stack) {
		return stack.getItem() instanceof ElectricDrillItem;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		ItemStack tool = items.get(TOOL_SLOT);
		ItemStack module = items.get(MODULE_SLOT);
		String upgrade = upgradeOf(module);

		status = ToolUpgradeStatus.resolve(
				upgradeable(tool),
				upgrade != null,
				upgrade != null && DrillUpgrades.has(tool, upgrade));

		ProcessingCycle.Job job = cycle.job(baseEuPerTick(), Config.upgradeTableDuration);

		boolean readyExceptEnergy = status.canWork();
		boolean canWork = readyExceptEnergy && energy.getAmount() >= job.euPerTick();

		return job.canWork(canWork)
				.readyExceptEnergy(readyExceptEnergy)
				.jobIntact(status.jobIntact())
				.run(level, () -> {
					items.get(MODULE_SLOT).shrink(1);
					DrillUpgrades.install(tool, upgrade);
					// The tool's components changed in place — same item, same count — so nothing else
					// would mark the block entity dirty and the open screen would keep drawing the old
					// stack. The repair bench needs this line for the same reason.
					syncBlockEntityToClient();
				});
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (!isMachineHalf()) {
			return false;
		}
		return switch (slot) {
			// Deliberately broad: a drill that already carries the upgrade may be inserted, so the screen
			// can say why nothing happens. Automation is held to the stricter test below.
			case TOOL_SLOT -> upgradeable(stack);
			case MODULE_SLOT -> upgradeOf(stack) != null;
			default -> false;
		};
	}

	/**
	 * Automation may only insert a tool the table can actually work on. Without this a hopper pair would
	 * cycle for ever: {@link #isOutputSlot} hands the finished drill out, and an unrestricted insert
	 * would push it straight back in.
	 */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		if (!isMachineHalf() || !super.canPlaceItemThroughFace(slot, stack, side)) {
			return false;
		}
		if (slot != TOOL_SLOT) {
			return true;
		}
		// A module MUST be loaded for automation to insert a tool. Allowing it with an empty module
		// slot made this predicate and isOutputSlot true at the same time, and a hopper pair then
		// shuttled the same drill in and out for ever — the very loop the repair bench's asymmetry
		// exists to prevent, reintroduced by treating "nothing to do" as "come in".
		String pending = upgradeOf(items.get(MODULE_SLOT));
		return pending != null && upgradeable(stack) && !DrillUpgrades.has(stack, pending);
	}

	/**
	 * The tool slot becomes extractable exactly when the table has finished with what is in it — either
	 * it already carries the loaded module's upgrade, or there is no module to fit. Mid-installation the
	 * drill is not an output and automation cannot take it.
	 */
	@Override
	protected boolean isOutputSlot(int slot) {
		if (slot != TOOL_SLOT) {
			return false;
		}
		ItemStack tool = items.get(TOOL_SLOT);
		if (tool.isEmpty()) {
			return false;
		}
		String pending = upgradeOf(items.get(MODULE_SLOT));
		return pending == null || DrillUpgrades.has(tool, pending);
	}

	/**
	 * Whether this block entity is the assembled lower half — the only piece that is a machine.
	 *
	 * <p>Every state carries a block entity (the repository's menu and capability sweeps probe blocks in
	 * their default state and need one there), so the casing and the upper half own an inventory they
	 * must never let anyone reach. That is what the guards below are for: a hopper aimed at the top of
	 * the table finds a container that reports no slots on any face, rather than a second, invisible
	 * inventory to lose items in.
	 */
	private boolean isMachineHalf() {
		BlockState state = getBlockState();
		return state.getBlock() instanceof UpgradeTableBlock
				&& state.getValue(UpgradeTableBlock.PART) == WorkstationPart.LOWER;
	}

	/** Consumer: every face accepts energy except the inert FACING front (R-NRG-03) — and nothing at all
	 * unless this is the assembled lower half. Mirror of {@code UpgradeTableBlock.isCableConnectable}: a
	 * cable must not draw an arm toward a face that takes nothing. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return isMachineHalf() ? facingAwareRole(worldFace, EnergyRole.IN) : EnergyRole.NONE;
	}

	/** No face of a casing or an upper half exposes a slot — see {@link #isMachineHalf()}. */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return isMachineHalf() ? super.getSlotsForFace(side) : new int[0];
	}

	/** Swapping the tool restarts the installation. */
	@Override
	protected boolean resetProgressOnInputChange() {
		return true;
	}

	/**
	 * Both halves show the same lit state, so the machine reads as one object rather than a lit base
	 * under a dark top. The base implementation would only ever flip the half this block entity sits
	 * on, so this replaces it instead of extending it: {@link UpgradeTableBlock#setLit} writes the pair
	 * and skips whichever half already agrees.
	 */
	@Override
	protected void updateLit(boolean working) {
		if (level == null || level.isClientSide()) {
			return;
		}
		UpgradeTableBlock.setLit(level, getBlockPos(), working);
	}

	private final ContainerData tableData = new ContainerData() {
		@Override
		public int get(int index) {
			return index == STATUS_CHANNEL
					? status.code()
					: UpgradeTableBlockEntity.this.dataAccess.get(index);
		}

		@Override
		public void set(int index, int value) {
			if (index == STATUS_CHANNEL) {
				status = ToolUpgradeStatus.byCode(value);
			} else {
				UpgradeTableBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return tableData;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.upgrade_table");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new UpgradeTableMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
