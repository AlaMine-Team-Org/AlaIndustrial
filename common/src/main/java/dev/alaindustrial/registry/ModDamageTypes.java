package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** Data-driven damage types used by Ala Industrial. */
public final class ModDamageTypes {
	public static final ResourceKey<DamageType> ELECTRIC_SHOCK =
			ResourceKey.create(Registries.DAMAGE_TYPE, Industrialization.id("electric_shock"));

	/** Radiation sickness (MOD-470). Unscaled by difficulty: a dose is a dose. */
	public static final ResourceKey<DamageType> RADIATION =
			ResourceKey.create(Registries.DAMAGE_TYPE, Industrialization.id("radiation"));

	private ModDamageTypes() {
	}

	public static DamageSource radiation(Level level) {
		return new DamageSource(level.registryAccess()
				.lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(RADIATION));
	}

	public static DamageSource electricShock(Level level) {
		return new DamageSource(level.registryAccess()
				.lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(ELECTRIC_SHOCK));
	}
}
