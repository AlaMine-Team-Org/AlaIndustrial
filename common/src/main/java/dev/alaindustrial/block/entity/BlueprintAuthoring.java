package dev.alaindustrial.block.entity;

import static dev.alaindustrial.block.entity.AssemblerBlockEntity.BLANK_SLOT;
import static dev.alaindustrial.block.entity.AssemblerBlockEntity.BLUEPRINT_SLOT_COUNT;
import static dev.alaindustrial.block.entity.AssemblerBlockEntity.BLUEPRINT_SLOT_START;
import static dev.alaindustrial.block.entity.AssemblerBlockEntity.PATTERN_GRID_SIZE;
import static dev.alaindustrial.block.entity.AssemblerBlockEntity.PATTERN_RESULT_SLOT;
import static dev.alaindustrial.block.entity.AssemblerBlockEntity.PATTERN_SLOT_COUNT;

import dev.alaindustrial.item.assembler.AssemblyBlueprintItem;
import dev.alaindustrial.item.assembler.BlueprintPattern;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The assembler's Record tab (MOD-275): nine ghost cells, the result they resolve to, and the "Write"
 * action that stamps them onto a blank blueprint. A component of {@link AssemblerBlockEntity} since
 * MOD-714; the block entity keeps the public methods the menu calls and delegates here, and it owns the
 * side effects (marking itself changed, syncing, waking).
 */
final class BlueprintAuthoring {

	/** NBT key of the authoring grid. Its own key on purpose — the base writes {@code items} wholesale. */
	private static final String PATTERN_KEY = "Pattern";

	/** The machine's own inventory, where the blank blueprint is read from and the written one goes. */
	private final List<ItemStack> items;

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

	BlueprintAuthoring(List<ItemStack> items) {
		this.items = items;
	}

	/** The authoring grid as a container, for the menu to hang slots off. */
	Container container() {
		return pattern;
	}

	/** Re-solve the preview once after a load, on the first server tick that has a level to ask. */
	void refreshIfStale(@Nullable Level level) {
		if (patternStale) {
			patternStale = false;
			refreshPatternResult(level);
		}
	}

	/**
	 * Put one of {@code stack} into ghost cell {@code index} (an empty stack clears it), then re-solve
	 * the preview. Always a single item: a blueprint records what goes where, never how many.
	 *
	 * @return whether {@code index} is a cell at all; nothing changed when it is not
	 */
	boolean setCell(int index, ItemStack stack, @Nullable Level level) {
		if (index < 0 || index >= PATTERN_GRID_SIZE) {
			return false;
		}
		pattern.setItem(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
		refreshPatternResult(level);
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
	private void refreshPatternResult(@Nullable Level level) {
		ItemStack result = level instanceof ServerLevel server
				? solve(server, patternGrid()).map(holder -> holder.value().assemble(
						CraftingInput.ofPositioned(BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH,
								patternGrid()).input()))
						.orElse(ItemStack.EMPTY)
				: ItemStack.EMPTY;
		pattern.setItem(PATTERN_RESULT_SLOT, result);
	}

	/** The crafting recipe a nine-cell grid resolves to, if any — see {@link AssemblerBlockEntity#solve}. */
	Optional<RecipeHolder<CraftingRecipe>> solve(ServerLevel server, List<ItemStack> grid) {
		return patternCache.get(server,
				CraftingInput.ofPositioned(BlueprintPattern.GRID_WIDTH, BlueprintPattern.GRID_WIDTH, grid).input());
	}

	/** The preview stack the grid currently resolves to; empty when it resolves to nothing. */
	ItemStack result() {
		return pattern.getItem(PATTERN_RESULT_SLOT);
	}

	/** Whether {@link #write} would do anything right now — see {@link AssemblerBlockEntity#canWrite}. */
	boolean canWrite() {
		return !result().isEmpty() && writeDestination() >= 0;
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
	 * Stamp the authoring grid onto the blank blueprint in the Record tab's slot — see
	 * {@link AssemblerBlockEntity#writePattern}.
	 *
	 * @return whether anything was written
	 */
	boolean write(ServerLevel server) {
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
		return true;
	}

	/**
	 * Write the nine ghost cells, cell by cell with the empties kept.
	 *
	 * <p>Written with {@code ItemStack.OPTIONAL_CODEC.listOf()} rather than the ready-made
	 * {@code SimpleContainer#storeAsItemList}: that helper skips empty stacks (verified in the 26.2
	 * bytecode), which for a positional grid means a saved pattern comes back shifted — the one thing
	 * that must not happen to a recipe layout. The derived result preview is not written; it is
	 * re-solved on load.
	 */
	void save(ValueOutput output) {
		output.store(PATTERN_KEY, ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(patternGrid()));
	}

	/** Read the nine ghost cells back onto their own indices; a short or missing list leaves the rest empty. */
	void load(ValueInput input) {
		List<ItemStack> saved = input.read(PATTERN_KEY, ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
		for (int i = 0; i < PATTERN_GRID_SIZE; i++) {
			pattern.setItem(i, i < saved.size() ? saved.get(i) : ItemStack.EMPTY);
		}
		// The preview is derived; on load `level` is not set yet, so it is left empty and the first
		// server tick re-solves it.
		pattern.setItem(PATTERN_RESULT_SLOT, ItemStack.EMPTY);
	}
}
