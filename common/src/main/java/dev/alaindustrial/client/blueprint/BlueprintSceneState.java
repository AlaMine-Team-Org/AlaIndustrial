package dev.alaindustrial.client.blueprint;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

/**
 * One frame of a blueprint scene, handed from the screen to {@link BlueprintSceneRenderer} through the
 * GUI's picture-in-picture queue (MOD-797). A record on purpose: NeoForge pools renderers and matches a
 * state to its renderer by {@code equals}.
 *
 * @param def    the blueprint
 * @param step   0-based step shown
 * @param reveal how far the step has played, 0..1 — new blocks are drawn as an outline at first
 * @param camera the camera angle
 * @param scale  GUI pixels per block
 */
public record BlueprintSceneState(BlueprintDef def, int step, float reveal, BlueprintCamera camera, float scale,
		int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
		implements PictureInPictureRenderState {

	/** A frame filling the rectangle ({@code x0}, {@code y0})–({@code x1}, {@code y1}) of the screen. */
	public static BlueprintSceneState of(BlueprintDef def, int step, float reveal, BlueprintCamera camera,
			float scale, int x0, int y0, int x1, int y1) {
		ScreenRectangle area = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
		return new BlueprintSceneState(def, step, reveal, camera, scale, x0, y0, x1, y1, area,
				PictureInPictureRenderState.getBounds(x0, y0, x1, y1, area));
	}
}
