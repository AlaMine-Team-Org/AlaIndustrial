package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.machine.AssemblyRefusal;
import dev.alaindustrial.core.machine.MachineRates;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.assembler.AssemblyPlanner;
import dev.alaindustrial.item.assembler.BlueprintPattern;
import dev.alaindustrial.menu.AssemblerMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageCluster;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Assembler (MOD-275) — the mod's first MV machine: it holds a queue of three
 * {@linkplain dev.alaindustrial.item.assembler.AssemblyBlueprintItem assembly blueprints} and stamps
 * out their crafting-table recipes continuously for EU, taking materials from the adjacent modular
 * warehouse (MOD-287) and dropping the results into its own six-slot output area.
 *
 * <p><b>What lives here and what does not (MOD-714).</b> This class is the machine: the crafting
 * cycle in {@link #onServerTick} (plan at the start, one tick of EU per working tick, re-plan and commit
 * at the finish), the blueprint queue with its round-robin cursor, the GUI bridge and persistence.
 * Planning one operation against the warehouse is {@link AssemblyPlanner}'s, over the Minecraft-free
 * {@link dev.alaindustrial.core.machine.ScratchInventory}; the Record tab is {@link BlueprintAuthoring};
 * the status line, and which of several idle reasons it shows, is {@link AssemblerStatus}. A queued
 * blueprint survives a reload because the recipe it carries is a data component on the stack and the base
 * class persists {@code items} wholesale.
 *
 * <p><b>Why it extends {@link MachineBlockEntity} directly</b> instead of
 * {@link AbstractProcessingMachineBlockEntity}: that base hardcodes two slots
 * ({@code super(..., 2, ...)}), declares {@code onServerTick} {@code final} and resolves its recipe
 * from a single {@link ItemStack}. None of the three fits a machine with a blueprint queue, a
 * six-slot output and an off-block material source. {@link GalvanicBathBlockEntity} is the model
 * followed here: a multi-slot machine on the raw base, with its own tick, status enum and widened
 * {@link ContainerData}.
 */
public class AssemblerBlockEntity extends MachineBlockEntity implements Overclockable, MenuProvider {
	/** First blueprint slot; the queue occupies {@code [0, BLUEPRINT_SLOT_COUNT)}. */
	public static final int BLUEPRINT_SLOT_START = 0;
	/** Queue length. Three, following Immersive Engineering's assembler — see the OKF spec. */
	public static final int BLUEPRINT_SLOT_COUNT = 3;
	/** First output slot; the output area occupies {@code [3, 3 + OUTPUT_SLOT_COUNT)}. */
	public static final int OUTPUT_SLOT_START = BLUEPRINT_SLOT_START + BLUEPRINT_SLOT_COUNT;
	/**
	 * Output area size. Six rather than one on purpose: a single output slot is the known cause of a
	 * jammed auto-crafter — one full slot and the whole line stops (the spec's "Inventory" section).
	 */
	public static final int OUTPUT_SLOT_COUNT = 6;
	/** One past the last output slot. */
	public static final int OUTPUT_SLOT_END = OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT;
	/**
	 * The blank blueprint "Write" stamps onto — the Record tab's own slot (MOD-275, the tab split).
	 *
	 * <p>Its own slot rather than "any blank sitting in the queue", because the queue lives on the
	 * <em>other</em> tab: a Record tab that needs the player to visit the Work tab first would put the
	 * two jobs back in one place, which is the defect the tabs exist to fix. With this slot the Record
	 * tab is self-sufficient — grid in, blueprint out — and the Work tab consumes the finished item.
	 */
	public static final int BLANK_SLOT = OUTPUT_SLOT_END;
	/** Machine-specific slot count; the four upgrade slots are appended by the base class. */
	public static final int SLOT_COUNT = BLANK_SLOT + 1;

	/** Cells of the authoring grid — the nine ghosts the player lays a recipe out in. */
	public static final int PATTERN_GRID_SIZE = BlueprintPattern.GRID_SIZE;
	/** Index of the resolved-result preview inside the pattern container (server-computed, read-only). */
	public static final int PATTERN_RESULT_SLOT = PATTERN_GRID_SIZE;
	/** Size of the pattern container the menu binds: nine ghost cells plus the result preview. */
	public static final int PATTERN_SLOT_COUNT = PATTERN_GRID_SIZE + 1;

	/** NBT key of the queue slot the in-flight operation belongs to. */
	private static final String ACTIVE_KEY = "Active";
	/** NBT key of the round-robin cursor. */
	private static final String CURSOR_KEY = "Cursor";

	private AssemblerStatus status = AssemblerStatus.NO_BLUEPRINT;

	/** The Record tab: the ghost grid, its preview and the "Write" action (MOD-714 component). */
	private final BlueprintAuthoring authoring = new BlueprintAuthoring(items);

	/** Plans this machine's operations against the warehouse; holds the operations' own recipe cache. */
	private final AssemblyPlanner planner = new AssemblyPlanner();

	/** The queue slot the current operation belongs to, or {@code -1} when nothing is in flight. */
	private int activeSlot = -1;
	/** Where the round-robin starts looking; advanced past whichever blueprint was chosen last. */
	private int cursor = 0;
	/** Why the last {@link #planOperation} call failed — the reason the status line shows. */
	private AssemblerStatus planFailure = AssemblerStatus.NO_MATERIALS;

	/**
	 * How many times this machine has planned an operation since it was loaded.
	 *
	 * <p>Diagnostic, not state: never persisted, never synced, never read by the machine. It exists
	 * because the machine's central performance claim — <em>the warehouse is read twice per operation,
	 * not on every one of the forty ticks in between</em> — is otherwise unobservable from outside, and
	 * a wall-clock benchmark is a poor guard for it: measuring the rig showed that re-planning on every
	 * tick costs only about four times more in total, which any timing threshold loose enough not to
	 * flake would happily let through. Counting the plans turns that claim into an exact assertion
	 * (see {@code AssemblerPerfScenarios}).
	 */
	private int plansRun;

	public AssemblerBlockEntity(BlockPos pos, BlockState state) {
		// EU consumer at MV: maxInsert = tier voltage (so the network sees a consumer), maxExtract = 0.
		// The buffer is the machine's own knob (8000 EU = 25 operations), not the tier default.
		super(ModContent.ASSEMBLER_BE.get(), pos, state, EnergyTier.MV, SLOT_COUNT,
				Config.assemblerBuffer, EnergyTier.MV.maxVoltage(), 0L);
		this.maxProgress = MachineRates.duration(Config.assemblerDuration, Config.globalMachineSpeedMultiplier);
	}

	/** The assembler's own MV tariff — six times an LV machine (MOD-275). */
	@Override
	public int baseEuPerTick() {
		return Config.assemblerEuPerTick;
	}


	/**
	 * EU per working tick, chips and the global speed knob included.
	 *
	 * <p>Note this also closes an older inconsistency: the duration has always gone through
	 * {@code scaledDuration} (in the constructor) while the rate was read raw, so on a server that
	 * retuned {@code globalMachineSpeedMultiplier} the assembler's energy per craft drifted away from
	 * what {@code PERFORMANCE.md} and the recipe viewers both print. Both halves now move together.
	 */
	private int euPerTick() {
		return effectiveEuPerTick(Config.assemblerEuPerTick);
	}

	/**
	 * One tick of the crafting cycle.
	 *
	 * <p>Two states, and the split between them is what keeps the machine cheap and honest:
	 *
	 * <ul>
	 *   <li><b>Working</b> ({@code activeSlot >= 0}, progress below the finish line): draw one tick of
	 *       EU and advance. Nothing is looked up — no warehouse scan, no recipe lookup — so the cost of
	 *       an operation is one plan at the start and one at the finish, not forty.</li>
	 *   <li><b>Idle</b>: pick the next blueprint that can actually run and start it. Picking spends no
	 *       EU at all, and a machine that can start nothing sleeps {@value #IDLE_SLEEP_TICKS} ticks
	 *       instead of re-scanning the warehouse every tick — that is what stops a starved assembler
	 *       from spinning.</li>
	 * </ul>
	 */
	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		authoring.refreshIfStale(this.level);
		if (!(level instanceof ServerLevel server)) {
			return IDLE_SLEEP_TICKS;
		}
		if (activeSlot >= 0) {
			return tickOperation(server);
		}
		return startNextOperation(server);
	}

	/** The in-flight operation: burn a tick of EU, or finish it. */
	private int tickOperation(ServerLevel server) {
		ItemStack blueprint = items.get(BLUEPRINT_SLOT_START + activeSlot);
		if (!AssemblyBlueprintItem.isRecorded(blueprint)) {
			// The blueprint was pulled (or replaced) mid-operation. Drop the operation rather than
			// leave the machine pointing at an empty slot forever; nothing has been consumed yet.
			abortOperation();
			recordEuRate(0);
			return IDLE_SLEEP_TICKS;
		}
		// Re-derived every tick, not once in the constructor: a chip dropped into the panel mid-run has
		// to take effect now, and the old constructor-only value could not see it at all.
		int euPerTick = euPerTick();
		this.maxProgress = effectiveDuration(Config.assemblerDuration);
		if (progress < maxProgress) {
			boolean paid = energy.getAmount() >= euPerTick;
			// MOD-125/MOD-440: the statistics panel's "now" line is this tick's draw. Only the paid ticks
			// report a rate — the planning tick and the finish tick draw nothing and say so.
			recordEuRate(paid ? euPerTick : 0);
			// Mid-run only the supply can be missing (the plan is re-checked at the finish): MOD-712 skills.
			if (!spendOperationEnergy(server, euPerTick, paid, true)) {
				// Wait, keeping the progress: the ingredients are still in the warehouse (they are only
				// taken at the finish), so nothing is lost and nothing is burnt. Sleeping here rather than
				// re-checking every tick is free — any delivery into the buffer commits a transaction,
				// and that hook wakes the machine on the very next tick (R-29).
				setStatus(AssemblerStatus.NO_ENERGY);
				return IDLE_SLEEP_TICKS;
			}
			progress++;
			setStatus(AssemblerStatus.READY);
			setChangedQuietly();
			return 0;
		}
		// At the finish line: re-plan against the warehouse as it is NOW and commit atomically. A plan
		// that no longer holds (someone emptied the warehouse, the datapack dropped the recipe) parks
		// the machine here — progress and EU are kept, and it completes the moment the plan holds again.
		recordEuRate(0);
		AssemblyPlanner.Plan plan = planOperation(server, activeSlot);
		if (plan == null) {
			setStatus(planFailure);
			return IDLE_SLEEP_TICKS;
		}
		plan.commit(items);
		completeOperation(server, (long) euPerTick * maxProgress); // MOD-125/MOD-440 counter, MOD-133 XP
		progress = 0;
		activeSlot = -1;
		setStatus(AssemblerStatus.READY);
		setChangedQuietly();
		syncBlockEntityToClient();
		return 0;
	}

	/**
	 * Pick the next blueprint that can run and start it, walking the queue round-robin from the
	 * persisted cursor.
	 *
	 * <p>Round-robin rather than strict top-down on purpose: under a strict priority the first
	 * blueprint with endless materials would never hand the queue over, and the other two would never
	 * run. The cursor advances past whichever blueprint was chosen, so the next selection starts at the
	 * one after it.
	 */
	private int startNextOperation(ServerLevel server) {
		recordEuRate(0); // picking spends nothing (MOD-125/MOD-440)
		AssemblerStatus reason = AssemblerStatus.NO_BLUEPRINT;
		for (int i = 0; i < BLUEPRINT_SLOT_COUNT; i++) {
			int slot = (cursor + i) % BLUEPRINT_SLOT_COUNT;
			if (!AssemblyBlueprintItem.isRecorded(items.get(BLUEPRINT_SLOT_START + slot))) {
				continue;
			}
			AssemblyPlanner.Plan plan = planOperation(server, slot);
			if (plan != null) {
				// Planned, not committed: the ingredients stay in the warehouse until the finish, which
				// is what makes "no EU is spent unless the operation can actually complete" true.
				activeSlot = slot;
				cursor = (slot + 1) % BLUEPRINT_SLOT_COUNT;
				progress = 0;
				setStatus(AssemblerStatus.READY);
				setChanged();
				return 0;
			}
			// A blueprint that cannot run is skipped; the queue keeps the most actionable of the reasons.
			reason = AssemblerStatus.moreActionable(reason, planFailure);
		}
		setStatus(reason);
		return IDLE_SLEEP_TICKS;
	}

	/** Give up the in-flight operation without consuming anything. */
	private void abortOperation() {
		activeSlot = -1;
		progress = 0;
		setStatus(AssemblerStatus.NO_BLUEPRINT);
		setChanged();
	}

	private void setStatus(AssemblerStatus next) {
		if (status != next) {
			status = next;
			setChanged();
		}
	}

	/** The machine's current idle reason, for the GUI and for tests. */
	public AssemblerStatus getStatus() {
		return status;
	}

	/** The queue slot the machine is working from, or {@code -1} when it is not working. */
	public int activeBlueprintSlot() {
		return activeSlot;
	}

	/** Where the round-robin will look first next time — persisted, so a reload does not reset fairness. */
	public int queueCursor() {
		return cursor;
	}

	/** How many operations this machine has planned since it was loaded — see {@link #plansRun}. */
	public int plansRun() {
		return plansRun;
	}

	// --- Planning one operation against the warehouse ---

	/**
	 * Plan the operation blueprint {@code slot} would run right now, or {@code null} with
	 * {@link #planFailure} set to the reason it cannot. The planning itself is {@link AssemblyPlanner}'s;
	 * this runs only twice per operation (once to decide whether to start, once to finish), never on the
	 * thirty-eight ticks in between.
	 */
	private AssemblyPlanner.@Nullable Plan planOperation(ServerLevel server, int slot) {
		plansRun++;
		planFailure = AssemblerStatus.NO_MATERIALS;
		ItemStack blueprint = items.get(BLUEPRINT_SLOT_START + slot);
		return switch (planner.plan(server, blueprint, () -> warehouse(server), this, OUTPUT_SLOT_START,
				OUTPUT_SLOT_END)) {
			case AssemblyPlanner.Plan plan -> plan;
			case AssemblyPlanner.Refused(AssemblyRefusal reason) -> {
				planFailure = AssemblerStatus.of(reason);
				yield null;
			}
		};
	}

	/**
	 * The warehouse this assembler draws from: the cluster behind the first face-adjacent storage
	 * module, in a fixed {@link Direction} order. Fixed order matters when several assemblers share one
	 * cluster — they then read its slots in the same sequence, so competition for the last plank is
	 * decided the same way every time instead of by tick order alone.
	 */
	private Container warehouse(ServerLevel server) {
		for (Direction dir : Direction.values()) {
			BlockPos side = worldPosition.relative(dir);
			if (server.isLoaded(side) && server.getBlockEntity(side) instanceof StorageModuleBlockEntity) {
				return StorageCluster.of(server, side).container();
			}
		}
		return null;
	}

	// --- Authoring grid: nine ghost cells, the result preview, and the "Write" action ---

	/**
	 * The authoring grid as a container, for the menu to hang slots off. Its cells are ghosts — the
	 * slots the menu builds over it refuse both placement and pickup, so the only way anything lands
	 * here is {@link #setPatternCell}, which the menu drives from the vanilla button channel.
	 */
	public Container getPatternContainer() {
		return authoring.container();
	}

	/**
	 * Put one of {@code stack} into ghost cell {@code index} (an empty stack clears it), then re-solve
	 * the preview. Always a single item: a blueprint records what goes where, never how many.
	 */
	public void setPatternCell(int index, ItemStack stack) {
		if (!authoring.setCell(index, stack, level)) {
			return;
		}
		setChanged();
		syncBlockEntityToClient();
	}

	/**
	 * Flip ingredient substitution on the blueprint in queue slot {@code slot} — the toggle next to the
	 * queue.
	 *
	 * <p>Refuses on a blank or empty slot: there is no recipe for the flag to mean anything against.
	 * Flipping it wakes the machine, because a blueprint that was stalled for want of the exact recorded
	 * item may now be able to run.
	 *
	 * @return whether anything changed
	 */
	public boolean toggleSubstitution(int slot) {
		if (slot < 0 || slot >= BLUEPRINT_SLOT_COUNT) {
			return false;
		}
		ItemStack blueprint = items.get(BLUEPRINT_SLOT_START + slot);
		if (!AssemblyBlueprintItem.isRecorded(blueprint)) {
			return false;
		}
		AssemblyBlueprintItem.toggleSubstitution(blueprint);
		setChanged();
		syncBlockEntityToClient();
		wake();
		return true;
	}

	/**
	 * The crafting recipe a nine-cell grid resolves to, if any.
	 *
	 * <p>{@code RecipeType.CRAFTING} on purpose and nothing else: it covers shaped and shapeless, and
	 * with it every vanilla recipe, every recipe of this mod (they are declared as plain
	 * {@code minecraft:crafting_shaped}), every third-party mod's and every datapack's — with no code
	 * here to make that true. Tag ingredients come along for free, because the stock {@code Ingredient}
	 * does the matching.
	 */
	public Optional<RecipeHolder<CraftingRecipe>> solve(ServerLevel server, List<ItemStack> grid) {
		return authoring.solve(server, grid);
	}

	/** The preview stack the grid currently resolves to; empty when it resolves to nothing. */
	public ItemStack getPatternResult() {
		return authoring.result();
	}

	/**
	 * Whether {@link #writePattern} would do anything right now — the precondition the "Write" button
	 * greys itself out on, so the player learns it from the button instead of from a dead click.
	 *
	 * <p>Deliberately a check on the machine's own state rather than a re-implementation of it in the
	 * menu: the two would drift, and the one that drifts is always the one the player sees.
	 */
	public boolean canWrite() {
		return authoring.canWrite();
	}

	/**
	 * Stamp the authoring grid onto the blank blueprint in the Record tab's slot — the "Write" button.
	 *
	 * <p>Refuses, rather than half-succeeding, when the grid resolves to nothing (writing a blueprint
	 * that can never craft is a trap) or when there is no blank to write on.
	 *
	 * @return whether anything was written
	 */
	public boolean writePattern() {
		if (!(level instanceof ServerLevel server) || !authoring.write(server)) {
			return false;
		}
		setChanged();
		syncBlockEntityToClient();
		wake();
		return true;
	}

	/**
	 * GUI sync channels (MOD-712, BE-7): the base four, the active blueprint slot (-1 when none) and the
	 * {@link AssemblerStatus} ordinal; both read-only.
	 */
	public enum Channel { ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, ACTIVE_SLOT, STATUS }

	/** Width of {@link #getDataAccess()}, which the menu's client stub sizes itself from (MOD-235). */
	public static final int DATA_COUNT = Channel.values().length;

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.read(Channel.ACTIVE_SLOT, () -> activeBlueprintSlot())
				.read(Channel.STATUS, () -> status.ordinal())
				.build();
	}

	/**
	 * Blueprint slots and the blank slot take blueprints; the output area is machine-fill only.
	 *
	 * <p>The blank slot accepts a recorded blueprint too, on purpose: refusing one would make it
	 * impossible to pick a freshly written blueprint back up with a full cursor, and a recorded
	 * blueprint parked there simply blocks the next write until it is taken out.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return (slot >= BLUEPRINT_SLOT_START && slot < OUTPUT_SLOT_START || slot == BLANK_SLOT)
				&& stack.is(ModContent.ASSEMBLY_BLUEPRINT.get());
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot >= OUTPUT_SLOT_START && slot < OUTPUT_SLOT_END;
	}

	// --- persistence: the authoring grid needs its own key (the base writes `items` wholesale) ---

	/** The authoring grid under its own key ({@link BlueprintAuthoring#save}), then the queue state. */
	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		authoring.save(output);
		// The queue state: which blueprint the in-flight operation belongs to (the base persists its
		// progress) and where the round-robin resumes. Without the cursor a reload would restart the
		// rotation at the first blueprint and the fairness it exists for would quietly reset.
		output.putInt(ACTIVE_KEY, activeSlot);
		output.putInt(CURSOR_KEY, cursor);
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		activeSlot = Math.clamp(input.getIntOr(ACTIVE_KEY, -1), -1, BLUEPRINT_SLOT_COUNT - 1);
		cursor = Math.clamp(input.getIntOr(CURSOR_KEY, 0), 0, BLUEPRINT_SLOT_COUNT - 1);
		authoring.load(input);
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new AssemblerMenu(syncId, inventory, this,
				ContainerLevelAccess.create(level, worldPosition));
	}
}
