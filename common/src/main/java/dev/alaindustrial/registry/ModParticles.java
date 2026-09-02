package dev.alaindustrial.registry;

import java.util.List;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Every particle type the mod registers, declared once for both loaders (MOD-085, MOD-555).
 *
 * <p><b>Why eager objects here, and not a per-loader handle like {@link ModSounds}.</b> The torch block
 * constructor passes its particle straight to {@code super(SimpleParticleType, Properties)}, and a
 * block's constructor runs during its registry's static class-init — which, in the Fabric gametest
 * environment, happens when a test class touches {@code ModBlocks} <i>before</i> the mod's
 * {@code onInitialize} runs. A handle bound only in {@code onInitialize} would be read too early and
 * throw. Constructing the {@link SimpleParticleType} as a class-load constant sidesteps all ordering: the
 * object exists the moment this class loads, and each loader merely <i>registers</i> that same instance
 * into {@code PARTICLE_TYPE} later, for networking and spawning — exactly as vanilla builds the object
 * before {@code Registry.register}. The {@code protected SimpleParticleType(boolean)} constructor is
 * reached through an anonymous subclass, the same trick {@code FabricParticleTypes.simple()} uses.
 *
 * <p>So {@link #PARTICLES} carries no {@code bind}: there is no handle to publish into. What it removes
 * is the pair of hand-written registration blocks the two loaders used to keep — the one place a new
 * particle could be added to one loader and forgotten on the other, which shows up as a particle that is
 * simply missing in game with nothing in the log.
 */
public final class ModParticles {

	/**
	 * One particle type: its registry path and the shared instance both loaders register.
	 *
	 * @param id       registry path ({@code alaindustrial:<id>})
	 * @param instance the class-load constant above — the SAME object on both loaders
	 */
	public record ParticleDef(String id, SimpleParticleType instance) {
	}

	/**
	 * The green flame of the Enriched Uranium Torch. An eager class-load constant so the torch block
	 * constructor can read it with no init-order dependency; each loader registers this same instance.
	 */
	public static final SimpleParticleType ENRICHED_URANIUM_FLAME = new SimpleParticleType(false) {
	};

	/** The Sprinkler's spray (MOD-525) — see {@code NutrientSprayParticle} for why it is not vanilla's. */
	public static final SimpleParticleType NUTRIENT_SPRAY = new SimpleParticleType(false) {
	};

	/** Every particle type, in one shared registration order. Both loaders replay this list. */
	public static final List<ParticleDef> PARTICLES = List.of(
			new ParticleDef("enriched_uranium_flame", ENRICHED_URANIUM_FLAME),
			new ParticleDef("nutrient_spray", NUTRIENT_SPRAY));

	private ModParticles() {
	}
}
