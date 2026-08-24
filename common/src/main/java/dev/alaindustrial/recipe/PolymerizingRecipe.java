package dev.alaindustrial.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * A fluid → item processing recipe for the Polymerizer (MOD-019), expressed as a real vanilla
 * {@link Recipe} so it loads from {@code data/<ns>/recipe/polymerizing/*.json}, refreshes on
 * {@code /reload}, and can be added or overridden by a datapack — the same contract
 * {@link AlaProcessingRecipe} gives the item-fed machines.
 *
 * <p><b>Why a separate class.</b> {@link AlaProcessingRecipe} consumes ordered item stacks matched by
 * {@link net.minecraft.world.item.crafting.Ingredient}s. The
 * Polymerizer consumes a <em>fluid volume</em> out of its own tank, which is neither, so it needs its own
 * input type ({@link FluidRecipeInput}) and its own JSON shape. Making the ingredient optional on the
 * shared class instead would have loosened the schema for all eleven item-fed families, where a missing
 * ingredient must stay an error.
 *
 * <p><b>The fluid is a {@link HolderSet}</b>, i.e. exactly what an {@code Ingredient} is for items:
 * {@code "fluid": "alaindustrial:oil"} names one fluid and {@code "fluid": "#c:oil"} names a tag. The
 * shipped recipe uses the conventional {@code c:oil} tag (see {@code ModTags.Fluids.C_OIL}), so another
 * mod's oil is accepted without a compat patch.
 *
 * @param fluid  which fluids this recipe accepts (a fluid id or a fluid tag)
 * @param amount how much of it one operation consumes, in mB ({@link FluidAmounts#BUCKET} = 1000)
 * @param result the produced stack template — an {@link ItemStackTemplate} rather than a live
 *               {@link ItemStack} for the same reason as {@link AlaProcessingRecipe#result()}: a raw
 *               {@code ItemStack.CODEC} throws during early datapack loading
 * @param energy total EU spent to complete one operation
 */
public record PolymerizingRecipe(ModRecipes.FluidKind<PolymerizingRecipe> kind,
		HolderSet<Fluid> fluid, int amount, ItemStackTemplate result, int energy)
		implements Recipe<FluidRecipeInput> {

	/** Backward-compatible constructor for the Polymerizer's one existing recipe family. */
	public PolymerizingRecipe(HolderSet<Fluid> fluid, int amount, ItemStackTemplate result, int energy) {
		this(ModRecipes.POLYMERIZING, fluid, amount, result, energy);
	}

	/** JSON form: {@code {fluid, amount?, result, energy?}}. */
	public static MapCodec<PolymerizingRecipe> mapCodec(ModRecipes.FluidKind<PolymerizingRecipe> kind) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
				RegistryCodecs.homogeneousList(Registries.FLUID).fieldOf("fluid").forGetter(PolymerizingRecipe::fluid),
				Codec.intRange(1, Integer.MAX_VALUE)
						.optionalFieldOf("amount", (int) FluidAmounts.BUCKET).forGetter(PolymerizingRecipe::amount),
				ItemStackTemplate.CODEC.fieldOf("result").forGetter(PolymerizingRecipe::result),
				// Range-limited like `amount`: a zero or negative cost would make the machine fall back to its
				// default duration while the recipe viewers divided by it and printed a 0.1 s operation.
				Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("energy", kind.defaultEnergy())
						.forGetter(PolymerizingRecipe::energy)
		).apply(instance, (fluid, amount, result, energy) ->
				new PolymerizingRecipe(kind, fluid, amount, result, energy)));
	}

	/** Network sync codec (recipes travel to the client for the recipe viewers). */
	public static StreamCodec<RegistryFriendlyByteBuf, PolymerizingRecipe> streamCodec(
			ModRecipes.FluidKind<PolymerizingRecipe> kind) {
		return StreamCodec.composite(
				ByteBufCodecs.holderSet(Registries.FLUID), PolymerizingRecipe::fluid,
				ByteBufCodecs.INT, PolymerizingRecipe::amount,
				ItemStackTemplate.STREAM_CODEC, PolymerizingRecipe::result,
				ByteBufCodecs.INT, PolymerizingRecipe::energy,
				(fluid, amount, result, energy) ->
						new PolymerizingRecipe(kind, fluid, amount, result, energy));
	}

	@Override
	public boolean matches(FluidRecipeInput input, Level level) {
		return !input.isEmpty() && input.amount() >= amount && accepts(input.fluid().fluid());
	}

	/** Whether {@code candidate} is one of the fluids this recipe consumes. */
	public boolean accepts(Fluid candidate) {
		// MOD-498 — wrapAsHolder, not the deprecated intrusive Fluid#builtInRegistryHolder(). For a
		// registered fluid it returns that very Holder.Reference, so a tag-backed HolderSet still matches
		// by identity; HolderSet#contains takes Holder<Fluid>, so the Holder.Reference subtype the
		// deprecated getter returns was never needed here.
		// Precondition: the value is REGISTERED. wrapAsHolder falls back to Holder.direct for an
		// unregistered one, and a direct holder answers false to every tag test — silently. The
		// deprecated getter was immune to registration order; this is not.
		return fluid.contains(BuiltInRegistries.FLUID.wrapAsHolder(candidate));
	}

	/**
	 * The accepted fluids a tank can actually hold and a recipe viewer should show — <b>sources only</b>.
	 *
	 * <p>A fluid tag conventionally lists both variants of a flowing fluid: {@code c:oil} names
	 * {@code alaindustrial:oil} <em>and</em> {@code alaindustrial:flowing_oil}, because world-scanning code
	 * (the pump's source search) has to recognise the flowing one. Stored fluid is a different question —
	 * "flowing oil" is a world state, not a thing a bucket or a tank ever contains. Without this filter the
	 * recipe card listed the same fluid twice, once labelled "Flowing Oil", and a tank that a foreign pipe
	 * had pushed a partial amount of the flowing variant into could never be topped up by ordinary oil
	 * (the tank is single-variant), leaving it stuck below the recipe volume forever.
	 */
	public List<Holder<Fluid>> displayFluids() {
		return fluid.stream().filter(holder -> isSourceFluid(holder.value())).toList();
	}

	/** Whether {@code fluid} is a source rather than a flowing variant — see {@link #displayFluids()}. */
	public static boolean isSourceFluid(Fluid fluid) {
		return fluid.isSource(fluid.defaultFluidState());
	}

	@Override
	public ItemStack assemble(FluidRecipeInput input) {
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
	public RecipeSerializer<? extends Recipe<FluidRecipeInput>> getSerializer() {
		return kind.serializer();
	}

	@Override
	public RecipeType<? extends Recipe<FluidRecipeInput>> getType() {
		return kind.type();
	}

	@Override
	public boolean isSpecial() {
		// Silences the vanilla load-time warning "Recipe ... can't be placed due to empty ingredients
		// and will be ignored" (MOD-249). RecipeManager#finalizeRecipeLoading logs it for every recipe
		// where !isSpecial() && placementInfo().isImpossibleToPlace() — and ours is deliberately
		// NOT_PLACEABLE, see below. "Special" in vanilla means exactly this: a recipe that has no
		// recipe-book form, which is true of every machine recipe. Nothing is lost by claiming it —
		// with no ingredients to contribute, the property-set collectors this unblocks find nothing.
		return true;
	}

	@Override
	public PlacementInfo placementInfo() {
		// There is no way to place a fluid volume in a crafting grid, so this recipe has no grid form at
		// all — unlike AlaProcessingRecipe, which can at least describe its item ingredient.
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		// Machines have no recipe-book UI of their own; reuse a vanilla category to satisfy the API.
		return RecipeBookCategories.CRAFTING_MISC;
	}
}
