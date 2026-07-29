package dev.alaindustrial.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.registry.ModRecipes;
import java.util.List;
import java.util.Optional;
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
 * A one- or two-input → single-output processing recipe for an Industrialization machine, expressed
 * as a <em>real</em> vanilla
 * {@link Recipe}. Loaded by the vanilla {@link net.minecraft.world.item.crafting.RecipeManager} from
 * {@code data/<ns>/recipe/**.json} (so {@code /reload} refreshes it, datapacks can add/override it,
 * and each ingredient may be a tag — see R-14/R-15). Replaces the previous hand-rolled JSON loaders
 * ({@code MacerationRecipes} / {@code ProcessingRecipes}).
 *
 * <p>The {@link ModRecipes.Kind} carries which machine this recipe belongs to (its {@link RecipeType}
 * + {@link RecipeSerializer}), so the four machine types stay distinct while sharing this one class.
 *
 * @param kind       owning machine type (type + serializer + default energy)
 * @param ingredients one or two ordered accepted inputs (each an item or tag)
 * @param inputCounts how many items each ingredient consumes, positionally. Omitted in JSON means one
 *                   of each — the shape every machine but the Vulcanizer uses. Counts are deliberately
 *                   NOT part of {@link #matches} (a recipe with too few items still resolves), so a
 *                   machine can tell the player "not enough sulfur" instead of "no matching recipe";
 *                   the machine gates work on {@link #hasEnough} and consumes via {@link #consume}
 * @param result     produced stack template (item + count). An {@link ItemStackTemplate} rather than a
 *                   live {@link ItemStack} so the result codec is safe during early datapack loading
 *                   (a raw {@code ItemStack.CODEC} throws "item does not have components yet" there).
 * @param energy     total EU spent to complete one operation
 * @param chance     probability the operation actually yields its result (MOD-118), or
 *                   {@link #CHANCE_UNSET} when the recipe does not state one and the machine should
 *                   fall back to its own default. Only the incubator reads this; every other machine
 *                   is deterministic and ignores it.
 */
public record AlaProcessingRecipe(ModRecipes.Kind kind, List<Ingredient> ingredients, List<Integer> inputCounts,
		ItemStackTemplate result, int energy, double chance) implements Recipe<ProcessingRecipeInput> {

	/**
	 * Sentinel for "this recipe does not state a chance". A machine that cares (the incubator) then
	 * uses its own per-mode default from Config; the deterministic machines never read the field.
	 */
	public static final double CHANCE_UNSET = -1.0;

	/** Validate and defensively copy the one- or two-ingredient contract. */
	public AlaProcessingRecipe {
		ingredients = List.copyOf(ingredients);
		if (ingredients.isEmpty() || ingredients.size() > 2) {
			throw new IllegalArgumentException("processing recipe must contain 1 or 2 ingredients");
		}
		// An empty list is the JSON default "one of each" — expand it so every reader can index
		// positionally without a special case.
		inputCounts = inputCounts.isEmpty()
				? ingredients.stream().map(ignored -> 1).toList()
				: List.copyOf(inputCounts);
		if (inputCounts.size() != ingredients.size()) {
			throw new IllegalArgumentException("processing recipe 'input_counts' needs one entry per ingredient");
		}
		for (int count : inputCounts) {
			if (count < 1 || count > MAX_INPUT_COUNT) {
				throw new IllegalArgumentException("processing recipe input count out of range: " + count);
			}
		}
	}

	/** Upper bound for a single input — one vanilla stack; the machine slots hold no more. */
	public static final int MAX_INPUT_COUNT = 64;

	/** Backward-compatible Java constructor for the existing one-input machines and recipe mirrors. */
	public AlaProcessingRecipe(ModRecipes.Kind kind, Ingredient ingredient, ItemStackTemplate result,
			int energy, double chance) {
		this(kind, List.of(ingredient), List.of(), result, energy, chance);
	}

	/** A recipe with no stated chance — the form every machine but the incubator uses. */
	public AlaProcessingRecipe(ModRecipes.Kind kind, Ingredient ingredient, ItemStackTemplate result, int energy) {
		this(kind, List.of(ingredient), List.of(), result, energy, CHANCE_UNSET);
	}

	/** A one- or two-input recipe consuming one of each, with no stated chance. */
	public AlaProcessingRecipe(ModRecipes.Kind kind, List<Ingredient> ingredients, ItemStackTemplate result,
			int energy) {
		this(kind, ingredients, List.of(), result, energy, CHANCE_UNSET);
	}

	/** How many items input {@code index} consumes per operation. */
	public int inputCount(int index) {
		return inputCounts.get(index);
	}

	/** True when every input consumes exactly one — the shape of all but the Vulcanizer's recipe. */
	public boolean consumesOneEach() {
		return inputCounts.stream().allMatch(count -> count == 1);
	}

	/**
	 * Whether the offered slots hold enough of each input for one operation. Separate from
	 * {@link #matches} on purpose: the recipe still resolves with too few items, so the machine can
	 * name the missing ingredient instead of reporting "no matching recipe".
	 */
	public boolean hasEnough(ProcessingRecipeInput input) {
		for (int i = 0; i < inputCounts.size(); i++) {
			if (input.getItem(i).getCount() < inputCounts.get(i)) {
				return false;
			}
		}
		return true;
	}

	/** Consume one operation's worth from the positional input slots. */
	public void consume(List<ItemStack> slots) {
		for (int i = 0; i < inputCounts.size(); i++) {
			slots.get(i).shrink(inputCounts.get(i));
		}
	}

	@Override
	public boolean matches(ProcessingRecipeInput input, Level level) {
		if (input.size() != ingredients.size()) {
			return false;
		}
		for (int i = 0; i < ingredients.size(); i++) {
			if (!ingredients.get(i).test(input.getItem(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack assemble(ProcessingRecipeInput input) {
		return result.create();
	}

	/**
	 * The first ingredient, retained for source compatibility with one-input consumers.
	 * Multi-input-aware code must use {@link #ingredients()}.
	 */
	public Ingredient ingredient() {
		return ingredients.getFirst();
	}

	/** A fresh output {@link ItemStack} (item + count) for the machine's slot logic. */
	public ItemStack resultStack() {
		return result.create();
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
	public RecipeSerializer<? extends Recipe<ProcessingRecipeInput>> getSerializer() {
		return kind.serializer();
	}

	@Override
	public RecipeType<? extends Recipe<ProcessingRecipeInput>> getType() {
		return kind.type();
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.create(ingredients);
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		// Machines have no recipe-book UI of their own; reuse a vanilla category to satisfy the API.
		return RecipeBookCategories.CRAFTING_MISC;
	}

	private record IngredientFields(Optional<List<Ingredient>> ingredients, Optional<Ingredient> ingredient) {
		private static final MapCodec<IngredientFields> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Ingredient.CODEC.listOf(1, 2).optionalFieldOf("ingredients")
						.forGetter(IngredientFields::ingredients),
				Ingredient.CODEC.optionalFieldOf("ingredient").forGetter(IngredientFields::ingredient)
		).apply(instance, IngredientFields::new));

		private static DataResult<List<Ingredient>> decode(IngredientFields fields) {
			if (fields.ingredients.isPresent() == fields.ingredient.isPresent()) {
				return DataResult.error(() ->
						"processing recipe must define exactly one of 'ingredient' or 'ingredients'");
			}
			if (fields.ingredients.isPresent() && fields.ingredients.orElseThrow().size() != 2) {
				return DataResult.error(() ->
						"processing recipe field 'ingredients' must contain exactly 2 positional inputs");
			}
			return DataResult.success(fields.ingredients.orElseGet(() -> List.of(fields.ingredient.orElseThrow())));
		}

		private static DataResult<IngredientFields> encode(List<Ingredient> ingredients) {
			return ingredients.size() == 1
					? DataResult.success(new IngredientFields(Optional.empty(), Optional.of(ingredients.getFirst())))
					: DataResult.success(new IngredientFields(Optional.of(ingredients), Optional.empty()));
		}
	}

	private static final MapCodec<List<Ingredient>> INGREDIENTS_CODEC =
			IngredientFields.CODEC.flatXmap(IngredientFields::decode, IngredientFields::encode);

	/** MapCodec for a machine kind's JSON form: {@code {ingredient|ingredients, result, energy?, chance?}}. */
	public static MapCodec<AlaProcessingRecipe> mapCodec(ModRecipes.Kind kind) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
				INGREDIENTS_CODEC.forGetter(AlaProcessingRecipe::ingredients),
				// Written back only when it carries information, so the 60-odd one-of-each recipes
				// round-trip byte-identical instead of growing a noise field.
				Codec.INT.listOf(1, 2).optionalFieldOf("input_counts").forGetter(recipe ->
						recipe.consumesOneEach() ? Optional.empty() : Optional.of(recipe.inputCounts())),
				ItemStackTemplate.CODEC.fieldOf("result").forGetter(AlaProcessingRecipe::result),
				Codec.INT.optionalFieldOf("energy", kind.defaultEnergy()).forGetter(AlaProcessingRecipe::energy),
				Codec.DOUBLE.optionalFieldOf("chance", CHANCE_UNSET).forGetter(AlaProcessingRecipe::chance)
		).apply(instance, (ingredients, inputCounts, result, energy, chance) ->
				new AlaProcessingRecipe(kind, ingredients, inputCounts.orElse(List.of()), result, energy, chance)));
	}

	/** Network sync codec for a machine kind. */
	public static StreamCodec<RegistryFriendlyByteBuf, AlaProcessingRecipe> streamCodec(ModRecipes.Kind kind) {
		return StreamCodec.composite(
				Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list(2)), AlaProcessingRecipe::ingredients,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(2)), AlaProcessingRecipe::inputCounts,
				ItemStackTemplate.STREAM_CODEC, AlaProcessingRecipe::result,
				ByteBufCodecs.INT, AlaProcessingRecipe::energy,
				ByteBufCodecs.DOUBLE, AlaProcessingRecipe::chance,
				(ingredients, inputCounts, result, energy, chance) ->
						new AlaProcessingRecipe(kind, ingredients, inputCounts, result, energy, chance));
	}
}
