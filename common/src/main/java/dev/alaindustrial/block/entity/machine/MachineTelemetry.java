package dev.alaindustrial.block.entity.machine;

import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyRole;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The statistics a machine keeps about itself (MOD-125): working time, the EU rate it reports, and the
 * operations it completed — with the EU totals, which live in the {@link EnergyBuffer} because every
 * transfer already passes through it, saved and loaded beside them under the {@code Stats*} keys.
 *
 * <p>One component of the machine base (MOD-712, BE-1): the base holds one and delegates to it, so a
 * call site keeps saying {@code machine.activeTicks()}. Whether anything is measured at all is the
 * buffer's counter switch, which the base sets from the statistics chip before every tick (MOD-692).
 */
public final class MachineTelemetry {

	private final EnergyBuffer energy;

	/**
	 * Ticks this block has actually WORKED — produced or consumed EU — not ticks since it was placed.
	 *
	 * <p>The distinction is the whole meaning of the readout. A furnace sitting unpowered in a corner for
	 * a week has an age of a week and a working time of zero, and reporting the age under the label
	 * "working time" is simply a false number: it climbs on a machine that has never run once.
	 *
	 * <p>Accumulated per tick, which is safe precisely BECAUSE of the sleep gate rather than in spite of
	 * it: a block skips its tick only while idle, and an idle tick is one this counter must not count
	 * anyway. (An age-since-placed counter is the opposite case and could NOT be accumulated this way.)
	 */
	private long activeTicks;

	/** Last per-tick EU rate this block reported. Session state: never persisted, 0 after a reload. */
	private int currentEuRate;

	/** Highest {@link #currentEuRate} seen since the block entity loaded. Session state, deliberately. */
	private int peakEuRate;

	/**
	 * Completed operations over this block's lifetime. Counted and persisted from the start (MOD-125) even
	 * though the MVP panel does not draw it: the machines task that will show it then needs no save
	 * migration, and a counter that starts at zero on every existing machine would be worse than useless.
	 */
	private long totalItemsProcessed;

	public MachineTelemetry(EnergyBuffer energy) {
		this.energy = energy;
	}

	/**
	 * Record this tick's EU rate — production for a generator, draw for a consumer.
	 *
	 * <p>A non-zero rate is also this block's definition of "working", so the working-time counter is
	 * advanced from the same call: the two answers can never disagree about whether the machine ran.
	 *
	 * <p>Gated on the same switch as the energy counters in the buffer, so "measuring starts when you
	 * install the sensor" holds for every number the panel can show, and the rate and the totals can never
	 * disagree about whether this block measures.
	 */
	public void recordEuRate(int euPerTick) {
		if (!energy.countersEnabled()) {
			currentEuRate = 0;
			return;
		}
		int rate = Math.max(0, euPerTick);
		currentEuRate = rate;
		if (rate > peakEuRate) {
			peakEuRate = rate;
		}
		if (rate > 0) {
			activeTicks++;
		}
	}

	public long activeTicks() {
		return activeTicks;
	}

	public int currentEuRate() {
		return currentEuRate;
	}

	public int peakEuRate() {
		return peakEuRate;
	}

	public long totalItemsProcessed() {
		return totalItemsProcessed;
	}

	/** Count one finished operation. */
	public void recordItemProcessed() {
		totalItemsProcessed++;
	}

	/** Write the counters under their {@code Stats*} keys, in the order a save has always had them. */
	public void save(ValueOutput output) {
		output.putLong("StatsActiveTicks", activeTicks);
		output.putLong("StatsEnergyIn", energy.getTotalEnergyIn());
		output.putLong("StatsEnergyOut", energy.getTotalEnergyOut());
		output.putLong("StatsEnergyGenerated", energy.getTotalEnergyGenerated());
		output.putLong("StatsEnergyConsumed", energy.getTotalEnergyConsumed());
		output.putLong("StatsItemsProcessed", totalItemsProcessed);
	}

	/** Read what {@link #save} wrote; a missing key reads as 0 (a save from before MOD-125). */
	public void load(ValueInput input) {
		activeTicks = input.getLongOr("StatsActiveTicks", 0L);
		energy.restoreCounters(
				input.getLongOr("StatsEnergyIn", 0L),
				input.getLongOr("StatsEnergyOut", 0L),
				input.getLongOr("StatsEnergyGenerated", 0L),
				input.getLongOr("StatsEnergyConsumed", 0L));
		totalItemsProcessed = input.getLongOr("StatsItemsProcessed", 0L);
	}

	/**
	 * How many of the six direct faces of {@code machine} currently sit against a usable energy port,
	 * split into sources (something that could feed it) and sinks (something it could feed).
	 *
	 * <p>Six lookups, run only when a statistics packet is actually being built — not per tick, and never
	 * a walk of the cable graph. A cable neighbour counts as one connection, not as everything behind it:
	 * answering "what is on the other end of the wire" is the network analyzer's job, not this panel's.
	 *
	 * @return sources in the low 16 bits, sinks in the next 16 — packed because the pair travels together
	 *     and a two-field return would need a record no other caller wants
	 */
	public static int countDirectConnections(EnergyBlockEntity machine) {
		Level level = machine.getLevel();
		if (level == null) {
			return 0;
		}
		int sources = 0;
		int sinks = 0;
		EnergyLookup lookup = EnergyLookup.get();
		for (Direction dir : Direction.values()) {
			EnergyRole role = machine.energyRoleForFace(dir);
			if (role == EnergyRole.NONE) {
				continue;
			}
			EnergyPort port = lookup.find(level, machine.getBlockPos().relative(dir), dir.getOpposite());
			if (port == null) {
				continue;
			}
			// Mirror the direct-push rules (R-NRG-03): a face that cannot extract has no sink behind it
			// even when the neighbour would accept energy, and vice versa.
			if (role.canInsert() && port.supportsExtraction()) {
				sources++;
			}
			if (role.canExtract() && port.supportsInsertion()) {
				sinks++;
			}
		}
		return (sources & 0xFFFF) | ((sinks & 0xFFFF) << 16);
	}
}
