package dev.alaindustrial.client;

import dev.alaindustrial.client.render.ConcentratorSchematicRenderer;
import dev.alaindustrial.client.render.RepellerDomeRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import dev.alaindustrial.client.render.NetworkOverlayRenderer;

/**
 * Fabric adapter for the loader-neutral Network Analyzer highlight (MOD-016, MOD-033): registers the
 * world-render hook and delegates everything — topology, geometry, animation — to the common
 * {@link NetworkOverlayRenderer}.
 *
 * <p>The seam is genuinely Fabric-only: {@link LevelRenderEvents#AFTER_TRANSLUCENT_FEATURES} for the
 * render-time {@code SubmitNodeCollector} (NeoForge exposes the same collector at the same frame point
 * through {@code SubmitCustomGeometryEvent}). The payloads that feed the overlays arrive through the
 * shared {@code ClientPayloadManifest}, replayed by {@code IndustrializationClient} (MOD-706).
 */
public final class NetworkVisualizationClient {
	private NetworkVisualizationClient() {
	}

	public static void init() {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(NetworkVisualizationClient::render);
	}

	private static void render(LevelRenderContext context) {
		NetworkOverlayRenderer.submitFrame(context.submitNodeCollector(),
				context.levelState().cameraRenderState);
		RepellerDomeRenderer.submitFrame(context.submitNodeCollector(),
				context.levelState().cameraRenderState);

		// MOD-603: the concentrator's assembly schematic rides the same frame point — it is a
		// per-frame read of the world in front of the player, with no state of its own.
		ConcentratorSchematicRenderer.submitFrame(context.submitNodeCollector(),
				context.levelState().cameraRenderState);
		// MOD-764: the wrench's x-ray of piezo plates rides the same frame point.
		dev.alaindustrial.client.render.PiezoPlateXray.submitFrame(context.submitNodeCollector(),
				context.levelState().cameraRenderState);
	}
}
