package dev.alaindustrial.command.demo.energy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;

/**
 * What a built bay measures (MOD-799): its probes with their world positions and reset values, its cables and
 * the gap cells the owner may fill. Produced by {@link BayWriter} while the bay is built — or re-derived in dry
 * mode by {@code check}.
 */
record BayPlan(Bay bay, List<Probe> probes, List<BlockPos> cables, Map<String, BlockPos> gaps) {

	/**
	 * One measured block.
	 *
	 * @param rate   for a {@link ProbeKind#SOURCE}: its output, from the declared needs of the bay's machines
	 * @param chips  for a {@link ProbeKind#MACHINE}: Overclocker Chips I to fit
	 * @param charge for a {@link ProbeKind#STORE}: the charge it is reset to
	 */
	record Probe(String name, ProbeKind kind, BlockPos pos, ToIntFunction<Map<String, Long>> rate, int chips,
			long charge) {
		static Probe of(String name, ProbeKind kind, BlockPos pos) {
			return new Probe(name, kind, pos, null, 0, 0L);
		}

		static Probe source(String name, BlockPos pos, ToIntFunction<Map<String, Long>> rate) {
			return new Probe(name, ProbeKind.SOURCE, pos, rate, 0, 0L);
		}

		static Probe machine(String name, BlockPos pos, int chips) {
			return new Probe(name, ProbeKind.MACHINE, pos, null, chips, 0L);
		}

		static Probe store(String name, BlockPos pos, long charge) {
			return new Probe(name, ProbeKind.STORE, pos, null, 0, charge);
		}
	}

	static final class Builder {
		private final Bay bay;
		private final List<Probe> probes = new ArrayList<>();
		private final List<BlockPos> cables = new ArrayList<>();
		private final Map<String, BlockPos> gaps = new LinkedHashMap<>();

		Builder(Bay bay) {
			this.bay = bay;
		}

		void probe(Probe probe) {
			for (Probe p : probes) {
				if (p.name().equals(probe.name())) {
					throw new IllegalStateException("bay " + bay.id() + " declares probe " + probe.name() + " twice");
				}
			}
			probes.add(probe);
		}

		void cable(BlockPos pos) {
			cables.add(pos);
		}

		void gap(String flag, BlockPos pos) {
			gaps.put(flag, pos);
		}

		BayPlan build() {
			return new BayPlan(bay, List.copyOf(probes), List.copyOf(cables), Map.copyOf(gaps));
		}
	}
}
