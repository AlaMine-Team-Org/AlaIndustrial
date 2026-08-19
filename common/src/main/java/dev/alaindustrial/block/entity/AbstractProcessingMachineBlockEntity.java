package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Common base for every "one input → one output" processing machine (Compressor, Macerator,
 * Extractor, Electric Furnace). Replaces four near-identical copies of the same tick loop with a
 * single, well-tested implementation; each subclass only declares its recipe source(s), default
 * duration, buffer and energy tier.
 *
 * <p>The per-tick contract pinned here (R-NRG-10, R-29, MOD-133):
 * <ul>
 *   <li><b>Duration</b> — when a recipe is present, {@code maxProgress} = scaled
 *       {@code recipe.energy / machineEuPerTick}; otherwise it falls back to the machine's default
 *       duration so the GUI shows a meaningful bar even while idle.</li>
 *   <li><b>Progress</b> — only advances when EU is available AND the output slot can accept the
 *       result. EU is spent every working tick (energy-first), so a stalled output freezes progress
 *       without burning more energy.</li>
 *   <li><b>Reset</b> — if the recipe disappears (input swapped for something with no recipe),
 *       progress resets to zero; mere power loss or a full output does NOT reset it (it resumes
 *       when work can continue).</li>
 *   <li><b>Idle sleep</b> — when no work is possible, returns {@link #IDLE_SLEEP_TICKS} so the base
 *       {@link EnergyBlockEntity#serverTick} gate can skip the next few ticks until inventory /
 *       energy changes wake it.</li>
 *   <li><b>XP credit</b> — on a completed op, the full EU cost is credited to the owner via
 *       {@link #creditUsefulWork} (the only XP source in the mod).</li>
 * </ul>
 *
 * <p>A subclass overrides {@link #resolveInput} to return a {@link RecipeSolution} for the current
 * input and {@link #canPlaceInput} for slot validation. The base handles the output-slot check, the
 * result placement (stack-merge up to {@code min(OUTPUT_MAX, maxStackSize)}), the EU drain, and the
 * lit-state toggle.
 */
public abstract class AbstractProcessingMachineBlockEntity extends MachineBlockEntity implements Overclockable {
	/** Input slot index, shared by every processing machine. Subclasses re-export as {@code public} ({@code CompressorBlockEntity.INPUT_SLOT} etc.) for callers. */
	protected static final int INPUT_SLOT = 0;
	/** Output slot index, shared by every processing machine. Subclasses re-export as {@code public}. */
	protected static final int OUTPUT_SLOT = 1;
	/** Machine-slot count — input + output — shared by every processing machine; the client menu stubs size their container from it (MOD-439). */
	public static final int SLOT_COUNT = 2;

	/**
	 * {@link ProcessingMachineStatus} ordinal — the family's readout channel (MOD-458), appended after the
	 * base 0..3.
	 */
	public static final int DATA_STATUS = MachineBlockEntity.DATA_COUNT;

	/**
	 * Five-wide data — hides {@link MachineBlockEntity#DATA_COUNT} so this name is the single source of
	 * width for the bridge below AND for every client menu stub in the family (MOD-235).
	 *
	 * <p>Widened here rather than in {@link MachineBlockEntity} on purpose: five menus outside this family
	 * (the generators, the solar panels, the mob repeller) size their stubs from the base constant and
	 * would otherwise inherit a channel nothing ever fills.
	 *
	 * <p><b>A subclass that appends its own channels must offset them from this constant, not from a
	 * literal</b> — {@link SawmillBlockEntity#DATA_MODE} does. Nothing checks the semantics of an index:
	 * {@code MenuDataWidthScenarios} compares channel <em>counts</em>, so two machines claiming index 4
	 * would leave every gate green and merely feed the sawmill's mode buttons a status ordinal.
	 */
	public static final int DATA_COUNT = MachineBlockEntity.DATA_COUNT + 1;

	/**
	 * How many consecutive unpaid evaluations it takes before an empty buffer is reported as
	 * {@link ProcessingMachineStatus#NO_ENERGY}.
	 *
	 * <p>Without this the line strobes. A machine fed slightly under its draw — one LV solar panel at
	 * 1 EU/t against a 2 EU/t machine, an extremely ordinary early setup — banks a tick, spends it, banks
	 * the next: it works every other tick, and a bare {@code buffer < cost} test would flip the caption on
	 * and off at 10 Hz while the arrow visibly advances. Two evaluations is the smallest grace that covers
	 * that pattern, because the alternating case never fails twice in a row.
	 *
	 * <p>Counted in evaluations, not game ticks, deliberately: an idle machine sleeps
	 * {@link #IDLE_SLEEP_TICKS} between ticks, so a truly dead one reports within about two seconds while
	 * a game-time threshold would need the sleep folded back in — and {@code drive()} in the gametests
	 * does not advance game time at all.
	 */
	private static final int STARVED_GRACE_EVALUATIONS = 2;

	/**
	 * The fixed result of resolving the current input against the machine's recipe source(s). The
	 * base class's per-tick loop calls {@link #resolveInput} once per tick to obtain one of these;
	 * subclasses populate it from their recipe source(s).
	 *
	 * @param energy the recipe's EU cost, used to derive the operation duration
	 *     ({@code energy / machineEuPerTick}). When {@code <= 0} (e.g. a vanilla recipe that does not
	 *     carry an EU field), the machine's default duration applies — pass the default explicitly
	 *     only when the subclass wants that fallback.
	 * @param result the assembled output stack; {@link ItemStack#EMPTY} means "no recipe matched".
	 * @param inputCount how many items one operation consumes from the single input slot — the recipe's
	 *     {@code input_counts} entry (MOD-455). Carried as a plain number rather than the whole
	 *     {@link AlaProcessingRecipe} because these machines have exactly ONE input slot, so
	 *     {@code inputCount(0)} is the complete story — and because the Electric Furnace's vanilla
	 *     fallback has no {@code AlaProcessingRecipe} to carry in the first place.
	 */
	public record RecipeSolution(int energy, ItemStack result, int inputCount) {
		/**
		 * A solution consuming one item per operation — the shape of every recipe but the batch ones,
		 * and the form the Electric Furnace's vanilla fallback builds.
		 */
		public RecipeSolution(int energy, ItemStack result) {
			this(energy, result, 1);
		}

		/** No recipe matched — equivalent to "nothing to do this tick". */
		public static RecipeSolution empty() {
			return new RecipeSolution(0, ItemStack.EMPTY);
		}

		/** A mod {@link AlaProcessingRecipe} matched — carries its EU cost, result stack and batch size. */
		public static RecipeSolution of(AlaProcessingRecipe recipe) {
			return new RecipeSolution(recipe.energy(), recipe.resultStack(), recipe.inputCount(0));
		}

		public boolean hasRecipe() {
			return !result.isEmpty();
		}
	}

	private final int defaultDuration;

	/** Why the machine is idle, for the screen. Server-authoritative, derived every tick, not persisted. */
	private ProcessingMachineStatus status = ProcessingMachineStatus.NO_INPUT;

	/** Consecutive evaluations that could not pay for a tick; see {@link #STARVED_GRACE_EVALUATIONS}. */
	private int starvedEvaluations;

	protected AbstractProcessingMachineBlockEntity(
			BlockEntityType<?> type, BlockPos pos, BlockState state,
			EnergyTier tier, long buffer, int defaultDuration) {
		super(type, pos, state, tier, SLOT_COUNT, buffer, EnergyTier.LV.maxVoltage(), 0L);
		this.defaultDuration = defaultDuration;
		this.maxProgress = Config.scaledDuration(defaultDuration);
	}


	/**
	 * Resolve the current input against the machine's recipe source(s). Subclasses override this to
	 * return a {@link RecipeSolution} (use {@link RecipeSolution#of} for a matched
	 * {@link AlaProcessingRecipe}, or build one directly when assembling from a non-mod source like
	 * a vanilla smelting recipe). Called once per tick from {@link #onServerTick}; never called with
	 * an empty input (the base short-circuits first).
	 */
	protected abstract RecipeSolution resolveInput(ServerLevel level, ItemStack input);

	@Override
	protected final int onServerTick(Level level, BlockPos pos, BlockState state) {
		int euPerTick = effectiveEuPerTick(Config.machineEuPerTick);
		ItemStack input = items.get(INPUT_SLOT);
		RecipeSolution solution = level instanceof ServerLevel sl
				? resolveInput(sl, input) : RecipeSolution.empty();

		int baseDuration = solution.hasRecipe() && solution.energy() > 0
				? Math.max(1, solution.energy() / Config.machineEuPerTick) : defaultDuration;
		this.maxProgress = effectiveDuration(baseDuration);
		// MOD-455: a batch recipe (glowstone dust ×4) needs its whole price on hand every tick, not just
		// at completion — checking it only in the completion branch would let one dust buy a full block.
		boolean canWork = solution.hasRecipe() && input.getCount() >= solution.inputCount()
				&& energy.getAmount() >= euPerTick
				&& canOutput(OUTPUT_SLOT, solution.result());

		// MOD-458: the starvation counter is driven by the SAME expression that gates work, so the caption
		// can never contradict the arrow. It resets on any paid tick, including one that finishes an op.
		if (canWork) {
			starvedEvaluations = 0;
		} else if (energy.getAmount() < euPerTick) {
			starvedEvaluations = Math.min(starvedEvaluations + 1, STARVED_GRACE_EVALUATIONS);
		}
		setStatus(diagnose(input, solution, euPerTick));

		updateLit(canWork);

		// MOD-125: the statistics panel's "now" line for a consumer is its draw, and a stopped machine must
		// report 0 rather than keep its last reading — the same contract generators follow.
		recordEuRate(canWork ? euPerTick : 0);

		if (canWork) {
			energy.drainInternal(euPerTick);
			progress++;
			if (progress >= maxProgress) {
				progress = 0;
				items.get(INPUT_SLOT).shrink(solution.inputCount());
				addOutput(OUTPUT_SLOT, solution.result());
				recordItemProcessed(); // MOD-125: lifetime operation counter (persisted, drawn later)
				creditUsefulWork(level, (long) euPerTick * maxProgress); // MOD-133: completed op → XP
			}
			setChanged();
		} else if (!solution.hasRecipe() && progress != 0) {
			// Recipe gone (input removed/changed): reset progress. On mere power loss or a full output
			// (recipe still present) neither branch runs, so progress stays FROZEN and resumes when work
			// can continue (R-NRG-10).
			progress = 0;
			setChanged();
		}
		// Idle (no recipe / no power / output full) → sleep until input, energy or output changes (R-29).
		return canWork ? 0 : IDLE_SLEEP_TICKS;
	}

	/**
	 * Why the machine is idle, in the order the player should fix things (MOD-458).
	 *
	 * <p>Each test is only meaningful once the ones above it pass, so the order is structural rather than
	 * a matter of taste: there is no useful recipe verdict on an empty slot, no batch size without a
	 * recipe, and no "output blocked" without a result stack to test the output against.
	 *
	 * <p><b>Energy comes last, and only after a grace period.</b> Last because the energy bar sits a few
	 * pixels from this line and already draws an empty buffer — the Incubator omits the state outright for
	 * exactly that reason. It is reported here anyway because an unpowered machine is the one stall a new
	 * player has no vocabulary for; the grace period is what keeps that honest rather than strobing (see
	 * {@link #STARVED_GRACE_EVALUATIONS}).
	 */
	private ProcessingMachineStatus diagnose(ItemStack input, RecipeSolution solution, int euPerTick) {
		if (input.isEmpty()) {
			return ProcessingMachineStatus.NO_INPUT;
		}
		if (!solution.hasRecipe()) {
			return ProcessingMachineStatus.NO_RECIPE;
		}
		if (input.getCount() < solution.inputCount()) {
			return ProcessingMachineStatus.NOT_ENOUGH_INPUT;
		}
		if (!canOutput(OUTPUT_SLOT, solution.result())) {
			return ProcessingMachineStatus.OUTPUT_BLOCKED;
		}
		if (energy.getAmount() < euPerTick && starvedEvaluations >= STARVED_GRACE_EVALUATIONS) {
			return ProcessingMachineStatus.NO_ENERGY;
		}
		return ProcessingMachineStatus.READY;
	}

	private void setStatus(ProcessingMachineStatus next) {
		if (status != next) {
			status = next;
			setChanged();
		}
	}

	/** Why the machine is idle. Read by the menu's readout channel and by the gametests. */
	public ProcessingMachineStatus status() {
		return status;
	}

	/**
	 * Five-wide bridge: 0..3 delegate to the shared machine data, {@link #DATA_STATUS} carries the
	 * diagnosis. Exposed to subclasses so one that appends further channels delegates <em>here</em> and
	 * not to {@code dataAccess} — routing round this bridge would drop the status silently.
	 */
	protected final ContainerData processingData = new ContainerData() {
		@Override
		public int get(int index) {
			return index == DATA_STATUS ? status.ordinal() : dataAccess.get(index);
		}

		@Override
		public void set(int index, int value) {
			// The diagnosis is derived and server-authoritative; only the base channels take a write.
			if (index < DATA_STATUS) {
				dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return processingData;
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == OUTPUT_SLOT;
	}

	/** Consumer: every face accepts energy except the inert FACING front (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	@Override
	protected boolean resetProgressOnInputChange() {
		return true;
	}

	/**
	 * Whether the given stack is a valid input for this machine (used by {@code canPlaceItem} so the
	 * GUI rejects invalid inserts). Default: any stack that resolves to a recipe via this machine's
	 * {@link #resolveInput}. Override when the machine has additional recipe sources that should be
	 * accepted before the resolver runs (e.g. Electric Furnace also accepts vanilla smelting recipes).
	 */
	protected boolean canPlaceInput(ItemStack stack) {
		return level instanceof ServerLevel sl && !stack.isEmpty() && resolveInput(sl, stack).hasRecipe();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == INPUT_SLOT && canPlaceInput(stack);
	}

	/**
	 * Helper for subclasses whose primary recipe source is a single mod {@link ModRecipes.Kind}
	 * (Compressor / Macerator / Extractor). Returns a cached-lookup {@link RecipeManager.CachedCheck}
	 * bound to the given kind; pass it to {@link #lookupKind} inside the subclass's
	 * {@link #resolveInput} override.
	 */
	protected static RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> checkFor(
			ModRecipes.Kind kind) {
		return kind.newCheck();
	}

	/** Look up a single-kind recipe against a cached check; null when no recipe matches. */
	protected static AlaProcessingRecipe lookupKind(
			RecipeManager.CachedCheck<ProcessingRecipeInput, AlaProcessingRecipe> check,
			ServerLevel level, ItemStack input) {
		return ModRecipes.lookup(check, level, input);
	}
}
