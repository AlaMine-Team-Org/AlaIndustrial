package dev.alaindustrial.mixin;

import dev.alaindustrial.entity.TemperedGearSpawns;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives naturally-spawned skeletons (Skeleton/Stray/Bogged/Parched, via {@code super.finalizeSpawn})
 * a small, difficulty-scaled chance of tempered-iron armour — MOD-130. Armour only: the skeleton's
 * mainhand is set unconditionally to a bow by vanilla and its attack behaviour is bow-keyed, so no tempered weapon.
 * Runs at {@code TAIL}, after vanilla population, filling only empty slots.
 *
 * <p>Excluded: {@link WitherSkeleton} (carries a stone sword by design). Its {@code finalizeSpawn}
 * calls {@code super.finalizeSpawn}, so this TAIL would otherwise fire for it too.
 */
@Mixin(AbstractSkeleton.class)
public abstract class SkeletonSpawnEquipmentMixin {

	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void alaindustrial$temperedArmor(ServerLevelAccessor level, DifficultyInstance difficulty,
			EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		Object self = this;
		if (self instanceof WitherSkeleton) {
			return;
		}
		RandomSource random = level.getRandom();
		TemperedGearSpawns.onFinalizeSpawn((Mob) self, spawnReason, false, random,
				difficulty.getSpecialMultiplier());
	}
}
