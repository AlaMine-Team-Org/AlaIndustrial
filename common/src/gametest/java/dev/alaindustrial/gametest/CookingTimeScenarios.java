package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;

/**
 * The mod's ores take as long in the vanilla blast furnace as vanilla's own ore (MOD-724).
 *
 * <p>What a recipe's {@code cookingtime} means differs between the lines: on 26.2 it is the time in the
 * furnace that runs the recipe, on 26.3 it is the time in a plain furnace, and the blast furnace divides it
 * by its fuel's speed multiplier (2.0). The 26.3 port wrote the 26.2 numbers into the mod's blasting
 * recipes, and they ran twice as fast as vanilla's. Both checks take vanilla iron in the same game as the
 * oracle, so they hold on either line without a per-line coefficient written down here.
 */
public final class CookingTimeScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(CookingTimeScenarios::modBlastingKeepsVanillaBlastToSmeltRatio,
						"cooking_time_mod_blasting_keeps_vanilla_blast_to_smelt_ratio"),
				RosterEntry.of(CookingTimeScenarios::modOreBlastsAsFastAsVanillaOre,
						"cooking_time_mod_ore_blasts_as_fast_as_vanilla_ore").ticks(300));

		private Roster() {}
	}

	/** The mod's blasting recipes, each with a smelting twin; a new one changes this on purpose. */
	private static final int EXPECTED_PAIRS = 15;
	private static final Identifier VANILLA_BLAST =
			Identifier.withDefaultNamespace("iron_ingot_from_blasting_raw_iron");
	private static final Identifier VANILLA_SMELT =
			Identifier.withDefaultNamespace("iron_ingot_from_smelting_raw_iron");

	private static final BlockPos IRON_FURNACE = new BlockPos(1, 2, 1);
	private static final BlockPos TIN_FURNACE = new BlockPos(3, 2, 1);
	/** {@code AbstractFurnaceBlockEntity.SLOT_INPUT}, {@code SLOT_FUEL}, {@code SLOT_RESULT} (protected). */
	private static final int SLOT_INPUT = 0;
	private static final int SLOT_FUEL = 1;
	private static final int SLOT_RESULT = 2;
	/** When the verdict is read; vanilla raw iron takes 100 ticks in a blast furnace on both lines. */
	private static final int VERDICT_TICK = 130;
	/** The latest tick an ingot may appear: vanilla's 100 plus a margin for the furnace lighting. */
	private static final int LATEST_DONE = 120;

	private CookingTimeScenarios() {}

	/**
	 * Every blasting recipe of the mod relates to its smelting twin as vanilla iron's blasting recipe relates
	 * to vanilla iron's smelting recipe. Pairs are matched by input and result, not by name.
	 *
	 * @implements MOD-724 — the mod's blasting recipes keep vanilla's blasting-to-smelting time ratio.
	 */
	public static void modBlastingKeepsVanillaBlastToSmeltRatio(GameTestHelper helper) {
		RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
		AbstractCookingRecipe vanillaBlast = cooking(helper, manager.byKey(key(VANILLA_BLAST)), VANILLA_BLAST);
		AbstractCookingRecipe vanillaSmelt = cooking(helper, manager.byKey(key(VANILLA_SMELT)), VANILLA_SMELT);
		List<RecipeHolder<?>> blasts = new ArrayList<>();
		List<RecipeHolder<?>> smelts = new ArrayList<>();
		for (RecipeHolder<?> holder : manager.getRecipes()) {
			if (!Industrialization.MOD_ID.equals(holder.id().identifier().getNamespace())) {
				continue;
			}
			if (holder.value() instanceof BlastingRecipe) {
				blasts.add(holder);
			} else if (holder.value() instanceof SmeltingRecipe) {
				smelts.add(holder);
			}
		}
		List<String> problems = new ArrayList<>();
		int pairs = 0;
		for (RecipeHolder<?> blastHolder : blasts) {
			BlastingRecipe blast = (BlastingRecipe) blastHolder.value();
			Optional<SmeltingRecipe> twin = smelts.stream().map(h -> (SmeltingRecipe) h.value())
					.filter(smelt -> sameInputAndResult(blast, smelt)).findFirst();
			if (twin.isEmpty()) {
				problems.add(blastHolder.id().identifier()
						+ " has no smelting recipe with the same input and result");
				continue;
			}
			pairs++;
			int smeltTime = twin.get().cookingTime();
			if ((long) blast.cookingTime() * vanillaSmelt.cookingTime()
					!= (long) smeltTime * vanillaBlast.cookingTime()) {
				problems.add(blastHolder.id().identifier() + ": blasting " + blast.cookingTime() + " against smelting "
						+ smeltTime + ", vanilla iron " + vanillaBlast.cookingTime() + " against "
						+ vanillaSmelt.cookingTime());
			}
		}
		if (pairs != EXPECTED_PAIRS) {
			problems.add(0, "checked " + pairs + " of " + EXPECTED_PAIRS + " blasting/smelting pairs (found "
					+ blasts.size() + " blasting recipes); a pair added or removed on purpose updates "
					+ "EXPECTED_PAIRS");
		}
		if (!problems.isEmpty()) {
			helper.fail(problems.size() + " cooking time problem(s): " + String.join("; ", problems));
			return;
		}
		helper.succeed();
	}

	/**
	 * Raw tin and raw iron in two blast furnaces lit with coal come out within a tick of each other, and in
	 * vanilla's time.
	 *
	 * @implements MOD-724 — the mod's ore smelts in the vanilla blast furnace as fast as vanilla's ore.
	 */
	public static void modOreBlastsAsFastAsVanillaOre(GameTestHelper helper) {
		BlastFurnaceBlockEntity iron = blastFurnace(helper, IRON_FURNACE);
		BlastFurnaceBlockEntity tin = blastFurnace(helper, TIN_FURNACE);
		long start = helper.getTick();
		long[] done = {-1, -1};
		for (BlastFurnaceBlockEntity furnace : List.of(iron, tin)) {
			furnace.setItem(SLOT_FUEL, new ItemStack(Items.COAL));
		}
		iron.setItem(SLOT_INPUT, new ItemStack(Items.RAW_IRON));
		tin.setItem(SLOT_INPUT, new ItemStack(ModContent.RAW_TIN.get()));
		helper.onEachTick(() -> {
			if (done[0] < 0 && !iron.getItem(SLOT_RESULT).isEmpty()) {
				done[0] = helper.getTick() - start;
			}
			if (done[1] < 0 && !tin.getItem(SLOT_RESULT).isEmpty()) {
				done[1] = helper.getTick() - start;
			}
		});
		helper.runAfterDelay(VERDICT_TICK, () -> {
			String times = "raw iron done on tick " + done[0] + ", raw tin on tick " + done[1];
			if (done[0] < 0 || done[1] < 0) {
				helper.fail("a blast furnace produced nothing in " + VERDICT_TICK + " ticks: " + times);
				return;
			}
			if (Math.abs(done[0] - done[1]) > 1) {
				helper.fail("the mod's ore must blast as fast as vanilla's: " + times);
				return;
			}
			if (Math.max(done[0], done[1]) > LATEST_DONE) {
				helper.fail("blasting took longer than vanilla's 100 ticks plus a margin: " + times);
				return;
			}
			if (!resultIs(iron, Items.IRON_INGOT) || !resultIs(tin, ModContent.TIN_INGOT.get())) {
				helper.fail("unexpected results: " + iron.getItem(SLOT_RESULT) + " and " + tin.getItem(SLOT_RESULT));
				return;
			}
			helper.succeed();
		});
	}

	private static boolean sameInputAndResult(AbstractCookingRecipe a, AbstractCookingRecipe b) {
		Set<Item> inputs = items(a);
		if (inputs.isEmpty() || !inputs.equals(items(b))) {
			return false;
		}
		ItemStack probe = new ItemStack(inputs.iterator().next());
		ItemStack left = a.assemble(new SingleRecipeInput(probe));
		ItemStack right = b.assemble(new SingleRecipeInput(probe));
		return ItemStack.isSameItemSameComponents(left, right) && left.getCount() == right.getCount();
	}

	private static Set<Item> items(AbstractCookingRecipe recipe) {
		return recipe.input().items().map(Holder::value).collect(Collectors.toSet());
	}

	private static ResourceKey<Recipe<?>> key(Identifier id) {
		return ResourceKey.create(Registries.RECIPE, id);
	}

	private static AbstractCookingRecipe cooking(GameTestHelper helper, Optional<RecipeHolder<?>> found,
			Identifier id) {
		if (found.isEmpty() || !(found.get().value() instanceof AbstractCookingRecipe recipe)) {
			helper.fail("fixture error: vanilla cooking recipe " + id + " is not loaded");
			throw new IllegalStateException("unreachable: helper.fail throws");
		}
		return recipe;
	}

	private static BlastFurnaceBlockEntity blastFurnace(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, Blocks.BLAST_FURNACE);
		BlastFurnaceBlockEntity be = helper.getBlockEntity(pos, BlastFurnaceBlockEntity.class);
		if (be == null) {
			helper.fail("fixture error: no blast furnace block entity at " + pos);
			throw new IllegalStateException("unreachable: helper.fail throws");
		}
		return be;
	}

	private static boolean resultIs(BlastFurnaceBlockEntity furnace, Item item) {
		return furnace.getItem(SLOT_RESULT).getItem() == item;
	}
}
