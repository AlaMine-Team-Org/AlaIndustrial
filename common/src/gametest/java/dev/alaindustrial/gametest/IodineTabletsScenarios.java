package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GalvanicBathBlockEntity;
import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.core.radiation.RadiationDose;
import dev.alaindustrial.effect.IodineProtectionEffect;
import dev.alaindustrial.item.misc.IodineTabletsItem;
import dev.alaindustrial.recipe.AlaProcessingRecipe;
import dev.alaindustrial.recipe.ProcessingRecipeInput;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModEffects;
import dev.alaindustrial.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;

/**
 * Iodine tablets (MOD-916): radiation builds up slower under Iodine Protection — prevention, never a cure. The
 * dose a sweep adds is asked of the same call the radiation sweep uses, on two players that differ only by the tablet.
 */
public final class IodineTabletsScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(IodineTabletsScenarios::protectedPlayerTakesExactlyTheConfiguredShare,
						"iodine_tablets_dose_builds_slower").ticks(20, 40),
				RosterEntry.of(IodineTabletsScenarios::tabletDoesNotCureACarriedDose,
						"iodine_tablets_no_cure").ticks(20, 40),
				RosterEntry.of(IodineTabletsScenarios::secondTabletExtendsAndDoesNotStrengthen,
						"iodine_tablets_second_extends").ticks(20, 40),
				RosterEntry.of(IodineTabletsScenarios::tabletIsNotFoodAndQuick,
						"iodine_tablets_not_food_quick").ticks(20, 40),
				RosterEntry.of(IodineTabletsScenarios::chainRecipesResolve,
						"iodine_tablets_chain_recipes").ticks(20, 40));

		private Roster() {}
	}

	private IodineTabletsScenarios() {
	}

	/** Refined uranium in the pocket: tag radioactive_high, uncapped, so the raw sweep dose is a plain product. */
	private static final int URANIUM = 4;

	/**
	 * The same raw sweep dose — a pocketful of uranium — reaches the bare player whole and the protected one at
	 * exactly {@code iodineDosePercent} of it. The radiation sweep hands every player's dose through this one
	 * call ({@code RadiationTicker}); mock players are not ticked by the test server, so the sweep's own timing
	 * cannot be waited for here and the call is asked directly.
	 *
	 * @implements MOD-916 — under the effect the same source in the same time gives a smaller dose, by exactly the
	 *     configured share.
	 */
	public static void protectedPlayerTakesExactlyTheConfiguredShare(GameTestHelper helper) {
		ServerPlayer bare = AlaGameTestHelper.survivalPlayer(helper);
		ServerPlayer protectedOne = AlaGameTestHelper.survivalPlayer(helper);
		new ItemStack(ModContent.IODINE_TABLETS.get()).finishUsingItem(helper.getLevel(), protectedOne);
		int raw = URANIUM * RadiationConfig.radiationDoseHighPerItem;
		int toBare = IodineProtectionEffect.doseAdded(raw, bare);
		int toProtected = IodineProtectionEffect.doseAdded(raw, protectedOne);
		if (toBare != raw) {
			helper.fail("a player without the tablet must take the whole dose " + raw + ", took " + toBare);
			return;
		}
		int expected = raw * RadiationConfig.iodineDosePercent / 100;
		if (toProtected != expected || toProtected >= raw) {
			helper.fail("a protected player must take " + RadiationConfig.iodineDosePercent + " % of " + raw + " = "
					+ expected + ", took " + toProtected);
			return;
		}
		helper.succeed();
	}

	/**
	 * A tablet taken with a dose already on board leaves that dose exactly where it was.
	 *
	 * @implements MOD-916 — the tablet does not remove a dose already carried.
	 */
	public static void tabletDoesNotCureACarriedDose(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.getInventory().clearContent();
		RadiationDose.apply(player, 3000, RadiationConfig.radiationDoseCapacity, false);
		new ItemStack(ModContent.IODINE_TABLETS.get()).finishUsingItem(helper.getLevel(), player);
		int dose = RadiationDose.of(player);
		if (dose != 3000) {
			helper.fail("the tablet must not touch a carried dose: 3000 before, " + dose + " after");
			return;
		}
		if (!player.hasEffect(ModEffects.IODINE_PROTECTION.get())) {
			helper.fail("the tablet must start Iodine Protection");
			return;
		}
		helper.succeed();
	}

	/**
	 * A second tablet sets the time back to full and keeps the protection at one level.
	 *
	 * @implements MOD-916 — another tablet extends the effect, it does not strengthen it.
	 */
	public static void secondTabletExtendsAndDoesNotStrengthen(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		ItemStack tablets = new ItemStack(ModContent.IODINE_TABLETS.get(), 2);
		tablets.finishUsingItem(helper.getLevel(), player);
		// Stand in for a protection that has run most of its course (mock players are not ticked).
		player.removeEffect(ModEffects.IODINE_PROTECTION.get());
		player.addEffect(new MobEffectInstance(ModEffects.IODINE_PROTECTION.get(), 100));
		tablets.finishUsingItem(helper.getLevel(), player);
		MobEffectInstance second = player.getEffect(ModEffects.IODINE_PROTECTION.get());
		if (second == null || second.getDuration() != RadiationConfig.iodineProtectionTicks
				|| second.getAmplifier() != 0) {
			helper.fail("a second tablet must reset the time to " + RadiationConfig.iodineProtectionTicks
					+ " at level I, got " + second);
			return;
		}
		helper.succeed();
	}

	/**
	 * Tablets are not food: swallowed on a full stomach, quicker than a meal, filling nothing.
	 *
	 * @implements MOD-916 — the tablet is not food and is taken quickly.
	 */
	public static void tabletIsNotFoodAndQuick(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.getFoodData().setFoodLevel(20);
		ItemStack tablet = new ItemStack(ModContent.IODINE_TABLETS.get());
		Consumable consumable = tablet.get(DataComponents.CONSUMABLE);
		if (consumable == null || !consumable.canConsume(player, tablet) || tablet.has(DataComponents.FOOD)) {
			helper.fail("a tablet must be takeable on a full stomach and carry no food component");
			return;
		}
		if (consumable.consumeSeconds() != IodineTabletsItem.CONSUME_SECONDS) {
			helper.fail("a tablet must take " + IodineTabletsItem.CONSUME_SECONDS + " s, took "
					+ consumable.consumeSeconds());
			return;
		}
		helper.succeed();
	}

	/**
	 * The whole chain resolves: the macerator grinds dried kelp into kelp ash, the bath takes ash and sulfur dust
	 * into its two material slots and makes iodine, the compressor makes tablets of iodine.
	 *
	 * @implements MOD-916 — the recipes of the chain exist and fit their machines.
	 */
	public static void chainRecipesResolve(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AlaProcessingRecipe grind = ModRecipes.lookup(ModRecipes.MACERATION.newCheck(), level,
				new ItemStack(Items.DRIED_KELP));
		ItemStack ash = grind == null ? ItemStack.EMPTY : grind.resultStack();
		if (!ash.is(ModContent.KELP_ASH.get())) {
			helper.fail("the macerator must grind dried kelp into kelp ash, got " + ash);
			return;
		}
		ItemStack sulfur = new ItemStack(ModContent.SULFUR_DUST.get());
		if (!GalvanicBathBlockEntity.acceptsInput(GalvanicBathBlockEntity.FIBER_SLOT, ash)
				|| !GalvanicBathBlockEntity.acceptsInput(GalvanicBathBlockEntity.SILVER_SLOT, sulfur)) {
			helper.fail("the galvanic bath must take kelp ash in its first slot and sulfur dust in its second");
			return;
		}
		AlaProcessingRecipe bath = ModRecipes.lookup(ModRecipes.GALVANIC_BATH.newCheck(), level,
				new ProcessingRecipeInput(ash, sulfur));
		if (bath == null || !bath.resultStack().is(ModContent.IODINE.get())) {
			helper.fail("kelp ash + sulfur dust in the bath must make iodine, got " + bath);
			return;
		}
		AlaProcessingRecipe press = ModRecipes.lookup(ModRecipes.COMPRESSING.newCheck(), level,
				new ItemStack(ModContent.IODINE.get()));
		if (press == null || !press.resultStack().is(ModContent.IODINE_TABLETS.get())
				|| press.resultStack().getCount() != 2) {
			helper.fail("the compressor must press one iodine into two tablets, got " + press);
			return;
		}
		helper.succeed();
	}
}
