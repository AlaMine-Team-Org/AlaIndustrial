package dev.alaindustrial.client.sound;

import dev.alaindustrial.block.MachineHumProvider;
import dev.alaindustrial.sound.MachineHum;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side manager for machine hum loops. Called once per client tick for every loaded
 * {@link MachineHumProvider} block entity (via {@code humMachineTicker}). It keeps at most one
 * looping {@link MachineHumSoundInstance} per position: it starts the loop when the machine is
 * {@linkplain MachineHumProvider#isWorking working} and the listener is within
 * {@link #START_DISTANCE_SQR} — deliberately larger than the sound's audible radius, so the loop is
 * already playing (silently) before it can be heard and ramps up smoothly on approach instead of
 * snapping on. The instance self-terminates and this map drops it once it is no longer active.
 * Installed from each loader's client entrypoint via {@link #register()}; client-only, never
 * referenced on a dedicated server.
 */
public final class MachineHumClientHook implements MachineHum.ClientHook {

	/** Start the (initially inaudible) loop this far out so approach fades in smoothly. */
	private static final double START_DISTANCE_SQR = 24.0 * 24.0;

	/** Prune stopped entries once the map grows past this (bounds memory while travelling). */
	private static final int PRUNE_THRESHOLD = 64;

	private final Map<Long, MachineHumSoundInstance> active = new HashMap<>();

	/** Install this manager as the client hook. Call once from a client entrypoint. */
	public static void register() {
		MachineHum.CLIENT = new MachineHumClientHook();
	}

	@Override
	public void tick(Level level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof MachineHumProvider hum)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}

		long key = pos.asLong();
		MachineHumSoundInstance instance = active.get(key);
		if (instance != null && (instance.isStopped() || !mc.getSoundManager().isActive(instance))) {
			active.remove(key);
			instance = null;
		}

		boolean working = hum.isWorking(level, pos, state);
		if (!working || instance != null) {
			return;
		}

		double cx = pos.getX() + 0.5;
		double cy = pos.getY() + 0.5;
		double cz = pos.getZ() + 0.5;
		if (mc.player.distanceToSqr(cx, cy, cz) > START_DISTANCE_SQR) {
			return;
		}

		if (active.size() > PRUNE_THRESHOLD) {
			active.values().removeIf(i -> i.isStopped() || !mc.getSoundManager().isActive(i));
		}

		MachineHumSoundInstance created =
				new MachineHumSoundInstance(hum.humSound().get(), pos.immutable(), state.getBlock(), hum.humVolume());
		mc.getSoundManager().play(created);
		active.put(key, created);
	}
}
