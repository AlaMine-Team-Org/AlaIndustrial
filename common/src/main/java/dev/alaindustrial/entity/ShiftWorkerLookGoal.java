package dev.alaindustrial.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
 * <p>Looking and a greeting are all a worker does about a player: its walk ({@link ShiftWorkerWalkGoal}) and its
 * shift planner never read one. Being pushed by a player's body is physics, not a reaction, and stays vanilla.
 *
 * <p><b>The greeting</b> (MOD-882, the owner's pick of variant C): when the worker turns to a player it has not
 * greeted for {@link #GREET_AGAIN_TICKS}, it waves and nods ({@link ShiftWorker#greet}); a running worker does not.
 * While it watches a player the client switches its stand's look about off ({@link ShiftWorker#setWatching}).
 * Whom it greeted when is kept in the goal, not saved: after a reload a worker greets once more.
 */
public final class ShiftWorkerLookGoal extends LookAtPlayerGoal {

	/** How far a worker notices a player, in blocks: a villager's. */
	public static final float LOOK_DISTANCE = 6.0F;
	/** How long a worker waits before it greets the same player again, in ticks (two minutes). */
	public static final int GREET_AGAIN_TICKS = 2400;

	private final ShiftWorker worker;
	/** Server only: game time each player was last greeted at. */
	private final Map<UUID, Long> greeted = new HashMap<>();

	private final TargetingConditions noticed;

	public ShiftWorkerLookGoal(ShiftWorker worker) {
		super(worker, Player.class, LOOK_DISTANCE);
		this.worker = worker;
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

	@Override
	public void start() {
		super.start();
		worker.setWatching(true);
		if (lookAt instanceof Player player && !worker.isFleeing()) {
			long now = worker.level().getGameTime();
			Long last = greeted.get(player.getUUID());
			if (last == null || now - last >= GREET_AGAIN_TICKS) {
				greeted.values().removeIf(at -> now - at >= GREET_AGAIN_TICKS);
				greeted.put(player.getUUID(), now);
				worker.greet();
			}
		}
	}

	@Override
	public void stop() {
		super.stop();
		worker.setWatching(false);
	}
}
