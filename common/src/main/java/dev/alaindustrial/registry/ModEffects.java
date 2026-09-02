package dev.alaindustrial.registry;

import dev.alaindustrial.effect.RadiationEffect;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/**
 * Every status effect the mod registers, declared once for both loaders (MOD-470, MOD-555).
 *
 * <p>Same facade as {@link ModSounds}, and for the same reason: NeoForge freezes the vanilla
 * {@code MOB_EFFECT} registry before mod construction, so the eager {@code Registry.register} that is
 * correct on Fabric throws {@code Registry is already frozen} there. {@link #EFFECTS} is the one place
 * that says which effects exist; each loader replays it in a loop of its own.
 *
 * <p><b>Why effects get their own list instead of riding along with the sounds.</b> A handle here is a
 * {@link Holder}, not a bare {@link MobEffect}: everything that applies or queries an effect
 * ({@code addEffect}, {@code getEffect}, {@code hasEffect}) is keyed on the holder, and getting one takes
 * a different registration call on each loader — {@code Registry.registerForHolder} on Fabric, the
 * {@code DeferredHolder} itself on NeoForge. That is a different replay, so it is a different list. The
 * alternative — one list with a per-entry flag — would put the difference inside the loop, where the next
 * entry inherits whichever branch it happens to land in.
 */
public final class ModEffects {

	/**
	 * One status effect: its registry path, how to build it, and where to publish the registered holder.
	 *
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param factory builds the effect instance the loader registers
	 * @param bind    publishes the registered holder into its handle above
	 */
	public record EffectDef(String id, Supplier<MobEffect> factory,
			Consumer<Supplier<Holder<MobEffect>>> bind) {
	}

	/** Bound once per loader before anything can be irradiated; unbound = loud failure, never a silent NPE. */
	public static Supplier<Holder<MobEffect>> RADIATION = () -> {
		throw new IllegalStateException("ModEffects.RADIATION read before its loader bound it");
	};

	/** Every status effect, in one shared registration order. Both loaders replay this list. */
	public static final List<EffectDef> EFFECTS = List.of(
			new EffectDef("radiation", RadiationEffect::new, h -> RADIATION = h));

	private ModEffects() {
	}
}
