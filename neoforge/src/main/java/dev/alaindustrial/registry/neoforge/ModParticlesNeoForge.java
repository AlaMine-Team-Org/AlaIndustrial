package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModParticles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge particle registration: a replay of the shared {@link ModParticles#PARTICLES} list (MOD-085,
 * MOD-555), mirroring {@link ModSoundsNeoForge}.
 *
 * <p>The particle objects themselves are eager class-load constants in {@link ModParticles} (so a block
 * constructor can read one with no init-order dependency); this class only publishes those same instances
 * to the {@code PARTICLE_TYPE} registry for networking and spawning. There is no facade handle to bind,
 * hence no {@code bind} on the shared entry — a block never reads through this class.
 */
public final class ModParticlesNeoForge {
	public static final DeferredRegister<ParticleType<?>> PARTICLES =
			DeferredRegister.create(Registries.PARTICLE_TYPE, Industrialization.MOD_ID);

	/** Every shared entry, queued the moment this class loads. */
	private static final List<DeferredHolder<ParticleType<?>, SimpleParticleType>> REGISTERED = registerAll();

	private static List<DeferredHolder<ParticleType<?>, SimpleParticleType>> registerAll() {
		List<DeferredHolder<ParticleType<?>, SimpleParticleType>> registered = new ArrayList<>();
		for (ModParticles.ParticleDef def : ModParticles.PARTICLES) {
			registered.add(PARTICLES.register(def.id(), def::instance));
		}
		return List.copyOf(registered);
	}

	/** Class-load trigger for the {@code @Mod} ctor; also checks the replay covered the whole list. */
	public static void init() {
		if (REGISTERED.size() != ModParticles.PARTICLES.size()) {
			throw new IllegalStateException("ModParticlesNeoForge registered " + REGISTERED.size() + " of "
					+ ModParticles.PARTICLES.size() + " shared particle types");
		}
	}

	private ModParticlesNeoForge() {
	}
}
