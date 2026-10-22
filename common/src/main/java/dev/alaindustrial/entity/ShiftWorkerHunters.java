package dev.alaindustrial.entity;

import dev.alaindustrial.mixin.MobTargetSelectorAccessor;
import dev.alaindustrial.mixin.NearestAttackableTargetGoalAccessor;
import dev.alaindustrial.mixin.TargetGoalAccessor;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;

/**
 * Hostile mobs hunt shift workers as they hunt players (owner decision 2026-10-22, MOD-824): a zombie, a skeleton, a
 * pillager — every hostile mob whose goals hunt the nearest player — gets one more target goal that hunts the nearest
 * shift worker by the very same rule, at the very same priority.
 *
 * <p><b>Copied, not listed.</b> There is no list of mob types: when a mob joins a level ({@code ServerHookRoster}
 * {@code ENTITY_JOIN}), its target goals are read, and the first goal that hunts {@link Player} is the template. Its
 * targeting conditions — range, line of sight and the mob's own selector (a drowned hunts on land only at night) —
 * and its "must see" and "must reach" flags carry over, so a mob of another mod that hunts players hunts workers too.
 * A spider's player goal also checks the light in its own {@code canUse}, which the conditions do not carry; the
 * one exception is written out in {@link HuntGoal#canUse}.
 *
 * <p><b>Left alone:</b> a neutral mob (an enderman, a zombified piglin) — its player goal fires only when angered at
 * that player, and a worker cannot anger it; a mob that plans with a brain instead of goals (piglins, hoglins, the
 * warden, the breeze); and a mob with no player goal at all. The goal is not saved — every join adds it again — and
 * a mob that already has it does not get a second.
 */
public final class ShiftWorkerHunters {

	private ShiftWorkerHunters() {
	}

	/** {@code ServerHookRoster} {@code ENTITY_JOIN}: gives a hostile {@code entity} its goal to hunt shift workers. */
	public static void onEntityJoin(Entity entity, ServerLevel level) {
		if (entity instanceof Mob mob && mob instanceof Enemy && !(mob instanceof NeutralMob)) {
			addHuntGoal(mob);
		}
	}

	/** Gives {@code mob} its goal to hunt shift workers; false when it hunts no players or has the goal already. */
	public static boolean addHuntGoal(Mob mob) {
		GoalSelector targets = ((MobTargetSelectorAccessor) mob).alaindustrial$targetSelector();
		if (huntsWorkers(mob)) {
			return false;
		}
		Optional<WrappedGoal> template = targets.getAvailableGoals().stream()
				.filter(wrapped -> huntsPlayers(wrapped.getGoal()))
				.min((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
		if (template.isEmpty()) {
			return false;
		}
		NearestAttackableTargetGoal<?> playerGoal = (NearestAttackableTargetGoal<?>) template.get().getGoal();
		targets.addGoal(template.get().getPriority(), new HuntGoal(mob, playerGoal));
		return true;
	}

	/** Whether {@code mob} has its goal to hunt shift workers. */
	public static boolean huntsWorkers(Mob mob) {
		return ((MobTargetSelectorAccessor) mob).alaindustrial$targetSelector().getAvailableGoals().stream()
				.anyMatch(wrapped -> wrapped.getGoal() instanceof HuntGoal);
	}

	private static boolean huntsPlayers(Object goal) {
		if (!(goal instanceof NearestAttackableTargetGoal<?>) || goal instanceof HuntGoal) {
			return false;
		}
		Class<?> type = ((NearestAttackableTargetGoalAccessor) goal).alaindustrial$targetType();
		return type == Player.class || type == ServerPlayer.class;
	}

	/**
	 * Hunts the nearest shift worker by the rule {@code playerGoal} hunts the nearest player: a worker passes when
	 * the player goal's conditions would pass it.
	 */
	static final class HuntGoal extends NearestAttackableTargetGoal<ShiftWorker> {

		HuntGoal(Mob mob, NearestAttackableTargetGoal<?> playerGoal) {
			super(mob, ShiftWorker.class, 10, ((TargetGoalAccessor) playerGoal).alaindustrial$mustSee(),
					((TargetGoalAccessor) playerGoal).alaindustrial$mustReach(),
					rule(mob, ((NearestAttackableTargetGoalAccessor) playerGoal).alaindustrial$targetConditions()));
		}

		private static TargetingConditions.Selector rule(Mob mob, TargetingConditions playerConditions) {
			return (target, level) -> {
				try {
					return playerConditions.test(level, mob, target);
				} catch (ClassCastException notAPlayer) {
					// A selector of another mod written for players only: it hunts players, not workers.
					return false;
				}
			};
		}

		@Override
		@SuppressWarnings("deprecation") // the very call Spider.SpiderTargetGoal makes; vanilla has no other form
		public boolean canUse() {
			// A spider's player goal stops in the light in its own canUse (Spider.SpiderTargetGoal), not in its
			// targeting conditions; the same check, so a spider leaves a worker alone by day as it does a player.
			if (mob instanceof Spider && mob.getLightLevelDependentMagicValue() >= 0.5F) {
				return false;
			}
			return super.canUse();
		}
	}
}
