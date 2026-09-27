package dev.alaindustrial.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;

/**
 * Hands a per-frame gizmo to vanilla's own gizmo collection — the path the game's debug boxes take.
 *
 * <p><b>Why not {@code DrawableGizmoPrimitives.submit(collector, …)}.</b> On 26.3 a primitive list
 * submitted straight into the frame's {@code SubmitNodeCollector} from a mod render hook never reaches
 * the screen: the Fabric hooks the overlay used fire in the execute stage, after the frame's submits
 * were prepared, and even a submit at {@code BEFORE_GIZMOS} did not draw. 26.2 drew the same call.
 * The network analyzer's trace was invisible on 26.3 from 0.1.181 on, and a client-gametest frame
 * (NetworkOverlayStand) proved it: an identical frame with and without the trace, while the same
 * geometry routed through {@link Gizmos} inside {@code collectPerFrameRenderThreadGizmos()} shows
 * (MOD-665).
 *
 * <p>The collection is drained by vanilla's {@code finalizeGizmoCollection} once per frame, so a gizmo
 * added here is drawn at most one frame later and is gone after that — nothing accumulates. Both
 * loaders call it from their existing frame hook; the API is the same on 26.2.
 */
public final class FrameGizmos {
	private FrameGizmos() {
	}

	/** Add {@code gizmo} to this frame's render-thread gizmos; {@code alwaysOnTop} draws it through walls. */
	public static void emit(Gizmo gizmo, boolean alwaysOnTop) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.levelRenderer == null) {
			return;
		}
		try (Gizmos.TemporaryCollection ignored = minecraft.levelRenderer.collectPerFrameRenderThreadGizmos()) {
			GizmoProperties properties = Gizmos.addGizmo(gizmo);
			if (alwaysOnTop) {
				properties.setAlwaysOnTop();
			}
		}
	}
}
