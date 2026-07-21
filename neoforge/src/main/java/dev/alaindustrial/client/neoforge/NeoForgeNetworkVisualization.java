package dev.alaindustrial.client.neoforge;

import dev.alaindustrial.client.render.NetworkOverlayRenderer;
import dev.alaindustrial.network.neoforge.NeoForgeNetworkClient;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * NeoForge adapter for the loader-neutral Network Analyzer highlight (MOD-016, MOD-033): forwards the
 * render-time collector from {@link SubmitCustomGeometryEvent} to the common
 * {@link NetworkOverlayRenderer}, giving NeoForge the exact same per-frame tube/joint/flow-spark
 * geometry as Fabric.
 *
 * <p>History: the first NeoForge port (MOD-022) approximated the overlay with vanilla per-tick
 * gizmos ({@code Minecraft#collectPerTickGizmos()} + {@code Gizmos.line/cuboid/point}) because
 * {@code RenderLevelStageEvent} exposes no {@code SubmitNodeCollector}. NeoForge 26.2 does expose
 * one through {@link SubmitCustomGeometryEvent}, which fires in {@code LevelRenderer.submitFeatures}
 * right before vanilla submits its own gizmo primitives — the same frame point as the Fabric
 * {@code LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES} hook — so the per-tick approximation (20 Hz
 * motion, pixel-sized flow points) is gone entirely (MOD-033, MOD-060).
 *
 * <p>Unlike Fabric, where the payload receiver pushes into the renderer, the NeoForge receive seam
 * ({@link NeoForgeNetworkClient}) just stores the latest payload — so this adapter polls it each
 * frame; {@link NetworkOverlayRenderer#updatePayload} compares by reference and recomputes topology
 * only when the payload actually changed.
 */
public final class NeoForgeNetworkVisualization {

	private NeoForgeNetworkVisualization() {
	}

	/** Game-bus listener: submit this frame's overlay geometry. Registered in
	 * {@code IndustrializationNeoForgeClient}. */
	public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
		NetworkOverlayRenderer.updatePayload(NeoForgeNetworkClient.latest());
		NetworkOverlayRenderer.submitFrame(event.getSubmitNodeCollector(),
				event.getLevelRenderState().cameraRenderState);
	}
}
