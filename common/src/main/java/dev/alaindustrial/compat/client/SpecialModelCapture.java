package dev.alaindustrial.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * The model a block-entity renderer draws for a block that has no block model of its own — a chest, a skull, a
 * banner — taken apart so the caller can draw it again into a buffer of its own render type (MOD-824, owner's round
 * 1, R1-3: the town hall's schematic showed a chest as a box of oak planks). A version facade (ADR-036): every
 * Minecraft line has a twin of this class with the same signatures, and only the body differs, because the submit
 * collector interface and the block model set it reads are the line's own.
 *
 * <p><b>This twin: Minecraft 26.3.</b> The block model set ({@code ModelManager.getBlockModelSet()}) answers such a
 * block with a {@code SpecialBlockModelWrapper}: {@code update} puts the block's special renderer (for a chest
 * {@code ChestSpecialRenderer}, its half chosen by the chest type, its facing in the model transformation) into a
 * {@link BlockModelRenderState}, and {@code submit} calls that renderer with a collector. Here the collector only
 * records: each model the renderer submits through an atlas sprite (the chest's {@code SpriteId} overload) or a
 * plain texture becomes a {@link Part} — the model, its state, the pose it was submitted at and the sprite that maps
 * its UVs. A renderer that submits a bare {@code RenderType} gives no texture the caller could draw from, so such a
 * block is answered with no parts at all, never half a model. NeoForge's collector extension adds only default
 * methods, so this collector satisfies both loaders.
 */
public final class SpecialModelCapture {

	private SpecialModelCapture() {
	}

	/**
	 * The parts of {@code state}'s special model, posed for the unit block at the origin, or an empty list when the
	 * block has none, or one this facade cannot redraw.
	 */
	public static List<Part> capture(BlockState state) {
		BlockModelRenderState renderState = new BlockModelRenderState();
		Minecraft.getInstance().getModelManager().getBlockModelSet().get(state)
				.update(renderState, state, BlockDisplayContext.create(), 42L);
		Recorder recorder = new Recorder();
		renderState.submit(new PoseStack(), recorder, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		return recorder.unsupported ? List.of() : List.copyOf(recorder.parts);
	}

	/** One model a special renderer submitted, ready to be drawn again from the texture it names. */
	public static final class Part {

		private final Identifier texture;
		private final Model<?> model;
		private final Runnable animate;
		private final Matrix4f pose;
		@Nullable
		private final UvMapping uv;
		private final PoseStack scratch = new PoseStack();

		private Part(Identifier texture, Model<?> model, Runnable animate, Matrix4f pose, @Nullable UvMapping uv) {
			this.texture = texture;
			this.model = model;
			this.animate = animate;
			this.pose = pose;
			this.uv = uv;
		}

		/** The texture (an atlas, or a plain image) the part's UVs point into. */
		public Identifier texture() {
			return texture;
		}

		/**
		 * Writes the part into {@code consumer}, a buffer of a render type that samples {@link #texture()}, with the
		 * unit block at {@code at}. Render thread only: the model is the renderer's shared instance, posed here.
		 */
		public void draw(PoseStack.Pose at, VertexConsumer consumer, int lightCoords, int overlayCoords, int colour) {
			scratch.last().set(at);
			scratch.last().mulPose(pose);
			animate.run();
			model.renderToBuffer(scratch, uv == null ? consumer : uv.wrap(consumer), lightCoords, overlayCoords,
					colour);
		}
	}

	/** A collector that keeps the textured model submissions and nothing else. */
	private static final class Recorder implements SubmitNodeCollector {

		final List<Part> parts = new ArrayList<>();
		boolean unsupported;

		private <S> void keep(Identifier texture, Model<? super S> model, S state, PoseStack poseStack,
				@Nullable UvMapping uv) {
			parts.add(new Part(texture, model, () -> model.setupAnim(state), new Matrix4f(poseStack.last().pose()),
					uv));
		}

		@Override
		public <S> void submitModel(Model<S> model, S state, PoseStack poseStack, int lightCoords, int overlayCoords,
				int tintedColor, SpriteId sprite, SpriteGetter sprites, int outlineColor) {
			keep(sprite.atlasLocation(), model, state, poseStack, sprites.get(sprite));
		}

		@Override
		public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, Identifier texture,
				int lightCoords, int overlayCoords, int outlineColor) {
			keep(texture, model, state, poseStack, null);
		}

		@Override
		public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
				int lightCoords, int overlayCoords, int tintedColor, @Nullable UvMapping uv, int outlineColor) {
			unsupported = true;
		}

		@Override
		public OrderedSubmitNodeCollector order(int order) {
			return this;
		}

		@Override
		public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
		}

		@Override
		public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name,
				boolean seeThrough, int lightCoords, CameraRenderState camera) {
		}

		@Override
		public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean dropShadow,
				Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor, int outlineColor) {
		}

		@Override
		public void submitTextBackground(PoseStack poseStack, float x0, float y0, float x1, float y1, int color,
				Font.DisplayMode displayMode, int lightCoords) {
		}

		@Override
		public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
		}

		@Override
		public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
		}

		@Override
		public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack,
				RenderType renderType, int lightCoords, int overlayCoords, int tintedColor,
				ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		}

		@Override
		public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState,
				int lightCoords) {
		}

		@Override
		public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> modelParts,
				int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
		}

		@Override
		public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> modelParts, int progress,
				boolean flag) {
		}

		@Override
		public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color,
				float width, boolean flag) {
		}

		@Override
		public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords,
				int overlayCoords, int outlineColor, int[] tintLayers, ItemQuads quads,
				ItemStackRenderState.FoilType foilType) {
		}

		@Override
		public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
				SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
		}

		@Override
		public void submitQuadParticleGroup(QuadParticleRenderState particleGroup) {
		}

		@Override
		public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera,
				boolean flag) {
		}
	}
}
