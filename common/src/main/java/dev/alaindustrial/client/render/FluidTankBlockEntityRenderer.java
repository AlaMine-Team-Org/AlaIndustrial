package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runtime fluid prism inside the portable tank (MOD-111).
 *
 * <p>Minecraft 26.2 bakes the active loader's fluid registrations into
 * {@code ModelManager#getFluidStateModelSet}. Reading that model here gives vanilla and modded fluids
 * their own still sprite and tint without hardcoding water/lava or importing either loader API.
 */
public final class FluidTankBlockEntityRenderer
		implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankBlockEntityRenderer.State> {
	private static final float MIN = 3.0625F / 16.0F;
	private static final float MAX = 12.9375F / 16.0F;
	private static final float BOTTOM = 3.25F / 16.0F;
	private static final float MAX_HEIGHT = 9.5F / 16.0F;
	private static final float GLASS_MIN = 3.0F / 16.0F;
	private static final float GLASS_MAX = 13.0F / 16.0F;
	private static final SpriteId GLASS_SPRITE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("fluid_tank_glass"));
	private static final RenderType GLASS_RENDER_TYPE =
			GLASS_SPRITE.renderType(ignored -> Sheets.translucentBlockItemSheet());

	private final SpriteGetter sprites;

	public FluidTankBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FluidTankBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		Level level = entity.getLevel();
		state.glassLight = level == null
				? LightCoordsUtil.FULL_BRIGHT
				: LightCoordsUtil.getLightCoords(level, entity.getBlockPos());
		state.visible = entity.fluidTank.amount > 0 && !entity.fluidTank.fluid.isEmpty();
		if (!state.visible) {
			return;
		}
		state.height = Math.max(1.0F / 64.0F,
				Math.min(1.0F, (float) entity.fluidTank.amount / entity.fluidTank.capacity) * MAX_HEIGHT);
		Fluid fluid = entity.fluidTank.fluid.fluid();
		BlockState legacy = fluid.defaultFluidState().createLegacyBlock();
		FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
				.get(fluid.defaultFluidState());
		state.sprite = model.stillMaterial().sprite();
		var tintSource = model.tintSource();
		int tint = tintSource == null
				? -1
				: level instanceof BlockAndTintGetter tintGetter
						? tintSource.colorInWorld(legacy, tintGetter, entity.getBlockPos())
						: tintSource.color(legacy);
		// Custom geometry uses a translucent sheet; keep the registered RGB but supply a stable alpha.
		state.color = (tint & 0x00FFFFFF) | (fluid == Fluids.LAVA ? 0xF0000000 : 0xD8000000);
		state.light = level == null || fluid == Fluids.LAVA
				? LightCoordsUtil.FULL_BRIGHT
				: LightCoordsUtil.getLightCoords(level, entity.getBlockPos());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.visible && state.sprite != null) {
			float top = BOTTOM + state.height;
			collector.submitCustomGeometry(poseStack, Sheets.translucentBlockItemSheet(),
					(pose, consumer) -> renderPrism(pose, consumer, state.sprite, state.color, state.light, top));
		}
		TextureAtlasSprite glass = sprites.get(GLASS_SPRITE);
		collector.submitCustomGeometry(poseStack, GLASS_RENDER_TYPE,
				(pose, consumer) -> renderGlass(pose, consumer, glass, state.glassLight));
	}

	private static void renderGlass(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite, int light) {
		renderGlassFace(pose, out, sprite, light, 0, 0, -1,
				GLASS_MIN, GLASS_MIN, GLASS_MIN, GLASS_MAX, GLASS_MIN, GLASS_MIN,
				GLASS_MAX, GLASS_MAX, GLASS_MIN, GLASS_MIN, GLASS_MAX, GLASS_MIN);
		renderGlassFace(pose, out, sprite, light, 0, 0, 1,
				GLASS_MIN, GLASS_MAX, GLASS_MAX, GLASS_MAX, GLASS_MAX, GLASS_MAX,
				GLASS_MAX, GLASS_MIN, GLASS_MAX, GLASS_MIN, GLASS_MIN, GLASS_MAX);
		renderGlassFace(pose, out, sprite, light, -1, 0, 0,
				GLASS_MIN, GLASS_MIN, GLASS_MAX, GLASS_MIN, GLASS_MIN, GLASS_MIN,
				GLASS_MIN, GLASS_MAX, GLASS_MIN, GLASS_MIN, GLASS_MAX, GLASS_MAX);
		renderGlassFace(pose, out, sprite, light, 1, 0, 0,
				GLASS_MAX, GLASS_MAX, GLASS_MAX, GLASS_MAX, GLASS_MAX, GLASS_MIN,
				GLASS_MAX, GLASS_MIN, GLASS_MIN, GLASS_MAX, GLASS_MIN, GLASS_MAX);
	}

	private static void renderGlassFace(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
			int light, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		quad(pose, out, sprite, -1, light, nx, ny, nz,
				ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz,
				0, 1, 1, 1, 1, 0, 0, 0);
		quad(pose, out, sprite, -1, light, -nx, -ny, -nz,
				dx, dy, dz, cx, cy, cz, bx, by, bz, ax, ay, az,
				0, 0, 1, 0, 1, 1, 0, 1);
	}

	private static void renderPrism(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
			int color, int light, float top) {
		float sideV = Math.max(0.0F, 1.0F - (top - BOTTOM));
		quad(pose, out, sprite, color, light, 0, 0, -1,
				MIN, BOTTOM, MIN, MAX, BOTTOM, MIN, MAX, top, MIN, MIN, top, MIN,
				0, 1, 1, 1, 1, sideV, 0, sideV);
		quad(pose, out, sprite, color, light, 0, 0, 1,
				MIN, top, MAX, MAX, top, MAX, MAX, BOTTOM, MAX, MIN, BOTTOM, MAX,
				0, sideV, 1, sideV, 1, 1, 0, 1);
		quad(pose, out, sprite, color, light, -1, 0, 0,
				MIN, BOTTOM, MAX, MIN, BOTTOM, MIN, MIN, top, MIN, MIN, top, MAX,
				0, 1, 1, 1, 1, sideV, 0, sideV);
		quad(pose, out, sprite, color, light, 1, 0, 0,
				MAX, top, MAX, MAX, top, MIN, MAX, BOTTOM, MIN, MAX, BOTTOM, MAX,
				0, sideV, 1, sideV, 1, 1, 0, 1);
		// The translucent sheet culls back faces. Submit the four walls in reverse winding as well,
		// otherwise the wall facing the camera disappears from one side of the tank.
		quad(pose, out, sprite, color, light, 0, 0, 1,
				MIN, top, MIN, MAX, top, MIN, MAX, BOTTOM, MIN, MIN, BOTTOM, MIN,
				0, sideV, 1, sideV, 1, 1, 0, 1);
		quad(pose, out, sprite, color, light, 0, 0, -1,
				MIN, BOTTOM, MAX, MAX, BOTTOM, MAX, MAX, top, MAX, MIN, top, MAX,
				0, 1, 1, 1, 1, sideV, 0, sideV);
		quad(pose, out, sprite, color, light, 1, 0, 0,
				MIN, top, MAX, MIN, top, MIN, MIN, BOTTOM, MIN, MIN, BOTTOM, MAX,
				0, sideV, 1, sideV, 1, 1, 0, 1);
		quad(pose, out, sprite, color, light, -1, 0, 0,
				MAX, BOTTOM, MAX, MAX, BOTTOM, MIN, MAX, top, MIN, MAX, top, MAX,
				0, 1, 1, 1, 1, sideV, 0, sideV);
		// Keep both windings for the horizontal surface too. The first quad is visible from
		// inside/below; the second is the actual upward-facing surface seen through the tank lid.
		quad(pose, out, sprite, color, light, 0, -1, 0,
				MIN, top, MIN, MAX, top, MIN, MAX, top, MAX, MIN, top, MAX,
				0, 0, 1, 0, 1, 1, 0, 1);
		quad(pose, out, sprite, color, light, 0, 1, 0,
				MIN, top, MAX, MAX, top, MAX, MAX, top, MIN, MIN, top, MIN,
				0, 1, 1, 1, 1, 0, 0, 0);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
			int color, int light, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd) {
		vertex(pose, out, sprite, color, light, nx, ny, nz, ax, ay, az, ua, va);
		vertex(pose, out, sprite, color, light, nx, ny, nz, bx, by, bz, ub, vb);
		vertex(pose, out, sprite, color, light, nx, ny, nz, cx, cy, cz, uc, vc);
		vertex(pose, out, sprite, color, light, nx, ny, nz, dx, dy, dz, ud, vd);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
			int color, int light, float nx, float ny, float nz,
			float x, float y, float z, float u, float v) {
		out.addVertex(pose, x, y, z)
				.setColor(color)
				.setUv(sprite.getU(u), sprite.getV(v))
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	public static final class State extends BlockEntityRenderState {
		private boolean visible;
		private float height;
		private TextureAtlasSprite sprite;
		private int color = -1;
		private int light = LightCoordsUtil.FULL_BRIGHT;
		private int glassLight = LightCoordsUtil.FULL_BRIGHT;
	}
}
