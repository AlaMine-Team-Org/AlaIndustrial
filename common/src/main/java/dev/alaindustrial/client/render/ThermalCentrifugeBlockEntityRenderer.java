package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.LitMachineBlock;
import dev.alaindustrial.block.entity.ThermalCentrifugeBlockEntity;
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
 * The rotor turning inside the Thermal Centrifuge (MOD-424).
 *
 * <p>The block's whole identity is a machine you have to switch ON and then wait for: a held redstone
 * signal starts it, and a stopped rotor needs {@code thermalCentrifugeSpinupTicks} paid ticks to reach
 * working speed. None of that is legible from a texture swap, so the rotor is real geometry visible
 * through real openings in the housing — the player watches it wind up, hold a slow idle turn, run hard
 * while shavings are coming out, and stop dead the moment the lever goes off.
 *
 * <p><b>It is a centrifuge basket, not a set of blades.</b> The first version was six flat, near-white
 * plates in a dark box, and it read as loose paper: nothing dark to silhouette against, no visible axis
 * to rotate about, and not a trace of the uranium the machine exists to refine. What is drawn now is an
 * assembly with a structure to it — a dark spindle running the full height of the chamber, a hub disc at
 * each end, canted vanes strung between the two hubs like a squirrel cage, and a uranium-green core
 * sleeve on the spindle with a matching hot edge down each vane. Three materials at three luminances
 * (dark deepslate ~72, bright iron ~220, tinted glow) is what makes any of it read at 16 px.
 *
 * <p><b>The split is the condenser's</b> (MOD-393): the housing is the block model and lights normally,
 * and this renderer draws only the part that moves. Only the GREEN is forced to full brightness, and
 * only while the machine is actually processing — steel is not a light source, so the spindle and the
 * vanes take the room's light like everything else.
 *
 * <p><b>The rate arithmetic is not here.</b> It lives in {@link RotorSpin}, Minecraft-free, because the
 * L3 lane can photograph the rotor but cannot measure how fast it turns; only an L1 test can (see
 * {@code RotorSpinTest}).
 *
 * <p><b>Facing is deliberately ignored.</b> The rotor stands on the block's own vertical axis and turns
 * about it, so the front window and both side windows show the same thing whichever way the block was
 * placed — unlike the water mill's wheel, which hangs off one face and must be rotated to match.
 */
public final class ThermalCentrifugeBlockEntityRenderer
		implements BlockEntityRenderer<ThermalCentrifugeBlockEntity,
				ThermalCentrifugeBlockEntityRenderer.State> {

	public static final ModelLayerLocation MODEL_LAYER =
			new ModelLayerLocation(Industrialization.id("thermal_centrifuge_rotor"), "main");

	/**
	 * Spindle, hubs and bearing collars in dark machined stone (average luminance ~72 against the vanes'
	 * ~220). The mod's own casing sprites were tried first and rejected: every one of them averages
	 * 90&ndash;100, which is close enough to iron that the assembly flattened into a single pale mass and
	 * the axis — the one thing that tells the eye what the rotor turns about — disappeared into it.
	 */
	private static final SpriteId STRUCTURE =
			Sheets.BLOCKS_MAPPER.defaultNamespaceApply("polished_deepslate");
	/** Vanes in bright iron, the same material the garden drone's blades use, for the same reason. */
	private static final SpriteId VANES = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("iron_block");
	/**
	 * The glow pieces are drawn on a FLAT, near-white sprite and coloured by the vertex tint below, so the
	 * uranium ramp can be quoted exactly rather than approximated by whatever green block happens to ship.
	 * {@code white_concrete} is the only near-white vanilla block with no pattern at all (luminance
	 * 210&ndash;213 across all 256 pixels), which is what keeps the tint the thing you see.
	 */
	private static final SpriteId GLOW = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("white_concrete");

	/**
	 * Uranium ramp steps used for the rotor's glow, as opaque ARGB — the same eight-step ramp the front
	 * texture glows with and the same one every uranium item icon is drawn from. Three states, three
	 * steps, no interpolation: a tint lerped per frame flickers as the spin permille steps.
	 */
	private static final int GLOW_STOPPED = 0xFF2F4F18;   // U0 — all but black; the machine is off
	private static final int GLOW_TURNING = 0xFF639D36;   // U3 — spinning up, or standing by at speed
	private static final int GLOW_PROCESSING = 0xFFB1E982; // U6 — turning out shavings

	/**
	 * Vanes around the basket, evenly spaced — and therefore the rotor's rotational symmetry, which
	 * {@link RotorSpin} leans on to justify its coarse, stepped rate. Change this number and that javadoc
	 * has to change with it.
	 *
	 * <p><b>Four, not six.</b> Six vanes of this width at this radius close the basket completely — measured
	 * in a captured frame, the wall came to a hair over 100 % of its own circumference — and a closed basket
	 * hides the two things it exists to be seen around: the dark spindle and the green core sleeve. What
	 * came out looked like a white drum with green seams, which is the flat-plate reading this rebuild was
	 * meant to end. Four vanes leave a ~2.7 px gap between neighbours against a 3.0 px spindle, so the axis
	 * shows through for most of every revolution and winks only while a blade is dead in front of it.
	 */
	private static final int VANE_COUNT = 4;
	/** Distance from the axis to each vane's own pivot, in model pixels. */
	private static final float VANE_RADIUS = 3.8F;
	/**
	 * How far each vane is turned out of the purely tangential, in radians. A basket of flat tangential
	 * plates is a cylinder: it presents the same silhouette at every angle and reads as standing still
	 * however fast it spins. Canted, each vane shows a different amount of its face as it comes round,
	 * so the assembly visibly changes shape through the window.
	 */
	private static final float VANE_CANT = (float) Math.toRadians(30.0);
	/** Half-width of each hub disc, in model pixels — matched to the vanes' own sweep. */
	private static final float HUB_HALF = 3.8F;

	/**
	 * The rotor's centre inside the block, in blocks. The housing's cavity runs from 2 to 14 pixels
	 * horizontally and from 3 to 13 vertically, so its centre is the block's own centre.
	 *
	 * <p>Every authored coordinate below is a MODEL PIXEL measured from that centre, and vanilla's cube
	 * baker divides by 16 on its own — dividing again here is the mistake that once shrank the garden
	 * drone sixteenfold. The widest sweep is a canted vane's outer glow corner and a hub disc's corner,
	 * both {@code 5.37} pixels from the axis; the cavity's horizontal half-width is 6, so no part of the
	 * rotor ever pokes through the housing — front window, side windows or wall — at any angle. Vertically
	 * the spindle stops 0.1 px short of the chamber's floor and ceiling.
	 */
	private static final float CENTRE = 0.5F;

	private final Model.Simple structureModel;
	private final Model.Simple vaneModel;
	private final Model.Simple glowModel;
	private final SpriteGetter sprites;

	public ThermalCentrifugeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		ModelPart root = context.bakeLayer(MODEL_LAYER);
		this.structureModel = new Model.Simple(root.getChild("structure"),
				ignored -> STRUCTURE.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.vaneModel = new Model.Simple(root.getChild("vanes"),
				ignored -> VANES.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.glowModel = new Model.Simple(root.getChild("glow"),
				ignored -> GLOW.renderType(unused -> Sheets.cutoutBlockItemSheet()));
		this.sprites = context.sprites();
	}

	/**
	 * Original geometry for the rotor, in model pixels ({@code 16 == one block}) around its own centre, so
	 * one {@code YP} rotation of the whole assembly turns it in place.
	 *
	 * <p>Three groups because they are three materials, not because they move differently — everything
	 * here turns together. Fourteen boxes in all, against the garden drone's 53, which is this project's
	 * documented ceiling for a block-entity renderer.
	 *
	 * <p>Each vane hangs off its own arm, and the glow strip that runs down its outer edge hangs off an
	 * identical arm in the glow group: the arm carries the yaw around the axis, and the vane carries the
	 * cant, applied in the arm's already-rotated frame. Authoring the vanes at absolute coordinates and
	 * canting them in the root frame would turn all six the same way in world space instead of each one
	 * about its own radius, and the "basket" would come apart into a row of parallel plates.
	 */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition structure = root.addOrReplaceChild("structure", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition vanes = root.addOrReplaceChild("vanes", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition glow = root.addOrReplaceChild("glow", CubeListBuilder.create(), PartPose.ZERO);

		// The axis, floor to ceiling. This is the piece the first version was missing entirely: without a
		// visible shaft the vanes look like they are floating, and a floating ring has no rotation the eye
		// can attach to.
		structure.addOrReplaceChild("spindle",
				CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -4.9F, -1.5F, 3.0F, 9.8F, 3.0F),
				PartPose.ZERO);
		// Hub discs. Square rather than approximated-round on purpose: at this size a square plate's
		// corners sweeping past the window are one of the clearest "this is turning" cues available.
		structure.addOrReplaceChild("hub_top",
				CubeListBuilder.create().texOffs(0, 0)
						.addBox(-HUB_HALF, 2.8F, -HUB_HALF, HUB_HALF * 2, 1.4F, HUB_HALF * 2),
				PartPose.ZERO);
		structure.addOrReplaceChild("hub_bottom",
				CubeListBuilder.create().texOffs(0, 0)
						.addBox(-HUB_HALF, -4.2F, -HUB_HALF, HUB_HALF * 2, 1.4F, HUB_HALF * 2),
				PartPose.ZERO);
		// Bearing collars where the spindle meets the chamber: mostly above and below the window's
		// aperture, so they are seen at an angle rather than head-on — which is exactly what makes the
		// assembly look mounted in something instead of suspended in it.
		structure.addOrReplaceChild("collar_top",
				CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 4.2F, -2.0F, 4.0F, 0.7F, 4.0F),
				PartPose.ZERO);
		structure.addOrReplaceChild("collar_bottom",
				CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -4.9F, -2.0F, 4.0F, 0.7F, 4.0F),
				PartPose.ZERO);

		// The core sleeve: a band around the middle of the spindle, wider than the shaft so it reads as a
		// separate part rather than as a painted stripe. It is the green that survives being looked at
		// through the gaps between vanes.
		glow.addOrReplaceChild("core_sleeve",
				CubeListBuilder.create().texOffs(0, 0).addBox(-1.9F, -2.2F, -1.9F, 3.8F, 4.4F, 3.8F),
				PartPose.ZERO);

		float step = (float) (Math.PI * 2.0 / VANE_COUNT);
		for (int i = 0; i < VANE_COUNT; i++) {
			PartPose arm = PartPose.rotation(0.0F, i * step, 0.0F);
			PartPose blade = PartPose.offsetAndRotation(VANE_RADIUS, 0.0F, 0.0F, 0.0F, VANE_CANT, 0.0F);
			// Vane: thin radially, tall enough to sink into both hub discs (they span 2.8..4.2, the vane
			// ±3.4), wide tangentially. That overlap is what makes it visibly ATTACHED at both ends.
			vanes.addOrReplaceChild("arm_" + i, CubeListBuilder.create(), arm)
					.addOrReplaceChild("vane_" + i,
							CubeListBuilder.create().texOffs(0, 0)
									.addBox(-0.5F, -3.4F, -1.6F, 1.0F, 6.8F, 3.2F),
							blade);
			// The hot edge: a strip laid on the vane's outer face, on the SAME pivot and the same cant, so
			// it tracks the blade exactly. Outer rather than inner (both were offered) because the outer
			// face is the one nothing else in the basket can hide — the core sleeve already covers the
			// "seen between the blades" case, and putting both greens where they wink together would leave
			// the rotor with moments of no uranium in it at all.
			glow.addOrReplaceChild("edge_arm_" + i, CubeListBuilder.create(), arm)
					.addOrReplaceChild("edge_" + i,
							CubeListBuilder.create().texOffs(0, 0)
									.addBox(-0.5F, -3.0F, 1.6F, 1.0F, 6.0F, 0.3F),
							blade);
		}

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ThermalCentrifugeBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		Level level = entity.getLevel();
		// Both inputs reach every client that can see the block, which is exactly this renderer's audience.
		// The spin rides the block-entity update packet (the machine pushes it when it crosses a tenth) and
		// LIT is part of the block state. The machine's ContainerData channels would NOT do: they only flow
		// while a menu is open, so a closed GUI would freeze the rotor for everybody else.
		BlockState blockState = entity.getBlockState();
		boolean processing = blockState.hasProperty(LitMachineBlock.LIT)
				&& blockState.getValue(LitMachineBlock.LIT);
		int permille = entity.spinPermille();
		state.angle = RotorSpin.angle(level == null ? 0L : level.getGameTime(), partialTicks,
				permille, processing);
		state.lightCoords = level == null
				? LightCoordsUtil.FULL_BRIGHT
				: LightCoordsUtil.getLightCoords(level, entity.getBlockPos());
		// Three steps up the uranium ramp, and only the top one is a light source. A machine standing by
		// glows faintly at whatever the room's light is; a machine working lights its own chamber, which is
		// the difference the player is meant to read across a base without opening anything.
		if (permille <= 0) {
			state.glowTint = GLOW_STOPPED;
			state.glowLight = state.lightCoords;
		} else if (processing) {
			state.glowTint = GLOW_PROCESSING;
			state.glowLight = LightCoordsUtil.FULL_BRIGHT;
		} else {
			state.glowTint = GLOW_TURNING;
			state.glowLight = state.lightCoords;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		// No early return on a stopped rotor, unlike the condenser's crystal: the rotor is hardware, not a
		// readout. A centrifuge whose rotor vanished the moment the lever went off would read as a broken
		// machine rather than as a switched-off one — standing still IS the "off" state.
		poseStack.pushPose();
		poseStack.translate(CENTRE, CENTRE, CENTRE);
		poseStack.mulPose(Axis.YP.rotation(state.angle));
		collector.submitModel(structureModel, Unit.INSTANCE, poseStack, state.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, STRUCTURE, sprites, 0, state.breakProgress);
		collector.submitModel(vaneModel, Unit.INSTANCE, poseStack, state.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, VANES, sprites, 0, state.breakProgress);
		collector.submitModel(glowModel, Unit.INSTANCE, poseStack, state.glowLight,
				OverlayTexture.NO_OVERLAY, state.glowTint, GLOW, sprites, 0, state.breakProgress);
		poseStack.popPose();
	}

	/** Render state: primitives only, no block-entity reference. */
	public static final class State extends BlockEntityRenderState {
		float angle;
		int glowTint = GLOW_STOPPED;
		int glowLight;
	}
}
