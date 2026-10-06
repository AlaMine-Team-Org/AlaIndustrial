package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.MobWheelScenarios.batteryCharge;
import static dev.alaindustrial.gametest.MobWheelScenarios.clearBox;
import static dev.alaindustrial.gametest.MobWheelScenarios.deck;
import static dev.alaindustrial.gametest.MobWheelScenarios.energyRig;
import static dev.alaindustrial.gametest.MobWheelScenarios.gate;
import static dev.alaindustrial.gametest.MobWheelScenarios.swingGate;

import dev.alaindustrial.block.MobWheelStructure;
import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.block.entity.MobWheelRoster;
import dev.alaindustrial.core.environment.MobWheelFeed;
import dev.alaindustrial.core.environment.MobWheelLayout;
import dev.alaindustrial.core.environment.MobWheelOutput;
import dev.alaindustrial.core.environment.MobWheelProfile;
import dev.alaindustrial.core.environment.MobWheelStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the mob wheel's runner roster (MOD-763, decisions D8 and D9): every accepted type fits the
 * wheel's passage and agrees with the Minecraft-free table; the creeper is the strongest runner and the chicken
 * the weakest; a stray runs harder in a snowy biome; the species traits act on the live mob; and an occupant
 * with a player target close by is distracted — it stops, makes nothing, spends nothing and is not turned.
 *
 * <p>Rates are read on the very first running tick, when stamina is full, so each expectation is an exact
 * number from the table, not a range. The rig is {@link MobWheelScenarios}'s: facing south, battery east.
 */
public final class MobWheelRunnerScenarios {

	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(MobWheelRunnerScenarios::everyRunnerFitsThePassage, "mob_wheel_every_runner_fits")
						.ticks(40),
				RosterEntry.of(MobWheelRunnerScenarios::creeperRunsAndIsTheStrongest,
						"mob_wheel_creeper_runs_strongest_no_swell").ticks(40),
				RosterEntry.of(MobWheelRunnerScenarios::chickenIsTheWeakest, "mob_wheel_chicken_is_weakest")
						.ticks(40),
				RosterEntry.of(MobWheelRunnerScenarios::strayRunsHarderInASnowyBiome,
						"mob_wheel_stray_bonus_in_snowy_biome").ticks(40),
				RosterEntry.of(MobWheelRunnerScenarios::playerTargetDistractsTheOccupant,
						"mob_wheel_player_target_distracts").ticks(40),
				RosterEntry.of(MobWheelRunnerScenarios::traitsActOnTheLiveMob, "mob_wheel_traits_act_on_mob")
						.ticks(40));

		private Roster() {}
	}

	private MobWheelRunnerScenarios() {
	}

	/** Shut a fresh adult of {@code type} in the rig's wheel; fails when the gate does not take it. */
	private static <T extends Mob> T shutIn(GameTestHelper helper, MobWheelBlockEntity drive, EntityType<T> type) {
		T mob = helper.spawn(type, deck());
		mob.setBaby(false);
		swingGate(helper);
		if (drive.species() != MobWheelRoster.TYPES.get(type)) {
			helper.fail("the gate did not take a " + MobWheelRoster.typeId(type) + " (" + drive.species() + ")");
		}
		return mob;
	}

	/** The output of a fresh {@code profile} at pace 1, no bonus — the table's number with the shipped knobs. */
	private static int freshOutput(MobWheelProfile profile) {
		return MobWheelOutput.euFor(profile.basePowerEuPerTick(), 100, 0, 1.0, 1.0);
	}

	// --- scenarios ---

	/**
	 * D8: the live roster and the Minecraft-free table name the same types in the same order; every accepted
	 * type, spawned as an adult, is at most 0.9 wide and no taller than the wheel's clear height (floor to
	 * ceiling of {@link MobWheelLayout}); and every food in the feed table is a real item.
	 */
	public static void everyRunnerFitsThePassage(GameTestHelper helper) {
		List<String> live = MobWheelRoster.TYPES.keySet().stream().map(MobWheelRoster::typeId).toList();
		if (!live.equals(MobWheelProfile.RUNNER_TYPE_IDS)) {
			helper.fail("live roster " + live + " differs from the table " + MobWheelProfile.RUNNER_TYPE_IDS);
		}
		double clearHeight = (MobWheelLayout.ROTOR_CEILING_PX - MobWheelLayout.ROTOR_FLOOR_PX) / 16.0;
		double clearWidth = MobWheelLayout.FOOTPRINT_WIDTH_PX / 16.0;
		List<String> misfits = new ArrayList<>();
		for (Map.Entry<EntityType<?>, MobWheelProfile> entry : MobWheelRoster.TYPES.entrySet()) {
			if (!(helper.spawn(entry.getKey(), new BlockPos(4, 2, 4)) instanceof Mob mob)) {
				helper.fail(MobWheelRoster.typeId(entry.getKey()) + " is not a mob");
				return;
			}
			mob.setBaby(false);
			if (mob.getBbWidth() > clearWidth + 1.0E-6 || mob.getBbHeight() > clearHeight + 1.0E-6) {
				misfits.add(MobWheelRoster.typeId(entry.getKey()) + " " + mob.getBbWidth() + "x" + mob.getBbHeight());
			}
			if (MobWheelRoster.profileOf(mob) != entry.getValue()) {
				misfits.add(MobWheelRoster.typeId(entry.getKey()) + " maps to " + MobWheelRoster.profileOf(mob));
			}
			mob.discard();
		}
		if (!misfits.isEmpty()) {
			helper.fail("runners that do not fit " + clearWidth + "x" + clearHeight + ": " + misfits);
		}
		for (MobWheelProfile profile : MobWheelProfile.values()) {
			for (String item : MobWheelFeed.foodsOf(profile).keySet()) {
				if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(item))) {
					helper.fail(profile + " eats " + item + ", which is not an item");
				}
			}
		}
		helper.succeed();
	}

	/**
	 * D8: a creeper runs, makes the table's 10 EU/t on its first tick — no other species makes more — and while
	 * it runs it does not swell, even when something set it swelling.
	 */
	public static void creeperRunsAndIsTheStrongest(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		Creeper creeper = shutIn(helper, drive, EntityTypes.CREEPER);
		AlaGameTestHelper.drive(drive, helper, 1);
		int rate = drive.productionRate();
		if (drive.status() != MobWheelStatus.RUNNING || rate != freshOutput(MobWheelProfile.CREEPER)) {
			helper.fail("the creeper is " + drive.status() + " at " + rate + " EU/t, expected running at "
					+ freshOutput(MobWheelProfile.CREEPER));
		}
		for (MobWheelProfile other : MobWheelProfile.values()) {
			if (freshOutput(other) > rate) {
				helper.fail(other + " makes " + freshOutput(other) + " EU/t, more than the creeper's " + rate);
			}
		}
		creeper.setSwellDir(1);
		AlaGameTestHelper.drive(drive, helper, 1);
		if (creeper.getSwellDir() > 0) {
			helper.fail("a running creeper was left swelling");
		}
		AlaGameTestHelper.drive(drive, helper, 20);
		if (!creeper.isAlive() || batteryCharge(helper) <= 0) {
			helper.fail("the creeper did not run the wheel quietly");
		}
		helper.succeed();
	}

	/** D8: a chicken runs and makes the table's 1 EU/t on its first tick — no species makes less. */
	public static void chickenIsTheWeakest(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		shutIn(helper, drive, EntityTypes.CHICKEN);
		AlaGameTestHelper.drive(drive, helper, 1);
		int rate = drive.productionRate();
		if (drive.status() != MobWheelStatus.RUNNING || rate != freshOutput(MobWheelProfile.CHICKEN)) {
			helper.fail("the chicken is " + drive.status() + " at " + rate + " EU/t");
		}
		for (MobWheelProfile other : MobWheelProfile.values()) {
			if (freshOutput(other) < rate) {
				helper.fail(other + " makes " + freshOutput(other) + " EU/t, less than the chicken's " + rate);
			}
		}
		helper.succeed();
	}

	/**
	 * D8: a stray makes {@code 6 × 1.5 = 9} EU/t in a snowy biome; back in plains, a tick later, it makes the
	 * plain {@code floor(6 × (0.5 + 0.5 × 5999/6000)) = 5}. The biome is local to the test, unlike time or weather.
	 */
	public static void strayRunsHarderInASnowyBiome(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		shutIn(helper, drive, EntityTypes.STRAY);
		helper.setBiome(Biomes.SNOWY_PLAINS);
		AlaGameTestHelper.drive(drive, helper, 1);
		int snowy = drive.productionRate();
		int expectedSnowy = MobWheelOutput.euFor(6, 100, 50, 1.0, 1.0);
		helper.setBiome(Biomes.PLAINS);
		AlaGameTestHelper.drive(drive, helper, 1);
		int plain = drive.productionRate();
		int stamina = MobWheelProfile.STRAY.staminaTicks();
		int expectedPlain = MobWheelOutput.euFor(6, 100, 0, 1.0, (stamina - 1) / (double) stamina);
		if (snowy != expectedSnowy || plain != expectedPlain) {
			helper.fail("stray: " + snowy + " EU/t in snowy plains (expected " + expectedSnowy + "), " + plain
					+ " in plains (expected " + expectedPlain + ")");
		}
		helper.succeed();
	}

	/**
	 * D9: a zombie with a live player target four blocks away is distracted — no EU, no stamina spent, and the
	 * wheel no longer turns it along the run; with the target gone it runs again, facing the run.
	 */
	public static void playerTargetDistractsTheOccupant(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		Zombie zombie = shutIn(helper, drive, EntityTypes.ZOMBIE);
		AlaGameTestHelper.drive(drive, helper, 1);
		// The in-level mock player reports creative, and a mob never targets a creative player; the detached
		// mock keeps its survival game mode. It has no connection, so it is placed with setPos, not snapTo.
		ServerPlayer player = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		Vec3 front = helper.absoluteVec(new Vec3(gate().getX() + 0.5, gate().getY(), gate().getZ() + 3.0));
		player.setPos(front);
		zombie.setTarget(player);
		if (zombie.getTarget() != player) {
			helper.fail("the zombie did not take the survival player as its target");
		}
		zombie.setYRot(37.0F);
		int stamina = drive.staminaPermille();
		long charge = batteryCharge(helper);
		AlaGameTestHelper.drive(drive, helper, 10);
		if (drive.status() != MobWheelStatus.DISTRACTED || drive.productionRate() != 0) {
			helper.fail("with a player target the zombie is " + drive.status() + " at " + drive.productionRate());
		}
		if (drive.staminaPermille() != stamina || batteryCharge(helper) != charge) {
			helper.fail("a distracted zombie spent stamina or charged the battery");
		}
		if (Math.abs(zombie.getYRot() - 37.0F) > 1.0E-3) {
			helper.fail("the wheel turned a distracted zombie to " + zombie.getYRot());
		}
		zombie.setTarget(null);
		AlaGameTestHelper.drive(drive, helper, 1);
		float run = MobWheelStructure.runYaw(Direction.SOUTH);
		if (drive.status() != MobWheelStatus.RUNNING || Math.abs(zombie.getYRot() - run) > 1.0E-3) {
			helper.fail("without a target the zombie is " + drive.status() + " facing " + zombie.getYRot());
		}
		helper.succeed();
	}

	/**
	 * D8: a goat turns the wheel twice as fast; a piglin held in the overworld does not start turning into a
	 * zombified piglin, and gets that back once released.
	 */
	public static void traitsActOnTheLiveMob(GameTestHelper helper) {
		clearBox(helper);
		MobWheelBlockEntity drive = energyRig(helper);
		Mob goat = shutIn(helper, drive, EntityTypes.GOAT);
		AlaGameTestHelper.drive(drive, helper, 1);
		if (drive.speedPercent() != 200) {
			helper.fail("a fresh goat turns the wheel at " + drive.speedPercent() + " %, expected 200");
		}
		swingGate(helper);
		goat.discard();
		swingGate(helper);
		swingGate(helper);
		Piglin piglin = shutIn(helper, drive, EntityTypes.PIGLIN);
		if (!piglin.isConverting()) {
			helper.fail("precondition: a piglin in the test level should be converting");
		}
		AlaGameTestHelper.drive(drive, helper, 1);
		if (piglin.isConverting()) {
			helper.fail("a piglin held in the wheel is still turning into a zombified piglin");
		}
		swingGate(helper);
		if (drive.species() != null || !piglin.isConverting()) {
			helper.fail("the released piglin did not get its conversion back");
		}
		helper.succeed();
	}
}
