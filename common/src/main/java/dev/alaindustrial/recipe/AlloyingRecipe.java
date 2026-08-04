package dev.alaindustrial.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A multi-input alloying recipe for the Alloy Smelter (MOD-064): two or three sized components melt
 * into one output stack. A real vanilla {@link Recipe}, so it loads from
 * {@code data/<ns>/recipe/alloying/*.json}, refreshes on {@code /reload} and can be overridden by a
 * datapack — the same contract every other machine family gives.
 *
 * <p><b>Why a separate class.</b> {@link AlaProcessingRecipe} already carries one <em>or two</em>
 * inputs, but they are <em>positional</em>: its slot 0 holds raw rubber and slot 1 holds sulfur, and
 * swapping them stops the Vulcanizer working. The smelter accepts its components in any slot in any
 * order, so matching is a bipartite assignment rather than an index-by-index test (see
 * {@link AlloyMatch}) and the JSON field is {@code components} rather than the positional
 * {@code ingredients}. Widening the shared class instead would have made position optional for the
 * two shipped machines that depend on it.
 *
 * <p><b>Counts are not part of {@link #matches}</b>, deliberately, and for the same reason
 * {@link AlaProcessingRecipe} leaves them out: a recipe with too few items still resolves, so the
 * machine can hold the operation and let the player see what is missing instead of the input silently
 * reading as "no matching recipe".
 *
 * @param kind       owning recipe family (type + serializer + default energy)
 * @param components the two or three sized inputs, in no particular order
 * @param result     produced stack template. An {@link ItemStackTemplate} rather than a live
 *                   {@link ItemStack} so the result codec is safe during early datapack loading
 * @param energy     total EU spent to complete one operation
 */
public record AlloyingRecipe(ModRecipes.AlloyKind<AlloyingRecipe> kind, List<AlloyComponent> components,
		ItemStackTemplate result, int energy) implements Recipe<AlloyRecipeInput> {

	/** Fewest components an alloy can have — one input melting into another item is not an alloy. */
	public static final int MIN_COMPONENTS = 2;

	/** Validate and defensively copy the component list. */
	public AlloyingRecipe {
		components = List.copyOf(components);
		if (components.size() < MIN_COMPONENTS || components.size() > AlloyRecipeInput.SLOT_COUNT) {
			throw new IllegalArgumentException("alloying recipe must contain " + MIN_COMPONENTS + ".."
					+ AlloyRecipeInput.SLOT_COUNT + " components, got " + components.size());
		}
	}

	@Override
	public boolean matches(AlloyRecipeInput input, Level level) {
		return assign(input, false) != null;
	}

	/**
	 * Whether the offered slots hold enough of every component for one operation. Separate from
	 * {@link #matches} on purpose — see the class javadoc.
	 */
	public boolean hasEnough(AlloyRecipeInput input) {
		return assign(input, true) != null;
	}

	/**
	 * Which slot feeds which component, or {@code null} when this recipe does not fit the input.
	 *
	 * @param checkCounts when true a slot only satisfies a component if it also holds enough items
	 */
	public int[] assign(AlloyRecipeInput input, boolean checkCounts) {
		// The strictness rule: a two-component recipe must not run with junk in the third slot.
		if (!AlloyMatch.fillMatchesComponents(input.filledSlots(), components.size())) {
			return null;
		}
		boolean[][] accepts = new boolean[components.size()][AlloyRecipeInput.SLOT_COUNT];
		for (int component = 0; component < components.size(); component++) {
			AlloyComponent spec = components.get(component);
			for (int slot = 0; slot < AlloyRecipeInput.SLOT_COUNT; slot++) {
				ItemStack stack = input.getItem(slot);
				accepts[component][slot] = checkCounts ? spec.testItemAndCount(stack) : spec.testItem(stack);
			}
		}
		return AlloyMatch.assign(accepts, AlloyRecipeInput.SLOT_COUNT);
	}

	/**
	 * Consume one operation's worth from {@code slots}, taking each component from the slot it was
	 * matched to.
	 *
	 * <p>Takes the assignment as an argument rather than recomputing it: the caller has already decided
	 * the operation may run, and re-deriving the mapping here could pick a different (equally valid)
	 * assignment than the one the affordability check approved.
	 */
	public void consume(List<ItemStack> slots, int[] assignment) {
		// Guard rather than trust: passing the assignment from assign(input, false) — which ignores
		// counts — would shrink a stack below zero, and an UNASSIGNED entry would index at -1. Both are
		// caller mistakes worth failing loudly on, not silently corrupting an inventory.
		if (assignment == null || assignment.length != components.size()) {
			throw new IllegalArgumentException("alloying assignment does not match the component count");
		}
		for (int component = 0; component < components.size(); component++) {
			int slot = assignment[component];
			AlloyComponent spec = components.get(component);
			if (slot < 0 || slot >= slots.size() || slots.get(slot).getCount() < spec.count()) {
				throw new IllegalArgumentException(
						"alloying assignment points at a slot that cannot pay for its component");
			}
		}
		for (int component = 0; component < components.size(); component++) {
			slots.get(assignment[component]).shrink(components.get(component).count());
		}
	}

	@Override
	public ItemStack assemble(AlloyRecipeInput input) {
		return result.create();
	}

	/** A fresh output {@link ItemStack} (item + count) for the machine's slot logic. */
	public ItemStack resultStack() {
		return result.create();
	}

	/** Whether any component of this recipe accepts {@code stack} — used by the input-slot filter. */
	public boolean acceptsAnywhere(ItemStack stack) {
		for (AlloyComponent component : components) {
			if (component.testItem(stack)) {
				return true;
			}
		}
		return false;
	}

	/** The components' ingredients, in declaration order — for the recipe viewers and placement info. */
	public List<Ingredient> ingredients() {
		List<Ingredient> out = new ArrayList<>(components.size());
		for (AlloyComponent component : components) {
			out.add(component.ingredient());
		}
		return out;
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public RecipeSerializer<? extends Recipe<AlloyRecipeInput>> getSerializer() {
		return kind.serializer();
	}

	@Override
	public RecipeType<? extends Recipe<AlloyRecipeInput>> getType() {
		return kind.type();
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.create(ingredients());
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		// Machines have no recipe-book UI of their own; reuse a vanilla category to satisfy the API.
		return RecipeBookCategories.CRAFTING_MISC;
	}

	/** JSON form: {@code {components: [{ingredient, count?}, …], result, energy?}}. */
	public static MapCodec<AlloyingRecipe> mapCodec(ModRecipes.AlloyKind<AlloyingRecipe> kind) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
				AlloyComponent.CODEC.listOf(MIN_COMPONENTS, AlloyRecipeInput.SLOT_COUNT)
						.fieldOf("components").forGetter(AlloyingRecipe::components),
				ItemStackTemplate.CODEC.fieldOf("result").forGetter(AlloyingRecipe::result),
				// Range-limited: a zero or negative cost would make the machine fall back to its default
				// duration while the recipe viewers divided by it and printed a 0.1 s operation.
				Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("energy", kind.defaultEnergy())
						.forGetter(AlloyingRecipe::energy)
		).apply(instance, (components, result, energy) ->
				new AlloyingRecipe(kind, components, result, energy)));
	}

	/** Network sync codec (recipes travel to the client for the recipe viewers). */
	public static StreamCodec<RegistryFriendlyByteBuf, AlloyingRecipe> streamCodec(
			ModRecipes.AlloyKind<AlloyingRecipe> kind) {
		return StreamCodec.composite(
				AlloyComponent.STREAM_CODEC.apply(ByteBufCodecs.list(AlloyRecipeInput.SLOT_COUNT)),
						AlloyingRecipe::components,
				ItemStackTemplate.STREAM_CODEC, AlloyingRecipe::result,
				ByteBufCodecs.INT, AlloyingRecipe::energy,
				(components, result, energy) -> new AlloyingRecipe(kind, components, result, energy));
	}
}
