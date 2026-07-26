package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.IncubatorBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.MutationGrades;
import dev.alaindustrial.menu.IncubatorMenu;
import dev.alaindustrial.mutation.MutationGrade;
import dev.alaindustrial.mutation.MutationRoll;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModTags;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.Nullable;

/**
 * The incubator (MOD-118): irradiates an item with uranium and mutates it.
 *
 * <p>Extends {@link MachineBlockEntity} directly rather than the processing base, which hardcodes a
 * two-slot inventory and seals its tick. Five slots here: the mode chip, the uranium fuel, the item
 * being mutated, the result and the ash.
 *
 * <p>Three things make it unlike every other machine in the mod:
 * <ul>
 * <li><b>The mode comes from the chip</b>, not a stored field — see {@link IncubatorMode}.</li>
 * <li><b>Fuel is a charge, not a per-operation cost.</b> One uranium ingot is pulled the moment it
 *     lands in an assembled machine and powers {@link Config#mutationAttemptsPerIngot} attempts;
 *     when it runs out the ingot turns into ash.</li>
 * <li><b>The outcome is a dice roll.</b> Success yields the recipe result plus a rarity grade;
 *     a small share of misses yields slag; the rest consume the input for nothing.</li>
 * </ul>
 */
public final class IncubatorBlockEntity extends MachineBlockEntity implements MenuProvider {

	public static final int CHIP_SLOT = 0;
	public static final int FUEL_SLOT = 1;
	public static final int INPUT_SLOT = 2;
	public static final int OUTPUT_SLOT = 3;
	public static final int ASH_SLOT = 4;
	public static final int SLOT_COUNT = 5;

	private static final int OUTPUT_MAX = 64;

	/** ContainerData channels beyond the base four; -1 in DATA_MODE means "no chip inserted". */
	public static final int DATA_MODE = 4;
	public static final int DATA_CHARGE = 5;
	public static final int DATA_FORMED = 6;
	public static final int DATA_STATUS = 7;
	/**
	 * Number of {@code ContainerData} channels, and the single place that states it.
	 *
	 * <p>Hides {@link MachineBlockEntity#DATA_COUNT} so {@code IncubatorBlockEntity.DATA_COUNT} names
	 * this machine's own width, for the bridge below and for {@link dev.alaindustrial.menu.IncubatorMenu}'s
	 * client stub (MOD-235). The two used to be independent literals, and adding {@link #DATA_STATUS}
	 * to one of them crashed the screen on open with an {@code ArrayIndexOutOfBoundsException}
	 * (MOD-234) — which is exactly why the width is stated once.
	 */
	public static final int DATA_COUNT = 8;

	private final RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe>[] checks = newChecks();

	/** Remaining irradiation attempts on the ingot currently loaded; 0 means none is loaded. */
	private int charge;
	/** Whether the dome sits on top — an operation will not run without it. */
	private boolean formed;
	/** Why the machine is idle, for the screen's status line. Recomputed every server tick. */
	private IncubatorStatus status = IncubatorStatus.READY;
	/** The glass the player placed, kept so dismantling returns exactly what they used. */
	private BlockState domeSource = Blocks.GLASS.defaultBlockState();

	/**
	 * The outcome of a finished cycle, waiting for somewhere to go. Rolled at the end of the cycle and
	 * held until the output slot can take it, so a graded result — which does not stack with a plain
	 * one — is never written into a slot that cannot merge it and never quietly destroyed along with
	 * the input and the irradiation charge.
	 *
	 * <p>Held, not re-rolled: while it waits, the attempt is already paid for. Emptying and refilling
	 * the input clears it and costs a whole cycle, so there is no way to shop for a better outcome.
	 *
	 * <p>Persisted (see {@link #saveAdditional}): the base class keeps {@code progress}, so a chunk
	 * unload while the outcome waits for a free slot must not hand the player a fresh roll on a cycle
	 * that already stands at full progress.
	 */
	@Nullable
	private MutationRoll.Outcome pendingOutcome;
	/** Grade of {@link #pendingOutcome}; meaningful only for a success. */
	private MutationGrade pendingGrade = MutationGrade.COMMON;
	/** Set by a creative break of the dome so its removal hook hands nothing back. */
	private boolean suppressDomeDrop;

	@SuppressWarnings("unchecked")
	private static RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe>[] newChecks() {
		IncubatorMode[] modes = IncubatorMode.values();
		RecipeManager.CachedCheck<SingleRecipeInput, AlaProcessingRecipe>[] result =
				new RecipeManager.CachedCheck[modes.length];
		for (int i = 0; i < modes.length; i++) {
			result[i] = modes[i].kind().newCheck();
		}
		return result;
	}

	public IncubatorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.INCUBATOR_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.incubatorBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	// ---------------------------------------------------------------- mode + structure

	/** The active mode, or {@code null} when the chip slot is empty (the machine then idles). */
	@Nullable
	public IncubatorMode activeMode() {
		return IncubatorMode.forChip(items.get(CHIP_SLOT));
	}

	public boolean isFormed() {
		return formed;
	}

	/** The glass to hand back when the dome comes off. */
	public BlockState domeSource() {
		return domeSource;
	}

	/** Records which glass became the dome, so the player gets that exact block back. */
	public void rememberDomeSource(BlockState glass) {
		this.domeSource = glass;
		setChanged();
	}

	/** Called by the block when the neighbourhood changes; resets progress if the dome is lost. */
	public void setFormed(boolean value) {
		if (formed == value) {
			return;
		}
		formed = value;
		if (!formed) {
			progress = 0;
		}
		setChanged();
		syncBlockEntityToClient();
		wake();
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// The ingot commits the moment it lands: an assembled machine converts fuel into its
		// irradiation charge right away — chip or no chip, input or no input. The player watches the
		// pips fill as immediate feedback that the uranium is spent (owner acceptance, MOD-118).
		// setItem() wakes the machine, so a hand-fed ingot is consumed on the very next tick.
		if (formed && charge == 0 && canPullFuel()) {
			pullFuel();
			setChanged();
		}

		IncubatorMode mode = activeMode();
		AlaProcessingRecipe recipe = mode != null && level instanceof ServerLevel serverLevel
				? lookup(mode, serverLevel, items.get(INPUT_SLOT))
				: null;

		int euPerTick = euPerTick();
		this.maxProgress = mode == null
				? Config.scaledDuration(Config.mutationDurationTransform)
				: Config.scaledDuration(durationOf(mode, recipe, euPerTick));

		if (recipe == null) {
			pendingOutcome = null;
		}

		// Two different room questions, and keeping them apart is what makes the machine honest.
		// Running is gated on the roll-independent one — can the slot take a plain result at all —
		// so nothing the player can see from outside (the lit texture) is a function of a roll that
		// has not happened yet. The exact outcome is checked only once it exists, below.
		boolean canWork = formed
				&& recipe != null
				&& energy.amount >= euPerTick
				&& charge > 0
				&& hasRoomForPlainResult(recipe)
				&& !(pendingOutcome != null && !hasRoomForOutcome(recipe));

		setStatus(diagnose(mode, recipe, canWork));
		updateLit(canWork);

		if (!canWork) {
			if (recipe == null && progress != 0) {
				progress = 0;
				setChanged();
			}
			return IDLE_SLEEP_TICKS;
		}

		energy.amount -= euPerTick;
		progress++;
		if (progress >= maxProgress) {
			// The dice are thrown here, at the end of a cycle that has already been paid for — never
			// at the start. Rolling early let a player scrub an unwanted outcome for free by pulling
			// the input out and putting it back, and leaked the result through the lit block state
			// before a single tick of work had been done.
			if (pendingOutcome == null) {
				rollPending(level, recipe, mode);
			}
			if (hasRoomForOutcome(recipe)) {
				progress = 0;
				finishAttempt(level, recipe, mode, (long) euPerTick * maxProgress);
			}
			// Otherwise the attempt is held, fully paid and unspent, until the slot has room: progress
			// stays at the top and the roll is kept, so waiting cannot turn into a second chance.
		}
		setChanged();
		return 0;
	}

	/**
	 * Why the machine is idle, in the order the player can act on it: fix the structure, then the
	 * chip, then give it something to mutate, then feed it. Energy is deliberately absent — an empty
	 * buffer is already drawn as an empty energy bar right next to this line.
	 */
	private IncubatorStatus diagnose(@Nullable IncubatorMode mode,
			@Nullable AlaProcessingRecipe recipe, boolean canWork) {
		if (canWork) {
			return IncubatorStatus.READY;
		}
		if (!formed) {
			return IncubatorStatus.NO_DOME;
		}
		if (mode == null) {
			return IncubatorStatus.NO_CHIP;
		}
		if (items.get(INPUT_SLOT).isEmpty()) {
			return IncubatorStatus.NO_INPUT;
		}
		if (recipe == null) {
			return IncubatorStatus.NO_RECIPE;
		}
		// Asked before the room test on purpose: with no charge the machine has not rolled anything,
		// so "clear the result slot" would be advice about a problem that has not happened yet.
		if (charge == 0) {
			return IncubatorStatus.NO_FUEL;
		}
		if (!hasRoomForPlainResult(recipe)
				|| (pendingOutcome != null && !hasRoomForOutcome(recipe))) {
			return IncubatorStatus.OUTPUT_BLOCKED;
		}
		return IncubatorStatus.READY;
	}

	private void setStatus(IncubatorStatus next) {
		if (status != next) {
			status = next;
			setChanged();
		}
	}

	public IncubatorStatus status() {
		return status;
	}

	private int euPerTick() {
		return Math.max(1, Math.round(Config.incubatorEuPerTick * Config.globalMachineSpeedMultiplier));
	}

	/** Recipe energy wins when it is set, so a per-recipe override changes the length of the cycle. */
	private static int durationOf(IncubatorMode mode, @Nullable AlaProcessingRecipe recipe, int euPerTick) {
		if (recipe == null || recipe.energy() <= 0) {
			return mode.baseDuration();
		}
		return Math.max(1, recipe.energy() / Math.max(1, Config.incubatorEuPerTick));
	}

	@Nullable
	private AlaProcessingRecipe lookup(IncubatorMode mode, ServerLevel level, ItemStack input) {
		if (input.isEmpty()) {
			return null;
		}
		return ModRecipes.lookup(checks[mode.ordinal()], level, input);
	}

	// ---------------------------------------------------------------- outcome

	/**
	 * Rolls what the cycle about to start will produce. Both dice are thrown here — the outcome and,
	 * on a success, the grade — because {@link #hasRoomForOutcome} has to know the exact stack before
	 * the input and the charge are spent.
	 */
	private void rollPending(Level level, AlaProcessingRecipe recipe, IncubatorMode mode) {
		MutationGrade inputGrade = MutationGrades.get(items.get(INPUT_SLOT));
		// A recipe may state its own chance (the duplicate value classes do); otherwise the mode's
		// Config default applies.
		double baseChance = recipe.chance() >= 0 ? recipe.chance() : mode.baseChance();
		double chance = MutationRoll.effectiveChance(baseChance, inputGrade, Config.mutationChanceCap);

		DoubleSupplier rng = level.getRandom()::nextDouble;
		pendingOutcome = MutationRoll.rollOutcome(chance, Config.mutationSlagChance, rng);
		pendingGrade = pendingOutcome == MutationRoll.Outcome.SUCCESS
				? MutationRoll.rollGrade(Config.mutationGradeRare, Config.mutationGradeEpic,
						Config.mutationGradeLegendary, rng)
				: MutationGrade.COMMON;
	}

	/**
	 * Resolves one attempt: consumes the input and the charge and writes the outcome rolled at the
	 * start of the cycle. The input is always consumed — a miss is the price of gambling.
	 */
	private void finishAttempt(Level level, AlaProcessingRecipe recipe, IncubatorMode mode, long euSpent) {
		MutationRoll.Outcome outcome = pendingOutcome;
		if (outcome == null) {
			return;
		}
		items.get(INPUT_SLOT).shrink(1);
		spendCharge();

		switch (outcome) {
			case SUCCESS -> {
				addTo(OUTPUT_SLOT, gradedResult(recipe));
				creditUsefulWork(level, euSpent);
			}
			case SLAG -> addTo(OUTPUT_SLOT, new ItemStack(ModContent.IRRADIATED_SLAG.get()));
			case FAILURE -> {
				// The input is gone and nothing takes its place — the honest cost of a miss.
			}
		}
		pendingOutcome = null;
		pendingGrade = MutationGrade.COMMON;
		// The attempt edited the inventory in place rather than through setItem, so nothing has told
		// the client yet. It matters here and not for other machines: the renderer draws the input
		// stack inside the dome, and a stale copy would leave a ghost item floating there. Once per
		// operation, so the cost is nothing.
		syncBlockEntityToClient();
	}

	/** The recipe result carrying the grade rolled for this cycle. */
	private ItemStack gradedResult(AlaProcessingRecipe recipe) {
		ItemStack result = recipe.resultStack();
		MutationGrades.set(result, pendingGrade);
		return result;
	}

	/**
	 * True when both slots can take what this cycle will produce. Checked before starting, against the
	 * already rolled outcome: a graded result does not stack with a plain one, and a miss produces
	 * nothing at all, so only the real stack gives an honest answer. The machine simply stops until
	 * the player empties the slot — nothing is ever consumed for a result that has nowhere to go.
	 */
	private boolean hasRoomForOutcome(AlaProcessingRecipe recipe) {
		// No roll yet means there is nothing to make room for; the caller gates on the plain-result test
		// instead. Not reached today — the roll is made before this is asked — but answering "it fits"
		// blindly would be a quiet trap for whoever reorders the tick next.
		ItemStack outcome = pendingOutcome == null ? ItemStack.EMPTY : switch (pendingOutcome) {
			case SUCCESS -> gradedResult(recipe);
			case SLAG -> new ItemStack(ModContent.IRRADIATED_SLAG.get());
			case FAILURE -> ItemStack.EMPTY;
		};
		return fits(OUTPUT_SLOT, outcome)
				&& fits(ASH_SLOT, new ItemStack(ModContent.DEPLETED_URANIUM.get()));
	}

	/**
	 * The roll-independent room test that gates the cycle: could the slot take an ordinary result of
	 * this recipe? It deliberately ignores grades and slag — the machine must not know, and must not
	 * show, anything about an outcome it has not rolled yet.
	 */
	private boolean hasRoomForPlainResult(AlaProcessingRecipe recipe) {
		return fits(OUTPUT_SLOT, recipe.resultStack())
				&& fits(ASH_SLOT, new ItemStack(ModContent.DEPLETED_URANIUM.get()));
	}

	private boolean fits(int slot, ItemStack candidate) {
		if (candidate.isEmpty()) {
			return true;
		}
		ItemStack current = items.get(slot);
		if (current.isEmpty()) {
			return true;
		}
		if (!ItemStack.isSameItemSameComponents(current, candidate)) {
			return false;
		}
		return current.getCount() + candidate.getCount() <= Math.min(OUTPUT_MAX, current.getMaxStackSize());
	}

	private void addTo(int slot, ItemStack stack) {
		ItemStack current = items.get(slot);
		if (current.isEmpty()) {
			items.set(slot, stack);
		} else if (ItemStack.isSameItemSameComponents(current, stack)) {
			current.grow(stack.getCount());
		}
	}

	// ---------------------------------------------------------------- fuel charge

	private boolean canPullFuel() {
		return items.get(FUEL_SLOT).is(ModTags.Items.INCUBATOR_FUEL);
	}

	private void pullFuel() {
		items.get(FUEL_SLOT).shrink(1);
		charge = Math.max(1, Config.mutationAttemptsPerIngot);
	}

	/** Burns one attempt off the loaded ingot; the last one drops ash into the ash slot. */
	private void spendCharge() {
		charge--;
		if (charge <= 0) {
			charge = 0;
			addTo(ASH_SLOT, new ItemStack(ModContent.DEPLETED_URANIUM.get()));
		}
	}

	public int charge() {
		return charge;
	}

	// ---------------------------------------------------------------- container

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			// One chip at a time: without the empty-slot guard automation would stuff a whole stack
			// into the slot (the lesson MOD-211 left on the solar panel).
			case CHIP_SLOT -> items.get(CHIP_SLOT).isEmpty() && IncubatorMode.forChip(stack) != null;
			case FUEL_SLOT -> stack.is(ModTags.Items.INCUBATOR_FUEL);
			case INPUT_SLOT -> true;
			default -> false;
		};
	}

	/**
	 * Automation takes the products and nothing else: the result and the ash.
	 *
	 * <p>The chip used to be on this list so a hopper could swap the mode, and only the DOWN face was
	 * closed to it. In play that is a trap rather than a feature — any pipe or chest set to drain the
	 * machine walks off with the chip from a side face and the incubator stops dead, with no hint as
	 * to why (owner report, 2026-07-26). Losing the chip costs more than automated mode-switching is
	 * worth, so the chip is now hand-only from every direction; the mode is a build-time decision.
	 * Insertion stays gated by {@link #canPlaceItem}.
	 */
	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == OUTPUT_SLOT || slot == ASH_SLOT;
	}

	@Override
	public EnergyRole energyRoleForFace(Direction face) {
		return facingAwareRole(face, EnergyRole.IN);
	}

	/**
	 * The base only watches slot 0 for the "input changed" reset, and slot 0 here is the chip — so
	 * both the input and the chip are handled explicitly. Swapping either restarts the cycle.
	 */
	@Override
	public void setItem(int slot, ItemStack stack) {
		if ((slot == INPUT_SLOT || slot == CHIP_SLOT)
				&& !ItemStack.isSameItem(items.get(slot), stack)) {
			progress = 0;
			// The pending outcome belongs to the item (and mode) that was in the machine; a swap
			// invalidates it, and the next cycle rolls again.
			pendingOutcome = null;
			pendingGrade = MutationGrade.COMMON;
		}
		super.setItem(slot, stack);
	}

	/**
	 * Hands the dome back before this block entity disappears. Whatever removed the base — a player,
	 * an explosion, {@code /setblock} — this hook still sees a live block entity, while the block's own
	 * removal hook runs after the block entity (and with it {@link #domeSource}) is already gone, which
	 * used to turn a player's coloured glass into plain glass.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			IncubatorBlock.releaseDome(level, pos, domeSource);
		}
		super.preRemoveSideEffects(pos, state);
	}

	/** Marks that the next dome removal at this structure must not drop glass (creative break). */
	public void suppressDomeDrop() {
		suppressDomeDrop = true;
	}

	/** Reads and clears the creative-break flag. */
	public boolean consumeDomeDropSuppression() {
		boolean value = suppressDomeDrop;
		suppressDomeDrop = false;
		return value;
	}

	// ---------------------------------------------------------------- data + persistence

	private final ContainerData incubatorData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_MODE -> {
					IncubatorMode mode = activeMode();
					yield mode == null ? -1 : mode.ordinal();
				}
				case DATA_CHARGE -> charge;
				case DATA_FORMED -> formed ? 1 : 0;
				case DATA_STATUS -> status.ordinal();
				default -> dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case DATA_MODE -> {
					// Derived from the chip slot — nothing to store.
				}
				case DATA_CHARGE -> charge = value;
				case DATA_FORMED -> formed = value != 0;
				case DATA_STATUS -> status = IncubatorStatus.byOrdinal(value);
				default -> dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return incubatorData;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("Charge", charge);
		output.putBoolean("Formed", formed);
		output.store("DomeSource", BlockState.CODEC, domeSource);
		// A held outcome outlives an unload on purpose. The base class persists progress, so without
		// this a player waiting on a blocked slot could leave, come back to a cycle still standing at
		// full progress, and have it rolled again — a free second chance at an attempt already paid for.
		if (pendingOutcome != null) {
			output.putString("PendingOutcome", pendingOutcome.name());
			output.putString("PendingGrade", pendingGrade.serializedName());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		charge = input.getIntOr("Charge", 0);
		formed = input.getBooleanOr("Formed", false);
		domeSource = input.read("DomeSource", BlockState.CODEC)
				.orElse(Blocks.GLASS.defaultBlockState());
		pendingOutcome = input.getString("PendingOutcome").map(IncubatorBlockEntity::outcomeByName)
				.orElse(null);
		pendingGrade = MutationGrade.byName(input.getStringOr("PendingGrade", ""));
	}

	/** Tolerant of a name this build no longer knows: an unreadable outcome simply means "none". */
	@Nullable
	private static MutationRoll.Outcome outcomeByName(String name) {
		for (MutationRoll.Outcome outcome : MutationRoll.Outcome.values()) {
			if (outcome.name().equals(name)) {
				return outcome;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- menu

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.incubator");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new IncubatorMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	/** Convenience for the block/renderer: the stack currently being irradiated. */
	public ItemStack displayedStack() {
		return items.get(INPUT_SLOT);
	}
}
