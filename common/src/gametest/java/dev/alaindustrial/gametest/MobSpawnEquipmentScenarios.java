package dev.alaindustrial.gametest;

import dev.alaindustrial.entity.TemperedGearRoll.EquipPlan;
import dev.alaindustrial.entity.TemperedGearSpawns;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Loader-neutral world scenarios for the tempered-gear mob-spawn equip path (MOD-130): the
 * {@link TemperedGearSpawns#equip} route the {@code finalizeSpawn} mixins invoke, driven with a
 * forced {@link EquipPlan} so the world assertions are stable, not probabilistic. The random roll
 * itself is pinned deterministically in the L1 {@code TemperedGearRollTest}.
 */
public final class MobSpawnEquipmentScenarios {

	private MobSpawnEquipmentScenarios() {
	}

	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/**
	 * A full plan equips every tempered-iron slot on an empty-handed zombie — the four armour pieces
	 * plus the mainhand sword — with the exact mod items.
	 * Mirrors: MobSpawnEquipmentGameTest.tcMob001_fullPlanEquipsTemperedGear
	 */
	public static void tcMob001_fullPlanEquipsTemperedGear(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, POS);
		boolean changed = TemperedGearSpawns.equip(zombie, new EquipPlan(true, true, true, true, true));

		helper.assertTrue(changed, "equip() should report it changed slots");
		assertSlot(helper, zombie, EquipmentSlot.HEAD, ModContent.TEMPERED_IRON_HELMET.get(), "helmet");
		assertSlot(helper, zombie, EquipmentSlot.CHEST, ModContent.TEMPERED_IRON_CHESTPLATE.get(), "chestplate");
		assertSlot(helper, zombie, EquipmentSlot.LEGS, ModContent.TEMPERED_IRON_LEGGINGS.get(), "leggings");
		assertSlot(helper, zombie, EquipmentSlot.FEET, ModContent.TEMPERED_IRON_BOOTS.get(), "boots");
		assertSlot(helper, zombie, EquipmentSlot.MAINHAND, ModContent.TEMPERED_IRON_SWORD.get(), "sword");
		helper.succeed();
	}

	/**
	 * The fill only touches empty slots: a zombie already wearing a vanilla iron helmet keeps it,
	 * while the still-empty chest slot receives the tempered chestplate — the guard that
	 * spawner-curated gear and a skeleton's bow are never clobbered.
	 * Mirrors: MobSpawnEquipmentGameTest.tcMob002_fillIsNonDestructive
	 */
	public static void tcMob002_fillIsNonDestructive(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, POS);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));

		TemperedGearSpawns.equip(zombie, new EquipPlan(true, true, false, false, false));

		assertSlot(helper, zombie, EquipmentSlot.HEAD, Items.IRON_HELMET, "pre-set iron helmet (kept)");
		assertSlot(helper, zombie, EquipmentSlot.CHEST, ModContent.TEMPERED_IRON_CHESTPLATE.get(), "chestplate (filled)");
		helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), "legs stay empty when not planned");
		helper.succeed();
	}

	/**
	 * An empty plan is a no-op — no slot is touched, and equip() reports no change.
	 * Mirrors: MobSpawnEquipmentGameTest.tcMob003_emptyPlanEquipsNothing
	 */
	public static void tcMob003_emptyPlanEquipsNothing(GameTestHelper helper) {
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
