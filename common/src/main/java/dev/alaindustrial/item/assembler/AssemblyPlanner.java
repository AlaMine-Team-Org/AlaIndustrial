package dev.alaindustrial.item.assembler;

import static dev.alaindustrial.core.machine.ScratchInventory.findAndTake;
import static dev.alaindustrial.core.machine.ScratchInventory.findAndTakeAny;
import static dev.alaindustrial.core.machine.ScratchInventory.insertInto;
import static dev.alaindustrial.core.machine.ScratchInventory.returnRemainder;

import dev.alaindustrial.core.machine.AssemblyRefusal;
import dev.alaindustrial.core.machine.ScratchInventory.Reserved;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.recipe.CraftingGridMapping;
import dev.alaindustrial.recipe.IngredientSubstitution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

/**
 * Plans one assembler operation against the warehouse (MOD-275, MOD-287) — the Minecraft side of the
 * planner, moved out of {@code AssemblerBlockEntity} by MOD-714.
 *
 * <p>The recipe, the substitution rules and the charge-blind comparison live here; the moves over the
 * scratch lists live in the Minecraft-free {@link dev.alaindustrial.core.machine.ScratchInventory}. The
 * recipe lookups ({@link RecipeCache}, {@link CraftingInput}, {@link CraftingGridMapping}) are kept in
 * this one class on purpose: they are where the API of the two Minecraft lines may drift apart.
 *
 * <p>One instance per machine, because it holds that machine's recipe cache. The warehouse is read
 * <b>once</b> into a scratch list per plan and every ingredient is then resolved against that list — not
 * re-scanned per ingredient.
 */
public final class AssemblyPlanner {

	private static final int GRID_SIZE = BlueprintPattern.GRID_SIZE;
	private static final int GRID_WIDTH = BlueprintPattern.GRID_WIDTH;

	/** Solver for operations, kept apart from the authoring grid's so the two do not evict each other. */
	private final RecipeCache cache = new RecipeCache(10);

	/** What a plan comes to: a {@link Plan} to commit, or the reason there is none. */
	public sealed interface Outcome permits Plan, Refused {
	}

	/** The operation cannot run now; nothing was changed. */
	public record Refused(AssemblyRefusal reason) implements Outcome {
	}

	/**
	 * A fully worked-out operation: the warehouse and the output area exactly as they will look once it
	 * has run. Building one either succeeds completely — ingredients found, recipe resolved, result and
	 * every remainder placed — or fails and changes nothing, which is what "an operation that cannot
	 * finish never starts" means in practice.
	 *
	 * <p>Nothing here touches the world; {@link #commit} is the only writer, and it writes the very lists
	 * the fitting check was performed on. The check and the act are therefore the same code, so they
	 * cannot drift apart and leave items nowhere.
	 */
	public record Plan(Container store, List<ItemStack> storeScratch, List<ItemStack> outputScratch,
			int outputStart) implements Outcome {

		/**
		 * Write the planned state back, slot by slot, touching only what actually changed: the warehouse
		 * through its container, the output area straight into the machine's {@code items} from
		 * {@code outputStart} on.
		 */
		public void commit(List<ItemStack> machineItems) {
			for (int i = 0; i < storeScratch.size(); i++) {
				if (!ItemStack.matches(store.getItem(i), storeScratch.get(i))) {
					store.setItem(i, storeScratch.get(i));
				}
			}
			store.setChanged();
			for (int i = 0; i < outputScratch.size(); i++) {
				int slot = outputStart + i;
				if (!ItemStack.matches(machineItems.get(slot), outputScratch.get(i))) {
					machineItems.set(slot, outputScratch.get(i));
				}
			}
		}
	}

	/** The ingredients reserved for one operation: the grid handed to the recipe and where each came from. */
	private record Reservation(List<ItemStack> grid, int[] source, boolean substituted) {
	}

	/**
	 * Plan the operation {@code blueprint} would run right now.
	 *
	 * @param warehouse looked up only once the blueprint is known to record a pattern; {@code null} when
	 *     no warehouse is attached
	 * @param machine the machine whose output area, slots {@code [outputStart, outputEnd)}, takes the result
	 */
	public Outcome plan(ServerLevel server, ItemStack blueprint, Supplier<@Nullable Container> warehouse,
			Container machine, int outputStart, int outputEnd) {
		BlueprintPattern recorded = AssemblyBlueprintItem.patternOf(blueprint);
		if (recorded.isBlank()) {
			return new Refused(AssemblyRefusal.NO_BLUEPRINT);
		}
		Container store = warehouse.get();
		if (store == null) {
			// No warehouse attached. Defined behaviour, not a crash: the machine has nowhere to take
			// materials from, which is the same situation as an empty warehouse.
			return new Refused(AssemblyRefusal.NO_MATERIALS);
		}
		List<ItemStack> storeScratch = snapshot(store, 0, store.getContainerSize());
		List<ItemStack> outputScratch = snapshot(machine, outputStart, outputEnd);

		// 0. Resolve the recipe from the RECORDED layout, before anything is reserved. Two reasons: the
		//    substitution candidates below have to come out of this recipe's own ingredients, and doing it
		//    here costs nothing extra — the reserved stacks carry the same items, so they resolve to the
		//    same recipe (an `Ingredient` compares items, not components).
		List<ItemStack> recordedGrid = cells(recorded);
		CraftingInput.Positioned recordedPositioned = CraftingInput.ofPositioned(GRID_WIDTH, GRID_WIDTH, recordedGrid);
		Optional<RecipeHolder<CraftingRecipe>> found = cache.get(server, recordedPositioned.input());
		if (found.isEmpty()) {
			// The blueprint's recipe does not exist any more (a datapack changed under it, or the
			// pattern never made anything). The machine stops and says so; it does not throw.
			return new Refused(AssemblyRefusal.NO_RECIPE);
		}
		CraftingRecipe recipe = found.get().value();

		// 1. Reserve one of each recorded ingredient (see reserve).
		Reservation reservation =
				reserve(blueprint, recorded, recipe, recordedPositioned, recordedGrid, storeScratch);
		if (reservation == null) {
			return new Refused(AssemblyRefusal.NO_MATERIALS);
		}
		return assemble(server, blueprint, recipe, reservation, new Plan(store, storeScratch, outputScratch,
				outputStart));
	}

	/**
	 * Assemble the reserved grid and place what it makes, completing {@code draft} — the plan whose
	 * scratch lists the reservation was taken from — or refuse.
	 */
	private static Outcome assemble(ServerLevel server, ItemStack blueprint, CraftingRecipe recipe,
			Reservation reservation, Plan draft) {
		// 2. Build the trimmed input from what was actually reserved. CraftingInput.of() drops empty edge
		//    rows and columns, so `input` is NOT 3x3 and its indices are not grid indices — which is why
		//    the remainder loop in place() goes back through CraftingGridMapping.
		CraftingInput.Positioned positioned = CraftingInput.ofPositioned(GRID_WIDTH, GRID_WIDTH, reservation.grid());
		CraftingInput input = positioned.input();

		// 3. Fixed order: assemble, THEN the remainders from the SAME input, and only then does anything
		//    shrink (in commit()). Any other order loses either the result or the remainders.
		ItemStack result = recipe.assemble(input);
		if (result.isEmpty()) {
			return new Refused(AssemblyRefusal.NO_RECIPE);
		}
		if (reservation.substituted() && !substitutionHolds(server, blueprint, recipe, input, result)) {
			// The stand-ins do not re-assemble into what the blueprint promises. Refuse the operation
			// rather than quietly making something else; the machine reports it as missing materials,
			// which is what it is — the materials it can actually use are not there.
			return new Refused(AssemblyRefusal.NO_MATERIALS);
		}
		NonNullList<ItemStack> remainders = recipe.getRemainingItems(input);

		// 4. The result must fit the output area, and every remainder must land somewhere. If not, the
		//    operation is not planned at all — no EU is spent and no item is left in limbo.
		if (!place(draft.storeScratch(), draft.outputScratch(), result, remainders, positioned,
				reservation.source())) {
			return new Refused(AssemblyRefusal.OUTPUT_FULL);
		}
		return draft;
	}

	/**
	 * Reserve one of each recorded ingredient, remembering which warehouse slot it came from, or
	 * {@code null} when the warehouse cannot supply one.
	 *
	 * <p>The stacks handed to the recipe are the REAL ones (a worn hammer keeps its damage), because the
	 * craft remainder is stack-aware — see the loader hooks from MOD-078. What the player recorded is
	 * always tried FIRST; only when the warehouse cannot supply it, and only when this blueprint has
	 * substitution switched on, is an alternative from this cell's own Ingredient considered.
	 */
	private static @Nullable Reservation reserve(ItemStack blueprint, BlueprintPattern recorded,
			CraftingRecipe recipe, CraftingInput.Positioned recordedPositioned, List<ItemStack> recordedGrid,
			List<ItemStack> storeScratch) {
		boolean maySubstitute = AssemblyBlueprintItem.substitutes(blueprint);
		List<List<Item>> candidates = maySubstitute
				? IngredientSubstitution.candidatesByCell(recipe, recordedPositioned, recordedGrid)
				: List.of();
		boolean substituted = false;
		List<ItemStack> grid = new ArrayList<>(GRID_SIZE);
		int[] source = new int[GRID_SIZE];
		Arrays.fill(source, -1);
		for (int cell = 0; cell < GRID_SIZE; cell++) {
			ItemStack want = recorded.cell(cell);
			if (want.isEmpty()) {
				grid.add(ItemStack.EMPTY);
				continue;
			}
			Optional<Reserved<ItemStack>> reserved = findAndTake(ItemStackOps.INSTANCE, storeScratch, want);
			if (reserved.isEmpty() && maySubstitute && cell < candidates.size()) {
				reserved = findAndTakeAny(ItemStackOps.INSTANCE, storeScratch, candidates.get(cell), want.getItem());
				substituted |= reserved.isPresent();
			}
			if (reserved.isEmpty()) {
				return null;
			}
			source[cell] = reserved.get().slot();
			grid.add(reserved.get().stack());
		}
		return new Reservation(grid, source, substituted);
	}

	/**
	 * Place the result in the output area and every non-empty remainder back where it belongs (its
	 * ingredient's own warehouse slot, {@code source[cell]}, first).
	 *
	 * @return whether everything found a place
	 */
	private static boolean place(List<ItemStack> storeScratch, List<ItemStack> outputScratch, ItemStack result,
			NonNullList<ItemStack> remainders, CraftingInput.Positioned positioned, int[] source) {
		if (!insertInto(ItemStackOps.INSTANCE, outputScratch, result)) {
			return false;
		}
		CraftingInput input = positioned.input();
		for (int i = 0; i < remainders.size(); i++) {
			ItemStack left = remainders.get(i);
			if (left.isEmpty()) {
				continue;
			}
			int cell = CraftingGridMapping.gridIndex(i, input.width(), positioned.left(), positioned.top(),
					GRID_WIDTH);
			int home = cell >= 0 && cell < GRID_SIZE ? source[cell] : -1;
			if (!returnRemainder(ItemStackOps.INSTANCE, storeScratch, outputScratch, home, left)) {
				return false;
			}
		}
		return true;
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
	private static boolean substitutionHolds(ServerLevel server, ItemStack blueprint, CraftingRecipe recipe,
			CraftingInput input, ItemStack result) {
		if (!recipe.matches(input, server)) {
			return false;
		}
		ItemStack promised = AssemblyBlueprintItem.resultOf(blueprint);
		if (promised.isEmpty()) {
			List<ItemStack> recordedGrid = cells(AssemblyBlueprintItem.patternOf(blueprint));
			promised = recipe.assemble(CraftingInput.ofPositioned(GRID_WIDTH, GRID_WIDTH, recordedGrid).input());
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

	/** The nine cells of a recorded pattern in row-major order, empties included. */
	private static List<ItemStack> cells(BlueprintPattern recorded) {
		List<ItemStack> grid = new ArrayList<>(GRID_SIZE);
		for (int cell = 0; cell < GRID_SIZE; cell++) {
			grid.add(recorded.cell(cell));
		}
		return grid;
	}

	/** Copy slots {@code [from, to)} of a container so they can be planned against without side effects. */
	private static List<ItemStack> snapshot(Container container, int from, int to) {
		List<ItemStack> copy = new ArrayList<>(to - from);
		for (int i = from; i < to; i++) {
			copy.add(container.getItem(i).copy());
		}
		return copy;
	}
}
