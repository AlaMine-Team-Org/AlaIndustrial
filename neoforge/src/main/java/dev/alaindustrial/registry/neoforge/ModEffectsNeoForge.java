package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge status-effect registration (MOD-470), the counterpart to Fabric's eager register.
 *
 * <p>Same shape and same reason as {@link ModSoundsNeoForge}: the vanilla {@code MOB_EFFECT} registry
 * is frozen before mod construction here, so registration goes through a {@link DeferredRegister} on
 * the mod bus and the neutral {@link ModEffects} handle is bound to the deferred holder afterwards. A
 * {@link DeferredHolder} already IS a {@code Holder<MobEffect>}, which is exactly what
 * {@code addEffect}/{@code getEffect} want.
 */
public final class ModEffectsNeoForge {
	public static final DeferredRegister<MobEffect> EFFECTS =
			DeferredRegister.create(Registries.MOB_EFFECT, Industrialization.MOD_ID);

	public static final DeferredHolder<MobEffect, MobEffect> RADIATION =
			EFFECTS.register("radiation", ModEffects::createRadiation);

	private ModEffectsNeoForge() {
	}

	/** Bind the loader-neutral handle. Called from the {@code @Mod} ctor after the bus registration. */
	public static void init() {
		ModEffects.RADIATION = () -> RADIATION;
	}
}
