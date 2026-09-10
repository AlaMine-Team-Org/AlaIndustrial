package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.LitMachineBlock;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
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
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The living part of the incubator (MOD-118): the item floating inside the glass dome, the emitter
 * ring the base spins under it while an operation runs, the equaliser on its front panel, and the
 * nutrient bath standing in that chamber, and the sight glass on its back that measures it
 * (MOD-605).
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
	 * coordinate {@code m} lands at {@code 1 + m/16} here: the floor plate ends at {@code m=2}
	 * (y 1.125) and the glass barrel gives way to the first shoulder at {@code m=10} (y 1.625),
	 * leaving a window barely half a block tall. The {@code GROUND} display transform also lifts an
	 * item above this origin, so a centre of 1.5 pushed the top of the item into that shoulder (owner
	 * report, 2026-07-26) — it hangs lower now, mid-window, with the bob amplitude counted in.
	 *
	 * <p>MOD-604 replaced the dome model and the window moved by half a pixel at the bottom, which
	 * this centre absorbs; the ring below did not get off so lightly.
	 */
	private static final float CHAMBER_Y = 1.3F;
	/** Peak-to-peak of the hover bob is twice this, in blocks. */
	private static final float BOB_AMPLITUDE = 0.05F;
	private static final float BOB_SPEED = 0.06F;
	private static final float IDLE_SPIN = 0.02F;
	private static final float WORKING_SPIN = 0.07F;
	/** Items render smaller than a full block so they clear the dome's frame ribs. */
	private static final float ITEM_SCALE = 0.55F;

	/**
	 * The emitter ring hovers just clear of the chamber floor and spins against the item.
	 *
	 * <p>Measured off the dome, not off the base. The old dome met the base with nothing in the way,
	 * so the ring sat 0.3 px above the base's top face; MOD-604's dome opens with a floor plate two
	 * pixels thick ({@code IncubatorDomeGeometry.STEPS} first row), and at the old
	 * height the ring would have been buried inside it. It now clears that plate by half a pixel.
	 */
	private static final float RING_Y = 1.16F;
	private static final float RING_INNER = 0.20F;
	private static final float RING_OUTER = 0.30F;
	private static final int RING_COLOR = 0xB063D863;
	private static final SpriteId RING_SPRITE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("incubator_glass"));
	private static final RenderType RING_RENDER_TYPE =
			RING_SPRITE.renderType(ignored -> Sheets.translucentBlockItemSheet());

	/**
	 * The front-panel equaliser. Its bars live in {@link IncubatorScreenGeometry} rather than in the
	 * {@code incubator_on} model, because a cube in a JSON model cannot change height and these have
	 * to move while the machine works. The idle model keeps its own static bars, so an incubator that
	 * is merely standing there — and its inventory icon — still shows the panel the designer drew.
	 */
	private static final SpriteId PANEL_SPRITE_LIT =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("incubator_body_on"));
	private static final RenderType PANEL_RENDER_TYPE =
			PANEL_SPRITE_LIT.renderType(ignored -> Sheets.cutoutBlockItemSheet());
	/** A bar never collapses to nothing — a dead panel reads as a broken machine, not an idle one. */
	private static final float BAR_MIN = 0.4F;
	/** Headroom under the well's ceiling, so a bar at full stretch does not pierce the panel. */
	private static final float BAR_MARGIN = 0.2F;
	/** Radians per tick. Slow on purpose: the panel is a hint that work is happening, not a strobe. */
	private static final float BAR_SPEED = 0.18F;
	/** Phase step between neighbours, so the row breathes instead of pumping as one block. */
	private static final float BAR_PHASE = 1.1F;

	/**
	 * The dome sits one block above the block entity that draws it, so every figure from
	 * {@link IncubatorBathGeometry} is lifted by a whole block before it is used.
	 */
	private static final float DOME_LIFT = 1.0F;
	/** Tolerance for "the water reached exactly this step boundary", in block pixels. */
	private static final float BATH_EPS = 1.0e-4F;

	/**
	 * The sight glass on the back, beside the water inlet: two pixels wide, nine tall, standing just
	 * proud of the recessed wall.
	 *
	 * <p>Unlike the equaliser, these numbers are NOT cut out of the designer's model — the model has no
	 * window, and this is the renderer inventing one. They are stated here rather than generated for
	 * exactly that reason. The patch of wall they sit on is free: the inlet's plate stops at x 11, the
	 * corner post starts at x 14, and the wall behind runs y 2..13.5, so nothing of the model is
	 * covered and nothing z-fights.
	 */
	private static final float GAUGE_X0 = 11.4F;
	private static final float GAUGE_X1 = 13.4F;
	private static final float GAUGE_Y0 = 3.0F;
	private static final float GAUGE_Y1 = 12.0F;
	/** The water sits in front of the wall (z 1.0), the pane in front of the water. */
	private static final float GAUGE_WATER_Z = 0.92F;
	private static final float GAUGE_GLASS_Z = 0.86F;
	/** A stable alpha over the fluid's registered RGB, as the portable tank does. */
	private static final int GAUGE_WATER_ALPHA = 0xD8000000;
	private static final RenderType GAUGE_GLASS_RENDER_TYPE =
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

		Level level = entity.getLevel();
		BlockPos pos = entity.getBlockPos();

		Direction facing = blockState.hasProperty(HorizontalMachineBlock.FACING)
				? blockState.getValue(HorizontalMachineBlock.FACING)
				: Direction.NORTH;
		// The mod turns a front-on-north model by (toYRot + 180); this model carries its panel on the
		// south face, so the blockstate adds another half turn and the renderer has to match it. Both
		// the panel and the sight glass ride this turn, which is why they cannot land on other faces.
		state.screenYaw = (facing.toYRot() + 180.0F + IncubatorScreenGeometry.FRONT_FACE_YAW) % 360.0F;
		state.time = (level == null ? 0L : level.getGameTime() % TIME_WRAP) + partialTicks;

		// The bath is read on every incubator, busy or not, so this sits BEFORE the early return: a
		// machine standing idle with a full tank still has to show it. The level reaches the client
		// without a packet of our own — getUpdateTag ships the block entity's whole save, tank
		// included — so no menu has to be open for a passer-by to see it.
		// Only a formed machine has a chamber to hold the bath: without this the water would stand in
		// mid-air above a lone base, one block up, where the dome is not.
		state.formed = entity.isFormed();
		state.waterFill = entity.fluidTank.amount <= 0
				? 0.0F
				: Math.min(1.0F, (float) entity.fluidTank.amount / entity.fluidTank.capacity);
		if (state.waterFill > 0.0F) {
			readWater(state, level);
		}
		// Light the bath by the CHAMBER it stands in and the gauge by the air in front of the panel it
		// is set into — never by this block's own position. The base is a solid, occluding cube, so the
		// light there is zero: the first version read it and the water came out a flat black mass that
		// looked nothing like water. The floating item two lines below has always done this correctly.
		if (level == null) {
			state.bathLight = LightCoordsUtil.FULL_BRIGHT;
			state.gaugeLight = LightCoordsUtil.FULL_BRIGHT;
		} else {
			state.bathLight = LightCoordsUtil.getLightCoords(level, pos.above());
			state.gaugeLight = LightCoordsUtil.getLightCoords(level, pos.relative(facing.getOpposite()));
		}

		if (!state.visible && !state.working) {
			return;
		}

		// Light the item by the chamber it floats in, not by the solid machine block underneath it.
		state.chamberLight = level == null
				? LightCoordsUtil.FULL_BRIGHT
				: LightCoordsUtil.getLightCoords(level, pos.above());

		float time = state.time;
		state.angle = time * (state.working ? WORKING_SPIN : IDLE_SPIN);
		state.ringAngle = -time * WORKING_SPIN;
		state.bob = Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;

		if (state.visible) {
			// updateForTopItem clears the state itself, so one instance can be reused frame to frame.
			this.itemModelResolver.updateForTopItem(state.item, shown, ItemDisplayContext.GROUND,
					level, null, (int) pos.asLong());
		}
	}

	/**
	 * The water's own sprite and tint, taken from the loader's fluid registrations rather than
	 * hardcoded, so a resource pack that restyles water restyles this gauge too.
	 */
	private static void readWater(State state, @Nullable Level level) {
		BlockState legacy = Fluids.WATER.defaultFluidState().createLegacyBlock();
		FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
				.get(Fluids.WATER.defaultFluidState());
		state.waterSprite = model.stillMaterial().sprite();
		// MOD-498 — the vanilla accessor is the only form available to both loaders; NeoForge's patch
		// deprecates it in favour of a type that does not exist on the Fabric side this file also
		// compiles for.
		@SuppressWarnings("deprecation")
		var tintSource = model.tintSource();
		int tint = tintSource == null
				? -1
				: level instanceof BlockAndTintGetter tintGetter
						? tintSource.colorInWorld(legacy, tintGetter, state.blockPos)
						: tintSource.color(legacy);
		state.waterColor = (tint & 0x00FFFFFF) | GAUGE_WATER_ALPHA;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		submitGauge(state, poseStack, collector);
		submitBath(state, poseStack, collector);

		if (state.working) {
			submitPanel(state, poseStack, collector);

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

	/**
	 * The nutrient bath standing in the chamber, rising with the tank.
	 *
	 * <p>Not turned by the panel's yaw: the chamber is square and its dome carries no facing at all, so
	 * the water sits the same way round however the machine was placed. Drawn as a closed prism with
	 * both windings, because the translucent sheet culls back faces and the wall nearest the camera
	 * would otherwise vanish as the player walks round it.
	 */
	private void submitBath(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (!state.formed || state.waterFill <= 0.0F || state.waterSprite == null) {
			return;
		}
		TextureAtlasSprite water = state.waterSprite;
		collector.submitCustomGeometry(poseStack, Sheets.translucentBlockItemSheet(),
				(pose, consumer) -> renderBath(pose, consumer, water, state));
	}

	private static void renderBath(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
			State state) {
		float[][] steps = IncubatorBathGeometry.STEPS;
		float top = IncubatorBathGeometry.FLOOR
				+ (IncubatorBathGeometry.CEILING - IncubatorBathGeometry.FLOOR) * state.waterFill;
		for (int i = 0; i < steps.length; i++) {
			float[] step = steps[i];
			if (top <= step[4] + BATH_EPS) {
				break;   // the water never climbed this far
			}
			float stepTop = Math.min(top, step[5]);
			renderBathStep(pose, out, sprite, state, step, stepTop);
			// The surface belongs on the HIGHEST step holding water: if the level spills into the next
			// one, this step is submerged and its lid would be a sheet of water inside the water.
			boolean spillsOver = i + 1 < steps.length && top > steps[i + 1][4] + BATH_EPS;
			if (!spillsOver) {
				renderBathSurface(pose, out, sprite, state, step, stepTop);
			}
		}
	}

	/** The four walls of one step, from its own floor up to wherever the water stands in it. */
	private static void renderBathStep(PoseStack.Pose pose, VertexConsumer out,
			TextureAtlasSprite sprite, State state, float[] step, float stepTop) {
		float x0 = step[0] / 16.0F;
		float z0 = step[1] / 16.0F;
		float x1 = step[2] / 16.0F;
		float z1 = step[3] / 16.0F;
		float y0 = DOME_LIFT + step[4] / 16.0F;
		float y1 = DOME_LIFT + stepTop / 16.0F;
		float u0 = sprite.getU0();
		float u1 = sprite.getU1();
		float v1 = sprite.getV1();
		// Cropped from the bottom of the tile up, so a shallow step shows a slice of water rather than
		// a whole tile squashed — the portable tank crops its walls the same way.
		float v0 = Mth.lerp(1.0F - (y1 - y0), sprite.getV0(), v1);
		int color = state.waterColor;
		int light = state.bathLight;
		bathQuad(pose, out, color, light, 0.0F, 0.0F, -1.0F,
				x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, u0, v0, u1, v1);
		bathQuad(pose, out, color, light, 0.0F, 0.0F, 1.0F,
				x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, u0, v0, u1, v1);
		bathQuad(pose, out, color, light, -1.0F, 0.0F, 0.0F,
				x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, u0, v0, u1, v1);
		bathQuad(pose, out, color, light, 1.0F, 0.0F, 0.0F,
				x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, u0, v0, u1, v1);
	}

	/** The one face a player looks down on through the glass. */
	private static void renderBathSurface(PoseStack.Pose pose, VertexConsumer out,
			TextureAtlasSprite sprite, State state, float[] step, float stepTop) {
		float x0 = step[0] / 16.0F;
		float z0 = step[1] / 16.0F;
		float x1 = step[2] / 16.0F;
		float z1 = step[3] / 16.0F;
		float y = DOME_LIFT + stepTop / 16.0F;
		bathQuad(pose, out, state.waterColor, state.bathLight, 0.0F, 1.0F, 0.0F,
				x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1,
				sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
	}

	/** One face of the bath, wound both ways so no angle can cull it away. */
	private static void bathQuad(PoseStack.Pose pose, VertexConsumer out, int color, int light,
			float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float u0, float v0, float u1, float v1) {
		bathVertex(pose, out, ax, ay, az, u0, v1, color, light, nx, ny, nz);
		bathVertex(pose, out, bx, by, bz, u1, v1, color, light, nx, ny, nz);
		bathVertex(pose, out, cx, cy, cz, u1, v0, color, light, nx, ny, nz);
		bathVertex(pose, out, dx, dy, dz, u0, v0, color, light, nx, ny, nz);

		bathVertex(pose, out, dx, dy, dz, u0, v0, color, light, -nx, -ny, -nz);
		bathVertex(pose, out, cx, cy, cz, u1, v0, color, light, -nx, -ny, -nz);
		bathVertex(pose, out, bx, by, bz, u1, v1, color, light, -nx, -ny, -nz);
		bathVertex(pose, out, ax, ay, az, u0, v1, color, light, -nx, -ny, -nz);
	}

	private static void bathVertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z,
			float u, float v, int color, int light, float nx, float ny, float nz) {
		out.addVertex(pose, x, y, z)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	/**
	 * The sight glass: a pane on the back wall with the bath showing through it.
	 *
	 * <p>Turned by the same yaw as the front panel, so the window stays beside the inlet whichever way
	 * the machine was placed. The pane is drawn whether or not there is water — an empty gauge is
	 * information too, and a window that vanishes when the tank runs dry reads as a glitch.
	 */
	private void submitGauge(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		TextureAtlasSprite glass = sprites.get(RING_SPRITE);
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.screenYaw));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		if (state.waterFill > 0.0F && state.waterSprite != null) {
			TextureAtlasSprite water = state.waterSprite;
			collector.submitCustomGeometry(poseStack, Sheets.translucentBlockItemSheet(),
					(pose, consumer) -> renderGaugeWater(pose, consumer, water, state));
		}
		collector.submitCustomGeometry(poseStack, GAUGE_GLASS_RENDER_TYPE,
				(pose, consumer) -> renderGaugePane(pose, consumer, glass, state.gaugeLight));
		poseStack.popPose();
	}

	private static void renderGaugeWater(PoseStack.Pose pose, VertexConsumer out,
			TextureAtlasSprite sprite, State state) {
		float top = GAUGE_Y0 + (GAUGE_Y1 - GAUGE_Y0) * state.waterFill;
		// The texture is cropped from the bottom up, so a half-full gauge shows half a water tile
		// rather than a whole one squashed — the same trick the portable tank's walls use.
		float v0 = Mth.lerp(1.0F - (top - GAUGE_Y0) / (GAUGE_Y1 - GAUGE_Y0), sprite.getV0(), sprite.getV1());
		gaugeQuad(pose, out, GAUGE_X0, GAUGE_Y0, GAUGE_X1, top, GAUGE_WATER_Z,
				sprite.getU0(), sprite.getU1(), v0, sprite.getV1(), state.waterColor, state.gaugeLight);
	}

	private static void renderGaugePane(PoseStack.Pose pose, VertexConsumer out,
			TextureAtlasSprite sprite, int light) {
		gaugeQuad(pose, out, GAUGE_X0, GAUGE_Y0, GAUGE_X1, GAUGE_Y1, GAUGE_GLASS_Z,
				sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), 0xFFFFFFFF, light);
	}

	/** One flat pane on the back wall, wound both ways so a glancing angle cannot cull it away. */
	private static void gaugeQuad(PoseStack.Pose pose, VertexConsumer out, float x0, float y0,
			float x1, float y1, float z, float u0, float u1, float v0, float v1, int color, int light) {
		float px0 = x0 / 16.0F;
		float px1 = x1 / 16.0F;
		float py0 = y0 / 16.0F;
		float py1 = y1 / 16.0F;
		float pz = z / 16.0F;
		gaugeVertex(pose, out, px0, py0, pz, u0, v1, color, light, -1.0F);
		gaugeVertex(pose, out, px1, py0, pz, u1, v1, color, light, -1.0F);
		gaugeVertex(pose, out, px1, py1, pz, u1, v0, color, light, -1.0F);
		gaugeVertex(pose, out, px0, py1, pz, u0, v0, color, light, -1.0F);

		gaugeVertex(pose, out, px0, py1, pz, u0, v0, color, light, 1.0F);
		gaugeVertex(pose, out, px1, py1, pz, u1, v0, color, light, 1.0F);
		gaugeVertex(pose, out, px1, py0, pz, u1, v1, color, light, 1.0F);
		gaugeVertex(pose, out, px0, py0, pz, u0, v1, color, light, 1.0F);
	}

	private static void gaugeVertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z,
			float u, float v, int color, int light, float normalZ) {
		out.addVertex(pose, x, y, z)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, 0.0F, 0.0F, normalZ);
	}

	/**
	 * The equaliser, turned onto whichever face the blockstate put the panel on.
	 *
	 * <p>The geometry is stated once, in the designer's own orientation (panel on south), and the same
	 * yaw the blockstate applies to the model is applied here — so the bars cannot drift onto a
	 * different face than the screen they belong to.
	 */
	private void submitPanel(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		TextureAtlasSprite sprite = sprites.get(PANEL_SPRITE_LIT);
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.screenYaw));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		collector.submitCustomGeometry(poseStack, PANEL_RENDER_TYPE,
				(pose, consumer) -> renderBars(pose, consumer, sprite, state));
		poseStack.popPose();
	}

	private static void renderBars(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
			State state) {
		// The panel only ever renders while the machine is lit, and a lit screen is its own light.
		int light = LightCoordsUtil.FULL_BRIGHT;
		float u = Mth.lerp(IncubatorScreenGeometry.INDICATOR_U, sprite.getU0(), sprite.getU1());
		float v = Mth.lerp(IncubatorScreenGeometry.INDICATOR_V, sprite.getV0(), sprite.getV1());
		float z = IncubatorScreenGeometry.FRONT_Z / 16.0F;
		for (int i = 0; i < IncubatorScreenGeometry.BARS.length; i++) {
			float[] bar = IncubatorScreenGeometry.BARS[i];
			float top = barTop(state, i) / 16.0F;
			float x0 = bar[0] / 16.0F;
			float x1 = bar[1] / 16.0F;
			float y0 = IncubatorScreenGeometry.BASE_Y / 16.0F;
			// Wound both ways for the same reason the ring is: the panel can be looked at from an
			// angle where a single winding would cull the bar away.
			panelVertex(pose, out, x0, y0, z, u, v, light);
			panelVertex(pose, out, x1, y0, z, u, v, light);
			panelVertex(pose, out, x1, top, z, u, v, light);
			panelVertex(pose, out, x0, top, z, u, v, light);

			panelVertex(pose, out, x0, y0, z, u, v, light);
			panelVertex(pose, out, x0, top, z, u, v, light);
			panelVertex(pose, out, x1, top, z, u, v, light);
			panelVertex(pose, out, x1, y0, z, u, v, light);
		}
	}

	/**
	 * Height of one bar: the designer's own figure at rest, a slow breath under load.
	 *
	 * <p>The ceiling is the well's top minus a margin, so a bar at full stretch stays inside the
	 * recess instead of standing proud of the panel around it.
	 */
	private static float barTop(State state, int index) {
		float ceiling = IncubatorScreenGeometry.WELL_TOP - BAR_MARGIN;
		float wave = 0.5F + 0.5F * Mth.sin(state.time * BAR_SPEED + index * BAR_PHASE);
		float low = IncubatorScreenGeometry.BASE_Y + BAR_MIN;
		return low + (ceiling - low) * wave;
	}

	private static void panelVertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z,
			float u, float v, int light) {
		out.addVertex(pose, x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, 0.0F, 0.0F, 1.0F);
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
		private float time;
		private float screenYaw;
		private boolean formed;
		private float waterFill;
		@Nullable
		private TextureAtlasSprite waterSprite;
		private int waterColor = 0xFFFFFFFF;
		private int bathLight = LightCoordsUtil.FULL_BRIGHT;
		private int gaugeLight = LightCoordsUtil.FULL_BRIGHT;
		private int chamberLight = LightCoordsUtil.FULL_BRIGHT;
	}
}
