package dev.alaindustrial.recipe;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/**
 * Mirrors vanilla {@link SmeltingRecipe}s into the electric furnace's own recipe-viewer category
 * (MOD-086).
 *
 * <p>The electric furnace falls back to {@code minecraft:smelting} whenever no
 * {@code alaindustrial:smelting} recipe matches (see {@code ElectricFurnaceBlockEntity}), so every
 * vanilla smelt is something the machine really performs. Registering the block as a crafting station
 * for the vanilla category (MOD-076) only makes that visible from the <em>vanilla</em> side; players
 * opening the machine's own category still saw the mod's 17 recipes alone. These mirrors close that
 * gap by presenting each vanilla smelt as an {@link AlaProcessingRecipe} carrying the furnace's real
 * EU cost and duration.
 *
 * <p>The mirrors are display-only: they are built for JEI/REI and never enter the
 * {@link net.minecraft.world.item.crafting.RecipeManager}. The machine keeps using the live vanilla
 * recipe at runtime, so behaviour and shown numbers come from the same place.
 *
 * <p><b>Known limitation:</b> the mod ships five recipes that numerically duplicate the vanilla
 * fallback (cobblestone → stone, sand → glass, raw copper/gold/iron → ingot — the "balance anchors"
 * documented in the electric furnace spec). Those appear twice in the category: once as the mod
 * recipe, once as the mirror. Both show the identical 200 EU / 5 s, so neither is wrong. They are not
 * de-duplicated because REI's server-side display filler gets no access to the recipe manager, and
 * filtering on JEI alone would make the two loaders disagree.
 */
public final class VanillaSmeltingMirror {
	/**
	 * {@link net.minecraft.world.item.crafting.SingleItemRecipe#assemble} ignores its input entirely
	 * ({@code return this.result.create()}), so an empty input is enough to read a recipe's result.
	 */
	private static final SingleRecipeInput NO_INPUT = new SingleRecipeInput(ItemStack.EMPTY);

	private VanillaSmeltingMirror() {
	}

	/**
	 * EU one vanilla smelt costs in the electric furnace — {@link Config#electricFurnaceVanillaSmeltEu},
	 * the same figure {@code ElectricFurnaceBlockEntity} ticks away, so the shown cost tracks the real
	 * one under any speed multiplier.
	 */
	public static int energy() {
		return Config.electricFurnaceVanillaSmeltEu();
	}

	/**
	 * One vanilla smelting recipe presented as an electric-furnace recipe, or {@code null} if it cannot
	 * be mirrored.
	 *
	 * <p>Returns {@code null} rather than throwing when a recipe yields no result, or when reading it
	 * fails: vanilla's {@code assemble} ignores its input, but another mod may subclass
	 * {@link SmeltingRecipe} and read it, which would throw on our empty input. This runs inside the
	 * JEI/REI plugin load, where an escaping exception takes down the whole integration — one unusable
	 * recipe must not cost the player every category.
	 */
	public static AlaProcessingRecipe mirror(SmeltingRecipe recipe) {
		ItemStack result;
		try {
			result = recipe.assemble(NO_INPUT);
		} catch (RuntimeException | LinkageError error) {
			Industrialization.LOGGER.warn("Skipping vanilla smelting recipe in the electric furnace category: "
					+ "its result could not be read (recipe class {})", recipe.getClass().getName(), error);
			return null;
		}
		if (result.isEmpty()) {
			return null;
		}
		return new AlaProcessingRecipe(
				ModRecipes.SMELTING,
				recipe.input(),
				ItemStackTemplate.fromStack(result),
				energy());
	}

	/**
	 * Every mirrorable vanilla smelting recipe in {@code recipes}, re-keyed under its original id so
	 * viewers keep a stable identity per recipe. Recipes that cannot be mirrored are skipped.
	 */
	public static List<RecipeHolder<AlaProcessingRecipe>> mirrorAll(RecipeMap recipes) {
		List<RecipeHolder<AlaProcessingRecipe>> mirrors = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes.values()) {
			if (holder.value() instanceof SmeltingRecipe smelting) {
				AlaProcessingRecipe mirror = mirror(smelting);
				if (mirror != null) {
					mirrors.add(new RecipeHolder<>(holder.id(), mirror));
				}
			}
		}
		return mirrors;
	}
}
