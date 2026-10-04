package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.ReactorCore;
import dev.alaindustrial.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a reactor sounds and looks like from across the floor (MOD-472, MOD-662; MOD-713, BE-5): the drone painted
 * onto the racks, the spin-down, the overheat siren and its critical repeat, and the steam puffs over boiling
 * stacks.
 *
 * <p>The puffs hold the one particle call that has to stay the same on both Minecraft lines — the 9-argument
 * {@code sendParticles}; keeping it in this one class keeps a future drift of the particle API to one file.
 */
public final class ReactorVoice {

	/**
	 * How many columns carry the drone at once.
	 *
	 * <p>Not "all of them", and the ceiling is the client's, not ours: a Minecraft client has on the
	 * order of twenty-five static sound channels for the whole game, and a minimum-size room packed
	 * solid already holds twenty-seven racks. Identical copies of one sample also sum at about +6 dB per
	 * doubling, so past a handful the room stops sounding bigger and starts sounding louder. Three keeps
	 * the drone spread across the floor — which is the whole reason it plays from the racks — while
	 * costing about a tenth of the channel budget.
	 */
	private static final int VOICED_COLUMNS = 3;

	/**
	 * Ticks the drone keeps playing after the last productive tick.
	 *
	 * <p><b>Without this the loop would stutter at twenty hertz.</b> A healthy reactor with somewhere to
	 * put its power alternates between producing and {@code BUFFER_FULL} tick by tick, because the
	 * sockets are drained and refilled every tick; and between the last rod burning out and the next room
	 * scan there is a gap of up to {@code reactorScanIntervalTicks}. Both would chop the sound to pieces.
	 * Two seconds of latch spans either.
	 */
	private static final int VOICE_LATCH_TICKS = 40;

	/** The controller's {@code setChanged}: the overheat latch is saved. */
	private final Runnable changed;

	/** Counts down from {@link #VOICE_LATCH_TICKS} after the last tick that actually made power. */
	private int voiceLatch;

	/** Whether the drone was sounding last tick — the edge that fires the spin-down. */
	private boolean wasVoiced;

	/**
	 * Whether the overheat alarm has already sounded and not yet re-armed.
	 *
	 * <p>Persisted, because the alternative is an alarm that fires again every time the chunk reloads on
	 * a core that has been sitting hot and unattended the whole time.
	 */
	private boolean overheatWarned;

	/**
	 * Ticks until the critical alarm sounds again, while the core sits at the top of the scale.
	 *
	 * <p>Deliberately NOT persisted: on the tick a chunk reloads this is zero, so a core that is still
	 * critical announces itself immediately rather than waiting out a countdown nobody heard. There is
	 * nothing to preserve — the state that matters is the temperature, and that is saved.
	 */
	private int criticalAlarmCooldown;

	// ── MOD-662: steam puffs over boiling stacks ──
	/** Controller ticks until the next puff pulse. A counter, not the game clock: it must advance per tick run. */
	private int plumeCountdown;

	/**
	 * Where the next pulse starts in the list of boiling stacks, so a room with more of them than
	 * {@code ReactorConfig.reactorSteamPlumeStacksPerPulse} lets every stack take its turn. Not persisted.
	 */
	private int plumeCursor;

	/**
	 * Stacks this controller has puffed steam over since it was loaded — one per stack per pulse.
	 *
	 * <p>The same reason as the hazard's melt counter: particles are drawn on the client, so the server side has no
	 * other way to be asked whether it sent any. Not persisted — a live counter, not a record.
	 */
	private int steamPuffsSent;

	public ReactorVoice(Runnable changed) {
		this.changed = changed;
	}

	/**
	 * Keeps the room's drone in step with what the reactor is doing (MOD-472): the latch half, answering whether
	 * the drone sounds this tick. {@link #sing} paints and cues it once the log has been told.
	 *
	 * <p>The signal is the reaction, counted from the live rods every tick. It used to be {@code lastOutput > 0},
	 * which is the sale: a room whose buffer filled up went silent and played the spin-down while its core kept
	 * burning (audit, MOD-623). A hum that stops on a reactor still heating tells the player the one thing that is
	 * not true. Not {@code idleReason} either: it reads {@code RUNNING} on a reaction too weak to pay a whole EU.
	 */
	public boolean latch(boolean reacting) {
		if (reacting) {
			voiceLatch = VOICE_LATCH_TICKS;
		} else if (voiceLatch > 0) {
			voiceLatch--;
		}
		return voiceLatch > 0;
	}

	/** The drone painted onto {@code columns} as {@link #latch} decided, and the spin-down on its falling edge. */
	public void sing(Level level, BlockPos pos, List<FuelRodAssemblyBlockEntity> columns, boolean voiced) {
		paintVoicedColumns(level, columns, voiced);
		if (wasVoiced && !voiced && level instanceof ServerLevel serverLevel) {
			// The core going quiet gets its own cue. It covers every way a reaction stops — the lever
			// pulled, the last rod spent, the throttle wound shut — because all three arrive here as the
			// same thing: a reaction that was running a moment ago and is not now.
			serverLevel.playSound(null, pos, ModSounds.REACTOR_SPINDOWN.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
		}
		wasVoiced = voiced;
	}

	/**
	 * Takes the drone off every rack in the room this controller last sealed.
	 *
	 * <p><b>Sweeps the remembered BOX, not the in-memory list</b>, and that is the whole point of the
	 * method. The room's list of assemblies is rebuilt by the scan and never saved, so after a chunk round-trip it
	 * is empty — and a room whose breach is first noticed on that very tick would have had nothing to
	 * silence. The flag, meanwhile, IS saved: it rides in the chunk like any blockstate. The box is
	 * likewise persisted for exactly this class of problem, so it is the only handle that survives the
	 * gap and can still find the racks.
	 *
	 * <p>Costs one sweep of the interior, on the transition only — never on a running tick.
	 */
	public void silence(Level level, ReactorRoom room) {
		paintVoicedColumns(level, room.collectColumns(level), false);
		room.box().clearActive(level);
		voiceLatch = 0;
		// wasVoiced is deliberately NOT cleared here. This runs from the scan, which happens BEFORE
		// runReactor in the same tick, so wiping it would swallow the very edge the spin-down listens
		// for — and a breach is the loudest of the four cases that cue is meant to cover.
	}

	/**
	 * Marks the first {@link #VOICED_COLUMNS} racks as the ones that sound, and clears the rest.
	 *
	 * <p>Scan order is a stable walk of the room's box, so the same racks keep the voice from one sweep
	 * to the next and the drone does not wander around the floor. The blockstate is written only when the
	 * value actually changes — the same discipline the shell's {@code formed} flag uses, and the reason
	 * painting a room full of columns costs nothing on the ticks in between.
	 *
	 * <p>Walks the block entities the tick already resolved rather than the room's list of positions, and reads
	 * each state off its block entity, where it is cached. Re-deriving the list would mean a second
	 * chunk lookup per rack on every tick of every reactor, for nothing.
	 */
	private void paintVoicedColumns(Level level, List<FuelRodAssemblyBlockEntity> columns, boolean voiced) {
		int painted = 0;
		for (FuelRodAssemblyBlockEntity column : columns) {
			BlockState state = column.getBlockState();
			if (!(state.getBlock() instanceof FuelRodAssemblyBlock)) {
				continue;
			}
			boolean wanted = voiced && painted < VOICED_COLUMNS && column.hasFuel();
			if (wanted) {
				painted++;
			}
			if (state.getValue(FuelRodAssemblyBlock.ACTIVE) != wanted) {
				level.setBlock(column.getBlockPos(), state.setValue(FuelRodAssemblyBlock.ACTIVE, wanted), 2);
			}
		}
	}

	/**
	 * Sounds the overheat siren once per excursion (MOD-472), on {@code percent} — the scale the reactor is judged
	 * on: heat in a room, instability in the open. {@code onWarn} runs on the blast itself (the log's line).
	 *
	 * <p>Edge, not level, and the reason is in the balance: an unplumbed pair of columns settles at 66 %
	 * of the heat scale, three points below the 70 % warning line, so a plain threshold test would fire
	 * and clear several times a second on a reactor that is merely warm. {@link ReactorCore} owns the
	 * arithmetic — it re-arms only once the coolant loop has pulled the core back to its own target — so
	 * the rule is covered by a unit test rather than by listening.
	 *
	 * <p>Played from the controller with a fixed long range and no muffling of any kind. The drone
	 * belongs to the room and is held in by the shell; the alarm is the opposite kind of sound — its
	 * entire job is reaching somebody who is not in the room.
	 */
	public void warnOnOverheat(Level level, BlockPos pos, int percent, Runnable onWarn) {
		if (ReactorCore.shouldSoundAlarm(percent, ReactorConfig.reactorHeatWarnPercent,
				ReactorConfig.reactorCoolantTargetPercent, overheatWarned)
				&& level instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, pos, ModSounds.REACTOR_ALARM.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
			onWarn.run();
		}
		boolean latched = ReactorCore.alarmStaysLatched(percent, ReactorConfig.reactorHeatWarnPercent,
				ReactorConfig.reactorCoolantTargetPercent, overheatWarned);
		if (latched != overheatWarned) {
			overheatWarned = latched;
			changed.run();
		}
		soundCriticalAlarm(level, pos, percent);
	}

	/**
	 * Keeps the siren going while the core is pinned at the top of the scale (MOD-472).
	 *
	 * <p>The threshold alarm above is a single blast by design, and that is exactly what leaves a core
	 * at a hundred percent sitting in silence: it crossed the warning line long ago and latched. A
	 * reactor in its worst state should not be quieter than one that is merely warm, so here it re-sounds
	 * every three to five seconds for as long as it stays there.
	 *
	 * <p>The gap is re-rolled after every blast rather than fixed. A siren on an exact metronome turns
	 * into background texture within a minute; an irregular one keeps reading as an alarm. Louder than
	 * the threshold blast, too — this is the emergency, not the warning.
	 *
	 * <p>Coming down off the top clears the countdown, so the next excursion sounds immediately instead
	 * of finishing a wait left over from the last one.
	 */
	private void soundCriticalAlarm(Level level, BlockPos pos, int heatPercent) {
		if (!ReactorCore.isCritical(heatPercent)) {
			criticalAlarmCooldown = 0;
			return;
		}
		if (criticalAlarmCooldown > 0) {
			criticalAlarmCooldown--;
			return;
		}
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, pos, ModSounds.REACTOR_ALARM.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
			criticalAlarmCooldown = serverLevel.getRandom().nextIntBetweenInclusive(
					ReactorCore.CRITICAL_ALARM_MIN_TICKS, ReactorCore.CRITICAL_ALARM_MAX_TICKS);
		}
	}

	/**
	 * Sends steam puffs up over the stacks that are boiling (MOD-662).
	 *
	 * <p><b>The puff is a readout, not an effect.</b> It answers from across the floor the two questions the
	 * «Coolant» tab answers from the panel: is this stack working, and how full is its steam. So it appears only over
	 * a stack that boiled within the last second, and it thickens with that stack's own steam — one small puff at
	 * an empty vessel, four large ones near the top, and a heavier burst once the exhaust counts as blocked. A sealed
	 * room only: a bare rack boils nothing, and a puff over it would report a loop that does not exist.
	 *
	 * <p>Only the 9-argument {@code sendParticles}: it is the one overload Minecraft 26.2 shares with 26.3.
	 */
	public void puffSteam(ServerLevel level, List<FuelRodAssemblyBlockEntity> columns) {
		if (plumeCountdown > 0) {
			plumeCountdown--;
			return;
		}
		plumeCountdown = Math.max(1, ReactorConfig.reactorSteamPlumeIntervalTicks) - 1;
		List<List<FuelRodAssemblyBlockEntity>> boiling = new ArrayList<>();
		for (List<FuelRodAssemblyBlockEntity> run : ReactorStacks.runs(columns)) {
			for (FuelRodAssemblyBlockEntity column : run) {
				if (column.isBoiling()) {
					boiling.add(run);
					break;
				}
			}
		}
		if (boiling.isEmpty()) {
			return;
		}
		int cap = Math.max(1, ReactorConfig.reactorSteamPlumeStacksPerPulse);
		int shown = Math.min(cap, boiling.size());
		int start = Math.floorMod(plumeCursor, boiling.size());
		plumeCursor = boiling.size() > cap ? start + shown : 0;
		for (int i = 0; i < shown; i++) {
			puffOver(level, boiling.get((start + i) % boiling.size()));
		}
	}

	/** One stack's puff, over the top face of its topmost column, sized by the stack's own steam. */
	private void puffOver(ServerLevel level, List<FuelRodAssemblyBlockEntity> run) {
		long steam = 0;
		long capacity = 0;
		for (FuelRodAssemblyBlockEntity column : run) {
			steam += column.steamAmount();
			capacity += column.steamCapacity();
		}
		int percent = capacity <= 0 ? 0 : (int) Math.min(100, steam * 100 / capacity);
		BlockPos top = run.get(run.size() - 1).getBlockPos();
		double x = top.getX() + 0.5;
		double y = top.getY() + 1.05;
		double z = top.getZ() + 0.5;
		level.sendParticles(new net.minecraft.core.particles.GeyserBaseParticleOptions(ParticleTypes.GEYSER_POOF, 0,
				0.5f + 0.5f * percent / 100f), x, y, z, 1 + 3 * percent / 100, 0.25, 0.05, 0.25, 0.0);
		if (percent >= dev.alaindustrial.core.structure.ReactorZone.STEAM_BLOCKED_PERCENT) {
			level.sendParticles(new net.minecraft.core.particles.GeyserBaseParticleOptions(ParticleTypes.GEYSER_BASE, 1,
					1.5f), x, y, z, 1, 0.1, 0.0, 0.1, 0.0);
		}
		steamPuffsSent++;
	}

	/** Writes the overheat latch — the one piece of the voice that outlives a reload. */
	public void saveWarned(ValueOutput output) {
		output.putBoolean("OverheatWarned", overheatWarned);
	}

	public void loadWarned(ValueInput input) {
		overheatWarned = input.getBooleanOr("OverheatWarned", false);
	}

	/** Whether the warning siren has sounded and not re-armed yet. */
	public boolean overheatWarned() {
		return overheatWarned;
	}

	/** Stacks puffed over since load — "are the puffs being sent". */
	public int steamPuffsSent() {
		return steamPuffsSent;
	}
}
