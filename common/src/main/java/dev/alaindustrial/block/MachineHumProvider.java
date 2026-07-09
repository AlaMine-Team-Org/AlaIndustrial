package dev.alaindustrial.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A machine block that emits a looping ambient hum while working. The client-side sound manager reads
 * the sound + volume from the block and drives a single looping instance per position (see
 * {@code dev.alaindustrial.sound.MachineHum}). The sound is exposed as a {@code Supplier<SoundEvent>}
 * because {@code ModSounds} is a per-loader facade (NeoForge binds the event lazily via a
 * {@code DeferredRegister}). Blocks that do not implement this interface are silent; adding a hum to a
 * new machine is just implementing {@link #humSound()}.
 *
 * <p>Two patterns are supported for telling the client the machine is working (so it can start/stop the
 * loop without server spam):
 * <ul>
 *   <li><b>Pattern A (default)</b> — the block carries the vanilla {@code lit} blockstate and flips it
 *       from its block entity. The default {@link #isWorking} reads it; no override needed.</li>
 *   <li><b>Pattern C (lit-less)</b> — blocks with no {@code lit} property (e.g. solar panels) override
 *       {@link #isWorking} and derive the working state themselves from side-agnostic world state
 *       (sky access, weather, time of day). This keeps the loop silent when the block genuinely stops
 *       producing, without any networked flag.</li>
 * </ul>
 */
public interface MachineHumProvider {

	/** The looping ambient sound played while this machine works (resolved lazily at play time). */
	Supplier<SoundEvent> humSound();

	/** Playback volume [0..1]; also sets the audible radius (lower = quieter, closer-range). */
	default float humVolume() {
		return 0.35f;
	}

	/**
	 * Whether the block at {@code pos} is currently working and should emit the hum. The default reads
	 * the vanilla {@code lit} blockstate (Pattern A); lit-less blocks override this to derive the state
	 * from world conditions (Pattern C). Called client-side every tick by the hum manager — the
	 * implementation must be side-agnostic and cheap (no network, no server-only APIs).
	 */
	default boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return state.hasProperty(LitMachineBlock.LIT) && state.getValue(LitMachineBlock.LIT);
	}
}
