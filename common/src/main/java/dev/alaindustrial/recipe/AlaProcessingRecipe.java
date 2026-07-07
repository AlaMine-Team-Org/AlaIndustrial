package dev.alaindustrial.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.registry.ModRecipes;
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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * A single-input → single-output processing recipe for an Industrialization machine (macerator,
 * electric furnace, compressor, extractor), expressed as a <em>real</em> vanilla
 * {@link Recipe}. Loaded by the vanilla {@link net.minecraft.world.item.crafting.RecipeManager} from
 * {@code data/<ns>/recipe/**.json} (so {@code /reload} refreshes it, datapacks can add/override it,
 * and the ingredient may be a tag — see R-14/R-15). Replaces the previous hand-rolled JSON loaders
 * ({@code MacerationRecipes} / {@code ProcessingRecipes}).
 *
 * <p>The {@link ModRecipes.Kind} carries which machine this recipe belongs to (its {@link RecipeType}
 * + {@link RecipeSerializer}), so the four machine types stay distinct while sharing this one class.
 *
 * @param kind       owning machine type (type + serializer + default energy)
 * @param ingredient accepted input (item or tag)
 * @param result     produced stack template (item + count). An {@link ItemStackTemplate} rather than a
 *                   live {@link ItemStack} so the result codec is safe during early datapack loading
 *                   (a raw {@code ItemStack.CODEC} throws "item does not have components yet" there).
 * @param energy     total EU spent to complete one operation
 */
public record AlaProcessingRecipe(ModRecipes.Kind kind, Ingredient ingredient, ItemStackTemplate result, int energy)
		implements Recipe<SingleRecipeInput> {

	@Override
	public boolean matches(SingleRecipeInput input, Level level) {
		return ingredient.test(input.item());
	}

	@Override
	public ItemStack assemble(SingleRecipeInput input) {
		return result.create();
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
	public RecipeSerializer<? extends Recipe<SingleRecipeInput>> getSerializer() {
		return kind.serializer();
	}

	@Override
	public RecipeType<? extends Recipe<SingleRecipeInput>> getType() {
		return kind.type();
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.create(ingredient);
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		// Machines have no recipe-book UI of their own; reuse a vanilla category to satisfy the API.
		return RecipeBookCategories.CRAFTING_MISC;
	}

	/** MapCodec for a machine kind's JSON form: {@code {ingredient, result, energy?}}. */
	public static MapCodec<AlaProcessingRecipe> mapCodec(ModRecipes.Kind kind) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
				Ingredient.CODEC.fieldOf("ingredient").forGetter(AlaProcessingRecipe::ingredient),
				ItemStackTemplate.CODEC.fieldOf("result").forGetter(AlaProcessingRecipe::result),
				Codec.INT.optionalFieldOf("energy", kind.defaultEnergy()).forGetter(AlaProcessingRecipe::energy)
		).apply(instance, (ingredient, result, energy) -> new AlaProcessingRecipe(kind, ingredient, result, energy)));
	}

	/** Network sync codec for a machine kind. */
	public static StreamCodec<RegistryFriendlyByteBuf, AlaProcessingRecipe> streamCodec(ModRecipes.Kind kind) {
		return StreamCodec.composite(
				Ingredient.CONTENTS_STREAM_CODEC, AlaProcessingRecipe::ingredient,
				ItemStackTemplate.STREAM_CODEC, AlaProcessingRecipe::result,
				ByteBufCodecs.INT, AlaProcessingRecipe::energy,
				(ingredient, result, energy) -> new AlaProcessingRecipe(kind, ingredient, result, energy));
	}
}
