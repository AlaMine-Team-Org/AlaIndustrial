package dev.alaindustrial.entity;

import java.util.function.DoubleSupplier;

/**
 * Pure decision logic for natural-spawn tempered-iron equipment (MOD-130) — no Minecraft on the
 * classpath, so it is L1-unit-testable. The Minecraft-facing wiring (reading the mob's slots,
 * placing {@code ItemStack}s, the {@code finalizeSpawn} mixins) lives in {@link TemperedGearSpawns}.
 *
 * <p>Context: in MC 26.2 wild-mob gear is hardcoded in {@code Mob.getEquipmentForSlot} — not tag- or
 * data-driven — so tempered iron cannot be injected via a datapack; a mixin rolls this plan instead.
 * Balance numbers are mirrored in {@code docs/PERFORMANCE.md} (the numeric source of truth).
 */
public final class TemperedGearRoll {

	private TemperedGearRoll() {
	}

	/** Zombie chance (before ×difficulty) for a tempered armour set to be rolled at all. */
	public static final float ZOMBIE_ARMOR_CHANCE = 0.04F;
	/** Zombie chance (before ×difficulty) for a tempered sword in the mainhand. */
	public static final float ZOMBIE_WEAPON_CHANCE = 0.02F;
	/** Skeleton chance (before ×difficulty) for a tempered armour set — below the zombie's. */
	public static final float SKELETON_ARMOR_CHANCE = 0.025F;
	/**
	 * Chance to keep adding the next armour piece once a set has been rolled (head→chest→legs→feet),
	 * stopping on the first miss. 0.55 yields mostly one- or two-piece partial sets, helmet-heavy.
	 */
	public static final float ARMOR_CONTINUE_CHANCE = 0.55F;

	/**
	 * Which tempered-iron pieces to equip; a pure decision, independent of any live mob.
	 *
	 * @param helmet     equip the tempered helmet
	 * @param chestplate equip the tempered chestplate
	 * @param leggings   equip the tempered leggings
	 * @param boots      equip the tempered boots
	 * @param weapon     equip the tempered sword in the mainhand (zombies only)
	 */
	public record EquipPlan(boolean helmet, boolean chestplate, boolean leggings, boolean boots, boolean weapon) {
		public static final EquipPlan NONE = new EquipPlan(false, false, false, false, false);

		public boolean any() {
			return helmet || chestplate || leggings || boots || weapon;
		}
	}

	/**
	 * Decide what tempered-iron gear a freshly-spawned mob should receive. Deterministic for a given
	 * {@code rng} sequence: each {@code rng.getAsDouble()} must yield a value in {@code [0, 1)}.
	 *
	 * @param allowWeapon        true for zombies (may get a tempered sword), false for skeletons (armour only)
	 * @param rng                a source of uniform {@code [0,1)} doubles — {@code randomSource::nextDouble} in game
	 * @param specialMultiplier  {@code DifficultyInstance.getSpecialMultiplier()} — higher difficulty, higher odds
	 */
	public static EquipPlan decide(boolean allowWeapon, DoubleSupplier rng, float specialMultiplier) {
		float armorChance = (allowWeapon ? ZOMBIE_ARMOR_CHANCE : SKELETON_ARMOR_CHANCE) * specialMultiplier;
		boolean helmet = false, chestplate = false, leggings = false, boots = false;
		if (rng.getAsDouble() < armorChance) {
			helmet = true; // a rolled set always includes at least the helmet
			if (rng.getAsDouble() < ARMOR_CONTINUE_CHANCE) {
				chestplate = true;
				if (rng.getAsDouble() < ARMOR_CONTINUE_CHANCE) {
					leggings = true;
					if (rng.getAsDouble() < ARMOR_CONTINUE_CHANCE) {
						boots = true;
					}
				}
			}
		}
		boolean weapon = allowWeapon && rng.getAsDouble() < ZOMBIE_WEAPON_CHANCE * specialMultiplier;
		return new EquipPlan(helmet, chestplate, leggings, boots, weapon);
	}
}
