package dev.alaindustrial.mixin;

import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor to whether a target goal needs to see and to reach its target (MOD-824): the goal a hostile mob hunts
 * shift workers with copies both from its goal that hunts players. {@code mustSee} is protected and
 * {@code mustReach} private in 26.3 (checked with javap).
 */
@Mixin(TargetGoal.class)
public interface TargetGoalAccessor {
	@Accessor("mustSee")
	boolean alaindustrial$mustSee();

	@Accessor("mustReach")
	boolean alaindustrial$mustReach();
}
