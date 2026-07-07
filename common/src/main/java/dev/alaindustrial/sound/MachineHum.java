package dev.alaindustrial.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bridge from the common machine ticker to the client-only looping-sound manager. The machine hum is
 * a client-side {@code AbstractTickableSoundInstance} (smooth positional attenuation, no periodic
 * one-shot restart). This holder carries no client imports, so it is safe to load on a dedicated
 * server; the client entrypoint of each loader installs a {@link ClientHook} (Fabric's
 * {@code IndustrializationClient}, NeoForge's {@code IndustrializationNeoForgeClient}), and the
 * machine ticker calls it only when {@code level.isClientSide()}. On a dedicated server {@link #CLIENT}
 * stays {@code null} and nothing client-side is ever referenced.
 */
public final class MachineHum {

	/** Installed by each loader's client entrypoint; invoked once per client tick per hum machine. */
	public interface ClientHook {
		void tick(Level level, BlockPos pos, BlockState state);
	}

	public static volatile ClientHook CLIENT;

	private MachineHum() {}
}
