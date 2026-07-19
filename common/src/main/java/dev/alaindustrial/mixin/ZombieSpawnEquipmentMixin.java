package dev.alaindustrial.mixin;

import dev.alaindustrial.entity.TemperedGearSpawns;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives naturally-spawned zombies (and Husk/Drowned/ZombieVillager, which inherit this method via
 * {@code super.finalizeSpawn}) a small, difficulty-scaled chance of tempered-iron armour and a
 * tempered sword — MOD-130. Runs at {@code TAIL}, after vanilla armour + weapon + enchant population,
 * and fills only empty slots (see {@link TemperedGearSpawns}).
 *
 * <p>Excluded: {@link ZombifiedPiglin} (wears golden gear by design — tempered iron would be wrong).
 * Drowned keep their trident/nautilus mainhand, so they get armour only (no tempered sword).
 */
@Mixin(Zombie.class)
public abstract class ZombieSpawnEquipmentMixin {

	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void alaindustrial$temperedGear(ServerLevelAccessor level, DifficultyInstance difficulty,
			EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		Object self = this;
		if (self instanceof ZombifiedPiglin) {
			return;
		}
		boolean allowWeapon = !(self instanceof Drowned);
		RandomSource random = level.getRandom();
		TemperedGearSpawns.onFinalizeSpawn((Zombie) self, spawnReason, allowWeapon, random,
				difficulty.getSpecialMultiplier());
	}
}
