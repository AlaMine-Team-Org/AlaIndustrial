package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
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
 *       {@link MachineBlockEntity#serverTick} gate can skip the next few ticks until inventory /
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
public abstract class AbstractProcessingMachineBlockEntity extends MachineBlockEntity {
	/** Output stack cap; per-recipe energy sets the duration. Shared across all processing machines. */
	protected static final int OUTPUT_MAX = 64;

	/** Input slot index, shared by every processing machine. Subclasses re-export as {@code public} ({@code CompressorBlockEntity.INPUT_SLOT} etc.) for callers. */
	protected static final int INPUT_SLOT = 0;
	/** Output slot index, shared by every processing machine. Subclasses re-export as {@code public}. */
	protected static final int OUTPUT_SLOT = 1;

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
	 */
	public record RecipeSolution(int energy, ItemStack result) {
		/** No recipe matched — equivalent to "nothing to do this tick". */
		public static RecipeSolution empty() {
			return new RecipeSolution(0, ItemStack.EMPTY);
		}

		/** A mod {@link AlaProcessingRecipe} matched — carries its EU cost and result stack. */
		public static RecipeSolution of(AlaProcessingRecipe recipe) {
			return new RecipeSolution(recipe.energy(), recipe.resultStack());
		}

		public boolean hasRecipe() {
			return !result.isEmpty();
		}
	}

	private final int defaultDuration;

	protected AbstractProcessingMachineBlockEntity(
			BlockEntityType<?> type, BlockPos pos, BlockState state,
			EnergyTier tier, long buffer, int defaultDuration) {
		super(type, pos, state, tier, 2, buffer, EnergyTier.LV.maxVoltage(), 0L);
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
		int euPerTick = Config.machineEuPerTickEffective();
		ItemStack input = items.get(INPUT_SLOT);
		RecipeSolution solution = level instanceof ServerLevel sl
				? resolveInput(sl, input) : RecipeSolution.empty();

		int baseDuration = solution.hasRecipe() && solution.energy() > 0
				? Math.max(1, solution.energy() / Config.machineEuPerTick) : defaultDuration;
		this.maxProgress = Config.scaledDuration(baseDuration);
		boolean canWork = solution.hasRecipe() && energy.amount >= euPerTick && canOutput(solution.result());

		updateLit(canWork);

		if (canWork) {
			energy.amount -= euPerTick;
			progress++;
			if (progress >= maxProgress) {
				progress = 0;
				items.get(INPUT_SLOT).shrink(1);
				addOutput(solution.result());
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

	/** Whether the output slot can accept one more {@code result} stack (empty, or same-item with room). */
	protected final boolean canOutput(ItemStack result) {
		ItemStack out = items.get(OUTPUT_SLOT);
		return out.isEmpty()
				|| (out.getItem() == result.getItem()
						&& out.getCount() + result.getCount() <= Math.min(OUTPUT_MAX, out.getMaxStackSize()));
	}

	/** Place one {@code result} stack into the output slot, growing an existing matching stack if present. */
	protected final void addOutput(ItemStack result) {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, result.copy());
		} else {
			out.grow(result.getCount());
		}
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
	protected static RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe> checkFor(ModRecipes.Kind kind) {
		return kind.newCheck();
	}

	/** Look up a single-kind recipe against a cached check; null when no recipe matches. */
	protected static AlaProcessingRecipe lookupKind(RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe> check,
			ServerLevel level, ItemStack input) {
		return ModRecipes.lookup(check, level, input);
	}
}
