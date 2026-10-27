package dev.alaindustrial.gametest;

import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.core.radiation.RadiationDose;
import dev.alaindustrial.item.misc.AntiradBarItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModEffects;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Milk "Antirad" bar (MOD-914): four bites, each a quarter of the radiation scale; the whole bar clears a full
 * dose and leaves every other effect alone. Bites go through the real consume path — food, use remainder and all.
 */
public final class AntiradBarScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(AntiradBarScenarios::fourBitesClearAFullDoseByQuarters,
						"antirad_bar_four_bites_by_quarters").ticks(20, 40),
				RosterEntry.of(AntiradBarScenarios::otherEffectsStay,
						"antirad_bar_other_effects_stay").ticks(20, 40),
				RosterEntry.of(AntiradBarScenarios::edibleOnAFullStomachAndPlainFoodWithoutRadiation,
						"antirad_bar_full_stomach_plain_food").ticks(20, 40),
				RosterEntry.of(AntiradBarScenarios::stagesStackToSixteen,
						"antirad_bar_stages_stack").ticks(20, 40),
				RosterEntry.of(AntiradBarScenarios::recipeGivesTwoBarsAndReturnsTheBucket,
						"antirad_bar_recipe_returns_bucket").ticks(20, 40));

		private Roster() {}
	}

	private AntiradBarScenarios() {
	}

	private static List<Item> stages() {
		return List.of(ModContent.ANTIRAD_BAR.get(), ModContent.ANTIRAD_BAR_THREE_QUARTERS.get(),
				ModContent.ANTIRAD_BAR_HALF.get(), ModContent.ANTIRAD_BAR_QUARTER.get());
	}

	/**
	 * A full scale of dose, then the four bites of one bar in a row: three quarters, a half, a quarter, nothing.
	 * Each bite hands back the next stage; the last one hands back nothing.
	 *
	 * @implements MOD-914 — four bites take the dose off by quarters; after the fourth it is gone.
	 */
	public static void fourBitesClearAFullDoseByQuarters(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		int capacity = RadiationConfig.radiationDoseCapacity;
		RadiationDose.apply(player, capacity, capacity, false);
		ItemStack hand = new ItemStack(ModContent.ANTIRAD_BAR.get());
		List<Item> stages = stages();
		for (int bite = 1; bite <= 4; bite++) {
			hand = hand.finishUsingItem(helper.getLevel(), player);
			int expected = capacity - capacity * bite * RadiationConfig.antiradBiteDosePercent / 100;
			int dose = RadiationDose.of(player);
			if (dose != Math.max(0, expected)) {
				helper.fail("bite " + bite + ": dose must be " + Math.max(0, expected) + ", got " + dose);
				return;
			}
			if (bite < 4 && !hand.is(stages.get(bite))) {
				helper.fail("bite " + bite + " must leave the next stage in hand, got " + hand);
				return;
			}
		}
		if (!hand.isEmpty()) {
			helper.fail("the last quarter must leave nothing, got " + hand);
			return;
		}
		if (player.hasEffect(ModEffects.RADIATION.get())) {
			helper.fail("after four bites the radiation effect must be gone");
			return;
		}
		helper.succeed();
	}

	/**
	 * Unlike a bucket of milk, the bar touches radiation only: another effect stays through all four bites.
	 *
	 * @implements MOD-914 — the bar leaves the player's other effects alone.
	 */
	public static void otherEffectsStay(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200));
		RadiationDose.apply(player, 2000, RadiationConfig.radiationDoseCapacity, false);
		ItemStack hand = new ItemStack(ModContent.ANTIRAD_BAR.get());
		for (int bite = 0; bite < 4 && !hand.isEmpty(); bite++) {
			hand = hand.finishUsingItem(helper.getLevel(), player);
		}
		if (!player.hasEffect(MobEffects.SPEED)) {
			helper.fail("the bar must not remove effects other than radiation");
			return;
		}
		helper.succeed();
	}

	/**
	 * On a full hunger bar the bar can still be eaten; with no radiation it is just light food and fills a little.
	 *
	 * @implements MOD-914 — edible on a full stomach; without radiation it works as ordinary light food.
	 */
	public static void edibleOnAFullStomachAndPlainFoodWithoutRadiation(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.getFoodData().setFoodLevel(20);
		ItemStack bar = new ItemStack(ModContent.ANTIRAD_BAR.get());
		if (!bar.get(DataComponents.CONSUMABLE).canConsume(player, bar)) {
			helper.fail("the bar must be edible on a full hunger bar");
			return;
		}
		player.getFoodData().setFoodLevel(10);
		bar.finishUsingItem(helper.getLevel(), player);
		int food = player.getFoodData().getFoodLevel();
		if (food != 10 + AntiradBarItem.NUTRITION) {
			helper.fail("a bite must fill " + AntiradBarItem.NUTRITION + " hunger, food went 10 → " + food);
			return;
		}
		if (player.hasEffect(ModEffects.RADIATION.get())) {
			helper.fail("a bite without radiation must not add any");
			return;
		}
		helper.succeed();
	}

	/**
	 * Every stage stacks to sixteen, is food, and is in the common candy tag.
	 *
	 * @implements MOD-914 — stages stack to 16 and count as food for other mods.
	 */
	public static void stagesStackToSixteen(GameTestHelper helper) {
		TagKey<Item> candy = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "foods/candy"));
		for (Item stage : stages()) {
			ItemStack stack = new ItemStack(stage);
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (stack.getMaxStackSize() != AntiradBarItem.STACK_SIZE || food == null || !food.canAlwaysEat()) {
				helper.fail(stage + " must stack to " + AntiradBarItem.STACK_SIZE + " and be always-edible food");
				return;
			}
			if (!stack.is(candy)) {
				helper.fail(stage + " must be in #c:foods/candy");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * One milk bucket, cocoa beans and two inulin make two whole bars on a crafting table, and the bucket comes
	 * back empty.
	 *
	 * @implements MOD-914 — 1 bucket of milk + 1 cocoa beans + 2 inulin → 2 bars, the empty bucket returned.
	 */
	public static void recipeGivesTwoBarsAndReturnsTheBucket(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack inulin = new ItemStack(ModContent.INULIN.get());
		CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.MILK_BUCKET),
				new ItemStack(Items.COCOA_BEANS), inulin.copy(), inulin.copy()));
		var holder = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
		if (holder == null) {
			helper.fail("milk bucket + cocoa beans + 2 inulin must match a crafting recipe");
			return;
		}
		ItemStack out = holder.value().assemble(input);
		if (!out.is(ModContent.ANTIRAD_BAR.get()) || out.getCount() != 2) {
			helper.fail("the recipe must give 2 whole bars, got " + out);
			return;
		}
		NonNullList<ItemStack> left = holder.value().getRemainingItems(input);
		if (!left.get(0).is(Items.BUCKET)) {
			helper.fail("the milk bucket must come back empty, got " + left);
			return;
		}
		helper.succeed();
	}
}
