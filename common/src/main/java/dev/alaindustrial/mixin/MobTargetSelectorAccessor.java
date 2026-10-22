package dev.alaindustrial.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor to a mob's target selector (owner feedback 2026-10-22, MOD-824): hostile mobs get a goal to hunt shift
 * workers next to their goal to hunt players ({@code ShiftWorkerHunters}). The field is protected and, unlike the
 * goal selector, has no public getter in 26.3 (checked with javap).
 */
@Mixin(Mob.class)
public interface MobTargetSelectorAccessor {
	@Accessor("targetSelector")
	GoalSelector alaindustrial$targetSelector();
}
