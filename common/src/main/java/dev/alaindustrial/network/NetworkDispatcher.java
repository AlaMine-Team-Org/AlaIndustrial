package dev.alaindustrial.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Platform-neutral packet-send entry point (MOD-022 Phase 3). The payload records + their
 * {@link net.minecraft.network.codec.StreamCodec}s already live in {@code common}; only the
 * <em>dispatch</em> differs per loader:
 * <ul>
 *   <li><b>Fabric</b> — {@code ServerPlayNetworking.send(player, payload)} (S2C) /
 *       {@code ClientPlayNetworking.send(payload)} (C2S).</li>
 *   <li><b>NeoForge</b> — {@code PacketDistributor.sendToPlayer(player, payload)} (S2C) /
 *       {@code ClientPacketDistributor.sendToServer(payload)} (C2S).</li>
 * </ul>
 * Common/content code (e.g. {@code NetworkAnalyzerItem}) sends through this interface instead of a
 * loader type, so the same class compiles and runs on both loaders.
 *
 * <p>Registration of the payload <em>type</em> (Fabric {@code PayloadTypeRegistry} /
 * NeoForge {@code RegisterPayloadHandlersEvent}) and the client-side <em>receiver</em> stay on their
 * respective loader side — those APIs have no neutral form. This interface covers only sending.
 *
 * <p>The active implementation is installed once at mod init by each loader's entrypoint via
 * {@link #install(NetworkDispatcher)}; common code reaches it through {@link #get()}. This mirrors
 * the set-once service-locator used for {@link dev.alaindustrial.core.energy.EnergyTransactions} and
 * {@link dev.alaindustrial.core.energy.EnergyLookup}.
 */
public interface NetworkDispatcher {

	/** Send a payload from the server to one client (S2C). */
	void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

	/**
	 * Send a payload from the client to the server (C2S). Callable only on the client thread; the
	 * loader impl targets the local player's connection. No caller uses this yet (the mod is S2C-only
	 * today) — it exists so a Phase-4 machine GUI can add a button/interaction packet without
	 * re-opening this seam.
	 */
	void sendToServer(CustomPacketPayload payload);

	// --- service locator (installed by the loader entrypoint) ---

	NetworkDispatcher[] INSTANCE = new NetworkDispatcher[1];

	/** Install the loader's implementation (called once from the loader entrypoint at mod init). */
	static void install(NetworkDispatcher impl) {
		INSTANCE[0] = impl;
	}

	/** The installed loader implementation. Throws if the entrypoint has not installed one yet. */
	static NetworkDispatcher get() {
		NetworkDispatcher impl = INSTANCE[0];
		if (impl == null) {
			throw new IllegalStateException(
					"NetworkDispatcher not installed — the loader entrypoint must call install() at init");
		}
		return impl;
	}
}
