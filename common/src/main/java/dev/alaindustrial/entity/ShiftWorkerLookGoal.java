package dev.alaindustrial.entity;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A shift worker looks at a player near it — but not at one in creative or spectator mode (owner decision, MOD-847):
 * as a hostile mob leaves a creative player alone, a worker goes on with its shift while a builder flies about the
 * town. The vanilla goal picks the nearest player by its own targeting conditions, which take a creative player;
 * this one picks only among the players a worker {@link #notices}, and stops looking at one who switches mode.
 *
 * <p>Looking is the only thing a worker does about a player: its walk ({@link ShiftWorkerWalkGoal}) and its shift
 * planner never read one. Being pushed by a player's body is physics, not a reaction, and stays vanilla.
 */
public final class ShiftWorkerLookGoal extends LookAtPlayerGoal {

	/** How far a worker notices a player, in blocks: a villager's. */
	public static final float LOOK_DISTANCE = 6.0F;

	private final TargetingConditions noticed;

	public ShiftWorkerLookGoal(ShiftWorker worker) {
		super(worker, Player.class, LOOK_DISTANCE);
		Predicate<Entity> notRiding = EntitySelector.notRiding(worker);
		this.noticed = TargetingConditions.forNonCombat().range(LOOK_DISTANCE)
				.selector((target, level) -> notices(target) && notRiding.test(target));
	}

	/** Whether a worker takes notice of {@code entity} at all: not of a player in creative or spectator mode. */
	public static boolean notices(@Nullable Entity entity) {
		return entity != null && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity);
	}

	/** Whether this worker would look at {@code target} now: the player-picking rule, without the random chance. */
	public boolean wouldLookAt(ServerLevel level, LivingEntity target) {
		return noticed.test(level, mob, target);
	}

	@Override
	public boolean canUse() {
		if (mob.getRandom().nextFloat() >= probability) {
			return false;
		}
		lookAt = getServerLevel(mob).getNearestPlayer(noticed, mob, mob.getX(), mob.getEyeY(), mob.getZ());
		return lookAt != null;
	}

	@Override
	public boolean canContinueToUse() {
		return notices(lookAt) && super.canContinueToUse();
	}
}
