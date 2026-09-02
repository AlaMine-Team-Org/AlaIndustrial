package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge status-effect registration: a replay of the shared {@link ModEffects#EFFECTS} list (MOD-470,
 * MOD-555), the counterpart to Fabric's eager register.
 *
 * <p>Same shape and same reason as {@link ModSoundsNeoForge}: the vanilla {@code MOB_EFFECT} registry is
 * frozen before mod construction here, so registration goes through a {@link DeferredRegister} on the mod
 * bus. A {@link DeferredHolder} already IS a {@code Holder<MobEffect>}, which is exactly what
 * {@code addEffect}/{@code getEffect} want — hence the {@code () -> holder} binding rather than the plain
 * holder the other registries bind.
 */
public final class ModEffectsNeoForge {
	public static final DeferredRegister<MobEffect> EFFECTS =
			DeferredRegister.create(Registries.MOB_EFFECT, Industrialization.MOD_ID);

	/** Every shared entry, queued and bound the moment this class loads. See {@code ModSoundsNeoForge}. */
	private static final List<DeferredHolder<MobEffect, MobEffect>> REGISTERED = registerAll();

	private static List<DeferredHolder<MobEffect, MobEffect>> registerAll() {
		List<DeferredHolder<MobEffect, MobEffect>> registered = new ArrayList<>();
		for (ModEffects.EffectDef def : ModEffects.EFFECTS) {
			DeferredHolder<MobEffect, MobEffect> holder = EFFECTS.register(def.id(), def.factory());
			def.bind().accept(() -> holder);
			registered.add(holder);
		}
		return List.copyOf(registered);
	}

	/** Class-load trigger for the {@code @Mod} ctor; also checks the replay covered the whole list. */
	public static void init() {
		if (REGISTERED.size() != ModEffects.EFFECTS.size()) {
			throw new IllegalStateException("ModEffectsNeoForge registered " + REGISTERED.size() + " of "
					+ ModEffects.EFFECTS.size() + " shared status effects");
		}
	}

	private ModEffectsNeoForge() {
	}
}
