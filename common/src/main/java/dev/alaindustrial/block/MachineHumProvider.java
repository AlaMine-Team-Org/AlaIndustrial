package dev.alaindustrial.block;

import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;

/**
 * A machine block that emits a looping ambient hum while working ({@code lit=true}). The client-side
 * sound manager reads the sound + volume from the block and drives a single looping instance per
 * position (see {@code dev.alaindustrial.sound.MachineHum}). The sound is exposed as a
 * {@code Supplier<SoundEvent>} because {@code ModSounds} is a per-loader facade (NeoForge binds the
 * event lazily via a {@code DeferredRegister}). Blocks that do not implement this interface are silent;
 * adding a hum to a new machine is just implementing {@link #humSound()}.
 */
public interface MachineHumProvider {

	/** The looping ambient sound played while this machine works (resolved lazily at play time). */
	Supplier<SoundEvent> humSound();

	/** Playback volume [0..1]; also sets the audible radius (lower = quieter, closer-range). */
	default float humVolume() {
		return 0.35f;
	}
}
