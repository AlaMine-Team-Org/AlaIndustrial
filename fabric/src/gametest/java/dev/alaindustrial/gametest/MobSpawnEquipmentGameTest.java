package dev.alaindustrial.gametest;

import dev.alaindustrial.entity.TemperedGearRoll.EquipPlan;
import dev.alaindustrial.entity.TemperedGearSpawns;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 server game tests for natural mob-spawn tempered-iron equipment (MOD-130).
 *
 * <p><b>What these guard:</b> the {@link TemperedGearSpawns#equip} path that the
 * {@code Zombie#finalizeSpawn} / {@code AbstractSkeleton#finalizeSpawn} mixins invoke — that the
 * chosen slots receive the real {@code alaindustrial:tempered_iron_*} stacks on a live mob, and that
 * the fill is <em>non-destructive</em> (a slot already holding gear is never overwritten, so
 * spawner-curated loadouts and a skeleton's bow survive). The random decision itself is pinned
 * separately and deterministically in the L1 {@code TemperedGearRollTest}; here we drive a forced
 * {@link EquipPlan} so the world assertions are stable, not probabilistic.
 *
 * <p>API verified against the 26.2 sources: {@code GameTestHelper.spawn(EntityType, BlockPos)} returns
 * the spawned mob; {@code LivingEntity.getItemBySlot/ setItemSlot(EquipmentSlot, ItemStack)} are public.
 */
public class MobSpawnEquipmentGameTest {

	/**
	 * TC-MOB-001: a full plan equips every tempered-iron slot on an empty-handed zombie — the four
	 * armour pieces plus the mainhand sword — with the exact mod items.
	 */
	@GameTest
	public void tcMob001_fullPlanEquipsTemperedGear(GameTestHelper helper) {
		MobSpawnEquipmentScenarios.tcMob001_fullPlanEquipsTemperedGear(helper);
	}

	/**
	 * TC-MOB-002: the fill only touches empty slots. A zombie already wearing a vanilla iron helmet
	 * keeps it; the still-empty chest slot receives the tempered chestplate. This is the guard that
	 * spawner-curated gear and a skeleton's bow are never clobbered.
	 */
	@GameTest
	public void tcMob002_fillIsNonDestructive(GameTestHelper helper) {
		MobSpawnEquipmentScenarios.tcMob002_fillIsNonDestructive(helper);
	}

	/** TC-MOB-003: an empty plan is a no-op — no slot is touched, and equip() reports no change. */
	@GameTest
	public void tcMob003_emptyPlanEquipsNothing(GameTestHelper helper) {
		MobSpawnEquipmentScenarios.tcMob003_emptyPlanEquipsNothing(helper);
	}
}
