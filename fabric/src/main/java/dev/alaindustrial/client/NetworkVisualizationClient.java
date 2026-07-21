package dev.alaindustrial.client;

import dev.alaindustrial.network.NetworkAnalyzerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import dev.alaindustrial.client.render.NetworkOverlayRenderer;

/**
 * Fabric adapter for the loader-neutral Network Analyzer highlight (MOD-016, MOD-033): registers the
 * {@link NetworkAnalyzerPayload} receiver and the world-render hook, and delegates everything —
 * topology, geometry, animation — to the common {@link NetworkOverlayRenderer}.
 *
 * <p>The two seams are genuinely Fabric-only: {@link ClientPlayNetworking} for the S2C payload
 * (NeoForge registers its handler through {@code RegisterPayloadHandlersEvent}) and
 * {@link LevelRenderEvents#AFTER_TRANSLUCENT_FEATURES} for the render-time
 * {@code SubmitNodeCollector} (NeoForge exposes the same collector at the same frame point through
 * {@code SubmitCustomGeometryEvent}).
 */
public final class NetworkVisualizationClient {
	private NetworkVisualizationClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(NetworkAnalyzerPayload.TYPE,
				(payload, context) -> NetworkOverlayRenderer.updatePayload(payload));
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(NetworkVisualizationClient::render);
	}

	private static void render(LevelRenderContext context) {
		NetworkOverlayRenderer.submitFrame(context.submitNodeCollector(),
				context.levelState().cameraRenderState);
	}
}
