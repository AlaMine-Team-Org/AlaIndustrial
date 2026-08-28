package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.SprinklerBlock;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.core.machine.RotorSpin;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Unit;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The spinning head on top of the Sprinkler (MOD-525).
 *
 * <p><b>Why the head is geometry and not a texture.</b> The block has exactly one thing to say —
 * whether it is spraying — and it has no screen to say it on. A head that visibly turns says it from
 * across the field, which is where a farm is looked at from; an animated texture would say it only
 * from arm's length.
 *
 * <p><b>Three arms, not four.</b> An even number of arms is rotationally symmetric at every quarter
 * turn, and at the speed this thing runs that reads as standing still. Three arms bring a different
 * silhouette round every 120°, so the eye catches the motion without having to track one arm.
 *
 * <p>The split follows the condenser's and the centrifuge's: the base and mast are the block model
 * and light normally, and this renderer draws only the part that moves. Every coordinate below is a
 * MODEL PIXEL measured from the head's own centre — vanilla's cube baker divides by 16 itself, and
 * dividing again here is the mistake that once shrank the garden drone sixteenfold.
 */
public final class SprinklerHeadBlockEntityRenderer
		implements BlockEntityRenderer<SprinklerBlockEntity, SprinklerHeadBlockEntityRenderer.State> {

	public static final ModelLayerLocation MODEL_LAYER =
			new ModelLayerLocation(Industrialization.id("sprinkler_head"), "main");

	/** Hub and arms in bright iron — the same material the garden drone's rotors use, for the same
	 * reason: it silhouettes against both a green field and a dark greenhouse floor. */
	private static final SpriteId METAL = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("iron_block");
	/** Nozzles in copper, so the working ends read as a different part at a glance. */
	private static final SpriteId NOZZLE = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("copper_block");

	private static final int ARM_COUNT = 3;
	/** Where the head sits on the mast: the mast tops out at 12 px, so its centre is just above. */
	private static final float HEAD_Y = 13.0F / 16.0F;
	/**
	 * How far each arm is canted out of the horizontal, in radians. A flat arm is a disc — the same
	 * silhouette at every angle, which reads as standing still however fast it turns. Tilting each one
	 * makes the assembly change shape as it comes round.
	 */
	private static final float ARM_CANT = (float) Math.toRadians(18.0);

	/** Radians per tick while spraying. Fast enough to read as working, slow enough not to strobe. */
	private static final float SPRAY_RATE = 0.42F;
	/** A slow idle drift when the tank is dry, so a stopped sprinkler still looks like a machine. */
	private static final float IDLE_RATE = 0.04F;

	private final Model.Simple hubModel;
	private final Model.Simple nozzleModel;
	private final SpriteGetter sprites;

	public SprinklerHeadBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		ModelPart root = context.bakeLayer(MODEL_LAYER);
		this.hubModel = new Model.Simple(root.getChild("hub"),
				ignored -> METAL.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.nozzleModel = new Model.Simple(root.getChild("nozzles"),
				ignored -> NOZZLE.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.sprites = context.sprites();
	}

	/**
	 * Original geometry for the head, in model pixels around its own centre, so one {@code YP} rotation
	 * of the whole assembly turns it in place.
	 *
	 * <p>Two groups because they are two materials, not because they move differently. Each arm hangs
	 * off its own pivot at the axis and is canted in that already-rotated frame: authoring the arms at
	 * absolute coordinates and canting them in the root frame would tilt all three the same way in
	 * world space, and the head would come apart into parallel bars instead of turning as one piece.
	 *
	 * <p>Seven boxes in all, well under the garden drone's 53 — this project's documented ceiling for
	 * a block-entity renderer.
	 */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition hub = root.addOrReplaceChild("hub", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition nozzles = root.addOrReplaceChild("nozzles", CubeListBuilder.create(), PartPose.ZERO);

		// The cap the arms turn on, centred on the axis.
		hub.addOrReplaceChild("cap",
				CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F),
				PartPose.ZERO);

		float step = (float) (Math.PI * 2.0 / ARM_COUNT);
		for (int i = 0; i < ARM_COUNT; i++) {
			PartPose pivot = PartPose.rotation(0.0F, i * step, 0.0F);
			// Arm: a thin bar running out along +X from the hub, tilted about its own length.
			hub.addOrReplaceChild("arm_" + i, CubeListBuilder.create(), pivot)
					.addOrReplaceChild("bar_" + i,
							CubeListBuilder.create().texOffs(0, 0)
									.addBox(1.8F, -0.5F, -0.75F, 4.2F, 1.0F, 1.5F),
							PartPose.rotation(ARM_CANT, 0.0F, 0.0F));
			// Nozzle: the flared tip the spray comes out of, on an identical pivot in the other group.
			nozzles.addOrReplaceChild("nozzle_arm_" + i, CubeListBuilder.create(), pivot)
					.addOrReplaceChild("tip_" + i,
							CubeListBuilder.create().texOffs(0, 0)
									.addBox(5.6F, -1.0F, -1.0F, 1.6F, 2.0F, 2.0F),
							PartPose.rotation(ARM_CANT, 0.0F, 0.0F));
		}

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SprinklerBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition,
				breakProgress);
		Level level = entity.getLevel();
		BlockState blockState = entity.getBlockState();
		boolean spraying = blockState.hasProperty(SprinklerBlock.SPRAYING)
				&& blockState.getValue(SprinklerBlock.SPRAYING);
		state.hanging = blockState.hasProperty(SprinklerBlock.HANGING)
				&& blockState.getValue(SprinklerBlock.HANGING);
		long gameTime = level == null ? 0L : level.getGameTime();
		// floorMod against the shared wrap, not a raw multiply: on a world that has been running for a
		// long time a float angle loses its fractional precision and the head freezes mid-turn.
		float time = Math.floorMod(gameTime, RotorSpin.TIME_WRAP) + partialTicks;
		state.angle = time * (spraying ? SPRAY_RATE : IDLE_RATE);
		state.lightCoords = level == null
				? LightCoordsUtil.FULL_BRIGHT
				: LightCoordsUtil.getLightCoords(level, entity.getBlockPos());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		poseStack.pushPose();
		// Mirror the head to the other end of the mast when the block hangs, then flip it: a head
		// drawn the right way up under a ceiling would have its nozzles pointing into the block.
		poseStack.translate(0.5F, state.hanging ? 1.0F - HEAD_Y : HEAD_Y, 0.5F);
		if (state.hanging) {
			poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
		}
		poseStack.mulPose(Axis.YP.rotation(state.angle));
		collector.submitModel(hubModel, Unit.INSTANCE, poseStack, state.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, METAL, sprites, 0, state.breakProgress);
		collector.submitModel(nozzleModel, Unit.INSTANCE, poseStack, state.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, NOZZLE, sprites, 0, state.breakProgress);
		poseStack.popPose();
	}

	/** What the renderer needs off the block entity: how far round the head is, and its light. */
	public static final class State extends BlockEntityRenderState {
		private float angle;
		private boolean hanging;
		private int lightCoords = LightCoordsUtil.FULL_BRIGHT;
	}
}
