package dev.alaindustrial.client.sound;

import dev.alaindustrial.block.MachineHumProvider;
import dev.alaindustrial.block.entity.MachineBlockEntity;
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
 * On a teleport (a new client level, or a large single-tick listener jump) it drops every tracked loop
 * so the ticker rebuilds them at the destination — otherwise the sound engine's stale/paused instances
 * would keep the base silent (MOD-129). Installed from each loader's client entrypoint via
 * {@link #register()}; client-only, never referenced on a dedicated server.
 */
public final class MachineHumClientHook implements MachineHum.ClientHook {

	/** Start the (initially inaudible) loop this far out so approach fades in smoothly. */
	private static final double START_DISTANCE_SQR = 24.0 * 24.0;

	/** Prune stopped entries once the map grows past this (bounds memory while travelling). */
	private static final int PRUNE_THRESHOLD = 64;

	private final Map<Long, MachineHumSoundInstance> active = new HashMap<>();

	// Listener continuity anchor (MOD-129): a teleport breaks the assumption that the listener moves
	// smoothly, and the sound engine then leaves loops in a state the per-position ticker can't recover
	// (see tick()). We track the client level and last listener position to detect the discontinuity.
	private Level lastLevel;
	private boolean hasListenerAnchor;
	private double lastListenerX;
	private double lastListenerY;
	private double lastListenerZ;

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
			// Between worlds (disconnect / dimension swap in progress): drop the level anchor so we neither
			// retain the dead ClientLevel nor miss the discontinuity — the next world's first tick then sees
			// a fresh level and rebuilds the loops. active holds only lightweight pos/block refs (no level),
			// and the engine's stopAll already silenced everything, so it needs no clearing here.
			lastLevel = null;
			hasListenerAnchor = false;
			return;
		}

		// MOD-129: after a teleport the listener no longer moves continuously, and the sound engine leaves
		// our loops in a state the per-position ticker can't recover from — a dimension change / reconnect
		// clears the engine but not our references (the instances' isStopped() is still false), and a
		// teleport's loading-screen pause routes the engine through tickMusicWhenPaused(), which freezes
		// each loop's self-terminating tick() while still reporting it active. Either way the guard below
		// (instance != null) then suppresses the restart and the base falls permanently silent. Detect the
		// discontinuity — a fresh ClientLevel, or a single-tick listener jump past TELEPORT_JUMP_SQR — and
		// drop every tracked loop so the ticker rebuilds the ones still in range, mirroring vanilla, which
		// recreates its ambient-sound handlers on each new level rather than carrying state across it.
		double listenerX = mc.player.getX();
		double listenerY = mc.player.getY();
		double listenerZ = mc.player.getZ();
		double jumpSqr = 0.0;
		if (hasListenerAnchor) {
			double dx = listenerX - lastListenerX;
			double dy = listenerY - lastListenerY;
			double dz = listenerZ - lastListenerZ;
			jumpSqr = dx * dx + dy * dy + dz * dz;
		}
		if (HumTeleportDetector.isDiscontinuity(mc.level != lastLevel, hasListenerAnchor, jumpSqr)) {
			resetLoops();
		}
		lastLevel = mc.level;
		lastListenerX = listenerX;
		lastListenerY = listenerY;
		lastListenerZ = listenerZ;
		hasListenerAnchor = true;

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
		// A mute chip in the machine's active upgrade slot silences it entirely (MOD-080): never start
		// the loop. Contents sync with the block entity, so this read is valid client-side.
		if (level.getBlockEntity(pos) instanceof MachineBlockEntity be && be.isMuted()) {
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

	/** Stop and forget every tracked loop after a teleport; the ticker recreates the ones still in range. */
	private void resetLoops() {
		if (active.isEmpty()) {
			return;
		}
		for (MachineHumSoundInstance instance : active.values()) {
			instance.endLoop();
		}
		active.clear();
	}
}
