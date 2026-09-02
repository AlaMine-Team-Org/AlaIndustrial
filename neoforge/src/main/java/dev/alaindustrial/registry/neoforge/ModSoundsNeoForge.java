package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge sound registration: a replay of the shared {@link ModSounds#SOUNDS} list (MOD-022 facade,
 * MOD-555).
 *
 * <p>NeoForge freezes the vanilla {@code SOUND_EVENT} registry before mod construction, so the neutral
 * {@link ModSounds} cannot self-register there the way it can on Fabric. What is left in this file is the
 * NeoForge registration MECHANISM and only that: the {@link DeferredRegister} (which has to live on this
 * side), its lazy {@code register}, and the binding of each resulting {@link DeferredHolder} — itself a
 * {@code Supplier<SoundEvent>} — into the entry's handle.
 *
 * <p>Before MOD-555 this file also carried the LIST: 25 holder fields and 25 more assignment lines in
 * {@code init()}, mirroring 25 registration lines in {@code IndustrializationFabric} by convention alone.
 */
public final class ModSoundsNeoForge {
	public static final DeferredRegister<SoundEvent> SOUNDS =
			DeferredRegister.create(Registries.SOUND_EVENT, Industrialization.MOD_ID);

	/**
	 * Every {@link ModSounds#SOUNDS} entry, queued the moment this class loads and bound into its handle
	 * right away — a {@code DeferredHolder} is a {@code Supplier} and resolves lazily, so binding before
	 * the {@code RegisterEvent} fires is legal and is exactly what the hand-written {@code init()} body
	 * used to do.
	 */
	private static final List<DeferredHolder<SoundEvent, SoundEvent>> REGISTERED = registerAll();

	private static List<DeferredHolder<SoundEvent, SoundEvent>> registerAll() {
		List<DeferredHolder<SoundEvent, SoundEvent>> registered = new ArrayList<>();
		for (ModSounds.SoundDef def : ModSounds.SOUNDS) {
			DeferredHolder<SoundEvent, SoundEvent> holder = SOUNDS.register(def.id(), def.factory());
			def.bind().accept(holder);
			registered.add(holder);
		}
		return List.copyOf(registered);
	}

	/**
	 * Class-load trigger for the {@code @Mod} ctor. Queueing and binding both happen in the static
	 * initializer above, so this only has to touch the class — and it checks, cheaply, that the replay
	 * covered the whole list rather than silently stopping short.
	 */
	public static void init() {
		if (REGISTERED.size() != ModSounds.SOUNDS.size()) {
			throw new IllegalStateException("ModSoundsNeoForge registered " + REGISTERED.size() + " of "
					+ ModSounds.SOUNDS.size() + " shared sound events");
		}
	}

	private ModSoundsNeoForge() {
	}
}
