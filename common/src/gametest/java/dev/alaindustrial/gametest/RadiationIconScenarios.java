package dev.alaindustrial.gametest;

import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.core.radiation.RadiationCore;
import dev.alaindustrial.core.radiation.RadiationDose;
import dev.alaindustrial.item.misc.AntiradBarItem;
import dev.alaindustrial.registry.ModEffects;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * How a dose is shown (MOD-568): the player sees the radiation icon in the HUD but no particles; a mob keeps its
 * particles, the only sign anything is happening to it. Both lanes run these through {@link Roster}.
 *
 * <p>The bodies call the production writers directly — {@link RadiationDose#apply} with the argument the player
 * sweep passes, and {@link AntiradBarItem#bite} — because a mock player is not ticked by the radiation sweep.
 */
public final class RadiationIconScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(RadiationIconScenarios::playerDoseShowsIconWithoutParticles,
						"rad_player_dose_shows_icon_without_particles").ticks(20, 40),
				RosterEntry.of(RadiationIconScenarios::mobDoseKeepsParticles,
						"rad_mob_dose_keeps_particles").ticks(20, 40));

		private Roster() {}
	}

	private static final BlockPos MOB_POS = new BlockPos(1, 2, 1);

	private RadiationIconScenarios() {
	}

	/**
	 * A player dose carries the icon and no particles, at the level the dose is in — on the first write, after a
	 * change of level (which removes and re-adds the effect) and after a bite of the Antirad bar.
	 *
	 * @implements MOD-568 — the player sees the radiation icon in the HUD; particles stay off for the player.
	 */
	public static void playerDoseShowsIconWithoutParticles(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
		int capacity = RadiationConfig.radiationDoseCapacity;
		RadiationDose.apply(player, capacity * 30 / 100, capacity, false);
		if (!shownAsPlayerDose(helper, player, capacity, "first dose")) {
			return;
		}
		RadiationDose.apply(player, capacity, capacity, false);
		if (!shownAsPlayerDose(helper, player, capacity, "after the level rose")) {
			return;
		}
		if (player.getEffect(ModEffects.RADIATION.get()).getAmplifier() != 3) {
			helper.fail("a full dose must be level IV (amplifier 3)");
			return;
		}
		AntiradBarItem.bite(player);
		if (!shownAsPlayerDose(helper, player, capacity, "after a bite of the Antirad bar")) {
			return;
		}
		helper.succeed();
	}

	/**
	 * A mob's dose keeps its particles: the fix for the player's icon must not take them away from mobs.
	 *
	 * @implements MOD-568 — a mob's dose still shows particles.
	 */
	public static void mobDoseKeepsParticles(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityTypes.VILLAGER, MOB_POS);
		int capacity = RadiationConfig.radiationDoseCapacity;
		RadiationDose.apply(villager, capacity / 2, capacity, true);
		MobEffectInstance effect = villager.getEffect(ModEffects.RADIATION.get());
		if (effect == null) {
			helper.fail("the villager must carry the radiation effect");
			return;
		}
		if (!effect.isVisible()) {
			helper.fail("a mob's dose must show particles");
			return;
		}
		helper.succeed();
	}

	private static boolean shownAsPlayerDose(GameTestHelper helper, LivingEntity player, int capacity, String when) {
		MobEffectInstance effect = player.getEffect(ModEffects.RADIATION.get());
		if (effect == null) {
			helper.fail(when + ": the player must carry the radiation effect");
			return false;
		}
		if (!effect.showIcon()) {
			helper.fail(when + ": the radiation icon must show in the HUD");
			return false;
		}
		if (effect.isVisible()) {
			helper.fail(when + ": the player must not get radiation particles");
			return false;
		}
		if (effect.isAmbient()) {
			helper.fail(when + ": the radiation effect must not be ambient");
			return false;
		}
		int expected = RadiationCore.level(effect.getDuration(), capacity) - 1;
		if (effect.getAmplifier() != expected) {
			helper.fail(when + ": amplifier must be " + expected + " for a dose of " + effect.getDuration()
					+ ", got " + effect.getAmplifier());
			return false;
		}
		return true;
	}
}
