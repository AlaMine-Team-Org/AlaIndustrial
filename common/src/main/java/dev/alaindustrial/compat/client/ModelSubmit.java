package dev.alaindustrial.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Submits one model of a block entity together with the block-breaking overlay that belongs to it — a
 * version facade (MOD-703, ADR-036): every Minecraft line has a twin of this class with the same
 * signature, and only the body differs. Five renderers call it at a dozen sites, so a renderer's source
 * is the same on every line and the cracks cannot be forgotten at one of them.
 *
 * <p><b>This twin: Minecraft 26.3.</b> Until 26.2 the pair was one call: {@code submitModel} took a
 * nullable {@code CrumblingOverlay} as its last argument and drew the cracks itself. In 26.3 the overlay
 * is a separate submission on a later order — {@code collector.order(1).submitCrumblingOverlay(...)} —
 * which is exactly what vanilla's own {@code ChestRenderer} and {@code ShulkerBoxRenderer} now do. A
 * renderer that forgot the second call would silently stop showing breaking cracks, and nothing in the
 * build would notice.
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

	/**
	 * Submits a model textured straight from {@code texture} (not an atlas sprite), with no breaking overlay
	 * — an entity part such as the boat's water patch (MOD-785). On 26.3 that is the seven-argument
	 * {@code submitModel}; 26.2 has the same call with a trailing {@code CrumblingOverlay}, here {@code null}.
	 */
	public static <S> void textured(SubmitNodeCollector collector, Model<? super S> model, S state,
			PoseStack poseStack, Identifier texture, int lightCoords, int overlayCoords, int outlineColor) {
		collector.submitModel(model, state, poseStack, texture, lightCoords, overlayCoords, outlineColor);
	}
}
