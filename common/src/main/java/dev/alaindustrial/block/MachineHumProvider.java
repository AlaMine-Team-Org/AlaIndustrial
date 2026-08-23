package dev.alaindustrial.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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

	/**
	 * Playback volume [0..1].
	 *
	 * <p><b>This does NOT set the audible radius</b>, despite what this javadoc claimed until MOD-472.
	 * {@code SoundEngine.play} computes the attenuation distance as
	 * {@code Math.max(instanceVolume, 1.0F) * sound.getAttenuationDistance()}, so every volume at or
	 * below 1 leaves the multiplier at exactly 1 and the radius comes solely from
	 * {@code attenuation_distance} in {@code sounds.json} — a key no entry in this mod sets, which puts
	 * every loop at vanilla's 16 blocks regardless of the number below. The same applies to
	 * {@code createFixedRangeEvent}: {@code SoundEvent#getRange} is read only by
	 * {@code ServerLevel.playSeededSound}, a path a client-side loop never takes.
	 */
	default float humVolume() {
		return 0.35f;
	}

	/**
	 * Volume for this tick, re-read while the loop plays — the hook for a machine whose loudness depends
	 * on something that changes without the loop stopping.
	 *
	 * <p>The default ignores the arguments and returns the fixed {@link #humVolume()}, so every machine
	 * that does not care is unaffected and pays nothing. The reactor's fuel columns override it: whether
	 * the listener is inside the sealed shell or outside it is a property of where the LISTENER is
	 * standing, not of the block, so it cannot be answered once when the loop starts.
	 *
	 * <p>⚠️ <b>Never return 0.</b> {@code SoundEngine.play} refuses a silent instance outright
	 * ({@code volume == 0} with {@code canStartSilent() == false}), the manager then sees a loop that is
	 * not active and drops it, and the ticker tries again on the very next tick — twenty failed starts a
	 * second, each one firing the subtitle listeners before the volume check, so the subtitle flickers on
	 * screen with no sound behind it. {@link dev.alaindustrial.client.sound.MachineHumSoundInstance}
	 * clamps to a floor for exactly this reason, but an override should not lean on that.
	 *
	 * <p>Called client-side every tick while the loop plays: same contract as {@link #isWorking} — cheap
	 * and side-agnostic.
	 */
	default float humVolume(Level level, BlockPos pos, BlockState state, Vec3 listener) {
		return humVolume();
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

	/**
	 * Where the loop should be heard from this tick, or {@code null} to leave it at the block's centre.
	 *
	 * <p>Almost every machine is a box that stays put, so the default is {@code null} and the sound
	 * engine never re-reads a position. The Garden Drone Station is the exception: the thing making the
	 * noise is the drone, which is somewhere out over the farm rather than on the dock, so it returns
	 * the drone's current position and the loop travels with it (MOD-329).
	 *
	 * <p>Called client-side every tick while the loop plays, so it must be cheap and side-agnostic —
	 * the same contract as {@link #isWorking}.
	 */
	@Nullable
	default Vec3 humPosition(Level level, BlockPos pos, BlockState state) {
		return null;
	}
}
