package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ElectricMobTrapBlockEntity;
import dev.alaindustrial.block.entity.ElectricMobTrapBlockEntity.Status;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.compat.Invulnerability;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.item.ItemNetworkManager;
import dev.alaindustrial.entity.HostileThreat;
import dev.alaindustrial.entity.MobRepellerField;
import dev.alaindustrial.entity.SoulVesselKills;
import dev.alaindustrial.fluid.ExperienceFluid;
import dev.alaindustrial.item.misc.SoulVesselItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDamageTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametests of the Electric Mob Trap, tier I (MOD-818, suite TC-MOBTRAP-001), run by both
 * loaders from the roster.
 *
 * <p>The tests drive {@link ElectricMobTrapBlockEntity#pulse} and {@link ElectricMobTrapBlockEntity#shock}
 * directly, so no test waits on a schedule or on a mob's own pathfinding. Every mob is spawned with its goals
 * switched off and without gravity: it stays where the test put it, and only the trap can move it.
 * Skeletons are the test mob because they are hostile, never baby, and give a fixed 5 points of experience.
 */
public final class ElectricMobTrapScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ElectricMobTrapScenarios::shockHurtsAndCostsEnergy, "mod818_shock_hurts_and_costs"),
				RosterEntry.of(ElectricMobTrapScenarios::nothingWithoutEnergy, "mod818_nothing_without_energy"),
				RosterEntry.of(ElectricMobTrapScenarios::farMobIsNotShocked, "mod818_far_mob_is_not_shocked"),
				RosterEntry.of(ElectricMobTrapScenarios::lootGoesToTheBuffer, "mod818_loot_goes_to_the_buffer"),
				RosterEntry.of(ElectricMobTrapScenarios::sweepPullsADropInReach, "mod818_sweep_pulls_a_drop_in_reach"),
				RosterEntry.of(ElectricMobTrapScenarios::experienceBecomesLiquid, "mod818_experience_becomes_liquid"),
				RosterEntry.of(ElectricMobTrapScenarios::fullTankKeepsTheOrb, "mod818_full_tank_keeps_the_orb"),
				RosterEntry.of(ElectricMobTrapScenarios::fullBufferStopsTheShocks,
						"mod818_full_buffer_stops_the_shocks"),
				RosterEntry.of(ElectricMobTrapScenarios::peacefulMobsAreLeftAlone,
						"mod818_peaceful_mobs_are_left_alone"),
				RosterEntry.of(ElectricMobTrapScenarios::bossesAndShulkersAreExcluded,
						"mod818_bosses_and_shulkers_excluded"),
				RosterEntry.of(ElectricMobTrapScenarios::repellerWins, "mod818_repeller_wins"),
				RosterEntry.of(ElectricMobTrapScenarios::calmNeutralMonstersAreTrapped,
						"mod818_calm_neutral_monsters_are_trapped"),
				RosterEntry.of(ElectricMobTrapScenarios::pullMakesAMobForgetThePlayer,
						"mod818_pull_makes_a_mob_forget_the_player"),
				RosterEntry.of(ElectricMobTrapScenarios::brainMobGetsAWalkTarget,
						"mod818_brain_mob_gets_a_walk_target"),
				RosterEntry.of(ElectricMobTrapScenarios::shockedEndermanStays, "mod818_shocked_enderman_stays"),
				RosterEntry.of(ElectricMobTrapScenarios::nearPlayerIsShocked, "mod818_survival_player_is_a_target"),
				RosterEntry.of(ElectricMobTrapScenarios::facesAndFluid, "mod818_faces_and_fluid"),
				RosterEntry.of(ElectricMobTrapScenarios::pulseWorkIsBounded, "mod818_pulse_work_is_bounded"),
				RosterEntry.of(ElectricMobTrapScenarios::pipeCarriesTheLootOut, "mod818_pipe_carries_the_loot_out"),
				RosterEntry.of(ElectricMobTrapScenarios::soulVesselStaysEmpty, "mod818_soul_vessel_stays_empty"),
				RosterEntry.of(ElectricMobTrapScenarios::stateSurvivesReload, "mod818_state_survives_reload"));

		private Roster() {}
	}

	private ElectricMobTrapScenarios() {}

	private static final BlockPos TRAP = new BlockPos(2, 2, 2);
	/** Next to the trap, well inside the strike radius. */
	private static final BlockPos NEAR = new BlockPos(2, 2, 3);
	/** Four blocks off: inside the reach of 6, outside the strike radius of 2. */
	private static final BlockPos FAR = new BlockPos(2, 2, 6);

	private static long cost() {
		return Math.max(1, Config.electricMobTrapEuPerStrike);
	}

	private static ElectricMobTrapBlockEntity place(GameTestHelper helper, long energy) {
		ElectricMobTrapBlockEntity be = AlaGameTestHelper.place(helper, TRAP, ModContent.ELECTRIC_MOB_TRAP.get(),
				ElectricMobTrapBlockEntity.class);
		be.getEnergyStorage().setAmountUntracked(energy);
		return be;
	}

	/** A hostile mob that stays exactly where it is put. */
	private static Skeleton skeleton(GameTestHelper helper, BlockPos at) {
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, at);
		skeleton.setNoAi(true);
		skeleton.setNoGravity(true);
		return skeleton;
	}

	/** A skeleton any shock kills. */
	private static Skeleton fragile(GameTestHelper helper, BlockPos at) {
		Skeleton skeleton = skeleton(helper, at);
		skeleton.setHealth(1.0F);
		return skeleton;
	}

	private static int bufferTotal(ElectricMobTrapBlockEntity be) {
		int n = 0;
		for (int slot = 0; slot < ElectricMobTrapBlockEntity.SLOT_COUNT; slot++) {
			n += be.getItem(slot).getCount();
		}
		return n;
	}

	private static AABB reachBox(GameTestHelper helper) {
		return new AABB(helper.absolutePos(TRAP)).inflate(ElectricMobTrapBlockEntity.reach());
	}

	/** The ids of every entity of {@code type} in reach: what was there before the test acted. */
	private static <T extends net.minecraft.world.entity.Entity> List<Integer> entityIds(GameTestHelper helper,
			Class<T> type) {
		return helper.getLevel().getEntitiesOfClass(type, reachBox(helper)).stream().map(e -> e.getId()).toList();
	}

	/** The entities of {@code type} in reach that were not there before: what the test's own kills dropped. */
	private static <T extends net.minecraft.world.entity.Entity> List<T> newEntities(GameTestHelper helper,
			Class<T> type, List<Integer> before) {
		return helper.getLevel().getEntitiesOfClass(type, reachBox(helper), e -> !before.contains(e.getId()));
	}

	/** Put a drop on the lid of the trap, still, so a sweep finds it touching the block. */
	private static void layOnTheLid(ElectricMobTrapBlockEntity be, net.minecraft.world.entity.Entity drop) {
		Vec3 c = Vec3.atCenterOf(be.getBlockPos());
		drop.setPos(c.x, c.y + 0.3, c.z);
		drop.setDeltaMovement(Vec3.ZERO);
		drop.setNoGravity(true);
	}

	/** Mob drops spawn with a pickup delay; the sweep, like a hopper, waits it out. The tests do not wait. */
	private static void item0PickupDelay(List<ItemEntity> loot) {
		loot.forEach(item -> item.setPickUpDelay(0));
	}

	private static int orbPointsNear(GameTestHelper helper) {
		int points = 0;
		for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, reachBox(helper))) {
			points += orb.getValue();
		}
		return points;
	}

	private static int orbsNear(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, reachBox(helper)).size();
	}

	private static int itemEntitiesNear(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, reachBox(helper)).size();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN01 — a powered pulse shocks the mob next to the trap for at most the
	 *     configured damage and takes exactly one shock's EU.
	 */
	public static void shockHurtsAndCostsEnergy(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, cost() * 3);
		Skeleton skeleton = skeleton(helper, NEAR);
		float before = skeleton.getHealth();

		Status status = be.pulse(helper.getLevel());

		helper.assertTrue(status == Status.WORKING, "a powered pulse next to a hostile mob must work, got " + status);
		helper.assertTrue(be.getEnergyStorage().getAmount() == cost() * 2,
				"one shock must cost exactly " + cost() + " EU, buffer is " + be.getEnergyStorage().getAmount());
		float lost = before - skeleton.getHealth();
		helper.assertTrue(lost > 0.0F && lost <= Config.electricMobTrapDamage,
				"the shock must hurt by at most " + Config.electricMobTrapDamage + ", it took " + lost);
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-NEG01 — with less energy than one shock the trap deals nothing and spends
	 *     nothing.
	 */
	public static void nothingWithoutEnergy(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, cost() - 1);
		Skeleton skeleton = skeleton(helper, NEAR);
		float before = skeleton.getHealth();

		Status status = be.pulse(helper.getLevel());

		helper.assertTrue(status == Status.NO_POWER, "an underpowered trap must report NO_POWER, got " + status);
		helper.assertTrue(skeleton.getHealth() == before, "an underpowered trap must not hurt the mob");
		helper.assertTrue(be.getEnergyStorage().getAmount() == cost() - 1, "an underpowered trap must spend nothing");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN02 — a mob in reach but outside the strike radius is not shocked and
	 *     costs nothing; only a mob that arrives is struck.
	 */
	public static void farMobIsNotShocked(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, cost() * 2);
		Skeleton far = skeleton(helper, FAR);
		float before = far.getHealth();

		Status status = be.pulse(helper.getLevel());

		helper.assertTrue(status == Status.IDLE, "a mob out of the strike radius must leave the trap idle, got "
				+ status);
		helper.assertTrue(far.getHealth() == before, "a far mob must not be shocked");
		helper.assertTrue(be.getEnergyStorage().getAmount() == cost() * 2, "no shock, no EU spent");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN03 — the loot of a killed mob falls as on any death, and the trap then draws
	 *     it in: a sweep takes every drop that rests on the block into the buffer. A dozen skeletons make an
	 *     all-empty roll (bones and arrows are random) about one chance in 3.10^11.
	 */
	public static void lootGoesToTheBuffer(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, cost() * 20);
		ServerLevel level = helper.getLevel();
		List<Integer> before = entityIds(helper, ItemEntity.class);
		for (int i = 0; i < 12; i++) {
			Skeleton skeleton = fragile(helper, NEAR);
			helper.assertTrue(be.shock(level, skeleton, cost()), "the shock must land on a living skeleton");
			helper.assertFalse(skeleton.isAlive(), "a one-hit-point skeleton must die of the shock");
		}
		List<ItemEntity> loot = newEntities(helper, ItemEntity.class, before);
		helper.assertTrue(!loot.isEmpty(), "the loot must fall into the world first, as on any death");
		helper.assertTrue(bufferTotal(be) == 0, "nothing may reach the buffer before a sweep");
		loot.forEach(item -> layOnTheLid(be, item));
		item0PickupDelay(loot);

		be.sweep(level);

		helper.assertTrue(bufferTotal(be) > 0, "the swept loot must be in the buffer");
		helper.assertTrue(loot.stream().noneMatch(ItemEntity::isAlive), "every drop resting on the block is taken");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN13 — a drop further out is pulled, not teleported: a sweep sets it moving
	 *     towards the trap, the Vacuum Collector's way.
	 */
	public static void sweepPullsADropInReach(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, 0);
		Vec3 c = Vec3.atCenterOf(be.getBlockPos());
		ItemEntity bone = new ItemEntity(helper.getLevel(), c.x + 3.0, c.y, c.z, new ItemStack(Items.BONE, 4));
		bone.setDeltaMovement(Vec3.ZERO);
		bone.setPickUpDelay(0);
		bone.setNoGravity(true);
		helper.getLevel().addFreshEntity(bone);

		be.sweep(helper.getLevel());

		helper.assertTrue(bone.isAlive(), "a drop out of touch must not be taken at once");
		helper.assertTrue(bone.getDeltaMovement().x < 0, "a drop east of the trap must be set moving west, got "
				+ bone.getDeltaMovement());
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN04 — the experience of a killed mob drops as orbs, and an orb resting on the
	 *     trap goes into the tank as liquid experience at 20 mB a point (a skeleton gives 5); the orbs falling at
	 *     all prove the kill is credited to the trap's player: experience is dropped only for a player's kill.
	 */
	public static void experienceBecomesLiquid(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, cost() * 2);
		List<Integer> before = entityIds(helper, ExperienceOrb.class);

		be.shock(helper.getLevel(), fragile(helper, NEAR), cost());
		List<ExperienceOrb> orbs = newEntities(helper, ExperienceOrb.class, before);
		int points = orbs.stream().mapToInt(ExperienceOrb::getValue).sum();
		helper.assertTrue(points == 5, "a skeleton killed for a player drops 5 points of orbs, got " + points);
		orbs.forEach(orb -> layOnTheLid(be, orb));

		be.sweep(helper.getLevel());

		helper.assertTrue(be.xpTank.amount == 5 * ExperienceFluid.MB_PER_POINT,
				"a skeleton's 5 points must be " + 5 * ExperienceFluid.MB_PER_POINT + " mB, tank holds "
						+ be.xpTank.amount);
		helper.assertTrue(be.xpTank.fluid.fluid() == ModContent.EXPERIENCE.get(),
				"the tank must hold liquid experience");
		helper.assertTrue(orbs.stream().noneMatch(ExperienceOrb::isAlive), "the orbs must be gone from the world");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-NEG02 — an orb the tank cannot hold whole stays in the world: the tank never
	 *     overflows and the experience is never lost silently.
	 */
	public static void fullTankKeepsTheOrb(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, 0);
		long almostFull = be.xpTank.capacity - ExperienceFluid.MB_PER_POINT;
		be.xpTank.fluid = FluidHolder.of(ModContent.EXPERIENCE.get());
		be.xpTank.amount = almostFull;
		Vec3 c = Vec3.atCenterOf(be.getBlockPos());
		ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), c.x, c.y + 0.3, c.z, 3);
		orb.setNoGravity(true);
		helper.getLevel().addFreshEntity(orb);

		be.sweep(helper.getLevel());

		helper.assertTrue(be.xpTank.amount == almostFull, "an orb of 3 points does not fit in room for 1");
		helper.assertTrue(orb.isAlive(), "the orb that did not fit must stay in the world");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-NEG03 — with every buffer slot at its cap the trap stops shocking, so
	 *     nothing is ever thrown out into the world; the energy stays untouched.
	 */
	public static void fullBufferStopsTheShocks(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, cost() * 2);
		List<Item> distinct = List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.COAL,
				Items.DIAMOND, Items.EMERALD, Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ);
		for (int slot = 0; slot < ElectricMobTrapBlockEntity.SLOT_COUNT; slot++) {
			be.setItem(slot, new ItemStack(distinct.get(slot), 64));
		}
		Skeleton skeleton = skeleton(helper, NEAR);
		float before = skeleton.getHealth();

		Status status = be.pulse(helper.getLevel());

		helper.assertTrue(status == Status.BUFFER_FULL, "a full buffer must report BUFFER_FULL, got " + status);
		helper.assertTrue(skeleton.getHealth() == before, "a trap with nowhere to put loot must not shock");
		helper.assertTrue(be.getEnergyStorage().getAmount() == cost() * 2, "no shock, no EU spent");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-NEG04 — peaceful mobs and villagers are never targets, however close and
	 *     however well powered the trap.
	 */
	public static void peacefulMobsAreLeftAlone(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, cost() * 4);
		Cow cow = helper.spawn(EntityTypes.COW, NEAR);
		Villager villager = helper.spawn(EntityTypes.VILLAGER, NEAR.east());
		cow.setNoAi(true);
		villager.setNoAi(true);
		float cowHealth = cow.getHealth();
		float villagerHealth = villager.getHealth();

		Status status = be.pulse(helper.getLevel());

		helper.assertTrue(status == Status.IDLE, "only peaceful mobs in reach must leave the trap idle, got "
				+ status);
		helper.assertTrue(cow.getHealth() == cowHealth, "a cow must not be shocked");
		helper.assertTrue(villager.getHealth() == villagerHealth, "a villager must not be shocked");
		helper.assertTrue(be.getEnergyStorage().getAmount() == cost() * 4, "nothing shocked, nothing spent");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-NEG05 — the three bosses and the shulker are excluded by type, although
	 *     the general threat filter would let every one of them through.
	 */
	public static void bossesAndShulkersAreExcluded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Mob> bosses = List.of(new EnderDragon(EntityTypes.ENDER_DRAGON, level),
				new WitherBoss(EntityTypes.WITHER, level), new Warden(EntityTypes.WARDEN, level));
		for (Mob boss : bosses) {
			helper.assertTrue(HostileThreat.isActiveThreat(boss),
					boss.getType() + " must pass the general threat filter, or this test proves nothing");
			helper.assertTrue(ElectricMobTrapBlockEntity.isBoss(boss), boss.getType() + " must be named a boss");
			helper.assertFalse(ElectricMobTrapBlockEntity.isTrappable(level, boss),
					boss.getType() + " must never be trapped");
		}
		Shulker shulker = new Shulker(EntityTypes.SHULKER, level);
		helper.assertFalse(ElectricMobTrapBlockEntity.isTrappable(level, shulker), "a shulker must never be trapped");
		helper.assertTrue(ElectricMobTrapBlockEntity.isTrappable(level, new Skeleton(EntityTypes.SKELETON, level)),
				"an ordinary skeleton must be trapped, or the exclusions above prove nothing");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN05 — a mob standing in the field of a live Mob Repeller is left alone,
	 *     while a mob outside it is shocked in the same pulse: the repeller wins.
	 */
	public static void repellerWins(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync().set("electricMobTrapStrikeRadius", 8)) {
			ElectricMobTrapBlockEntity be = place(helper, cost() * 4);
			BlockPos repeller = new BlockPos(6, 2, 6);
			Skeleton guarded = skeleton(helper, repeller);
			Skeleton free = skeleton(helper, NEAR);
			float guardedHealth = guarded.getHealth();
			MobRepellerField.sweep(helper.getLevel(), helper.absolutePos(repeller), 1);

			Status status = be.pulse(helper.getLevel());

			helper.assertTrue(status == Status.WORKING, "the unguarded mob must still be shocked, got " + status);
			helper.assertTrue(free.getHealth() < free.getMaxHealth(), "a mob outside the repeller's field is shocked");
			helper.assertTrue(guarded.getHealth() == guardedHealth, "a mob inside a live repeller field is not");
			helper.assertTrue(be.getEnergyStorage().getAmount() == cost() * 3, "exactly one shock was paid for");
			helper.succeed();
		}
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN09 — calm neutral monsters are trapped too (owner decision 2026-10-15): an
	 *     enderman, a zombified piglin and a piglin pass, though the repeller's threat filter would drop them.
	 */
	public static void calmNeutralMonstersAreTrapped(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob enderman = helper.spawn(EntityTypes.ENDERMAN, FAR);
		enderman.setNoAi(true);
		List<Mob> calm = List.of(enderman,
				new ZombifiedPiglin(EntityTypes.ZOMBIFIED_PIGLIN, level), new Piglin(EntityTypes.PIGLIN, level));
		for (Mob mob : calm) {
			helper.assertTrue(ElectricMobTrapBlockEntity.isTrappable(level, mob), mob.getType() + " must be trapped");
		}
		helper.assertFalse(HostileThreat.isActiveThreat(calm.get(0)),
				"a calm enderman must be dropped by the repeller's filter, or this test proves nothing");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN10 — a mob in reach that is chasing a player forgets the player when the
	 *     trap pulls it: a farm whose owner stands nearby would otherwise pull nothing.
	 */
	public static void pullMakesAMobForgetThePlayer(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, cost() * 2);
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		Skeleton skeleton = skeleton(helper, FAR);
		skeleton.setTarget(player);

		be.pulse(helper.getLevel());

		helper.assertTrue(skeleton.getTarget() == null, "a pulled mob must forget the player it was chasing");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN11 — a brain-driven mob is pulled through its brain: the trap writes the
	 *     centre as its walk target, which a navigation path would never survive.
	 */
	public static void brainMobGetsAWalkTarget(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, cost() * 2);
		Piglin piglin = helper.spawn(EntityTypes.PIGLIN, FAR);
		piglin.setNoGravity(true);
		piglin.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

		be.pulse(helper.getLevel());

		helper.assertTrue(piglin.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
				.map(target -> target.getTarget().currentPosition().distanceTo(Vec3.atCenterOf(be.getBlockPos())) < 0.5)
				.orElse(false), "a pulled piglin must walk to the trap's centre");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN12 — a shocked enderman does not teleport away: its shock names the trap's
	 *     fake player, and the anger it leaves is cleared. Ten shocks, each a 90 % teleport without the fix.
	 */
	public static void shockedEndermanStays(GameTestHelper helper) {
		try (ConfigOverrides o = ConfigOverrides.sync().set("electricMobTrapDamage", 1)) {
			ElectricMobTrapBlockEntity be = place(helper, cost() * 20);
			Mob enderman = helper.spawn(EntityTypes.ENDERMAN, NEAR);
			enderman.setNoAi(true);
			enderman.setNoGravity(true);
			Vec3 at = enderman.position();
			for (int i = 0; i < 10; i++) {
				Invulnerability.setGraceTicks(enderman, 0);
				be.shock(helper.getLevel(), enderman, cost());
			}
			helper.assertTrue(enderman.position().distanceTo(at) < 0.01, "a shocked enderman must not teleport");
			helper.assertTrue(enderman.getHealth() < enderman.getMaxHealth(), "the enderman must have been hurt");
			helper.assertTrue(enderman.getTarget() == null, "the shock must not leave the enderman a target");
			helper.succeed();
		}
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN14 — a survival player is a shock target (owner decision 2026-10-15), a
	 *     creative (invulnerable) player is not. Eligibility is asserted rather than lost health, as in the
	 *     cable-shock suite: hurting a mock player notifies its connection, and neither mock has a usable one.
	 */
	public static void nearPlayerIsShocked(GameTestHelper helper) {
		ServerPlayer survival = AlaGameTestHelper.detachedSurvivalPlayer(helper);
		helper.assertTrue(ElectricMobTrapBlockEntity.isShockable(survival), "a survival player must be shockable");
		ServerPlayer creative = AlaGameTestHelper.mockPlayerInLevel(helper);
		creative.getAbilities().invulnerable = true;
		helper.assertFalse(ElectricMobTrapBlockEntity.isShockable(creative), "a creative player must be left alone");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN06 — liquid experience carries the {@code c:experience} tag, comes out
	 *     of the lid and the floor only, and nothing goes in by hand: the sides take cables.
	 */
	public static void facesAndFluid(GameTestHelper helper) {
		ElectricMobTrapBlockEntity be = place(helper, 0);
		TagKey<Fluid> tag = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "experience"));
		helper.assertTrue(ModContent.EXPERIENCE.get().defaultFluidState().is(tag),
				"liquid experience must carry c:experience");
		ItemStack stack = new ItemStack(Items.IRON_INGOT);
		for (Direction face : Direction.values()) {
			boolean vertical = face.getAxis().isVertical();
			helper.assertTrue((be.fluidPort(face) != null) == vertical,
					"face " + face + (vertical ? " must" : " must not") + " expose the experience tank");
			int expected = vertical ? ElectricMobTrapBlockEntity.SLOT_COUNT : 0;
			helper.assertTrue(be.getSlotsForFace(face).length == expected,
					"face " + face + " must expose " + expected + " buffer slots");
			for (int slot : be.getSlotsForFace(face)) {
				helper.assertFalse(be.canPlaceItemThroughFace(slot, stack, face), "nothing goes in through " + face);
			}
			helper.assertTrue((be.energyRoleForFace(face) == EnergyRole.IN) != vertical,
					"only the four sides take energy, got " + be.energyRoleForFace(face) + " on " + face);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-PERF01 — a farm of sixty mobs in reach costs one pulse a bounded amount of
	 *     work: at most the configured number of shocks is paid for and dealt, however many mobs wait. The
	 *     time the pulse took is logged, not asserted: a wall-clock bound would be a flaky test.
	 */
	public static void pulseWorkIsBounded(GameTestHelper helper) {
		long start = cost() * 50;
		ElectricMobTrapBlockEntity be = place(helper, start);
		List<Skeleton> farm = new ArrayList<>();
		for (int i = 0; i < 60; i++) {
			farm.add(skeleton(helper, i % 2 == 0 ? NEAR : FAR));
		}
		int limit = Math.max(1, Config.electricMobTrapStrikesPerPulse);

		long started = System.nanoTime();
		Status status = be.pulse(helper.getLevel());
		long micros = (System.nanoTime() - started) / 1000L;

		long spent = start - be.getEnergyStorage().getAmount();
		long hurt = farm.stream().filter(mob -> mob.getHealth() < mob.getMaxHealth()).count();
		Industrialization.LOGGER.info("MOD-818 one pulse over {} mobs took {} us, {} shocks",
				farm.size(), micros, hurt);
		helper.assertTrue(status == Status.WORKING, "a farm in reach must keep the trap working, got " + status);
		helper.assertTrue(hurt >= 1 && hurt <= limit, "at most " + limit + " mobs may be shocked in a pulse, got "
				+ hurt);
		helper.assertTrue(spent == cost() * hurt, "exactly the shocks dealt must be paid for: spent " + spent
				+ " EU for " + hurt + " shocks");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN08 — a plain item pipe on the lid carries the buffer into a chest, so the
	 *     loot reaches an inventory without ever lying in the world.
	 */
	public static void pipeCarriesTheLootOut(GameTestHelper helper) {
		BlockPos pipePos = TRAP.above();
		BlockPos chestPos = pipePos.above();
		helper.setBlock(pipePos, ModContent.ITEM_PIPE.get());
		helper.setBlock(chestPos, ModContent.IRON_CHEST.get());
		ElectricMobTrapBlockEntity be = place(helper, 0);
		be.setItem(0, new ItemStack(Items.BONE, 16));
		ItemPipeBlockEntity pipe = helper.getBlockEntity(pipePos, ItemPipeBlockEntity.class);
		pipe.serverTick(helper.getLevel(), pipe.getBlockPos(), pipe.getBlockState());

		ItemNetworkManager.tickAll(helper.getLevel());

		Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(chestPos));
		int moved = 0;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			if (chest.getItem(slot).is(Items.BONE)) {
				moved += chest.getItem(slot).getCount();
			}
		}
		helper.assertTrue(moved > 0, "the pipe must carry bones out of the trap into the chest");
		helper.assertTrue(moved + be.getItem(0).getCount() == 16, "sixteen bones before and after");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-NEG06 — a kill by the trap banks no soul: the damage has no entity behind it,
	 *     so the Soul Vessel, which counts only a player's own kills, stays empty.
	 */
	public static void soulVesselStaysEmpty(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ItemStack vessel = new ItemStack(ModContent.SOUL_VESSEL.get());
		player.getInventory().setItem(0, vessel);
		ElectricMobTrapBlockEntity be = place(helper, cost() * 2);
		Skeleton skeleton = fragile(helper, NEAR);

		be.shock(helper.getLevel(), skeleton, cost());

		helper.assertFalse(skeleton.isAlive(), "the skeleton must die of the shock");
		helper.assertTrue(SoulVesselItem.kills(vessel) == 0, "the vessel must stay empty, got "
				+ SoulVesselItem.kills(vessel));
		helper.assertFalse(SoulVesselKills.onDeath(skeleton, ModDamageTypes.electricShock(helper.getLevel())),
				"a shock with no entity behind it must bank nothing");
		helper.succeed();
	}

	/**
	 * @implements TC-MOBTRAP-001-FUN07 — the buffer and the tank survive a save and a load.
	 */
	public static void stateSurvivesReload(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		ElectricMobTrapBlockEntity source = SaveFormatTestSupport.fresh(ElectricMobTrapBlockEntity::new,
				ModContent.ELECTRIC_MOB_TRAP.get());
		source.setItem(0, new ItemStack(Items.BONE, 23));
		source.setItem(8, new ItemStack(Items.ARROW, 64));
		source.xpTank.fluid = FluidHolder.of(ModContent.EXPERIENCE.get());
		source.xpTank.amount = 1340;

		CompoundTag saved = source.saveWithoutMetadata(registries);
		ElectricMobTrapBlockEntity restored = SaveFormatTestSupport.load(SaveFormatTestSupport.fresh(
				ElectricMobTrapBlockEntity::new, ModContent.ELECTRIC_MOB_TRAP.get()), registries, saved);

		for (int slot = 0; slot < ElectricMobTrapBlockEntity.SLOT_COUNT; slot++) {
			helper.assertTrue(ItemStack.matches(source.getItem(slot), restored.getItem(slot)),
					"slot " + slot + " held " + source.getItem(slot) + " and came back as " + restored.getItem(slot));
		}
		helper.assertTrue(restored.xpTank.amount == 1340, "the tank amount must come back, got "
				+ restored.xpTank.amount);
		helper.assertTrue(restored.xpTank.fluid.fluid() == ModContent.EXPERIENCE.get(),
				"the tank fluid must come back");
		helper.assertTrue(saved.equals(restored.saveWithoutMetadata(registries)), "the re-save must be the same tag");
		helper.succeed();
	}
}
