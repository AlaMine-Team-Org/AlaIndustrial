package dev.alaindustrial.command.demo.energy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything one bay of the energy stand did over the check window (MOD-799): one reading per probe, plus what
 * the bay's cables gained, how often its network was awake and the switches the owner can flip in the world (a
 * gap filled or not). Minecraft-free, like {@link ProbeReading}.
 *
 * @param bay        the bay id ({@code "E01"} …)
 * @param ticks      window length
 * @param probes     the readings by probe name, in the order the bay declared them
 * @param cableDelta change of the summed charge of every cable of the bay over the window
 * @param cables     number of cables in the bay
 * @param awakeTicks ticks of the window on which the bay's first network was awake
 * @param flags      named yes/no facts read from the world at the end of the window
 */
public record BayReading(String bay, int ticks, Map<String, ProbeReading> probes, long cableDelta, int cables,
		int awakeTicks, Map<String, Boolean> flags) {

	public BayReading {
		probes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(probes));
		flags = Map.copyOf(flags);
	}

	/** The probe named {@code name}; a bay that asks for a probe it never declared is a bug of the bay. */
	public ProbeReading probe(String name) {
		ProbeReading reading = probes.get(name);
		if (reading == null) {
			throw new IllegalArgumentException("bay " + bay + " has no probe " + name);
		}
		return reading;
	}

	/** A world flag; absent means false. */
	public boolean flag(String name) {
		return flags.getOrDefault(name, false);
	}

	/**
	 * The energy balance of the bay: what the sources gave minus what every consumer and store kept and what
	 * the cables gained — the resistive loss when every number is real, {@code null} when a probe ran without
	 * counters or a real generator's output is not known.
	 */
	public Long loss() {
		long in = 0;
		long kept = cableDelta;
		for (ProbeReading p : probes.values()) {
			switch (p.kind()) {
				case SOURCE -> in += p.out();
				case GENERATOR -> {
					return null;
				}
				case STORE -> kept += p.delta();
				case MACHINE -> {
					if (!p.counters()) {
						return null;
					}
					kept += p.received();
				}
				// A device with an unknown need and no upgrade panel has no counters: its charge is what it kept.
				case UNKNOWN -> kept += p.counters() ? p.received() : p.delta();
			}
		}
		return in - kept;
	}

	/** EU the creative sources of the bay gave in the window. */
	public long sourceOut() {
		long sum = 0;
		for (ProbeReading p : probes.values()) {
			if (p.kind() == ProbeKind.SOURCE) {
				sum += p.out();
			}
		}
		return sum;
	}
}
