package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;

/**
 * Loader-neutral particle definition (MOD-085): the Enriched Uranium Torch's green flame.
 *
 * <p><b>Why an eager object here, not a per-loader facade handle (unlike {@link ModSounds}).</b> The
 * torch block constructor passes this particle straight to {@code super(SimpleParticleType, Properties)},
 * and a block's constructor runs during its registry's static class-init — which, in the Fabric gametest
 * environment, happens when a test class touches {@code ModBlocks} <i>before</i> the mod's
 * {@code onInitialize} runs. A handle bound only in {@code onInitialize} is therefore read too early and
 * throws. Constructing the {@link SimpleParticleType} object as a class-load constant sidesteps all
 * ordering: the object exists the moment this class loads, and each loader merely <i>registers</i> that
 * same instance into the {@code PARTICLE_TYPE} registry later (for networking/spawning) — exactly as
 * vanilla builds the object before {@code Registry.register}. The {@code protected}
 * {@code SimpleParticleType(boolean)} constructor is reached via an anonymous subclass, the same trick
 * {@code FabricParticleTypes.simple()} uses.
 */
public final class ModParticles {

	/** The registry id, shared by both loaders' registration. */
	public static final Identifier ENRICHED_URANIUM_FLAME_ID = Industrialization.id("enriched_uranium_flame");

	/**
	 * The green flame particle of the Enriched Uranium Torch. An eager class-load constant so the torch
	 * block constructor can read it with no init-order dependency; each loader registers this same instance.
	 */
	public static final SimpleParticleType ENRICHED_URANIUM_FLAME = new SimpleParticleType(false) {
	};

	private ModParticles() {
	}
}
