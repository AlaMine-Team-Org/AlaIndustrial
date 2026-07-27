package dev.alaindustrial.gametest;

import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;

/**
 * Loader-neutral gametest bodies for the Forge Hammer (MOD-078, suite TC-HAMMER-001). Same pattern as
 * {@link ScytheScenarios}/{@link ElectricDrillScenarios}: plain {@code GameTestHelper} bodies wrapped
 * by the Fabric {@code HammerGameTest} suite and registered on the NeoForge {@code gameTestServer} lane
 * via {@code NeoForgeGameTests} — both loaders exercise the SAME logic.
 *
 * <p>The "hammer stays in the grid, taking 1 durability per plate" mechanic is a stack-aware
 * craft-remainder. These tests drive it through {@link CraftingRecipe#defaultCraftingReminder} — the
 * exact method both loaders inject their {@code getCraftingRemainder} hook into (Fabric via
 * {@code CraftingRecipeMixin}, NeoForge via the {@code CraftingRecipe} patch), and the method the
 * client and server both call for the grid remainder. So this is a true regression guard: with the
 * loader hook removed, {@code Item.getCraftingRemainder()} is null and every remainder here is empty,
 * failing FUN01/PRF01. Durability 128 is the code source of truth (see docs/PERFORMANCE.md).
 */
public final class HammerScenarios {

	private HammerScenarios() {}

	/** The remainder of the hammer slot after crafting one plate from {@code hammer} + one iron ingot. */
	private static ItemStack craftOnce(ItemStack hammer) {
		ItemStack ingot = new ItemStack(Items.IRON_INGOT);
		NonNullList<ItemStack> remainder =
				CraftingRecipe.defaultCraftingReminder(CraftingInput.of(2, 1, List.of(hammer, ingot)));
		// Slot 1 is the ingot: a plain ingredient, no remainder — it is consumed.
		if (!remainder.get(1).isEmpty()) {
			throw new AssertionError("the ingot must be consumed (no remainder), got " + remainder.get(1));
		}
		return remainder.get(0);
	}

	/** FUN01: a fresh hammer stays in the grid after a plate craft and comes back with exactly 1 damage. */
	public static void fun01HammerStaysWithOneDamage(GameTestHelper helper) {
		ItemStack back = craftOnce(new ItemStack(ModContent.FORGE_HAMMER.get()));
		if (back.isEmpty() || !back.is(ModContent.FORGE_HAMMER.get())) {
			helper.fail("the hammer must stay in the grid after crafting a plate, got " + back);
		}
		if (back.getDamageValue() != 1) {
			helper.fail("a fresh hammer must lose exactly 1 durability per plate, got damage " + back.getDamageValue());
		}
		helper.succeed();
	}

	/** PRF01: mid-life, one craft raises the hammer's damage by exactly 1 (not 0, not more). */
	public static void prf01ExactlyOneDamagePerPlate(GameTestHelper helper) {
		ItemStack hammer = new ItemStack(ModContent.FORGE_HAMMER.get());
		hammer.setDamageValue(50);
		ItemStack back = craftOnce(hammer);
		if (back.isEmpty() || back.getDamageValue() != 51) {
			helper.fail("a hammer at damage 50 must come back at exactly 51, got " + back);
		}
		helper.succeed();
	}

	/** NEG01: on its last durability point the hammer breaks — the plate is made, the grid slot stays empty. */
	public static void neg01LastDurabilityBreaks(GameTestHelper helper) {
		ItemStack hammer = new ItemStack(ModContent.FORGE_HAMMER.get());
		hammer.setDamageValue(hammer.getMaxDamage() - 1); // next damage breaks it
		ItemStack back = craftOnce(hammer);
		if (!back.isEmpty()) {
			helper.fail("a hammer on its last point must break: no remainder, grid slot empty, got " + back);
		}
		helper.succeed();
	}

	/** CON01: the Forge Hammer's durability is 128 (the balance number in docs/PERFORMANCE.md). */
	public static void con01Durability128(GameTestHelper helper) {
		int max = new ItemStack(ModContent.FORGE_HAMMER.get()).getMaxDamage();
		if (max != 128) {
			helper.fail("Forge Hammer durability must be 128 (PERFORMANCE.md), got " + max);
		}
		helper.succeed();
	}
}
