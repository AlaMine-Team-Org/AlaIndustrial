package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.core.structure.ReactorLog;
import dev.alaindustrial.core.structure.RoomScan;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The reactor controller's event log (MOD-622), with the latches that decide when a line is written (MOD-713,
 * BE-5): the last hundred things that happened, what the log last recorded about the room, the reaction and the
 * meltdown, and why the reaction last stopped.
 *
 * <p>The controller calls it at the moments its tick reaches a transition; the order of those calls inside one
 * tick is the controller's. A method given a {@code null} level writes nothing, as the controller never did
 * off-world.
 */
public final class ReactorEventLog {

	/** Throttle clicks by one player within this many ticks fold into one line of the log. */
	private static final int DEPTH_MERGE_TICKS = 100;

	/** The controller's {@code setChanged}: the log is saved, so every line has to reach the chunk. */
	private final Runnable changed;

	/** The last hundred things that happened to this reactor, persisted — see {@link ReactorLog}. */
	private final ReactorLog log = new ReactorLog();

	/**
	 * Whether a room scan has run since this block entity was loaded. Until one has, the room's status and its bare
	 * flag are defaults rather than findings, and the log would read a running reactor as one that had just stopped.
	 */
	private boolean scannedSinceLoad;

	/**
	 * The states the log last recorded, persisted with it. Comparing against these rather than against the live
	 * fields — most of which are deliberately not saved — is what keeps a chunk reload from writing "started" and
	 * "bare mode" again for a reactor that never changed, while a change made while the chunk was away is still
	 * written once.
	 */
	private boolean loggedRunning;
	private boolean loggedBare;
	private boolean loggedMelting;

	/** Blocks the current meltdown has melted; persisted, so an episode that spans a reload is counted whole. */
	private int episodeMelts;

	/** Ticks the room has spent under the melt line since the current meltdown last had it above. */
	private int meltCalmTicks;

	/** Why the reaction last stopped, taken on the tick it did; a reload forgets it and the log says "stopped". */
	private ReactorLog.Kind stopCause = ReactorLog.Kind.REACTION_STOPPED;

	public ReactorEventLog(Runnable changed) {
		this.changed = changed;
	}

	/**
	 * Writes one line of the event log (MOD-622). Game time stamps it for "how long ago"; the log's own sequence
	 * number orders it, because several events can share one tick.
	 */
	public void event(@Nullable Level level, ReactorLog.Kind kind, int a, int b, int c, String actor) {
		if (level == null) {
			return;
		}
		log.append(level.getGameTime(), kind, a, b, c, actor);
		changed.run();
	}

	/**
	 * The room's lines, on the scan that finds them (MOD-622). Sealed and unsealed key on the blockstate's
	 * {@code FORMED} flag, which the chunk saves, never on the room's status, which is not saved and reads "not in a
	 * wall" on the first scan after every load. Bare mode keys on what the log last recorded, for the same reason.
	 */
	public void logRoom(@Nullable Level level, RoomScan.Result result, boolean wasFormed, boolean bare,
			int bareRacks) {
		if (result.formed() && !wasFormed) {
			event(level, ReactorLog.Kind.ROOM_SEALED, result.sizeX(), result.sizeY(), result.sizeZ(), "");
		} else if (!result.formed() && wasFormed) {
			event(level, ReactorLog.Kind.ROOM_UNSEALED, 0, 0, 0, "");
		}
		if (bare != loggedBare) {
			loggedBare = bare;
			event(level, bare ? ReactorLog.Kind.BARE_ENTERED : ReactorLog.Kind.BARE_LEFT, bare ? bareRacks : 0, 0, 0,
					"");
		}
		scannedSinceLoad = true;
	}

	/**
	 * Takes note of the reaction starting or stopping, on the tick it does (MOD-622). Only this tick still knows why a
	 * reaction stopped; the line itself is written on the drone's latch, forty ticks on, so a redstone clock or a
	 * full buffer does not log a stop every second.
	 */
	public void reactionChanged(boolean nowReacting, boolean sealed, boolean bare, int liveRods, boolean signal,
			int depthPermille) {
		stopCause = nowReacting ? ReactorLog.Kind.REACTION_STOPPED
				: stopCauseFor(sealed, bare, liveRods, signal, depthPermille);
	}

	/**
	 * Started and stopped, on the drone's latch rather than on the reaction itself (MOD-622): a redstone clock or a
	 * buffer filling tick by tick would otherwise write a line a second. Waits for the first scan after a load,
	 * before which the room reads as unsealed and the reaction as stopped.
	 */
	public void logRunning(@Nullable Level level, boolean running, int liveRods, boolean bare, int depthPermille) {
		if (!scannedSinceLoad || running == loggedRunning) {
			return;
		}
		loggedRunning = running;
		if (running) {
			event(level, ReactorLog.Kind.REACTION_STARTED, liveRods, bare ? 100 : depthPermille / 10, 0, "");
		} else {
			event(level, stopCause, 0, 0, 0, "");
		}
	}

	/** Why a reaction that was running is not, judged on the tick it stopped. A broken room is already its own line. */
	private static ReactorLog.Kind stopCauseFor(boolean sealed, boolean bare, int liveRods, boolean signal,
			int depthPermille) {
		if (!sealed && !bare) {
			return ReactorLog.Kind.REACTION_STOPPED;
		}
		if (liveRods == 0) {
			return ReactorLog.Kind.OUT_OF_FUEL;
		}
		if (sealed && depthPermille <= 0) {
			return ReactorLog.Kind.RODS_WITHDRAWN;
		}
		return signal ? ReactorLog.Kind.REACTION_STOPPED : ReactorLog.Kind.REACTION_SCRAMMED;
	}

	/**
	 * One line when a meltdown starts and one when it is over, with the blocks it took (MOD-622). The melt line has no
	 * gap — every melted block carries heat out and drops the room back under it — so "over" waits until the room has
	 * stayed under the line for a whole melt cycle rather than flickering with each block, AND has cooled below the
	 * warning line. The second condition is not a nicety: a slow core — one rod, a low throttle, a trickle of water —
	 * spends minutes under the line between two melts and climbs back over it, and without it the log wrote a start
	 * and an end every twenty seconds and pushed the rest of its history out in a quarter of an hour.
	 */
	public void logMeltdown(@Nullable Level level, boolean melting, long heat) {
		if (!scannedSinceLoad) {
			return;
		}
		if (melting) {
			meltCalmTicks = 0;
			if (!loggedMelting) {
				loggedMelting = true;
				episodeMelts = 0;
				event(level, ReactorLog.Kind.MELTDOWN_STARTED,
						ReactorCore.heatPercent(heat, ReactorConfig.reactorHeatCapacity), 0, 0, "");
			}
			return;
		}
		if (!loggedMelting) {
			return;
		}
		meltCalmTicks = Math.min(meltCalmTicks + 1, Integer.MAX_VALUE - 1);
		boolean calm = meltCalmTicks >= Math.max(1, ReactorConfig.reactorMeltdownIntervalTicks)
				+ Math.max(0, ReactorConfig.reactorMeltWarnTicks);
		boolean cooled = ReactorCore.heatPercent(heat, ReactorConfig.reactorHeatCapacity)
				< Math.min(ReactorConfig.reactorHeatWarnPercent, ReactorConfig.reactorMeltdownStartPercent);
		if (calm && cooled) {
			loggedMelting = false;
			meltCalmTicks = 0;
			event(level, ReactorLog.Kind.MELTDOWN_ENDED, episodeMelts, 0, 0, "");
			episodeMelts = 0;
		}
	}

	/** One more block the current meltdown has melted. */
	public void countMelt() {
		episodeMelts++;
	}

	/**
	 * The throttle moved from {@code fromPermille} to {@code toPermille}, by {@code actor} — clicks by one player
	 * inside the merge window fold into one line (MOD-622).
	 */
	public void depthChanged(@Nullable Level level, int fromPermille, int toPermille, String actor) {
		if (level != null) {
			log.appendOrMerge(level.getGameTime(), ReactorLog.Kind.DEPTH_CHANGED, fromPermille / 10, toPermille / 10, 0,
					actor, DEPTH_MERGE_TICKS);
		}
	}

	/** The event log, oldest first — for the menu and for tests. */
	public List<ReactorLog.Entry> entries() {
		return log.entries();
	}

	/** The «Log» tab's snapshot for one viewer: every entry, and how far that player has read. */
	public dev.alaindustrial.network.ReactorLogPayload snapshot(int containerId, UUID viewer) {
		return new dev.alaindustrial.network.ReactorLogPayload(containerId, log.seenBy(viewer), log.entries());
	}

	/**
	 * Records that a player has seen the log up to {@code seq} — the newest entry their screen was actually sent,
	 * never "now": an alarm written after that send has not reached them and must keep their badge lit.
	 */
	public void markSeen(UUID viewer, int seq) {
		if (log.markSeen(viewer, Math.min(seq, log.newestSeq()))) {
			changed.run();
		}
	}

	/** Writes the log and the states it last recorded. */
	public void save(ValueOutput output) {
		ReactorLogStorage.save(output, log);
		output.putBoolean(ReactorLogStorage.RUNNING, loggedRunning);
		output.putBoolean(ReactorLogStorage.BARE, loggedBare);
		output.putBoolean(ReactorLogStorage.MELTING, loggedMelting);
		output.putInt(ReactorLogStorage.EPISODE_MELTS, episodeMelts);
	}

	public void load(ValueInput input) {
		ReactorLogStorage.load(input, log);
		loggedRunning = input.getBooleanOr(ReactorLogStorage.RUNNING, false);
		loggedBare = input.getBooleanOr(ReactorLogStorage.BARE, false);
		loggedMelting = input.getBooleanOr(ReactorLogStorage.MELTING, false);
		episodeMelts = input.getIntOr(ReactorLogStorage.EPISODE_MELTS, 0);
	}

	/** Takes the log out of the tag every nearby player receives with the chunk; it reaches a player only on screen. */
	public static void stripFromUpdateTag(CompoundTag tag) {
		ReactorLogStorage.stripFromUpdateTag(tag);
	}
}
