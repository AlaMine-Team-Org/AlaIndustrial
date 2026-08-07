package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.item.assembler.BlueprintPattern;
import dev.alaindustrial.menu.AssemblerMenu;
import dev.alaindustrial.recipe.CraftingGridMapping;
import dev.alaindustrial.recipe.IngredientSubstitution;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.storage.StorageCluster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Assembler (MOD-275) — the mod's first MV machine: it holds a queue of three
 * {@linkplain dev.alaindustrial.item.assembler.AssemblyBlueprintItem assembly blueprints} and stamps
 * out their crafting-table recipes continuously for EU, taking materials from the adjacent modular
 * warehouse (MOD-287) and dropping the results into its own six-slot output area.
 *
 * <p><b>Slice 3 of MOD-275 — the block and its inventory only.</b> The crafting cycle, the menu and
 * the screen are later slices, so {@link #onServerTick} does no work yet; what is already live is the
 * inventory shape, the MV energy contract, the idle diagnosis and persistence. A blueprint put into a
 * slot survives a chunk/world reload today, because the recipe it carries is a data component on the
 * stack and the base class persists {@code items} wholesale.
 *
 * <p><b>Why it extends {@link MachineBlockEntity} directly</b> instead of
 * {@link AbstractProcessingMachineBlockEntity}: that base hardcodes two slots
 * ({@code super(..., 2, ...)}), declares {@code onServerTick} {@code final} and resolves its recipe
 * from a single {@link ItemStack}. None of the three fits a machine with a blueprint queue, a
 * six-slot output and an off-block material source. {@link GalvanicBathBlockEntity} is the model
 * followed here: a multi-slot machine on the raw base, with its own tick, status enum and widened
 * {@link ContainerData}.
 */
public class AssemblerBlockEntity extends MachineBlockEntity implements MenuProvider {
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

	/** NBT key of the authoring grid. Its own key on purpose — the base writes {@code items} wholesale. */
	private static final String PATTERN_KEY = "Pattern";
	/** NBT key of the queue slot the in-flight operation belongs to. */
	private static final String ACTIVE_KEY = "Active";
	/** NBT key of the round-robin cursor. */
	private static final String CURSOR_KEY = "Cursor";

	private AssemblerStatus status = AssemblerStatus.NO_BLUEPRINT;

	/**
	 * The authoring grid and its resolved result.
	 *
	 * <p><b>Not part of {@code items}.</b> These are ghosts: they hold a copy of one of each ingredient
	 * to show <em>what</em> to record, nothing is ever consumed from them and nothing is ever dropped
	 * out of them. Keeping them out of the machine inventory is what makes that true for free — the
	 * block's loot table, {@code getSlotsForFace} and {@code Containers.dropContents} all work off
	 * {@code items} and cannot see this list, so a ghost can neither be piped out nor duplicated by
	 * breaking the block.
	 *
	 * <p>They still reach the client without a custom packet, because the menu binds them as ordinary
	 * slots and the vanilla container sync ships whatever a slot returns.
	 */
	private final SimpleContainer pattern = new SimpleContainer(PATTERN_SLOT_COUNT);

	/**
	 * Solver for the authoring grid. Ten entries, matching vanilla's own {@code CrafterBlock}, which
	 * holds a {@code new RecipeCache(10)} for exactly this job — repeatedly re-solving a grid that
	 * usually has not changed. Server-side only; the client never resolves anything.
	 */
	private final RecipeCache patternCache = new RecipeCache(10);

	/**
	 * Set on load, cleared by the first server tick that re-solves the preview. Needed because
	 * {@code loadAdditional} runs before the block entity has a {@code level}, so the grid comes back
	 * from disk with no way to ask the recipe manager what it makes.
	 */
	private boolean patternStale = true;

	/** Solver for operations, kept apart from the authoring grid's so the two do not evict each other. */
	private final RecipeCache operationCache = new RecipeCache(10);

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
		this.maxProgress = Config.scaledDuration(Config.assemblerDuration);
	}

	/** Consumer: every face accepts energy except the inert FACING front (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
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
		if (patternStale) {
			patternStale = false;
			refreshPatternResult();
		}
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
			return IDLE_SLEEP_TICKS;
		}
		if (progress < maxProgress) {
			if (energy.amount < Config.assemblerEuPerTick) {
				// Wait, keeping the progress: the ingredients are still in the warehouse (they are only
				// taken at the finish), so nothing is lost and nothing is burnt. Sleeping here rather than
				// re-checking every tick is free — any delivery into the buffer commits a transaction,
				// and that hook wakes the machine on the very next tick (R-29).
				setStatus(AssemblerStatus.NO_ENERGY);
				return IDLE_SLEEP_TICKS;
			}
			energy.amount -= Config.assemblerEuPerTick;
			progress++;
			setStatus(AssemblerStatus.READY);
			markDirtyAndSync();
			return 0;
		}
		// At the finish line: re-plan against the warehouse as it is NOW and commit atomically. A plan
		// that no longer holds (someone emptied the warehouse, the datapack dropped the recipe) parks
		// the machine here — progress and EU are kept, and it completes the moment the plan holds again.
		Operation op = planOperation(server, activeSlot);
		if (op == null) {
			setStatus(planFailure);
			return IDLE_SLEEP_TICKS;
		}
		op.commit();
		creditUsefulWork(server, (long) Config.assemblerEuPerTick * maxProgress); // MOD-133: op → XP
		progress = 0;
		activeSlot = -1;
		setStatus(AssemblerStatus.READY);
		markDirtyAndSync();
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
		AssemblerStatus reason = AssemblerStatus.NO_BLUEPRINT;
		for (int i = 0; i < BLUEPRINT_SLOT_COUNT; i++) {
			int slot = (cursor + i) % BLUEPRINT_SLOT_COUNT;
			if (!AssemblyBlueprintItem.isRecorded(items.get(BLUEPRINT_SLOT_START + slot))) {
				continue;
			}
			Operation op = planOperation(server, slot);
			if (op != null) {
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
			reason = moreActionable(reason, planFailure);
		}
		setStatus(reason);
		return IDLE_SLEEP_TICKS;
	}

	/**
	 * Of two idle reasons, the one worth showing the player.
	 *
	 * <p>With three blueprints stalled for three different reasons, only one line fits — so it shows the
	 * one the player can do something about first: a full output area is a jam they must clear, a
	 * missing recipe is a broken blueprint, and missing materials is the ordinary "waiting for the
	 * pipes" state.
	 */
	private static AssemblerStatus moreActionable(AssemblerStatus a, AssemblerStatus b) {
		return rank(b) > rank(a) ? b : a;
	}

	private static int rank(AssemblerStatus status) {
		return switch (status) {
			case OUTPUT_FULL -> 3;
			case NO_RECIPE -> 2;
			case NO_MATERIALS -> 1;
			default -> 0;
		};
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
	 * A fully worked-out operation: the warehouse and the output area exactly as they will look once it
	 * has run. Building one either succeeds completely — ingredients found, recipe resolved, result and
	 * every remainder placed — or fails and changes nothing, which is what "an operation that cannot
	 * finish never starts" means in practice.
	 *
	 * <p>Nothing here touches the world; {@link #commit()} is the only writer, and it writes the very
	 * arrays the fitting check was performed on. The check and the act are therefore the same code, so
	 * they cannot drift apart and leave items nowhere.
	 */
	private final class Operation {
		private final Container store;
		private final List<ItemStack> storeScratch;
		private final List<ItemStack> outputScratch;

		private Operation(Container store, List<ItemStack> storeScratch, List<ItemStack> outputScratch) {
			this.store = store;
			this.storeScratch = storeScratch;
			this.outputScratch = outputScratch;
		}

		/** Write the planned state back, slot by slot, touching only what actually changed. */
		private void commit() {
			for (int i = 0; i < storeScratch.size(); i++) {
				if (!ItemStack.matches(store.getItem(i), storeScratch.get(i))) {
					store.setItem(i, storeScratch.get(i));
				}
			}
			store.setChanged();
			for (int i = 0; i < outputScratch.size(); i++) {
				int slot = OUTPUT_SLOT_START + i;
				if (!ItemStack.matches(items.get(slot), outputScratch.get(i))) {
					items.set(slot, outputScratch.get(i));
				}
			}
		}
	}

	/**
	 * Plan the operation blueprint {@code slot} would run right now, or {@code null} with
	 * {@link #planFailure} set to the reason it cannot.
	 *
	 * <p>The warehouse is read <b>once</b> into a scratch list and every ingredient is then resolved
	 * against that list — not re-scanned per ingredient — and this runs only twice per operation (once
	 * to decide whether to start, once to finish), never on the thirty-eight ticks in between.
	 */
	private Operation planOperation(ServerLevel server, int slot) {
		plansRun++;
		planFailure = AssemblerStatus.NO_MATERIALS;
		ItemStack blueprint = items.get(BLUEPRINT_SLOT_START + slot);
		BlueprintPattern recorded = AssemblyBlueprintItem.patternOf(blueprint);
		if (recorded.isBlank()) {
			planFailure = AssemblerStatus.NO_BLUEPRINT;
			return null;
		}
		Container store = warehouse(server);
		if (store == null) {
			// No warehouse attached. Defined behaviour, not a crash: the machine has nowhere to take
			// materials from, which is the same situation as an empty warehouse.
			return null;
		}
		List<ItemStack> storeScratch = snapshot(store, 0, store.getContainerSize());
		List<ItemStack> outputScratch = snapshot(this, OUTPUT_SLOT_START, OUTPUT_SLOT_END);

		// 0. Resolve the recipe from the RECORDED layout, before anything is reserved. Two reasons: the
		//    substitution candidates below have to come out of this recipe's own ingredients, and doing it
		//    here costs nothing extra — the reserved stacks carry the same items, so they resolve to the
		//    same recipe (an `Ingredient` compares items, not components).
		List<ItemStack> recordedGrid = new ArrayList<>(PATTERN_GRID_SIZE);
		for (int cell = 0; cell < PATTERN_GRID_SIZE; cell++) {
			recordedGrid.add(recorded.cell(cell));
		}
		CraftingInput.Positioned recordedPositioned = CraftingInput.ofPositioned(
				BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH, recordedGrid);
		Optional<RecipeHolder<CraftingRecipe>> found =
				operationCache.get(server, recordedPositioned.input());
		if (found.isEmpty()) {
			// The blueprint's recipe does not exist any more (a datapack changed under it, or the
			// pattern never made anything). The machine stops and says so; it does not throw.
			planFailure = AssemblerStatus.NO_RECIPE;
			return null;
		}
		CraftingRecipe recipe = found.get().value();

		// 1. Reserve one of each recorded ingredient, remembering which warehouse slot it came from.
		//    The stacks handed to the recipe are the REAL ones (a worn hammer keeps its damage), because
		//    the craft remainder is stack-aware — see the loader hooks from MOD-078.
		//    What the player recorded is always tried FIRST; only when the warehouse cannot supply it, and
		//    only when this blueprint has substitution switched on, is an alternative from this cell's own
		//    Ingredient considered.
		boolean maySubstitute = AssemblyBlueprintItem.substitutes(blueprint);
		List<List<Item>> candidates = maySubstitute
				? IngredientSubstitution.candidatesByCell(recipe, recordedPositioned, recordedGrid)
				: List.of();
		boolean substituted = false;
		List<ItemStack> grid = new ArrayList<>(PATTERN_GRID_SIZE);
		int[] source = new int[PATTERN_GRID_SIZE];
		Arrays.fill(source, -1);
		for (int cell = 0; cell < PATTERN_GRID_SIZE; cell++) {
			ItemStack want = recorded.cell(cell);
			if (want.isEmpty()) {
				grid.add(ItemStack.EMPTY);
				continue;
			}
			Reserved reserved = findAndTake(storeScratch, want);
			if (reserved == null && maySubstitute && cell < candidates.size()) {
				reserved = findAndTakeAny(storeScratch, candidates.get(cell), want.getItem());
				substituted |= reserved != null;
			}
			if (reserved == null) {
				return null; // NO_MATERIALS
			}
			source[cell] = reserved.slot();
			grid.add(reserved.stack());
		}

		// 2. Build the trimmed input from what was actually reserved. CraftingInput.of() drops empty edge
		//    rows and columns, so `input` is NOT 3x3 and its indices are not grid indices — which is why
		//    the remainder loop below goes back through CraftingGridMapping.
		CraftingInput.Positioned positioned =
				CraftingInput.ofPositioned(BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH, grid);
		CraftingInput input = positioned.input();

		// 3. Fixed order: assemble, THEN the remainders from the SAME input, and only then does anything
		//    shrink (in commit()). Any other order loses either the result or the remainders.
		ItemStack result = recipe.assemble(input);
		if (result.isEmpty()) {
			planFailure = AssemblerStatus.NO_RECIPE;
			return null;
		}
		if (substituted && !substitutionHolds(server, blueprint, recipe, input, result)) {
			// The stand-ins do not re-assemble into what the blueprint promises. Refuse the operation
			// rather than quietly making something else; the machine reports it as missing materials,
			// which is what it is — the materials it can actually use are not there.
			return null;
		}
		NonNullList<ItemStack> remainders = recipe.getRemainingItems(input);

		// 4. The result must fit the output area, and every remainder must land somewhere. If not, the
		//    operation is not planned at all — no EU is spent and no item is left in limbo.
		if (!insertInto(outputScratch, result)) {
			planFailure = AssemblerStatus.OUTPUT_FULL;
			return null;
		}
		for (int i = 0; i < remainders.size(); i++) {
			ItemStack left = remainders.get(i);
			if (left.isEmpty()) {
				continue;
			}
			int cell = CraftingGridMapping.gridIndex(i, input.width(), positioned.left(), positioned.top(),
					BlueprintPattern.GRID_WIDTH);
			int home = cell >= 0 && cell < PATTERN_GRID_SIZE ? source[cell] : -1;
			if (!returnRemainder(storeScratch, outputScratch, home, left)) {
				planFailure = AssemblerStatus.OUTPUT_FULL;
				return null;
			}
		}
		return new Operation(store, storeScratch, outputScratch);
	}

	/** One reserved ingredient: the warehouse slot it came out of, and the actual single item taken. */
	private record Reserved(int slot, ItemStack stack) {
	}

	/**
	 * Take one {@code want} out of the warehouse scratch, or {@code null} when there is none.
	 *
	 * <p>Matching is by item, ignoring components, exactly as a crafting {@code Ingredient} does — that
	 * is what lets a worn tool satisfy a recipe written against a fresh one. The stack handed back is
	 * the real one (components and damage intact), because the craft remainder is computed from it.
	 */
	private static Reserved findAndTake(List<ItemStack> storeScratch, ItemStack want) {
		for (int i = 0; i < storeScratch.size(); i++) {
			ItemStack held = storeScratch.get(i);
			if (!held.isEmpty() && ItemStack.isSameItem(held, want)) {
				ItemStack unit = held.copyWithCount(1);
				ItemStack left = held.copy();
				left.shrink(1);
				storeScratch.set(i, left.isEmpty() ? ItemStack.EMPTY : left);
				return new Reserved(i, unit);
			}
		}
		return null;
	}

	/**
	 * Take one item of any {@code candidate} out of the warehouse scratch, skipping {@code recorded} —
	 * the item the blueprint named, which the caller has already tried and failed to find.
	 *
	 * <p>Candidate order is the recipe's own ingredient order, and the first one the warehouse can supply
	 * wins. Deterministic, so two assemblers sharing a warehouse make the same choice.
	 */
	private static Reserved findAndTakeAny(List<ItemStack> storeScratch, List<Item> candidates,
			Item recorded) {
		for (Item candidate : candidates) {
			if (candidate == recorded) {
				continue;
			}
			for (int i = 0; i < storeScratch.size(); i++) {
				ItemStack held = storeScratch.get(i);
				if (held.isEmpty() || !held.is(candidate)) {
					continue;
				}
				ItemStack unit = held.copyWithCount(1);
				ItemStack left = held.copy();
				left.shrink(1);
				storeScratch.set(i, left.isEmpty() ? ItemStack.EMPTY : left);
				return new Reserved(i, unit);
			}
		}
		return null;
	}

	/**
	 * The gate every substituted operation has to pass: the layout still matches the recipe, and it still
	 * makes <b>exactly</b> what the blueprint promised.
	 *
	 * <p>{@code matches} alone is not enough — a recipe can match a layout and produce a different stack
	 * (dyed, damaged, count-varying results), and a blueprint that silently starts making something else
	 * is the worst possible outcome of this feature. So the assembled stack is compared against the
	 * result cached on the blueprint at write time, components and count included
	 * ({@link ItemStack#matches}).
	 *
	 * <p>A blueprint written before results were cached has nothing to compare against; those fall back
	 * to the recipe's own idea of its output for the recorded layout, which is the same thing the machine
	 * would have made without substitution.
	 *
	 * <p><b>Charge is excluded from the comparison</b> (MOD-083). A charge-carrying recipe hands the
	 * result the EU its batteries brought, so the stack this operation makes legitimately differs from
	 * the one the blueprint recorded whenever the warehouse's batteries hold a different amount — and a
	 * component-exact comparison would read that as "this makes something else" and stall the machine on
	 * NO_MATERIALS while staring at a full warehouse. That is the exact failure AE2 hit automating IC2's
	 * RE-Battery, and it is why the two stacks are compared blind to their charge.
	 */
	private boolean substitutionHolds(ServerLevel server, ItemStack blueprint, CraftingRecipe recipe,
			CraftingInput input, ItemStack result) {
		if (!recipe.matches(input, server)) {
			return false;
		}
		ItemStack promised = AssemblyBlueprintItem.resultOf(blueprint);
		if (promised.isEmpty()) {
			List<ItemStack> recordedGrid = new ArrayList<>(PATTERN_GRID_SIZE);
			BlueprintPattern recorded = AssemblyBlueprintItem.patternOf(blueprint);
			for (int cell = 0; cell < PATTERN_GRID_SIZE; cell++) {
				recordedGrid.add(recorded.cell(cell));
			}
			promised = recipe.assemble(CraftingInput.ofPositioned(
					BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH, recordedGrid).input());
		}
		return ItemStack.matches(withoutCharge(promised), withoutCharge(result));
	}

	/**
	 * The stack as it would look with an empty buffer — the form in which two results of a
	 * charge-carrying recipe can be compared for "is this the same thing". Returns the stack itself when
	 * there is nothing to strip, so the common case allocates nothing.
	 */
	private static ItemStack withoutCharge(ItemStack stack) {
		if (ItemEnergy.capacity(stack) <= 0 || ItemEnergy.get(stack) <= 0) {
			return stack;
		}
		ItemStack blind = stack.copy();
		ItemEnergy.set(blind, 0L);
		return blind;
	}

	/**
	 * Put a craft remainder back: into the very warehouse slot its ingredient came from when that slot
	 * can take it, otherwise anywhere in the warehouse, otherwise the output area.
	 *
	 * <p>Preferring the original slot is what makes the Forge Hammer behave: it stays put and simply
	 * loses a point of durability, instead of migrating across the warehouse one craft at a time.
	 */
	private static boolean returnRemainder(List<ItemStack> storeScratch, List<ItemStack> outputScratch,
			int home, ItemStack left) {
		if (home >= 0 && home < storeScratch.size()) {
			ItemStack there = storeScratch.get(home);
			if (there.isEmpty()) {
				storeScratch.set(home, left);
				return true;
			}
			if (ItemStack.isSameItemSameComponents(there, left)
					&& there.getCount() + left.getCount() <= there.getMaxStackSize()) {
				ItemStack merged = there.copy();
				merged.grow(left.getCount());
				storeScratch.set(home, merged);
				return true;
			}
		}
		return insertInto(storeScratch, left) || insertInto(outputScratch, left);
	}

	/** Copy slots {@code [from, to)} of a container so they can be planned against without side effects. */
	private static List<ItemStack> snapshot(Container container, int from, int to) {
		List<ItemStack> copy = new ArrayList<>(to - from);
		for (int i = from; i < to; i++) {
			copy.add(container.getItem(i).copy());
		}
		return copy;
	}

	/**
	 * Insert {@code stack} into a scratch container, merging into matching stacks before using empty
	 * slots. All-or-nothing: a stack that does not fit entirely leaves the scratch untouched, so a
	 * failed plan cannot half-place anything.
	 */
	private static boolean insertInto(List<ItemStack> scratch, ItemStack stack) {
		int remaining = stack.getCount();
		int max = stack.getMaxStackSize();
		int[] add = new int[scratch.size()];
		for (int i = 0; i < scratch.size() && remaining > 0; i++) {
			ItemStack there = scratch.get(i);
			if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack)) {
				int room = Math.min(max, there.getMaxStackSize()) - there.getCount();
				if (room > 0) {
					add[i] = Math.min(room, remaining);
					remaining -= add[i];
				}
			}
		}
		for (int i = 0; i < scratch.size() && remaining > 0; i++) {
			if (scratch.get(i).isEmpty() && add[i] == 0) {
				add[i] = Math.min(max, remaining);
				remaining -= add[i];
			}
		}
		if (remaining > 0) {
			return false;
		}
		for (int i = 0; i < scratch.size(); i++) {
			if (add[i] == 0) {
				continue;
			}
			ItemStack there = scratch.get(i);
			if (there.isEmpty()) {
				scratch.set(i, stack.copyWithCount(add[i]));
			} else {
				ItemStack grown = there.copy();
				grown.grow(add[i]);
				scratch.set(i, grown);
			}
		}
		return true;
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
		return pattern;
	}

	/**
	 * Put one of {@code stack} into ghost cell {@code index} (an empty stack clears it), then re-solve
	 * the preview. Always a single item: a blueprint records what goes where, never how many.
	 */
	public void setPatternCell(int index, ItemStack stack) {
		if (index < 0 || index >= PATTERN_GRID_SIZE) {
			return;
		}
		pattern.setItem(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
		refreshPatternResult();
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

	/** The nine ghost cells in row-major order, empties included — the shape a blueprint records. */
	private List<ItemStack> patternGrid() {
		return pattern.getItems().subList(0, PATTERN_GRID_SIZE);
	}

	/**
	 * Re-solve the authoring grid and park the result in the preview cell. Server-side only: the
	 * client has no recipe manager worth asking, and the preview reaches it as slot content anyway.
	 */
	private void refreshPatternResult() {
		ItemStack result = level instanceof ServerLevel server
				? solve(server, patternGrid()).map(holder -> holder.value().assemble(
						CraftingInput.ofPositioned(BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH,
								patternGrid()).input()))
						.orElse(ItemStack.EMPTY)
				: ItemStack.EMPTY;
		pattern.setItem(PATTERN_RESULT_SLOT, result);
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
		return patternCache.get(server,
				CraftingInput.ofPositioned(BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH, grid).input());
	}

	/** The preview stack the grid currently resolves to; empty when it resolves to nothing. */
	public ItemStack getPatternResult() {
		return pattern.getItem(PATTERN_RESULT_SLOT);
	}

	/**
	 * Whether {@link #writePattern} would do anything right now — the precondition the "Write" button
	 * greys itself out on, so the player learns it from the button instead of from a dead click.
	 *
	 * <p>Deliberately a check on the machine's own state rather than a re-implementation of it in the
	 * menu: the two would drift, and the one that drifts is always the one the player sees.
	 */
	public boolean canWrite() {
		return !getPatternResult().isEmpty() && writeDestination() >= 0;
	}

	/**
	 * Where a freshly recorded blueprint would go, or {@code -1} when it could go nowhere.
	 *
	 * <p>Normally the blank slot itself: one blank in, one blueprint out, in place. Only when the slot
	 * holds a <em>stack</em> of blanks (a pipe can fill it) does the recorded blueprint need a home of
	 * its own — a recorded blueprint never stacks, so it may never merge back onto the remaining
	 * blanks — and then the first empty queue slot takes it.
	 */
	private int writeDestination() {
		ItemStack blank = items.get(BLANK_SLOT);
		if (!blank.is(ModContent.ASSEMBLY_BLUEPRINT.get()) || AssemblyBlueprintItem.isRecorded(blank)) {
			return -1;
		}
		if (blank.getCount() == 1) {
			return BLANK_SLOT;
		}
		for (int i = 0; i < BLUEPRINT_SLOT_COUNT; i++) {
			if (items.get(BLUEPRINT_SLOT_START + i).isEmpty()) {
				return BLUEPRINT_SLOT_START + i;
			}
		}
		return -1;
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
		if (!(level instanceof ServerLevel server)) {
			return false;
		}
		Optional<RecipeHolder<CraftingRecipe>> recipe = solve(server, patternGrid());
		if (recipe.isEmpty()) {
			return false;
		}
		// What the layout makes, resolved here rather than read off the preview cell: the preview is
		// re-solved on the first server tick after a load, and "Write" can be pressed before that tick.
		// This is the one moment the result can be worked out at all — the client has no recipe manager
		// to ask later (ModDataComponents.BLUEPRINT_RESULT), so it goes onto the stack now.
		ItemStack made = recipe.get().value().assemble(
				CraftingInput.ofPositioned(BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH,
						patternGrid()).input());
		int destination = writeDestination();
		if (destination < 0) {
			return false;
		}
		ItemStack blank = items.get(BLANK_SLOT);
		ItemStack recorded = AssemblyBlueprintItem.record(blank, BlueprintPattern.of(patternGrid()), made);
		if (destination == BLANK_SLOT) {
			items.set(BLANK_SLOT, recorded);
		} else {
			blank.shrink(1);
			items.set(destination, recorded);
		}
		setChanged();
		syncBlockEntityToClient();
		wake();
		return true;
	}

	/**
	 * Six-wide data — hides {@link MachineBlockEntity#DATA_COUNT} so
	 * {@code AssemblerBlockEntity.DATA_COUNT} is the single source of this machine's width for both
	 * the bridge below and the menu's client stub (MOD-235). Every channel fits a signed 16-bit short,
	 * which is what {@code ClientboundContainerSetDataPacket} puts on the wire.
	 */
	public static final int DATA_COUNT = 6;

	/**
	 * Base channels 0..3 (energy/capacity/progress/maxProgress) plus the active blueprint slot (4,
	 * {@code -1} when the queue is empty) and the idle reason (5). Both extra channels are derived,
	 * server-authoritative projections; nothing writes them back.
	 */
	private final ContainerData assemblerData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 4 -> activeBlueprintSlot();
				case 5 -> status.ordinal();
				default -> AssemblerBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index < MachineBlockEntity.DATA_COUNT) {
				AssemblerBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return assemblerData;
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

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.assembler");
	}

	// --- persistence: the authoring grid needs its own key (the base writes `items` wholesale) ---

	/**
	 * The nine ghost cells, written cell by cell with the empties kept.
	 *
	 * <p>Written with {@code ItemStack.OPTIONAL_CODEC.listOf()} rather than the ready-made
	 * {@code SimpleContainer#storeAsItemList}: that helper skips empty stacks (verified in the 26.2
	 * bytecode), which for a positional grid means a saved pattern comes back shifted — the one thing
	 * that must not happen to a recipe layout. The derived result preview is not written; it is
	 * re-solved on load.
	 */
	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store(PATTERN_KEY, ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(patternGrid()));
		// The queue state: which blueprint the in-flight operation belongs to (the base persists its
		// progress) and where the round-robin resumes. Without the cursor a reload would restart the
		// rotation at the first blueprint and the fairness it exists for would quietly reset.
		output.putInt(ACTIVE_KEY, activeSlot);
		output.putInt(CURSOR_KEY, cursor);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		activeSlot = Math.clamp(input.getIntOr(ACTIVE_KEY, -1), -1, BLUEPRINT_SLOT_COUNT - 1);
		cursor = Math.clamp(input.getIntOr(CURSOR_KEY, 0), 0, BLUEPRINT_SLOT_COUNT - 1);
		List<ItemStack> saved = input.read(PATTERN_KEY, ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
		for (int i = 0; i < PATTERN_GRID_SIZE; i++) {
			pattern.setItem(i, i < saved.size() ? saved.get(i) : ItemStack.EMPTY);
		}
		// The preview is derived; on load `level` is not set yet, so it is left empty and the first
		// server tick re-solves it.
		pattern.setItem(PATTERN_RESULT_SLOT, ItemStack.EMPTY);
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new AssemblerMenu(syncId, inventory, this,
				ContainerLevelAccess.create(level, worldPosition));
	}

	/** Why the assembler is idle — the status line of its GUI (spec: "Interface"). */
	public enum AssemblerStatus {
		/** Producing (or able to produce) — no complaint. */
		READY,
		/** Every blueprint slot is empty: the machine has nothing to make. */
		NO_BLUEPRINT,
		/** The warehouse cannot supply the active blueprint's ingredients. */
		NO_MATERIALS,
		/** Every output slot is full — production stops without burning EU. */
		OUTPUT_FULL,
		/** The buffer is below one tick's draw. */
		NO_ENERGY,
		/** The blueprint's recipe no longer exists (e.g. the datapack changed under it). */
		NO_RECIPE;

		private static final AssemblerStatus[] VALUES = values();

		/** Resolve a status from the synced ordinal, falling back to {@link #READY} on a bad index. */
		public static AssemblerStatus byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : READY;
		}

		/** Translation key for the screen's status line. */
		public String translationKey() {
			return "gui.alaindustrial.assembler.status." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}
}
