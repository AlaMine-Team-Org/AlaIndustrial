package dev.alaindustrial.gametest;

import dev.alaindustrial.entity.TemperedGearRoll.EquipPlan;
import dev.alaindustrial.entity.TemperedGearSpawns;
import dev.alaindustrial.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/**
	 * TC-MOB-001: a full plan equips every tempered-iron slot on an empty-handed zombie — the four
	 * armour pieces plus the mainhand sword — with the exact mod items.
	 */
	@GameTest
	public void tcMob001_fullPlanEquipsTemperedGear(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, POS);
		boolean changed = TemperedGearSpawns.equip(zombie, new EquipPlan(true, true, true, true, true));

		helper.assertTrue(changed, "equip() should report it changed slots");
		assertSlot(helper, zombie, EquipmentSlot.HEAD, ModItems.TEMPERED_IRON_HELMET, "helmet");
		assertSlot(helper, zombie, EquipmentSlot.CHEST, ModItems.TEMPERED_IRON_CHESTPLATE, "chestplate");
		assertSlot(helper, zombie, EquipmentSlot.LEGS, ModItems.TEMPERED_IRON_LEGGINGS, "leggings");
		assertSlot(helper, zombie, EquipmentSlot.FEET, ModItems.TEMPERED_IRON_BOOTS, "boots");
		assertSlot(helper, zombie, EquipmentSlot.MAINHAND, ModItems.TEMPERED_IRON_SWORD, "sword");
		helper.succeed();
	}

	/**
	 * TC-MOB-002: the fill only touches empty slots. A zombie already wearing a vanilla iron helmet
	 * keeps it; the still-empty chest slot receives the tempered chestplate. This is the guard that
	 * spawner-curated gear and a skeleton's bow are never clobbered.
	 */
	@GameTest
	public void tcMob002_fillIsNonDestructive(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, POS);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));

		TemperedGearSpawns.equip(zombie, new EquipPlan(true, true, false, false, false));

		assertSlot(helper, zombie, EquipmentSlot.HEAD, Items.IRON_HELMET, "pre-set iron helmet (kept)");
		assertSlot(helper, zombie, EquipmentSlot.CHEST, ModItems.TEMPERED_IRON_CHESTPLATE, "chestplate (filled)");
		helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), "legs stay empty when not planned");
		helper.succeed();
	}

	/** TC-MOB-003: an empty plan is a no-op — no slot is touched, and equip() reports no change. */
	@GameTest
	public void tcMob003_emptyPlanEquipsNothing(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, POS);
		boolean changed = TemperedGearSpawns.equip(zombie, EquipPlan.NONE);

		helper.assertTrue(!changed, "empty plan should report no change");
		helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "head stays empty");
		helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "mainhand stays empty");
		helper.succeed();
	}

	private static void assertSlot(GameTestHelper helper, Zombie zombie, EquipmentSlot slot, Item expected, String label) {
		Item actual = zombie.getItemBySlot(slot).getItem();
		helper.assertTrue(actual == expected, "slot " + label + " should hold " + label + " but held " + actual);
	}
}
