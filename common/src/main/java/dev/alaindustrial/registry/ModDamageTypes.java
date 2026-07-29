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

	private ModDamageTypes() {
	}

	public static DamageSource electricShock(Level level) {
		return new DamageSource(level.registryAccess()
				.lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(ELECTRIC_SHOCK));
	}
}
