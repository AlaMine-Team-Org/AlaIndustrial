package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModParticles;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge particle registration (MOD-085), mirroring {@link ModSoundsNeoForge}. Registers the shared
 * loader-neutral {@link ModParticles#ENRICHED_URANIUM_FLAME} object (the green flame of the Enriched
 * Uranium Torch) into the {@code PARTICLE_TYPE} registry via a {@link DeferredRegister} on the mod bus.
 *
 * <p>The particle object itself is an eager class-load constant in {@link ModParticles} (so the torch
 * block constructor can read it with no init-order dependency); this class only publishes that same
 * instance to the registry for networking/spawning. There is no facade handle to bind, hence no
 * {@code init()} — the block never reads through this class.
 */
public final class ModParticlesNeoForge {
	public static final DeferredRegister<ParticleType<?>> PARTICLES =
			DeferredRegister.create(Registries.PARTICLE_TYPE, Industrialization.MOD_ID);

	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ENRICHED_URANIUM_FLAME =
			PARTICLES.register("enriched_uranium_flame", () -> ModParticles.ENRICHED_URANIUM_FLAME);

	private ModParticlesNeoForge() {
	}
}
