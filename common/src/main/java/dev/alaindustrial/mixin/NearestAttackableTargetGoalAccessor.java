package dev.alaindustrial.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor to what a "hunt the nearest X" goal hunts and by which rule (MOD-824): {@code ShiftWorkerHunters} finds a
 * mob's goal that hunts players and gives the mob the same rule for shift workers. Both fields are protected in 26.3
 * (checked with javap).
 */
@Mixin(NearestAttackableTargetGoal.class)
public interface NearestAttackableTargetGoalAccessor {
	@Accessor("targetType")
	Class<? extends LivingEntity> alaindustrial$targetType();

	@Accessor("targetConditions")
	TargetingConditions alaindustrial$targetConditions();
}
