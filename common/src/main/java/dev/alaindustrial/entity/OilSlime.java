package dev.alaindustrial.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;

/**
 * The oil slime (MOD-767, docs/mobs/oil_slime.md): a vanilla slime soaked in crude oil. It moves, splits,
 * fights and sounds exactly like a slime — its children are oil slimes because the vanilla split creates
 * them from {@code getType()} — and differs in three ways only:
 *
 * <ul>
 *   <li>it breathes in oil, which drowns every other living thing (the oil-drowning mixin asks
 *       {@link #canBreatheUnderwater()});</li>
 *   <li>any fire damage sets it alight, so a slime killed on magma or in lava dies burning, and the loot
 *       table drops nothing for a burning one — the farm needs a trap that is not fire;</li>
 *   <li>it leaves dark ink droplets instead of green slime particles when it lands.</li>
 * </ul>
 */
public class OilSlime extends Slime {

	/** Seconds an oil slime keeps burning after it was touched by fire, lava or a magma block. */
	static final float BURN_SECONDS = 8.0F;

	public OilSlime(EntityType<? extends OilSlime> type, Level level) {
		super(type, level);
	}

	// MOD-498 kind B: only NeoForge's patch deprecates this (in favour of its FluidType overload, which Fabric
	// does not have); vanilla's breathing check and the mod's oil-drowning mixin both still ask it.
	@SuppressWarnings("deprecation")
	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE) && !isOnFire()) {
			igniteForSeconds(BURN_SECONDS);
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected ParticleOptions getParticleType() {
		return ParticleTypes.SQUID_INK;
	}
}
