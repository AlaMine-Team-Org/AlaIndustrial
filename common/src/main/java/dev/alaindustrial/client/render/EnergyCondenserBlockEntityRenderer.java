package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The orb inside the Energy Condenser (MOD-393).
 *
 * <p>It is the block's gauge: the fuller the bank, the faster it turns and the brighter it burns, so
 * a glance across the base says how close the next clot is without opening the screen. An idle
 * condenser turns slowly rather than stopping dead — a powered machine frozen solid reads as broken,
 * the lesson the drone's rotor already encodes.
 *
 * <p>Built from latitude rings rather than a rotated cube: a cube-based "sphere" reads as a cut gem,
 * and this is meant to be a ball of light. Only the orb is forced to full brightness; the frame is the
 * block model and lights normally, the same split the incubator uses for its item and its glass.
 */
public final class EnergyCondenserBlockEntityRenderer
		implements BlockEntityRenderer<EnergyCondenserBlockEntity,
				EnergyCondenserBlockEntityRenderer.State> {

	private static final SpriteId SPRITE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("energy_condenser_frame"));
	private static final RenderType RENDER_TYPE =
			SPRITE.renderType(ignored -> Sheets.translucentBlockItemSheet());

	/** Rings of latitude and segments of longitude — enough to read round at block scale. */
	private static final int LATITUDES = 8;
	private static final int SEGMENTS = 16;
	/**
	 * Two shells, because one translucent ball reads as a smudge. The inner core is small and nearly
	 * opaque so the eye has something solid to lock onto; the halo is wide and faint and turns the
	 * other way, which is what makes the thing look like contained energy rather than a marble.
	 */
	private static final float CORE_RADIUS = 0.17F;
	private static final float HALO_RADIUS = 0.30F;

	/** Wrap the animation clock by a Minecraft day; float loses precision on a long uptime. */
	private static final long TIME_WRAP = 24_000L;
	private static final float IDLE_SPIN = 0.010F;
	private static final float FULL_SPIN = 0.060F;
	private static final float BOB_SPEED = 0.05F;
	private static final float BOB_AMPLITUDE = 0.035F;

	public EnergyCondenserBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	private final SpriteGetter sprites;

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		float angle;
		float bob;
		/** 0..1 — progress toward the next clot tier; drives brightness and scale. */
		float fill;
		/** Bank holds nothing at all: the orb is not drawn, so the block reads as idle from across the base. */
		boolean empty;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(EnergyCondenserBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		// Progress toward the NEXT tier, not the fraction of the 4 000 000 tank. Against the tank the orb
		// spends the whole first tier below 6 % — indistinguishable from empty, so a working block looked
		// dead. Per tier it visibly swells and brightens between one clot and the next.
		float fill = Mth.clamp(entity.progressToNextTierPermille() / 1000.0F, 0.0F, 1.0F);
		long gameTime = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime() % TIME_WRAP;
		float time = gameTime + partialTicks;
		state.empty = entity.getEnergyStorage().getAmount() <= 0L;
		state.fill = fill;
		state.angle = time * (IDLE_SPIN + (FULL_SPIN - IDLE_SPIN) * fill);
		state.bob = Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		// Nothing banked, nothing to show. A glowing orb inside an empty condenser is a lie the player
		// reads across the base: it says "working" while the block holds zero. The frame stays, so the
		// machine is still visibly there — only its contents go dark.
		if (state.empty) {
			return;
		}
		TextureAtlasSprite sprite = this.sprites.get(SPRITE);
		float scale = 0.80F + 0.20F * state.fill;

		// Halo — wide, faint, turning the other way. Drawn first so the core sits inside it.
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F + state.bob, 0.5F);
		poseStack.mulPose(Axis.YP.rotation(-state.angle * 0.6F));
		poseStack.mulPose(Axis.XP.rotationDegrees(24.0F));
		poseStack.scale(scale, scale, scale);
		collector.submitCustomGeometry(poseStack, RENDER_TYPE,
				(pose, consumer) -> renderShell(pose, consumer, sprite, state.fill, HALO_RADIUS, false));
		poseStack.popPose();

		// Core — small, nearly solid, tilted the other way so the two shells never move together.
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F + state.bob, 0.5F);
		poseStack.mulPose(Axis.YP.rotation(state.angle));
		poseStack.mulPose(Axis.XP.rotationDegrees(18.0F));
		poseStack.scale(scale, scale, scale);
		// FULL_BRIGHT only here: the frame is the block model and lights normally.
		collector.submitCustomGeometry(poseStack, RENDER_TYPE,
				(pose, consumer) -> renderShell(pose, consumer, sprite, state.fill, CORE_RADIUS, true));
		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 64;
	}

	/**
	 * One UV-sphere shell of quads. Colour runs from dim amber to near-white as the bank fills, the
	 * same "more energy is hotter" reading the clot tiers use, so the block and the item it makes speak
	 * the same visual language.
	 */
	private static void renderShell(PoseStack.Pose pose, VertexConsumer consumer,
			TextureAtlasSprite sprite, float fill, float radius, boolean core) {
		float u = (sprite.getU0() + sprite.getU1()) * 0.5F;
		float v = (sprite.getV0() + sprite.getV1()) * 0.5F;
		int red = 255;
		int green = core ? (int) (170 + 85 * fill) : (int) (130 + 90 * fill);
		int blue = core ? (int) (60 + 195 * fill) : (int) (30 + 150 * fill);
		// The core stays readable even when empty; the halo only really shows up as the bank fills.
		int alpha = core ? (int) (200 + 55 * fill) : (int) (45 + 90 * fill);
		int light = LightCoordsUtil.FULL_BRIGHT;

		for (int lat = 0; lat < LATITUDES; lat++) {
			float phi0 = (float) Math.PI * lat / LATITUDES;
			float phi1 = (float) Math.PI * (lat + 1) / LATITUDES;
			for (int seg = 0; seg < SEGMENTS; seg++) {
				float th0 = (float) (2 * Math.PI) * seg / SEGMENTS;
				float th1 = (float) (2 * Math.PI) * (seg + 1) / SEGMENTS;
				// Quad corners on the sphere, wound so the outward face is visible; each is emitted
				// twice with opposite normals so the orb also reads from inside the open frame.
				float[] p00 = point(radius, phi0, th0);
				float[] p01 = point(radius, phi0, th1);
				float[] p11 = point(radius, phi1, th1);
				float[] p10 = point(radius, phi1, th0);
				quad(pose, consumer, p00, p01, p11, p10, u, v, light, red, green, blue, alpha, 1.0F);
				quad(pose, consumer, p10, p11, p01, p00, u, v, light, red, green, blue, alpha, -1.0F);
			}
		}
	}

	private static float[] point(float radius, float phi, float theta) {
		float sinPhi = Mth.sin(phi);
		return new float[] {
				radius * sinPhi * Mth.cos(theta),
				radius * Mth.cos(phi),
				radius * sinPhi * Mth.sin(theta),
		};
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, float[] a, float[] b,
			float[] c, float[] d, float u, float v, int light, int red, int green, int blue, int alpha,
			float normalSign) {
		vertex(pose, consumer, a, u, v, light, red, green, blue, alpha, normalSign);
		vertex(pose, consumer, b, u, v, light, red, green, blue, alpha, normalSign);
		vertex(pose, consumer, c, u, v, light, red, green, blue, alpha, normalSign);
		vertex(pose, consumer, d, u, v, light, red, green, blue, alpha, normalSign);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float[] p, float u, float v,
			int light, int red, int green, int blue, int alpha, float normalSign) {
		float len = Math.max(1.0E-4F, Mth.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]));
		consumer.addVertex(pose, p[0], p[1], p[2])
				.setColor(red, green, blue, alpha)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, normalSign * p[0] / len, normalSign * p[1] / len, normalSign * p[2] / len);
	}
}
