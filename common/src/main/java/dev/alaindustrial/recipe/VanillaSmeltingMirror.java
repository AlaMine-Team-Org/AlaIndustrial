package dev.alaindustrial.recipe;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
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
 * opening the machine's own category still saw only the mod's own recipes. These mirrors close that
 * gap by presenting each vanilla smelt as an {@link AlaProcessingRecipe} carrying the furnace's real
 * EU cost and duration.
 *
 * <p>The mirrors are display-only: they are built for JEI/REI and never enter the
 * {@link net.minecraft.world.item.crafting.RecipeManager}. The machine keeps using the live vanilla
 * recipe at runtime, so behaviour and shown numbers come from the same place.
 *
 * <p><b>Single source of truth (Mekanism-style) — with one known exception.</b> The mod ships no
 * {@code alaindustrial:smelting} recipe that a vanilla {@code minecraft:smelting} recipe already
 * covers with the same input and output for reasons unrelated to the mod (e.g. cobblestone → stone,
 * sand → glass, raw copper/gold/iron → ingot). Those smelts live entirely in the vanilla fallback, and
 * the mirrors below surface them in the electric furnace category once. Writing a mod-side duplicate
 * would either double-list it here, or pin a static EU cost that drifts from the runtime once
 * {@code globalMachineSpeedMultiplier} rounds its factors separately (see
 * {@link Config#electricFurnaceVanillaSmeltEu}); the fallback avoids both.
 *
 * <p><b>Exception, and how the double listing it would cause is avoided (MOD-523).</b> Twelve of the
 * mod's own {@code alaindustrial:smelting} recipes DO have a parallel {@code minecraft:smelting}
 * duplicate accepting the same item: the six metal dusts (copper/gold/iron/nickel/silver/tin), the
 * five raw materials (nickel/silver/tin/uranium/palladium) and iron ingot → tempered iron. Each
 * duplicate exists so the Iron Furnace — which only reads vanilla {@code RecipeType.SMELTING} and
 * never {@code alaindustrial:smelting} — can smelt that input too (MOD-455; copper/gold/iron shipped
 * this way since the mod's original import). They are intentional and must not be "fixed" by deleting
 * the duplicate JSON: that would silently break those smelts in the Iron Furnace and the plain vanilla
 * furnace. Instead {@link #mirrorAll} drops a vanilla smelt whose every input item a mod smelting
 * recipe already turns into the same stack, so the category lists each smelt exactly once. The mod
 * recipe is the survivor on purpose: its input is a tag ({@code #c:dusts/copper}), which covers every
 * other mod's copper dust as well, while the duplicate names one concrete item. Only uranium and
 * palladium DUST have no vanilla duplicate at all and are electric-furnace-exclusive.
 *
 * <p>One predicate serves both loaders: JEI filters inside {@link #mirrorAll} (it hands the whole
 * {@link RecipeMap} over), REI calls {@link #isCoveredByModSmelting} from its own per-recipe filler.
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
	 * viewers keep a stable identity per recipe. Recipes that cannot be mirrored are skipped, and so
	 * are the ones a mod smelting recipe already covers (MOD-523 — see the class javadoc).
	 */
	public static List<RecipeHolder<AlaProcessingRecipe>> mirrorAll(RecipeMap recipes) {
		return mirrorAll(recipes.values());
	}

	/**
	 * The same mirrors from a plain recipe collection — what a caller holding a
	 * {@link net.minecraft.world.item.crafting.RecipeManager} has ({@code getRecipes()}); vanilla's
	 * manager exposes no {@link RecipeMap} of its own, only NeoForge adds that accessor.
	 */
	public static List<RecipeHolder<AlaProcessingRecipe>> mirrorAll(Collection<RecipeHolder<?>> recipes) {
		Map<Item, ItemStack> coverage = modSmeltingCoverage(recipes);
		List<RecipeHolder<AlaProcessingRecipe>> mirrors = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes) {
			if (holder.value() instanceof SmeltingRecipe smelting) {
				AlaProcessingRecipe mirror = mirror(smelting);
				if (mirror != null && !isCoveredByModSmelting(mirror, coverage)) {
					mirrors.add(new RecipeHolder<>(holder.id(), mirror));
				}
			}
		}
		return mirrors;
	}

	/**
	 * What the mod's own {@code alaindustrial:smelting} recipes already turn each input item into:
	 * every item accepted by a single-input mod smelting recipe, mapped to the stack it produces.
	 *
	 * <p>Only single-ingredient recipes taking one item per craft are collected — a vanilla smelt
	 * consumes exactly one item, so nothing else can duplicate one. A tag ingredient contributes every
	 * item in the tag, which is what makes the check below work at all: the mod recipe reads
	 * {@code #c:dusts/copper}, the vanilla duplicate reads {@code alaindustrial:copper_dust}, and
	 * comparing the two {@link Ingredient}s directly would never have matched them.
	 */
	public static Map<Item, ItemStack> modSmeltingCoverage(Collection<RecipeHolder<?>> recipes) {
		Map<Item, ItemStack> coverage = new HashMap<>();
		for (RecipeHolder<?> holder : recipes) {
			if (!(holder.value() instanceof AlaProcessingRecipe recipe)
					|| recipe.kind() != ModRecipes.SMELTING
					|| recipe.ingredients().size() != 1
					|| recipe.inputCount(0) != 1) {
				continue;
			}
			ItemStack result = recipe.resultStack();
			if (result.isEmpty()) {
				continue;
			}
			for (Item item : itemsOf(recipe.ingredient())) {
				coverage.putIfAbsent(item, result);
			}
		}
		return coverage;
	}

	/**
	 * Whether a mirrored vanilla smelt is already shown in this category by a mod recipe, i.e. every
	 * item its input accepts is turned into the same stack by an {@code alaindustrial:smelting} recipe.
	 *
	 * <p>Containment, not equality: the mod side is a tag and the vanilla side one item out of it, so
	 * the mod recipe's card already covers this smelt. A vanilla recipe whose input the mod covers only
	 * partly, or covers with a different output, is a different smelt and stays.
	 */
	public static boolean isCoveredByModSmelting(AlaProcessingRecipe mirror, Map<Item, ItemStack> coverage) {
		if (coverage.isEmpty()) {
			return false;
		}
		ItemStack result = mirror.resultStack();
		List<Item> inputs = itemsOf(mirror.ingredient());
		if (inputs.isEmpty()) {
			return false;
		}
		for (Item item : inputs) {
			ItemStack byMod = coverage.get(item);
			if (byMod == null || byMod.getCount() != result.getCount()
					|| !ItemStack.isSameItemSameComponents(byMod, result)) {
				return false;
			}
		}
		return true;
	}

	// MOD-498 — Ingredient#items() is soft-deprecated by vanilla, but no non-deprecated accessor
	// exposes the item list this needs: Ingredient.values is private and display() is a different
	// (display-only) layer. Vanilla itself still calls items() from RecipeManager#isIngredientEnabled.
	@SuppressWarnings("deprecation")
	private static List<Item> itemsOf(Ingredient ingredient) {
		return ingredient.items().map(Holder::value).toList();
	}
}
