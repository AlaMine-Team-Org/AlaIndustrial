package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Platform-neutral sound handle (MOD-022 facade). NeoForge freezes the vanilla {@code SOUND_EVENT}
 * registry before mod construction, so a direct {@code Registry.register} (fine on Fabric) throws
 * {@code Registry is already frozen} there. Each loader binds {@link #MACERATOR_GRIND} during its own
 * registration — Fabric via an eager {@code Registry.register}, NeoForge via a {@code DeferredRegister}
 * holder (itself a {@link Supplier}) — and content reads it lazily through {@code .get()}.
 */
public final class ModSounds {

	/** The registry id, shared by both loaders' registration. */
	public static final Identifier MACERATOR_GRIND_ID = Industrialization.id("macerator_grind");

	/** Bound once per loader before any block entity plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> MACERATOR_GRIND = () -> {
		throw new IllegalStateException("ModSounds.MACERATOR_GRIND read before its loader bound it");
	};

	/** Build the event instance both loaders register (variable range, like vanilla machine sounds). */
	public static SoundEvent createMaceratorGrind() {
		return SoundEvent.createVariableRangeEvent(MACERATOR_GRIND_ID);
	}

	/** The registry id for the generator/geothermal ambient engine hum. */
	public static final Identifier GENERATOR_HUM_ID = Industrialization.id("generator_hum");

	/** Bound once per loader before any block plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> GENERATOR_HUM = () -> {
		throw new IllegalStateException("ModSounds.GENERATOR_HUM read before its loader bound it");
	};

	/** Build the generator-hum event instance both loaders register. */
	public static SoundEvent createGeneratorHum() {
		return SoundEvent.createVariableRangeEvent(GENERATOR_HUM_ID);
	}

	private ModSounds() {
	}
}
