package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.misc.ChewingGumItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModEffects;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;

/**
 * Rubber chewing gum (MOD-918): it clears Nausea, keeps it away while Fresh Breath lasts, and is chewed fast on
 * any stomach. The guard is proven through the same {@code addEffect} the radiation symptom and a pufferfish use,
 * not by reading the guard's own predicate.
 */
public final class ChewingGumScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ChewingGumScenarios::chewingClearsNauseaAndStartsTheGuard,
						"chewing_gum_clears_nausea_and_guards").ticks(20, 40),
				RosterEntry.of(ChewingGumScenarios::guardRefusesOnlyNauseaAndOnlyWhileItLasts,
						"chewing_gum_guard_refuses_only_nausea").ticks(20, 40),
				RosterEntry.of(ChewingGumScenarios::pufferfishNauseaIsRefused,
						"chewing_gum_pufferfish_nausea_refused").ticks(20, 40),
				RosterEntry.of(ChewingGumScenarios::chewedFastOnAFullStomachAndNotFilling,
						"chewing_gum_full_stomach_fast_not_filling").ticks(20, 40));

		private Roster() {}
	}

	private ChewingGumScenarios() {
	}

	/** The radiation symptom's own flags (ambient, no particles, no icon) — the gum must refuse that one too. */
	private static MobEffectInstance radiationStyleNausea() {
		return new MobEffectInstance(MobEffects.NAUSEA, 120, 0, true, false, false);
	}

	/**
	 * One piece takes Nausea away at once, starts Fresh Breath for the configured time and is used up.
	 *
	 * @implements MOD-918 — the gum clears Nausea and keeps it away for three minutes.
	 */
	public static void chewingClearsNauseaAndStartsTheGuard(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 600));
		ItemStack gum = new ItemStack(ModContent.CHEWING_GUM.get(), 2);
		gum.finishUsingItem(helper.getLevel(), player);
		if (player.hasEffect(MobEffects.NAUSEA)) {
			helper.fail("chewing must remove Nausea that is already there");
			return;
		}
		MobEffectInstance guard = player.getEffect(ModEffects.FRESH_BREATH.get());
		if (guard == null || guard.getDuration() != Config.chewingGumGuardTicks) {
			helper.fail("Fresh Breath must start at chewingGumGuardTicks=" + Config.chewingGumGuardTicks
					+ ", got " + guard);
			return;
		}
		if (gum.getCount() != 1) {
			helper.fail("one chew must use up exactly one piece, " + gum.getCount() + " left of 2");
			return;
		}
		helper.succeed();
	}

	/**
	 * Under Fresh Breath, Nausea is refused through the ordinary {@code addEffect} — radiation-style flags
	 * included — while other effects still land; once the guard is gone, Nausea lands again.
	 *
	 * @implements MOD-918 — Nausea cannot return while the guard lasts; nothing else is blocked.
	 */
	public static void guardRefusesOnlyNauseaAndOnlyWhileItLasts(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		new ItemStack(ModContent.CHEWING_GUM.get()).finishUsingItem(helper.getLevel(), player);
		if (player.addEffect(radiationStyleNausea()) || player.hasEffect(MobEffects.NAUSEA)) {
			helper.fail("Nausea must be refused while Fresh Breath lasts");
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120));
		if (!player.hasEffect(MobEffects.WEAKNESS)) {
			helper.fail("the guard must refuse Nausea only — Weakness has to land as usual");
			return;
		}
		player.removeEffect(ModEffects.FRESH_BREATH.get());
		player.addEffect(radiationStyleNausea());
		if (!player.hasEffect(MobEffects.NAUSEA)) {
			helper.fail("with Fresh Breath gone, Nausea must land again");
			return;
		}
		helper.succeed();
	}

	/**
	 * A pufferfish eaten under Fresh Breath still poisons, but brings no Nausea — a foreign source, through
	 * vanilla's own consume effects.
	 *
	 * @implements MOD-918 — the guard holds against any ordinary source of Nausea.
	 */
	public static void pufferfishNauseaIsRefused(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.getFoodData().setFoodLevel(0);
		new ItemStack(ModContent.CHEWING_GUM.get()).finishUsingItem(helper.getLevel(), player);
		new ItemStack(Items.PUFFERFISH).finishUsingItem(helper.getLevel(), player);
		if (!player.hasEffect(MobEffects.POISON)) {
			helper.fail("the pufferfish must still poison — the guard is not a general antidote");
			return;
		}
		if (player.hasEffect(MobEffects.NAUSEA)) {
			helper.fail("the pufferfish's Nausea must be refused under Fresh Breath");
			return;
		}
		helper.succeed();
	}

	/**
	 * The gum is not food: it fills nothing, can be chewed on a full bar, and is chewed in half the time of
	 * ordinary food.
	 *
	 * @implements MOD-918 — chewed fast, not filling, usable on a full stomach.
	 */
	public static void chewedFastOnAFullStomachAndNotFilling(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		player.getFoodData().setFoodLevel(20);
		ItemStack gum = new ItemStack(ModContent.CHEWING_GUM.get());
		Consumable consumable = gum.get(DataComponents.CONSUMABLE);
		if (consumable == null || !consumable.canConsume(player, gum)) {
			helper.fail("the gum must be chewable on a full hunger bar");
			return;
		}
		if (gum.has(DataComponents.FOOD)) {
			helper.fail("the gum must carry no food component — it fills nothing");
			return;
		}
		float ordinary = new ItemStack(Items.BREAD).get(DataComponents.CONSUMABLE).consumeSeconds();
		if (consumable.consumeSeconds() != ChewingGumItem.CONSUME_SECONDS || consumable.consumeSeconds() >= ordinary) {
			helper.fail("the gum must be chewed faster than bread (" + ordinary + " s), took "
					+ consumable.consumeSeconds() + " s");
			return;
		}
		helper.succeed();
	}
}
