package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge sound registration (MOD-022 facade). NeoForge freezes the vanilla {@code SOUND_EVENT}
 * registry before mod construction, so the neutral {@link ModSounds} cannot self-register there (unlike
 * Fabric). This {@link DeferredRegister} registers on the mod bus and binds the neutral
 * {@link ModSounds#MACERATOR_GRIND} handle to the deferred holder (itself a {@code Supplier<SoundEvent>}).
 */
public final class ModSoundsNeoForge {
	public static final DeferredRegister<SoundEvent> SOUNDS =
			DeferredRegister.create(Registries.SOUND_EVENT, Industrialization.MOD_ID);

	public static final DeferredHolder<SoundEvent, SoundEvent> MACERATOR_GRIND =
			SOUNDS.register("macerator_grind", ModSounds::createMaceratorGrind);

	public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_HUM =
			SOUNDS.register("generator_hum", ModSounds::createGeneratorHum);

	/** Bind the neutral handles to the deferred holders. Called from the {@code @Mod} ctor after register. */
	public static void init() {
		ModSounds.MACERATOR_GRIND = MACERATOR_GRIND;
		ModSounds.GENERATOR_HUM = GENERATOR_HUM;
	}

	private ModSoundsNeoForge() {
	}
}
