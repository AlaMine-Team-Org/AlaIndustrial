package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.effect.RadiationEffect;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * Platform-neutral status-effect handles (MOD-470) — the mod's first {@code MobEffect}.
 *
 * <p>Same facade as {@link ModSounds}, and for the same reason: NeoForge freezes the vanilla
 * {@code MOB_EFFECT} registry before mod construction, so the eager {@code Registry.register} that is
 * correct on Fabric throws {@code Registry is already frozen} there. Each loader binds the holder
 * during its own registration and content reads it lazily.
 *
 * <p>A {@link Holder}, not a bare {@link MobEffect}: everything that applies or queries an effect
 * ({@code addEffect}, {@code getEffect}, {@code hasEffect}) is keyed on the holder.
 */
public final class ModEffects {

	/** The registry id, shared by both loaders' registration. */
	public static final Identifier RADIATION_ID = Industrialization.id("radiation");

	/** Bound once per loader before anything can be irradiated; unbound = loud failure, never a silent NPE. */
	public static Supplier<Holder<MobEffect>> RADIATION = () -> {
		throw new IllegalStateException("ModEffects.RADIATION read before its loader bound it");
	};

	private ModEffects() {
	}

	/** Build the effect instance both loaders register. */
	public static MobEffect createRadiation() {
		return new RadiationEffect();
	}
}
