package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.block.ReactorAntennaMastBlock;
import dev.alaindustrial.block.entity.ReactorAntennaBlockEntity;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.core.structure.ReactorCore;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The reactor's alarm as a redstone level on its antenna booms (MOD-945): 0 normal, 7 warning, 15 emergency.
 *
 * <p>The level comes from three latches ({@link ReactorCore#alarmSignal}): the siren's warning latch, the
 * meltdown latch kept here, and the armed countdown. The meltdown line itself has no deadband, so without a latch
 * of its own the signal would flip 15/7 while the gauge wobbles at 85 %: it latches on at the meltdown line and lets
 * go only under the warning line. Only a sealed room has a meltdown line; a bare pile reaches 15 by its countdown.
 *
 * <p>The controller hands the level out as the LAST step of its tick, after the countdown, so the booms never lag a
 * tick behind the panel. An antenna that is not transmitting this tick gets 0 — no power, no link, no signal.
 */
public final class ReactorAlarmSignal {

	/** Save key of {@link #meltdownLatched}: additive, a save without it loads as "not latched". */
	private static final String MELTDOWN_KEY = "AlarmMeltdownLatched";

	private final Runnable changed;

	private boolean meltdownLatched;

	public ReactorAlarmSignal(Runnable changed) {
		this.changed = changed;
	}

	/**
	 * Settles the meltdown latch on this tick's {@code percent} and returns the level for the booms.
	 *
	 * @param meltdownLine whether this reactor has a meltdown line at all (a sealed room)
	 */
	public int level(int percent, boolean meltdownLine, boolean warned, boolean countdownArmed) {
		boolean latched = meltdownLine && ReactorCore.alarmStaysLatched(percent,
				ReactorConfig.reactorMeltdownStartPercent, ReactorConfig.reactorHeatWarnPercent, meltdownLatched);
		if (latched != meltdownLatched) {
			meltdownLatched = latched;
			changed.run();
		}
		return ReactorCore.alarmSignal(warned, meltdownLatched, countdownArmed);
	}

	/**
	 * Shows {@code signal} on the boom of every antenna in {@code antennas} — a silent antenna's boom shows 0 — and
	 * on every lamp pressed to the reactor ({@link ReactorLamps}).
	 */
	public static void broadcast(Level level, List<BlockPos> antennas, List<BlockPos> lamps, int signal) {
		ReactorLamps.show(level, lamps, signal);
		long now = level.getGameTime();
		for (BlockPos at : antennas) {
			if (level.getBlockEntity(at) instanceof ReactorAntennaBlockEntity antenna) {
				ReactorAntennaMastBlock.showAlarm(level, antenna.getBlockState(), at,
						antenna.isTransmitting(now) ? signal : ReactorCore.ALARM_SIGNAL_NONE);
			}
		}
	}

	public void save(ValueOutput output) {
		output.putBoolean(MELTDOWN_KEY, meltdownLatched);
	}

	public void load(ValueInput input) {
		meltdownLatched = input.getBooleanOr(MELTDOWN_KEY, false);
	}
}
