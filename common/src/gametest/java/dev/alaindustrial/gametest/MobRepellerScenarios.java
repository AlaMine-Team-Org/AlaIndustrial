package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import dev.alaindustrial.entity.HostileThreat;
import dev.alaindustrial.entity.MobRepellerField;
import dev.alaindustrial.entity.SoulVesselKills;
import dev.alaindustrial.item.misc.SoulVesselItem;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Loader-neutral gametest bodies for the Mob Repeller (MOD-278, suite TC-REP-001). The same bodies
 * run on the Fabric {@code @GameTest} lane and the NeoForge {@code gameTestServer} lane.
 *
 * <p><b>The zone is pinned to radius 1 in every case that sweeps.</b> The gametest server shares one
 * world across all scenarios and the structures sit a few blocks apart, so a live radius-8 field
 * would reach into a neighbour's rig — the exact class of bug that made MOD-277 red on NeoForge and
 * green on Fabric for the same code (the two loaders lay structures out differently). Restoring the
 * configured radius in a {@code finally} is part of the contract, not politeness.
 *
 * <p>Kills are banked through {@link SoulVesselKills#onDeath} with a real {@code DamageSource} rather
 * than by killing a mob and waiting: the vessel's rule is about WHO dealt the blow, and driving the
 * accounting directly is what lets a test tell "player killed it" from "something killed it" without
 * depending on the mob's own pathfinding reaching the player at all.
 */
public final class MobRepellerScenarios {

	private MobRepellerScenarios() {
	}

	private static final BlockPos REPELLER = new BlockPos(1, 2, 1);

	/** Run {@code body} with the LV field radius pinned to 1 (see the class doc). */
	private static void withIsolatedZone(Runnable body) {
		int configured = Config.mobRepellerRange;
		Config.mobRepellerRange = 1;
		try {
			body.run();
		} finally {
			Config.mobRepellerRange = configured;
		}
	}

	private static MobRepellerBlockEntity placePowered(GameTestHelper helper) {
		helper.setBlock(REPELLER, ModContent.MOB_REPELLER.get());
		MobRepellerBlockEntity repeller = helper.getBlockEntity(REPELLER, MobRepellerBlockEntity.class);
		repeller.getEnergyStorage().setAmountUntracked(Config.mobRepellerBuffer);
		return repeller;
	}

	private static ItemStack vessel(int kills) {
		ItemStack stack = new ItemStack(ModContent.SOUL_VESSEL.get());
		SoulVesselItem.setKills(stack, kills);
		return stack;
	}

	// --- FUN01: a hostile mob inside the zone is expelled, and the field bills its upkeep ---

	/**
	 * @implements TC-REP-001-FUN01 — one powered tick sweeps the zone, acts on the zombie standing in
	 *     it, and debits exactly one tick of upkeep.
	 */
	public static void fun01ExpelsHostileAndBillsUpkeep(GameTestHelper helper) {
		withIsolatedZone(() -> {
			MobRepellerBlockEntity repeller = placePowered(helper);
			long before = repeller.getEnergyStorage().getAmount();

			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, REPELLER.above());
			ServerLevel level = helper.getLevel();
			int repelled = MobRepellerField.sweep(level, helper.absolutePos(REPELLER),
					Config.mobRepellerRange);
			helper.assertTrue(repelled == 1,
					"the zombie standing in the zone must be expelled, got " + repelled);

			// Drive the block ENTITY: tickBlock only runs the block's own scheduled tick, while
			// the upkeep lives in onServerTick — a tickBlock test would read 0 EU forever.
			AlaGameTestHelper.drive(repeller, helper, 1);
			long spent = before - repeller.getEnergyStorage().getAmount();
			helper.assertTrue(spent == Config.mobRepellerEuPerTick,
					"one powered tick must cost exactly " + Config.mobRepellerEuPerTick
							+ " EU, spent " + spent);
			helper.assertTrue(zombie.isAlive(), "the field expels mobs, it must never kill them");
			helper.succeed();
		});
	}

	// --- FUN02: passive mobs are untouched ---

	/**
	 * @implements TC-REP-001-FUN02 — a cow in the zone is not a threat and must be ignored, otherwise
	 *     the repeller would empty a farm the player built around it.
	 */
	public static void fun02IgnoresPassiveMobs(GameTestHelper helper) {
		withIsolatedZone(() -> {
			placePowered(helper);
			Cow cow = helper.spawn(EntityTypes.COW, REPELLER.above());
			helper.assertFalse(HostileThreat.isActiveThreat(cow), "a cow is not an active threat");

			int repelled = MobRepellerField.sweep(helper.getLevel(), helper.absolutePos(REPELLER),
					Config.mobRepellerRange);
			helper.assertTrue(repelled == 0, "a passive mob must not be acted on, got " + repelled);
			helper.succeed();
		});
	}

	// --- FUN03: no EU, no field ---

	/**
	 * @implements TC-REP-001-FUN03 — an unpowered repeller neither bills nor lights up. The guard that
	 *     matters: a starved block must not keep sweeping for free.
	 */
	public static void fun03UnpoweredFieldIsDark(GameTestHelper helper) {
		helper.setBlock(REPELLER, ModContent.MOB_REPELLER.get());
		MobRepellerBlockEntity repeller = helper.getBlockEntity(REPELLER, MobRepellerBlockEntity.class);
		repeller.getEnergyStorage().setAmountUntracked(0);

		AlaGameTestHelper.drive(repeller, helper, 1);
		helper.assertTrue(repeller.getEnergyStorage().getAmount() == 0,
				"an empty buffer must stay empty — nothing to spend");
		helper.assertBlockProperty(REPELLER, net.minecraft.world.level.block.state.properties
				.BlockStateProperties.LIT, false);
		helper.succeed();
	}

	// --- FUN04: a full vessel evolves the block to the next tier, carrying its charge ---

	/**
	 * @implements TC-REP-001-FUN04 — a vessel holding the MV threshold turns the LV block into the MV
	 *     block on the next tick, is consumed whole, and the stored EU rides along.
	 */
	public static void fun04FullVesselEvolvesTier(GameTestHelper helper) {
		MobRepellerBlockEntity repeller = placePowered(helper);
		long charge = repeller.getEnergyStorage().getAmount();
		repeller.setItem(MobRepellerBlockEntity.VESSEL_SLOT, vessel(Config.mobRepellerEvolveKillsMv));

		AlaGameTestHelper.drive(repeller, helper, 1);

		helper.assertBlockPresent(ModContent.MOB_REPELLER_MV.get(), REPELLER);
		MobRepellerBlockEntity evolved = helper.getBlockEntity(REPELLER, MobRepellerBlockEntity.class);
		helper.assertTrue(evolved.getItem(MobRepellerBlockEntity.VESSEL_SLOT).isEmpty(),
				"the vessel must be consumed whole by the evolution");
		helper.assertTrue(evolved.getEnergyStorage().getAmount() == charge,
				"the evolved tier must keep the charge: expected " + charge
						+ ", got " + evolved.getEnergyStorage().getAmount());
		helper.assertTrue(evolved.fieldRange() == Config.mobRepellerRangeMv,
				"the evolved block must run the MV radius");
		helper.succeed();
	}

	// --- NEG01: an under-filled vessel is refused by the slot and never evolves ---

	/**
	 * @implements TC-REP-001-NEG01 — an under-filled vessel GOES IN (the slot no longer refuses it in
	 *     silence, which read as a broken slot) but does not evolve the block: the tick leaves the LV
	 *     repeller standing and the vessel where it is, so the player can watch the count climb.
	 */
	public static void neg01PartialVesselDoesNotEvolve(GameTestHelper helper) {
		MobRepellerBlockEntity repeller = placePowered(helper);
		ItemStack partial = vessel(Config.mobRepellerEvolveKillsMv - 1);

		helper.assertTrue(repeller.canPlaceItem(MobRepellerBlockEntity.VESSEL_SLOT, partial),
				"an under-filled vessel must be accepted — the screen explains what it still needs");

		repeller.setItem(MobRepellerBlockEntity.VESSEL_SLOT, partial);
		AlaGameTestHelper.drive(repeller, helper, 1);
		helper.assertBlockPresent(ModContent.MOB_REPELLER.get(), REPELLER);
		helper.assertTrue(!repeller.getItem(MobRepellerBlockEntity.VESSEL_SLOT).isEmpty(),
				"the vessel must stay in the slot until it is full enough to be spent");
		helper.succeed();
	}

	/**
	 * @implements TC-REP-001-NEG04 — anything that is not a Soul Vessel is still refused: the slot got
	 *     friendlier, not indiscriminate.
	 */
	public static void neg04ForeignItemRejected(GameTestHelper helper) {
		MobRepellerBlockEntity repeller = placePowered(helper);
		helper.assertFalse(repeller.canPlaceItem(MobRepellerBlockEntity.VESSEL_SLOT,
						new ItemStack(net.minecraft.world.item.Items.DIAMOND)),
				"only a Soul Vessel belongs in this slot");
		helper.succeed();
	}

	// --- FUN05: a personal kill banks a soul; a non-player kill does not ---

	/**
	 * @implements TC-REP-001-FUN05 — a hostile mob killed by the player banks exactly one soul into
	 *     the vessel in their inventory.
	 *
	 * <p><b>The mob is really killed first.</b> The original version called the accounting on a LIVE
	 *     zombie, which made the case unfailable: the production predicate asked
	 *     {@code LivingEntity.isAlive()}, which is false for every corpse the death hook ever sees, so
	 *     the shipped build banked nothing at all while this test stayed green. Killing the mob before
	 *     the assertion is the whole point of the case — restore the liveness check and it goes red.
	 */
	public static void fun05PlayerKillBanksSoul(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		// setItem, not add(): add() stores a COPY, so the local reference would never see the soul
		// the accounting writes — an earlier version of this test passed `banked` and still read 0.
		ItemStack stack = vessel(0);
		player.getInventory().setItem(0, stack);

		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, REPELLER.above());
		zombie.setHealth(0.0F);   // a corpse, exactly as the death hook receives it
		boolean banked = SoulVesselKills.onDeath(zombie,
				helper.getLevel().damageSources().playerAttack(player));

		helper.assertTrue(banked, "a hostile mob killed by the player must bank a soul");
		helper.assertTrue(SoulVesselItem.kills(stack) == 1,
				"exactly one soul per kill, got " + SoulVesselItem.kills(stack));
		helper.succeed();
	}

	/**
	 * @implements TC-REP-001-FUN06 — the whole chain end to end: the player deals lethal damage, the
	 *     LOADER's death hook fires, and the soul lands in the vessel. FUN05 drives the accounting
	 *     directly and so cannot see a hook that was never wired; this one can.
	 */
	public static void fun06RealKillThroughLoaderHook(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ItemStack stack = vessel(0);
		player.getInventory().setItem(0, stack);

		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, REPELLER.above());
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player),
				1000.0F);

		helper.assertFalse(zombie.isAlive(), "the zombie must actually be dead for this case to mean anything");
		helper.assertTrue(SoulVesselItem.kills(stack) == 1,
				"the loader death hook must bank exactly one soul, got " + SoulVesselItem.kills(stack));
		helper.succeed();
	}

	/**
	 * @implements TC-REP-001-NEG02 — the same kill dealt by something that is not the player banks
	 *     nothing. This is the rule that keeps the upgrade a measure of the player's own fighting; the
	 *     test goes red the moment the check is loosened to {@code getKillCredit()}, which would also
	 *     count a pet's kill.
	 */
	public static void neg02NonPlayerKillBanksNothing(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		// setItem, not add(): add() stores a COPY, so the local reference would never see the soul
		// the accounting writes — the first version of this test passed `banked` and still read 0.
		ItemStack stack = vessel(0);
		player.getInventory().setItem(0, stack);

		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, REPELLER.above());
		zombie.setHealth(0.0F);
		boolean banked = SoulVesselKills.onDeath(zombie, helper.getLevel().damageSources().fall());

		helper.assertFalse(banked, "a kill with no player behind it must bank nothing");
		helper.assertTrue(SoulVesselItem.kills(stack) == 0,
				"the vessel must stay empty, got " + SoulVesselItem.kills(stack));
		helper.succeed();
	}

	/**
	 * @implements TC-REP-001-NEG03 — killing a PASSIVE mob banks nothing even when the player dealt
	 *     the blow: the vessel measures fighting monsters, not butchering cows.
	 */
	public static void neg03PassiveKillBanksNothing(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		// setItem, not add(): add() stores a COPY, so the local reference would never see the soul
		// the accounting writes — the first version of this test passed `banked` and still read 0.
		ItemStack stack = vessel(0);
		player.getInventory().setItem(0, stack);

		Cow cow = helper.spawn(EntityTypes.COW, REPELLER.above());
		cow.setHealth(0.0F);   // same corpse state as FUN05, so the two cases differ only in the mob
		boolean banked = SoulVesselKills.onDeath(cow,
				helper.getLevel().damageSources().playerAttack(player));

		helper.assertFalse(banked, "a passive kill must bank nothing");
		helper.assertTrue(SoulVesselItem.kills(stack) == 0, "the vessel must stay empty");
		helper.succeed();
	}
}
