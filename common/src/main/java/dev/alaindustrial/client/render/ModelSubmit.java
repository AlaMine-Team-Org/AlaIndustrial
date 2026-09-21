package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import org.jspecify.annotations.Nullable;

/**
 * Submits one model of a block entity together with the block-breaking overlay that belongs to it.
 *
 * <p>Until 26.2 both were one call: {@code submitModel} took a nullable {@code CrumblingOverlay} as its
 * last argument and drew the cracks itself. In 26.3 the overlay is a separate submission on a later
 * order — {@code collector.order(1).submitCrumblingOverlay(...)} — which is exactly what vanilla's own
 * {@code ChestRenderer} and {@code ShulkerBoxRenderer} now do. This mod has a dozen such call sites
 * across five renderers, so the pair is written once here rather than twelve times: a renderer that
 * forgot the second call would silently stop showing breaking cracks, and nothing in the build would
 * notice.
 *
 * <p>The render type the overlay is drawn with is derived the way vanilla derives it — from the sprite
 * and the model's own render type — so the cracks keep following the artwork they sit on.
 */
public final class ModelSubmit {

	private ModelSubmit() {
	}

	public static <S> void withCrumbling(SubmitNodeCollector collector, Model<S> model, S state,
			PoseStack poseStack, int lightCoords, int overlayCoords, int tintedColor, SpriteId sprite,
			SpriteGetter sprites, int outlineColor,
			ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
		collector.submitModel(model, state, poseStack, lightCoords, overlayCoords, tintedColor, sprite,
				sprites, outlineColor);
		if (crumbling != null) {
			collector.order(1).submitCrumblingOverlay(model, state, poseStack,
					sprite.renderType(model.renderType()), lightCoords, overlayCoords, tintedColor,
					crumbling);
		}
	}
}
