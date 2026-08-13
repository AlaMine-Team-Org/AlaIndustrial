package dev.alaindustrial.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Mob Repeller's expulsion sweep (MOD-278): finds actively hostile mobs inside the field cube
 * and makes them leave — the way a creeper flees a cat. One static entry point so the three tier
 * block entities share the exact same behaviour and the gametests can drive a single sweep
 * deterministically.
 *
 * <p>The cascade, each step verified against the 26.2 sources (see the task's research log):
 * <ol>
 * <li><b>Navigation flight</b> — for goal-driven {@link PathfinderMob}s this is literally the
 *     creeper-vs-cat code: {@link DefaultRandomPos#getPosAway} then {@code navigation.moveTo}.
 *     {@code setTarget(null)} first, every sweep — a target re-acquires in ~10 ticks, and while a
 *     target exists {@code MeleeAttackGoal} re-paths toward it, fighting the flight.</li>
 * <li><b>Brain flight</b> — Warden/Breeze/Creaking/Zoglin/piglins/hoglins ignore outside calls to
 *     {@code setTarget}/navigation (their {@code MoveToTargetSink} owns the path), so they get what
 *     vanilla's own {@code SetWalkTargetAwayFrom} does: erase {@code ATTACK_TARGET}, write
 *     {@code WALK_TARGET} away from the block. (Piglins and hoglins are usually already covered for
 *     free by the {@code piglin_repellents}/{@code hoglin_repellents} block tags this block sits in;
 *     the brain write matters in the MV/HV zones, which are larger than the vanilla sensors' 8×4.)</li>
 * <li><b>Shove</b> — fliers and cube mobs whose navigation cannot express "run there" (ghast,
 *     phantom, vex, slimes) get an outward {@link Mob#push(Vec3)} — push, not
 *     {@code setDeltaMovement}: only push sets the sync flag the client needs to see the jolt.</li>
 * <li><b>Teleport</b> — an angered enderman is asked to {@code randomTeleport} outside; running is
 *     meaningless for it (vanilla itself does {@code setTarget(null); teleport()} in daylight).</li>
 * <li><b>Ignore</b> — the shulker: every movement path on it is a physical no-op by design
 *     ({@code setDeltaMovement} empty, {@code push} empty, {@code setPos} snaps to the grid).</li>
 * </ol>
 */
public final class MobRepellerField {
	/** How far beyond the field edge a flee target is asked for (blocks) — same 16/7 spread the
	 * vanilla {@code AvoidEntityGoal} uses, so the flight reads exactly like a creeper leaving. */
	private static final int FLEE_HORIZONTAL = 16;
	private static final int FLEE_VERTICAL = 7;
	/** Flee speed modifier — the creeper's own sprint value from {@code Creeper.registerGoals}. */
	private static final double FLEE_SPEED = 1.2;
	/** Outward shove impulse for step-3 mobs, per sweep. Mirror of the magnet's pull scale. */
	private static final double SHOVE_SPEED = 0.55;

	private MobRepellerField() {
	}

	/**
	 * One sweep: expel every active threat inside the cube of {@code radius} around {@code center}.
	 * Returns how many mobs were acted on (for the gametests; the field itself does not bill per mob).
	 */
	public static int sweep(ServerLevel level, BlockPos center, int radius) {
		Vec3 centerVec = Vec3.atCenterOf(center);
		AABB zone = new AABB(center).inflate(radius);
		List<Mob> threats = level.getEntitiesOfClass(Mob.class, zone,
				mob -> HostileThreat.isActiveThreat(mob));
		int repelled = 0;
		for (Mob mob : threats) {
			if (expel(mob, centerVec, radius)) {
				repelled++;
			}
		}
		return repelled;
	}

	/** Route one mob through the cascade. Returns whether anything was done to it. */
	private static boolean expel(Mob mob, Vec3 center, int radius) {
		if (mob instanceof Shulker) {
			return false; // physically unmovable by design — the documented exception
		}
		if (mob instanceof EnderMan enderman) {
			return teleportOut(enderman, center, radius);
		}
		if (isBrainMover(mob)) {
			return brainFlee(mob, center);
		}
		if (mob instanceof PathfinderMob pathfinder && navigationFlee(pathfinder, center)) {
			return true;
		}
		return shove(mob, center);
	}

	/** The seven hostile brain-driven movers of 26.2 — verified list, see the task research log. */
	private static boolean isBrainMover(Mob mob) {
		return mob instanceof AbstractPiglin || mob instanceof Hoglin || mob instanceof Zoglin
				|| mob instanceof Warden || mob instanceof Breeze || mob instanceof Creaking;
	}

	/** Step 1: the creeper-vs-cat flight. */
	private static boolean navigationFlee(PathfinderMob mob, Vec3 center) {
		mob.setTarget(null); // re-acquired in ~10 ticks — must be repeated every sweep
		Vec3 flee = DefaultRandomPos.getPosAway(mob, FLEE_HORIZONTAL, FLEE_VERTICAL, center);
		if (flee == null) {
			return false;
		}
		// Reject a "flee" point that is actually closer to the repeller — same sanity check
		// AvoidEntityGoal.canUse applies against its own random pick.
		if (center.distanceToSqr(flee) < center.distanceToSqr(mob.position())) {
			return false;
		}
		return mob.getNavigation().moveTo(flee.x, flee.y, flee.z, FLEE_SPEED);
	}

	/** Step 2: what vanilla's SetWalkTargetAwayFrom does, for mobs whose brain owns the path. */
	private static boolean brainFlee(Mob mob, Vec3 center) {
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		Vec3 flee = mob instanceof PathfinderMob pathfinder
				? LandRandomPos.getPosAway(pathfinder, FLEE_HORIZONTAL, FLEE_VERTICAL, center)
				: null;
		if (flee == null) {
			return shove(mob, center);
		}
		mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(flee, (float) FLEE_SPEED, 0));
		return true;
	}

	/** Step 3: outward shove for mobs navigation cannot move (fliers, slimes, stuck walkers). */
	private static boolean shove(Mob mob, Vec3 center) {
		Vec3 away = mob.position().subtract(center);
		// A mob standing exactly on the block's centre column has no outward direction — pick +X.
		Vec3 dir = away.horizontalDistanceSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
		mob.push(new Vec3(dir.x * SHOVE_SPEED, Math.max(0, dir.y) * 0.2, dir.z * SHOVE_SPEED));
		return true;
	}

	/** Step 4: the enderman is teleported to the field edge; running is meaningless for it. */
	private static boolean teleportOut(EnderMan enderman, Vec3 center, int radius) {
		Vec3 away = enderman.position().subtract(center);
		Vec3 dir = away.horizontalDistanceSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
		double edge = radius + 4.0;
		return enderman.randomTeleport(center.x + dir.x * edge, enderman.getY(), center.z + dir.z * edge, true);
	}
}
