package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.EnergyCondenserBlockEntity;
import dev.alaindustrial.core.machine.RotorSpin;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The energy crystal inside the Energy Condenser (MOD-546).
 *
 * <p>It is the block's gauge, and it reads at a glance: the crystal a condenser is carrying IS the
 * clot it would hand over right now. Tier I is the small cell, tier II grows the shell around it,
 * tier III adds the halves that breathe apart and back together. Below the first threshold there is
 * no clot yet and nothing is drawn — a crystal hanging in a condenser whose slot is empty promises
 * an item the player cannot take.
 *
 * <p>Built from the designer's cubes rather than from a sphere of latitude rings: the shape, its
 * three stages and the turn rate all come from one Blockbench file, and the geometry is generated
 * out of it into {@link EnergyCondenserCrystalGeometry}. The frame around it is an ordinary block
 * model and lights normally — only the crystal is forced bright, the same split the incubator uses
 * for its item and its glass.
 *
 * <p>Why hand-written vertices instead of {@code ModelPart}, which the centrifuge and the sprinkler
 * use: the designer's texture is a 69×69 palette addressed with per-face UVs, often one pixel to a
 * face, and a vanilla {@code CubeListBuilder} can only lay out box UVs. Feeding this through box UVs
 * would mean repacking the texture with a script — replacing the very file the designer edits.
 */
public final class EnergyCondenserBlockEntityRenderer
		implements BlockEntityRenderer<EnergyCondenserBlockEntity,
				EnergyCondenserBlockEntityRenderer.State> {

	private static final SpriteId SPRITE =
			Sheets.BLOCKS_MAPPER.apply(Industrialization.id("energy_condenser_body_on"));
	private static final RenderType SOLID_TYPE =
			SPRITE.renderType(ignored -> Sheets.cutoutBlockItemSheet());
	/**
	 * The force field around the cell is the only translucent part — one cube per stage, drawn at
	 * 39 % alpha straight out of the texture. It goes last so the solid crystal behind it is already
	 * in the depth buffer.
	 */
	private static final RenderType FIELD_TYPE =
			SPRITE.renderType(ignored -> Sheets.translucentBlockItemSheet());

	/** One turn per three seconds in the source animation — 60 ticks, so six degrees a tick. */
	private static final float SPIN_RADIANS_PER_TICK = (float) (Math.PI * 2.0 / 60.0);
	/** The float and the breathing share the animation's three-second loop. */
	private static final float CYCLE_RADIANS_PER_TICK = (float) (Math.PI * 2.0 / 60.0);
	private static final float PIXEL = 1.0F / 16.0F;
	/** Amplitudes straight from the keyframes: half a pixel for the bare core, a quarter for the
	 * bigger crystals, one pixel for the halves that pull apart. */
	private static final float BOB_SMALL = 0.25F * PIXEL;
	private static final float BOB_LARGE = 0.5F * PIXEL;
	private static final float SPREAD = 1.0F * PIXEL;

	private static final Stage[] STAGES = {
			new Stage(EnergyCondenserCrystalGeometry.TIER1_BODY,
					EnergyCondenserCrystalGeometry.TIER1_FIELD,
					EnergyCondenserCrystalGeometry.TIER1_SPIN,
					EnergyCondenserCrystalGeometry.TIER1_SPIN_DOWN,
					EnergyCondenserCrystalGeometry.TIER1_SPIN_UP,
					BOB_LARGE),
			new Stage(EnergyCondenserCrystalGeometry.TIER2_BODY,
					EnergyCondenserCrystalGeometry.TIER2_FIELD,
					EnergyCondenserCrystalGeometry.TIER2_SPIN,
					EnergyCondenserCrystalGeometry.TIER2_SPIN_DOWN,
					EnergyCondenserCrystalGeometry.TIER2_SPIN_UP,
					BOB_SMALL),
			new Stage(EnergyCondenserCrystalGeometry.TIER3_BODY,
					EnergyCondenserCrystalGeometry.TIER3_FIELD,
					EnergyCondenserCrystalGeometry.TIER3_SPIN,
					EnergyCondenserCrystalGeometry.TIER3_SPIN_DOWN,
					EnergyCondenserCrystalGeometry.TIER3_SPIN_UP,
					BOB_SMALL),
	};

	private final SpriteGetter sprites;

	public EnergyCondenserBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	/** Render state: only primitives, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		/** 0, 1 or 2 — index into {@link #STAGES}, not the clot tier itself. */
		int stage;
		float angle;
		float bob;
		float spread;
		/** No clot banked yet: nothing is drawn, and that absence is the readout. */
		boolean empty;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(EnergyCondenserBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition,
				breakProgress);
		// The crystal IS the clot in the output slot, so it may not appear before that clot exists.
		// Below the first threshold the slot is empty and the screen says "below tier I" — a crystal
		// hanging there anyway promises the player an item they cannot take.
		int tier = entity.tierForBank();
		state.empty = tier <= 0;
		state.stage = Math.max(0, tier - 1);
		// floorMod before the float: a world tens of millions of ticks old loses tick resolution in
		// a float, and the crystal freezes mid-turn.
		long gameTime = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
		float time = Math.floorMod(gameTime, RotorSpin.TIME_WRAP) + partialTicks;
		state.angle = time * SPIN_RADIANS_PER_TICK;
		// The keyframes ramp linearly up and back down, which turns at the peak with a visible
		// kink; a raised cosine covers the same travel over the same three seconds and reads as
		// floating rather than as being pulled.
		float wave = 0.5F - 0.5F * Mth.cos(time * CYCLE_RADIANS_PER_TICK);
		state.bob = STAGES[state.stage].bob() * wave;
		state.spread = SPREAD * wave;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (state.empty) {
			return;
		}
		TextureAtlasSprite sprite = this.sprites.get(SPRITE);
		Stage stage = STAGES[state.stage];

		poseStack.pushPose();
		poseStack.translate(0.0F, state.bob, 0.0F);
		submit(collector, poseStack, SOLID_TYPE, stage.body(), sprite);

		if (!stage.spin().isEmpty() || !stage.spinDown().isEmpty() || !stage.spinUp().isEmpty()) {
			poseStack.pushPose();
			// The pivot is the middle of the block on X and Z; its height does not matter, because
			// a turn about Y leaves everything on that axis where it was.
			poseStack.translate(0.5F, 0.0F, 0.5F);
			poseStack.mulPose(Axis.YP.rotation(state.angle));
			poseStack.translate(-0.5F, 0.0F, -0.5F);
			submit(collector, poseStack, SOLID_TYPE, stage.spin(), sprite);
			submitOffset(collector, poseStack, stage.spinDown(), sprite, -state.spread);
			submitOffset(collector, poseStack, stage.spinUp(), sprite, state.spread);
			poseStack.popPose();
		}

		submit(collector, poseStack, FIELD_TYPE, stage.field(), sprite);
		poseStack.popPose();
	}

	private static void submitOffset(SubmitNodeCollector collector, PoseStack poseStack, CubeMesh mesh,
			TextureAtlasSprite sprite, float offsetY) {
		if (mesh.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.0F, offsetY, 0.0F);
		submit(collector, poseStack, SOLID_TYPE, mesh, sprite);
		poseStack.popPose();
	}

	private static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type,
			CubeMesh mesh, TextureAtlasSprite sprite) {
		if (mesh.isEmpty()) {
			return;
		}
		collector.submitCustomGeometry(poseStack, type,
				// Full bright, and only here: the crystal is the light source, the frame around it
				// is a block model and lights the ordinary way.
				(pose, consumer) -> mesh.emit(pose, consumer, sprite, LightCoordsUtil.FULL_BRIGHT));
	}

	/** One stage of the crystal: the parts that move independently, plus its float amplitude. */
	private record Stage(CubeMesh body, CubeMesh field, CubeMesh spin, CubeMesh spinDown,
			CubeMesh spinUp, float bob) {
		Stage(float[][] body, float[][] field, float[][] spin, float[][] spinDown, float[][] spinUp,
				float bob) {
			// 0.5F: the crystal is modelled around zero on X and Z, so it is moved to the middle of
			// the block. The workstation's parts are exported in the block's own space and pass 0.
			this(new CubeMesh(body, 0.5F), new CubeMesh(field, 0.5F), new CubeMesh(spin, 0.5F),
					new CubeMesh(spinDown, 0.5F), new CubeMesh(spinUp, 0.5F), bob);
		}
	}

}
