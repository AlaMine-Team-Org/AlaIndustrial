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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * A fluid-input recipe that produces one or two exact fluid stacks.
 *
 * <p>This is the infrastructure recipe used by the {@code distilling} family. It deliberately has
 * no item result: machines consume {@link #fluidResults()} directly, while vanilla's item-shaped
 * {@link Recipe#assemble} contract returns {@link ItemStack#EMPTY}.
 */
public record FluidOutputRecipe(ModRecipes.FluidKind<FluidOutputRecipe> kind,
		HolderSet<Fluid> fluid, int amount, List<FluidStackTemplate> fluidResults, int energy)
		implements Recipe<FluidRecipeInput> {

	public FluidOutputRecipe {
		fluidResults = List.copyOf(fluidResults);
		if (fluidResults.isEmpty() || fluidResults.size() > 2) {
			throw new IllegalArgumentException("fluid-output recipe must contain 1 or 2 results");
		}
	}

	/** JSON form: {@code {fluid, amount?, fluid_results, energy?}}. */
	public static MapCodec<FluidOutputRecipe> mapCodec(ModRecipes.FluidKind<FluidOutputRecipe> kind) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
				RegistryCodecs.homogeneousList(Registries.FLUID).fieldOf("fluid")
						.forGetter(FluidOutputRecipe::fluid),
				Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("amount", (int) FluidAmounts.BUCKET)
						.forGetter(FluidOutputRecipe::amount),
				FluidStackTemplate.CODEC.listOf(1, 2).fieldOf("fluid_results")
						.forGetter(FluidOutputRecipe::fluidResults),
				Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("energy", kind.defaultEnergy())
						.forGetter(FluidOutputRecipe::energy)
		).apply(instance, (fluid, amount, results, energy) ->
				new FluidOutputRecipe(kind, fluid, amount, results, energy)));
	}

	/** Network sync codec used by recipe viewers. */
	public static StreamCodec<RegistryFriendlyByteBuf, FluidOutputRecipe> streamCodec(
			ModRecipes.FluidKind<FluidOutputRecipe> kind) {
		return StreamCodec.composite(
				ByteBufCodecs.holderSet(Registries.FLUID), FluidOutputRecipe::fluid,
				ByteBufCodecs.INT, FluidOutputRecipe::amount,
				FluidStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list(2)), FluidOutputRecipe::fluidResults,
				ByteBufCodecs.INT, FluidOutputRecipe::energy,
				(fluid, amount, results, energy) ->
						new FluidOutputRecipe(kind, fluid, amount, results, energy));
	}

	@Override
	public boolean matches(FluidRecipeInput input, Level level) {
		return !input.isEmpty() && input.amount() >= amount && accepts(input.fluid().fluid());
	}

	public boolean accepts(Fluid candidate) {
		return fluid.contains(candidate.builtInRegistryHolder());
	}

	/** Accepted source fluids suitable for a tank or recipe viewer. */
	public List<Holder<Fluid>> displayFluids() {
		return fluid.stream().filter(holder -> PolymerizingRecipe.isSourceFluid(holder.value())).toList();
	}

	@Override
	public ItemStack assemble(FluidRecipeInput input) {
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
	public RecipeSerializer<? extends Recipe<FluidRecipeInput>> getSerializer() {
		return kind.serializer();
	}

	@Override
	public RecipeType<? extends Recipe<FluidRecipeInput>> getType() {
		return kind.type();
	}

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
