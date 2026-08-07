package dev.alaindustrial.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.item.energy.ItemEnergy;
import dev.alaindustrial.registry.ModRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * A shaped crafting recipe that carries the charge of its ingredients into the result (MOD-083).
 *
 * <p>Ten of the mod's recipes build a powered item out of batteries. A vanilla {@code Ingredient}
 * compares items and ignores components, so a charged battery has always been accepted by them — and
 * its charge has always evaporated. That is a bad trade for the player: the battery is a carrier, and
 * pouring one into a drill should hand the drill the energy, not burn it. So these recipes sum the EU
 * of everything in the grid and give it to what comes out, clamped to the result's own buffer.
 *
 * <p><b>Why a subclass of {@link ShapedRecipe} and not a wrapper around one.</b> Two places in the game
 * branch on {@code instanceof ShapedRecipe} to recover the <em>positions</em> of a recipe's
 * ingredients — vanilla's {@code PlaceRecipeHelper} and this mod's {@code IngredientSubstitution},
 * which is what lets the Assembler substitute an ingredient cell by cell. A wrapper would still craft
 * correctly and would still fail no test, while quietly demoting every one of these ten recipes to the
 * "positions unknown" path. Extending keeps that behaviour, and inherits {@code display()} so the
 * recipe book and REI show it as the ordinary crafting-table recipe it is.
 *
 * <p>The JSON is <b>identical</b> to a vanilla shaped recipe — same {@code category}, {@code key},
 * {@code pattern}, {@code result} — because the codec is assembled from the very same component codecs
 * vanilla uses. Only {@code "type"} differs.
 */
public class ChargedCraftRecipe extends ShapedRecipe {
	// The four constructor arguments are kept because ShapedRecipe exposes no getters for the pattern or
	// the result, and the codec has to be able to write a recipe back out.
	private final Recipe.CommonInfo commonInfo;
	private final CraftingRecipe.CraftingBookInfo bookInfo;
	private final ShapedRecipePattern pattern;
	private final ItemStackTemplate result;

	public ChargedCraftRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
			ShapedRecipePattern pattern, ItemStackTemplate result) {
		super(commonInfo, bookInfo, pattern, result);
		this.commonInfo = commonInfo;
		this.bookInfo = bookInfo;
		this.pattern = pattern;
		this.result = result;
	}

	public static final MapCodec<ChargedCraftRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
					CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
					ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
					ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result))
					.apply(instance, ChargedCraftRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ChargedCraftRecipe> STREAM_CODEC =
			StreamCodec.composite(
					Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
					CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
					ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
					ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
					ChargedCraftRecipe::new);

	/**
	 * Assembles the vanilla result, then hands it the EU the ingredients brought with them.
	 *
	 * <p>The sum is over whole stacks ({@link ItemEnergy#stackGet}) because a grid cell may legitimately
	 * hold more than one battery when the player is crafting in bulk, and it is clamped to the result's
	 * own buffer — three full batteries (6 000 EU) into an Energy Pack (20 000) arrive intact, the same
	 * three into an Electric Hoe with a small buffer do not, and the surplus is lost. That loss is
	 * deliberate and documented: the alternative is a recipe that refuses to craft, which is worse.
	 *
	 * <p>Energy is never created here — the result can only receive what the inputs held — so this cannot
	 * become a dupe by repeated crafting.
	 */
	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack crafted = super.assemble(input);
		long capacity = ItemEnergy.capacity(crafted);
		if (capacity <= 0) {
			return crafted;
		}
		long carried = 0L;
		for (ItemStack ingredient : input.items()) {
			carried += ItemEnergy.stackGet(ingredient);
		}
		if (carried > 0) {
			ItemEnergy.set(crafted, Math.min(carried, capacity));
		}
		return crafted;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>The return type is {@code RecipeSerializer<ShapedRecipe>} because that is what
	 * {@link ShapedRecipe} declares and the generic is invariant, so a narrower one cannot override it.
	 * The cast is safe at runtime: this serializer's codec produces {@link ChargedCraftRecipe} and
	 * nothing else, and the registry stores every serializer as {@code RecipeSerializer<?>} anyway.
	 */
	@Override
	public RecipeSerializer<ShapedRecipe> getSerializer() {
		@SuppressWarnings("unchecked")
		RecipeSerializer<ShapedRecipe> asShaped =
				(RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) ModRecipes.chargedCraftSerializer();
		return asShaped;
	}
}
