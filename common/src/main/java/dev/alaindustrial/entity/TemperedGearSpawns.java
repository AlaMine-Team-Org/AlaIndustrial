package dev.alaindustrial.entity;

import dev.alaindustrial.entity.TemperedGearRoll.EquipPlan;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft-facing wiring for natural-spawn tempered-iron equipment on hostile mobs (MOD-130). The
 * pure decision logic + balance constants live in {@link TemperedGearRoll} (L1-unit-tested); this
 * class only reads/writes a live {@link Mob}'s slots and is exercised by the spawn mixins + gametests.
 *
 * <p>Invoked from the {@code TAIL} of {@code Zombie#finalizeSpawn} and {@code AbstractSkeleton#finalizeSpawn},
 * after vanilla has populated and enchanted its own gear. Only <em>empty</em> slots are filled, so a
 * spawner-curated loadout, a vanilla-rolled armour piece, or a skeleton's bow is never overwritten and
 * this composes non-destructively with everything else.
 *
 * <p>Drops are free: worn gear falls on death via {@code Mob.dropCustomDeathLoot} at the vanilla default
 * 8.5 % chance (× Looting), item-agnostic, with randomised durability — no extra wiring. Worn textures
 * render on the mob automatically because the armour's {@code EquipmentAsset} rides inside each stack's
 * {@code EQUIPPABLE} component (MOD-056), exactly as on a player.
 */
public final class TemperedGearSpawns {

	private TemperedGearSpawns() {
	}

	/**
	 * Full entry point for the spawn mixins: gate on spawn reason, roll a plan, equip empty slots.
	 * No-op for {@link EntitySpawnReason#CONVERSION} (matching vanilla's own equipment gate), so
	 * converting a villager/drowned never conjures gear.
	 *
	 * @param allowWeapon true for zombies (may receive a tempered sword), false for skeletons
	 */
	public static void onFinalizeSpawn(Mob mob, EntitySpawnReason spawnReason, boolean allowWeapon,
			RandomSource random, float specialMultiplier) {
		if (spawnReason == EntitySpawnReason.CONVERSION) {
			return;
		}
		equip(mob, TemperedGearRoll.decide(allowWeapon, random::nextDouble, specialMultiplier));
	}

	/**
	 * Apply a plan to a live mob, filling only empty slots. Returns whether anything was equipped.
	 * Public so gametests can drive the real equip path with a forced plan (deterministic, no chance roll).
	 */
	public static boolean equip(Mob mob, EquipPlan plan) {
		boolean changed = false;
		changed |= fillIfEmpty(mob, EquipmentSlot.HEAD, plan.helmet(), ModContent.TEMPERED_IRON_HELMET.get());
		changed |= fillIfEmpty(mob, EquipmentSlot.CHEST, plan.chestplate(), ModContent.TEMPERED_IRON_CHESTPLATE.get());
		changed |= fillIfEmpty(mob, EquipmentSlot.LEGS, plan.leggings(), ModContent.TEMPERED_IRON_LEGGINGS.get());
		changed |= fillIfEmpty(mob, EquipmentSlot.FEET, plan.boots(), ModContent.TEMPERED_IRON_BOOTS.get());
		changed |= fillIfEmpty(mob, EquipmentSlot.MAINHAND, plan.weapon(), ModContent.TEMPERED_IRON_SWORD.get());
		return changed;
	}

	private static boolean fillIfEmpty(Mob mob, EquipmentSlot slot, boolean wanted, Item item) {
		if (wanted && mob.getItemBySlot(slot).isEmpty()) {
			mob.setItemSlot(slot, new ItemStack(item));
			return true;
		}
		return false;
	}
}
