package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.LitMachineBlock;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The living part of the incubator (MOD-118): the item floating inside the glass dome and the
 * emitter ring the base spins under it while an operation runs.
 *
 * <p>Bound to the <b>base</b>, not the dome — the dome carries no block entity, and the base is what
 * knows the inventory, the mode and the progress. Everything drawn above therefore sits one block up
 * in this renderer's local space. That geometry leaves the base's own section, so the renderer opts
 * out of off-screen culling exactly as the wind mill rotor does.
 *
 * <p>The item reaches the client for free: {@code MachineBlockEntity#getUpdateTag} ships the whole
 * inventory, so {@code displayedStack()} is valid client-side without a packet of our own.
 */
public final class IncubatorBlockEntityRenderer
		implements BlockEntityRenderer<IncubatorBlockEntity, IncubatorBlockEntityRenderer.State> {

	/**
	 * Centre of the dome chamber, measured from the base block's own origin.
	 *
	 * <p>Sized against the dome model rather than eyeballed. The dome sits one block up, so its model
	 * coordinate {@code m} lands at {@code 1 + m/16} here: the floor plate ends at {@code m=1.5}
	 * (y 1.094) and the mid frame rib starts at {@code m=10} (y 1.625), leaving a window barely half a
	 * block tall. The {@code GROUND} display transform also lifts an item above this origin, so a
	 * centre of 1.5 pushed the top of the item into that rib (owner report, 2026-07-26) — it hangs
	 * lower now, mid-window, with the bob amplitude counted in.
	 */
	private static final float CHAMBER_Y = 1.3F;
	/** Peak-to-peak of the hover bob is twice this, in blocks. */
	private static final float BOB_AMPLITUDE = 0.05F;
	private static final float BOB_SPEED = 0.06F;
	private static final float IDLE_SPIN = 0.02F;
	private static final float WORKING_SPIN = 0.07F;
	/** Items render smaller than a full block so they clear the dome's frame ribs. */
	private static final float ITEM_SCALE = 0.55F;

	/** The emitter ring hovers just clear of the base's top face and spins against the item. */
	private static final float RING_Y = 1.02F;
	private static final float RING_INNER = 0.20F;
	private static final float RING_OUTER = 0.30F;
	private static final int RING_COLOR = 0xB063D863;
	private static final SpriteId RING_SPRITE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("incubator_glass"));
	private static final RenderType RING_RENDER_TYPE =
			RING_SPRITE.renderType(ignored -> Sheets.translucentBlockItemSheet());

	/** The game time is wrapped before it reaches a float — a raw tick count loses sub-tick precision
	 * after a few real-world days and the animation starts stepping. */
	private static final long TIME_WRAP = 24000L;

	private final ItemModelResolver itemModelResolver;
	private final SpriteGetter sprites;

	public IncubatorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(IncubatorBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);

		ItemStack shown = entity.displayedStack();
		state.visible = entity.isFormed() && !shown.isEmpty();
		// "Running" comes from the block state, not from the progress counter: the machine's data
		// channels only travel to a client that has the menu open, while LIT is part of the block
		// state and is therefore synced to everyone who can see the block — which is exactly the
		// audience of a renderer. It is the same signal the base uses to spawn its particles.
		BlockState blockState = entity.getBlockState();
		state.working = blockState.hasProperty(LitMachineBlock.LIT) && blockState.getValue(LitMachineBlock.LIT);
		if (!state.visible && !state.working) {
			return;
		}

		Level level = entity.getLevel();
		BlockPos pos = entity.getBlockPos();
		// Light the item by the chamber it floats in, not by the solid machine block underneath it.
		state.chamberLight = level == null
				? LightCoordsUtil.FULL_BRIGHT
				: LightCoordsUtil.getLightCoords(level, pos.above());

		float time = (level == null ? 0L : level.getGameTime() % TIME_WRAP) + partialTicks;
		state.angle = time * (state.working ? WORKING_SPIN : IDLE_SPIN);
		state.ringAngle = -time * WORKING_SPIN;
		state.bob = Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;

		if (state.visible) {
			// updateForTopItem clears the state itself, so one instance can be reused frame to frame.
			this.itemModelResolver.updateForTopItem(state.item, shown, ItemDisplayContext.GROUND,
					level, null, (int) pos.asLong());
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (state.working) {
			TextureAtlasSprite sprite = sprites.get(RING_SPRITE);
			poseStack.pushPose();
			poseStack.translate(0.5F, RING_Y, 0.5F);
			poseStack.mulPose(Axis.YP.rotation(state.ringAngle));
			collector.submitCustomGeometry(poseStack, RING_RENDER_TYPE,
					(pose, consumer) -> renderRing(pose, consumer, sprite));
			poseStack.popPose();
		}

		if (!state.visible || state.item.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, CHAMBER_Y + state.bob, 0.5F);
		poseStack.mulPose(Axis.YP.rotation(state.angle));
		poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
		// Full bright while irradiating: the item is lit by the emitter, not by the room.
		int light = state.working ? LightCoordsUtil.FULL_BRIGHT : state.chamberLight;
		state.item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		// The item is drawn a block above this block entity, outside the section box the culler tests.
		// Same reason the wind mill rotor opts out, and 26.2 has no getRenderBoundingBox to widen.
		return true;
	}

	/** A flat square annulus: four bars laid end to end around the centre. */
	private static void renderRing(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite) {
		bar(pose, out, sprite, -RING_OUTER, RING_OUTER, -RING_OUTER, -RING_INNER);
		bar(pose, out, sprite, -RING_OUTER, RING_OUTER, RING_INNER, RING_OUTER);
		bar(pose, out, sprite, -RING_OUTER, -RING_INNER, -RING_INNER, RING_INNER);
		bar(pose, out, sprite, RING_INNER, RING_OUTER, -RING_INNER, RING_INNER);
	}

	/**
	 * One horizontal bar of the ring, wound both ways: the translucent sheet culls back faces, so a
	 * single winding would make the ring vanish when looked at from the other side.
	 */
	private static void bar(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
			float x0, float x1, float z0, float z1) {
		float u0 = sprite.getU0();
		float u1 = sprite.getU1();
		float v0 = sprite.getV0();
		float v1 = sprite.getV1();
		vertex(pose, out, x0, z0, u0, v0, 1.0F);
		vertex(pose, out, x0, z1, u0, v1, 1.0F);
		vertex(pose, out, x1, z1, u1, v1, 1.0F);
		vertex(pose, out, x1, z0, u1, v0, 1.0F);

		vertex(pose, out, x0, z0, u0, v0, -1.0F);
		vertex(pose, out, x1, z0, u1, v0, -1.0F);
		vertex(pose, out, x1, z1, u1, v1, -1.0F);
		vertex(pose, out, x0, z1, u0, v1, -1.0F);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float z,
			float u, float v, float normalY) {
		out.addVertex(pose, x, 0.0F, z)
				.setColor(RING_COLOR)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT)
				.setNormal(pose, 0.0F, normalY, 0.0F);
	}

	public static final class State extends BlockEntityRenderState {
		private final ItemStackRenderState item = new ItemStackRenderState();
		private boolean visible;
		private boolean working;
		private float angle;
		private float ringAngle;
		private float bob;
		private int chamberLight = LightCoordsUtil.FULL_BRIGHT;
	}
}
