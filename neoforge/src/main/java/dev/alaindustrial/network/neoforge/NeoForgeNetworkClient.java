package dev.alaindustrial.network.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Client-dist receive seam for the Network Analyzer S2C payload on NeoForge (MOD-022 Phase 3). Runs
 * only on the client (invoked from the main-thread task in {@link NeoForgeNetwork#register}); it is
 * referenced solely from inside that handler lambda, so it is never linked on a dedicated server.
 *
 * <p>Stores the most recently received payload. The NeoForge world-render highlight that consumes it
 * — the counterpart to the Fabric {@code NetworkVisualizationClient} (which uses Fabric render events
 * with no neutral form) — is migrated in Phase 4; until then this seam only records the payload and
 * exposes it via {@link #latest()} so the channel is exercised end-to-end and the Phase-4 renderer
 * plugs straight in.
 */
public final class NeoForgeNetworkClient {

	@Nullable
	private static volatile NetworkAnalyzerPayload latest;

	private NeoForgeNetworkClient() {
	}

	/** Called on the client main thread when a {@link NetworkAnalyzerPayload} arrives. */
	public static void receive(NetworkAnalyzerPayload payload) {
		latest = payload;
		Industrialization.LOGGER.debug("NeoForge client received NetworkAnalyzerPayload ({} cables)",
				payload.cables().size());
	}

	/** The most recently received payload, or {@code null} if none has arrived. Phase-4 render entry point. */
	@Nullable
	public static NetworkAnalyzerPayload latest() {
		return latest;
	}
}
