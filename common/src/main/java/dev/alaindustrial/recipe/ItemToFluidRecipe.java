package dev.alaindustrial.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.registry.ModRecipes;
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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * An item → fluid processing recipe (MOD-924): one item in, a fixed volume of one fluid out, and
 * optionally the item's container back. The Honey Centrifuge spins combs, bottles and honey blocks into
 * liquid honey with it; a datapack adds another mod's comb as one more JSON file.
 *
 * <p><b>Why a separate class.</b> {@link AlaProcessingRecipe} produces an item, {@link PolymerizingRecipe}
 * consumes a fluid and {@link FluidOutputRecipe} turns a fluid into fluids — none of them takes an item
 * and makes a fluid, and widening one of them would loosen the schema every family of it relies on.
 *
 * <p>JSON form: {@code {ingredient, fluid_result: {fluid, amount}, remainder?, energy?}}. The output
 * fluid is an exact holder ({@link FluidStackTemplate}), never a tag: a machine has to put one fluid into
 * its tank.
 *
 * @param ingredient  which item one operation consumes (an item id or a tag), one at a time
 * @param result      the fluid and the volume in mB one operation produces
 * @param remainder   what is handed back for the consumed item, if anything (the empty bottle of a honey
 *                    bottle); stated in the recipe rather than read from the item, so a viewer shows it
 *                    and a datapack can change it
 * @param energy      total EU spent to complete one operation
 */
public record ItemToFluidRecipe(ModRecipes.ItemFluidKind kind, Ingredient ingredient, FluidStackTemplate result,
		Optional<ItemStackTemplate> remainder, int energy) implements Recipe<SingleRecipeInput> {

	/** JSON form — see the class javadoc. */
	public static MapCodec<ItemToFluidRecipe> mapCodec(ModRecipes.ItemFluidKind kind) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
				Ingredient.CODEC.fieldOf("ingredient").forGetter(ItemToFluidRecipe::ingredient),
				FluidStackTemplate.CODEC.fieldOf("fluid_result").forGetter(ItemToFluidRecipe::result),
				ItemStackTemplate.CODEC.optionalFieldOf("remainder").forGetter(ItemToFluidRecipe::remainder),
				// Range-limited for the same reason as the other families: a zero cost would make the viewers
				// divide by it and print an instant operation.
				Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("energy", kind.defaultEnergy())
						.forGetter(ItemToFluidRecipe::energy)
		).apply(instance, (ingredient, result, remainder, energy) ->
				new ItemToFluidRecipe(kind, ingredient, result, remainder, energy)));
	}

	/** Network sync codec (recipes travel to the client for the recipe viewers). */
	public static StreamCodec<RegistryFriendlyByteBuf, ItemToFluidRecipe> streamCodec(ModRecipes.ItemFluidKind kind) {
		return StreamCodec.composite(
				Ingredient.CONTENTS_STREAM_CODEC, ItemToFluidRecipe::ingredient,
				FluidStackTemplate.STREAM_CODEC, ItemToFluidRecipe::result,
				ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), ItemToFluidRecipe::remainder,
				ByteBufCodecs.INT, ItemToFluidRecipe::energy,
				(ingredient, result, remainder, energy) ->
						new ItemToFluidRecipe(kind, ingredient, result, remainder, energy));
	}

	@Override
	public boolean matches(SingleRecipeInput input, Level level) {
		return !input.item().isEmpty() && ingredient.test(input.item());
	}

	/** The fluid volume one operation yields, in mB. */
	public int amount() {
		return result.amount();
	}

	/** A fresh stack of what is handed back for the consumed item, or {@link ItemStack#EMPTY}. */
	public ItemStack remainderStack() {
		return remainder.map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
	}

	/** Item recipes need an assembled stack; this family produces a fluid, so the item result is empty. */
	@Override
	public ItemStack assemble(SingleRecipeInput input) {
		return ItemStack.EMPTY;
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

	/** A machine recipe has no recipe-book form — see {@link PolymerizingRecipe#isSpecial()}. */
	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return RecipeBookCategories.CRAFTING_MISC;
	}
}
