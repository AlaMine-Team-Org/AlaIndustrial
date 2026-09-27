package dev.alaindustrial.core.energy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Read-only introspection view over an {@link EnergyNetwork} — the per-network telemetry that the
 * Network Analyzer (MOD-016 / MOD-047) and tests read. Extracted from {@code EnergyNetwork} so the
 * tick orchestrator + wake-state stays the only concern of the network class itself, and the
 * introspection surface (positions, supply/demand estimates, last-tick telemetry) lives separately.
 *
 * <p>Construct from any {@link EnergyNetwork} and call the accessors; everything is computed live
 * against the network's current state, so the view picks up topology changes the moment they happen.
 *
 * <p>The {@link #producerSupplyEstimate()} and {@link #consumerDemandEstimate()} dry-run helpers
 * live here rather than on the network so a per-frame renderer does not pull a Class-coupling chain
 * through {@code MachineBlockEntity} / {@code EnergyLookup} from the network's hot path (those
 * classes are still referenced here, but only when diagnostics is queried).
 */
public final class EnergyNetworkDiagnostics {
	private final EnergyNetwork network;

	public EnergyNetworkDiagnostics(EnergyNetwork network) {
		this.network = network;
	}

	/** EU actually delivered by the network's most recent tick (0 if never ticked or asleep). */
	public long lastTickMoved() {
		return network.lastTickMoved();
	}

	/** The part of {@link #lastTickMoved()} that went into storage sinks (MOD-665). */
	public long lastTickToStorage() {
		return network.lastTickToStorage();
	}

	/** EU storage sources discharged into the line on the last tick (MOD-665). */
	public long lastTickFromStorage() {
		return network.lastTickFromStorage();
	}

	/** Game time of the network's last tick, {@link Long#MIN_VALUE} if it never ticked (MOD-665). */
	public long lastTickAt() {
		return network.lastTickAt();
	}

	/**
	 * For every cable, the faces through which the network hands EU on to a neighbouring cable, by the
	 * distribution kernel's own rule (MOD-665): downhill on the flow potential — toward the nearest demand,
	 * or outward from the producers while nothing waits — and, into a cable the downhill rule cannot reach,
	 * outward along the distance from the supplying producers (MOD-318). Two neighbours at equal potential
	 * exchange nothing, so no face points either way across that seam. Bit {@code 1 << Direction.ordinal()}.
	 *
	 * <p>This is the direction the next tick moves energy in, read from the field the last tick left; it
	 * says nothing about how much. Cables absent from the map hand nothing on.
	 */
	public Map<BlockPos, Integer> cableFlowFaces() {
		Set<BlockPos> cables = network.cables();
		Set<BlockPos> stranded = new LinkedHashSet<>(network.strandedCables());
		Map<BlockPos, Integer> out = new LinkedHashMap<>();
		for (BlockPos cable : cables) {
			Integer potential = network.flowPotentialAt(cable);
			Integer fromSource = network.producerDistanceAt(cable);
			int mask = 0;
			for (Direction dir : Direction.values()) {
				BlockPos next = cable.relative(dir);
				if (!cables.contains(next)) {
					continue;
				}
				boolean flows;
				if (stranded.contains(next)) {
					Integer nextFromSource = network.producerDistanceAt(next);
					flows = fromSource != null && nextFromSource != null && fromSource < nextFromSource;
				} else {
					Integer nextPotential = network.flowPotentialAt(next);
					flows = potential != null && nextPotential != null && potential > nextPotential;
				}
				if (flows) {
					mask |= 1 << dir.ordinal();
				}
			}
			if (mask != 0) {
				out.put(cable, mask);
			}
		}
		return out;
	}

	/**
	 * Producers — generators and stores alike — that put EU into a cable on the network's last tick
	 * (MOD-665). Empty when that tick is also when the network last ran; callers check freshness.
	 */
	public Set<BlockPos> lastTickFed() {
		return network.lastTickFed();
	}

	/** Positions of the network's producer endpoints (read-only introspection, e.g. MOD-016). */
	public List<BlockPos> producerPositions() {
		return network.producerPositions();
	}

	/** Positions of the network's consumer endpoints (read-only introspection, e.g. MOD-016). */
	public List<BlockPos> consumerPositions() {
		return network.consumerPositions();
	}

	/**
	 * Dry-run sum of what producers could extract this instant (no commit) — the network's potential
	 * supply, not what actually moves once consumer demand and the tier packet cap are applied. For
	 * diagnostics only.
	 */
	public long producerSupplyEstimate() {
		return network.producerSupplyEstimate();
	}

	/**
	 * Dry-run sum of what consumers could accept this instant (no commit) — the network's potential
	 * demand. For diagnostics only.
	 */
	public long consumerDemandEstimate() {
		return network.consumerDemandEstimate();
	}
}
