package dev.alaindustrial.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The single "is this mob an active threat" predicate shared by the Mob Repeller field and the Soul
 * Vessel kill accounting (MOD-278). One predicate on purpose: two near-copies of "hostile, but not
 * an unprovoked neutral" in different subsystems is exactly how the field and the vessel would
 * drift apart on the edge cases.
 *
 * <p>{@link Enemy} is a pure marker interface, and {@code EnderMan extends Monster implements
 * NeutralMob} — so an enderman (and a zombified piglin) is an {@code Enemy} whether or not it is
 * provoked. The extra {@link NeutralMob} check is what tells a calm enderman apart from an angry
 * one: {@code isAngry()} reads the persistent-anger timer without writing anything. Leaving calm
 * neutrals alone is load-bearing for zombified piglins — expelling one flags the whole herd
 * (vanilla alerts neighbours via {@code setTarget}), turning the repeller into an aggro machine.
 */
public final class HostileThreat {
	private HostileThreat() {
	}

	/**
	 * Whether {@code entity} is an actively hostile mob: an {@link Enemy}, minus any
	 * {@link NeutralMob} that is currently calm. Dead/removed entities are never threats.
	 */
	public static boolean isActiveThreat(LivingEntity entity) {
		if (!(entity instanceof Mob) || !(entity instanceof Enemy) || !entity.isAlive()) {
			return false;
		}
		if (entity instanceof NeutralMob neutral) {
			return neutral.isAngry() || ((Mob) entity).getTarget() != null;
		}
		return true;
	}

	/**
	 * Kill-accounting variant — deliberately NOT {@link #isActiveThreat}, for two reasons that both
	 * look like nitpicks and are not:
	 *
	 * <ol>
	 *   <li><b>No {@code isAlive()} check.</b> This runs from the death hook, where
	 *       {@code LivingEntity.isAlive()} is {@code !isRemoved() && getHealth() > 0} and the health is
	 *       already zero — so the field's liveness test rejects <em>every</em> kill. That was the first
	 *       version, and it silently banked nothing at all while every gate stayed green (the gametest
	 *       drove a LIVE mob, so it could not fail; see {@code MobRepellerScenarios}).</li>
	 *   <li><b>No anger check.</b> A neutral mob the player just killed was in a fight by definition,
	 *       and a one-shot kill can land before the anger timer is ever written — asking "was it angry"
	 *       of a corpse loses souls the player earned.</li>
	 * </ol>
	 *
	 * <p>What stays is the only question that matters here: was this a hostile mob at all.
	 */
	public static boolean isCountableKill(LivingEntity entity, ServerLevel level) {
		return entity instanceof Mob && entity instanceof Enemy;
	}
}
