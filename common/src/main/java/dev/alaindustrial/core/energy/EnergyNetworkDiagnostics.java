package dev.alaindustrial.core.energy;

import java.util.List;
import net.minecraft.core.BlockPos;

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
