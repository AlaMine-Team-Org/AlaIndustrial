package dev.alaindustrial.client.render.entity;

import dev.alaindustrial.settlement.shift.ShiftWorkerLook;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;

/**
 * The body of a shift worker (MOD-832): a player's model — the classic or the slim one, as the look says — drawn from
 * the look's skin, with the helmet on the hat layer and the hair that comes out from under an open helmet as pieces of
 * the head. The balloon on the back, its hose and the number are layers of their own ({@code ShiftWorkerGearLayer},
 * {@code ShiftWorkerNumberLayer}), and so is the window's glass ({@code ShiftWorkerGlassLayer}): glass drawn with the
 * translucent pass of its own, not as half-transparent pixels of the skin (MOD-828 research.md).
 *
 * <p><b>The skin's layout.</b> A player's for the head, body, arms and legs, the helmet on the hat. The jacket, sleeve
 * and trouser layers are not drawn — their parts are replaced by empty ones — and their area of the texture holds the
 * hair pieces: a ponytail at (0, 32), a braid at (0, 40), a bob at (16, 32), curls at (16, 40); and, on a slim
 * skin, the chest piece at (42, 32).
 *
 * <p><b>The chest piece</b> (MOD-851) tells a woman's look from a man's in profile, as a part of the suit rather than
 * of a body: a block 8 × 4 × 1 on the front of the slim body only, over the body's rows 2 to 5 — its front is those
 * rows of the skin, so the charge panel stands out one pixel with it. It is a child of the body and moves with it. Its
 * back face lies on the body's front face, but always behind its own front face, so the two never show together.
 *
 * <p><b>The hair stops above the number plate.</b> The ponytail and the braid hang from the helmet's edge to y = 3 of
 * the head (the body's y = 3 while the head is level), where the plate on the balloon begins; the bob and the curls
 * stay around the head, inside the open band of the helmet.
 *
 * <p><b>Movement</b> (MOD-882) is a humanoid's with {@link ShiftWorkerMotion} on top: the heavy suit's walk, a
 * breathing stand, a wave for a player who came up. The balloon hangs from an empty part {@code pack} on the body's
 * back (z = 2, the balloon's top front edge); the gear and the number layers draw through it, so they swing with it.
 * A hibernating worker — its goals off — and a sleeping, riding, swimming or crouching one keep the humanoid's pose.
 */
public class ShiftWorkerModel extends HumanoidModel<ShiftWorkerRenderState> {

	/** Texture size of a look's skin. */
	public static final int TEXTURE_SIZE = 64;
	/** Where the balloon hangs from: the body's back face, in the body's pixels. */
	public static final float PACK_Z = 2.0F;

	private final ModelPart tail;
	private final ModelPart braid;
	private final ModelPart bob;
	private final ModelPart curls;
	private final ModelPart pack;
	private final ShiftWorkerMotion motion = new ShiftWorkerMotion();

	public ShiftWorkerModel(ModelPart root) {
		super(root);
		this.tail = head.getChild("tail");
		this.braid = head.getChild("braid");
		this.bob = head.getChild("bob");
		this.curls = head.getChild("curls");
		this.pack = body.getChild("pack");
	}

	/** The balloon's hinge on the back: the gear and the number layers draw after its pose. */
	public ModelPart pack() {
		return pack;
	}

	/** The geometry of a worker with the {@code slim} or the classic body. */
	public static LayerDefinition createLayer(boolean slim) {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
		PartDefinition root = mesh.getRoot();
		// The overlay layers of a player are not drawn: their texture area holds the hair pieces below.
		root.getChild("body").addOrReplaceChild("jacket", CubeListBuilder.create(), PartPose.ZERO);
		root.getChild("left_arm").addOrReplaceChild("left_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		root.getChild("right_arm").addOrReplaceChild("right_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		root.getChild("left_leg").addOrReplaceChild("left_pants", CubeListBuilder.create(), PartPose.ZERO);
		root.getChild("right_leg").addOrReplaceChild("right_pants", CubeListBuilder.create(), PartPose.ZERO);
		// The balloon's hinge: an empty part at the top of the back, which the gear layer draws after.
		root.getChild("body").addOrReplaceChild("pack", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, PACK_Z));
		if (slim) {
			// The chest piece: on the body's front (z = -2), one pixel out, over its rows 2 to 5.
			root.getChild("body").addOrReplaceChild("chest", CubeListBuilder.create().texOffs(42, 32)
					.addBox(-4.0F, 2.0F, -3.0F, 8.0F, 4.0F, 1.0F), PartPose.ZERO);
		}

		PartDefinition head = root.getChild("head");
		// Down the back from under the helmet's edge (the hat's back face is at z = 4.5) to the top of the plate.
		head.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-1.0F, -2.0F, 4.55F, 2.0F, 5.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("braid", CubeListBuilder.create().texOffs(0, 40)
				.addBox(-1.0F, -2.0F, 4.55F, 2.0F, 5.0F, 1.0F), PartPose.ZERO);
		// Around the head in the helmet's open lower band, just inside the hat's planes (x and z at 4.5).
		head.addOrReplaceChild("bob", CubeListBuilder.create().texOffs(16, 32)
				.addBox(-4.0F, -1.5F, -1.0F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.4F)), PartPose.ZERO);
		head.addOrReplaceChild("curls", CubeListBuilder.create().texOffs(16, 40)
				.addBox(-4.0F, -2.0F, -1.0F, 8.0F, 3.0F, 5.0F, new CubeDeformation(0.45F)), PartPose.ZERO);
		return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	/**
	 * A trade's patch (MOD-888, R151): a 3 × 3 badge one pixel thick, in the left arm's coordinates with its outer face
	 * at x = 1; {@code ShiftWorkerPatchLayer} moves it onto the outside of the arm's shoulder end. Its two 3 × 3 sides
	 * (u 0 and u 4 of a 16 × 16 texture) carry the badge; whichever faces out shows it.
	 */
	public static LayerDefinition createPatchLayer() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("patch", CubeListBuilder.create().texOffs(0, 0)
				.addBox(0.0F, -1.0F, -1.5F, 1.0F, 3.0F, 3.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 16, 16);
	}

	/**
	 * The glass of the «window» helmet: one face, 7 × 7, just behind the helmet's front (the hat's front is at
	 * z = -4.5, the face at -4); its edges are hidden behind the helmet's copper frame. Drawn by
	 * {@code ShiftWorkerGlassLayer} from {@code window_glass.png}, in the translucent pass.
	 */
	public static LayerDefinition createGlassLayer() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("glass", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-3.5F, -7.5F, -4.3F, 7.0F, 7.0F, 0.0F, EnumSet.of(Direction.NORTH)), PartPose.ZERO);
		return LayerDefinition.create(mesh, 16, 16);
	}

	/**
	 * The balloon battery on the back and its hose to the helmet (variant E of MOD-828), in the body's coordinates.
	 * The balloon's back face (z = 5) carries the number plate, rows 3 to 8; drawn by {@code ShiftWorkerGearLayer} from
	 * {@code gear.png}.
	 */
	public static LayerDefinition createGearLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("balloon", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-4.0F, 0.0F, 2.0F, 8.0F, 10.0F, 3.0F), PartPose.ZERO);
		root.addOrReplaceChild("hose", CubeListBuilder.create().texOffs(24, 0)
				.addBox(1.0F, -4.0F, 4.6F, 1.0F, 4.0F, 1.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32);
	}

	/**
	 * The head alone — the classic body's head with its helmet on the hat and no hair, the base look an open
	 * helmet shows — for a screen to draw with {@code GuiGraphicsExtractor.skin} (MOD-844: the reception capsule
	 * puts it beside its worker count). The head sits inside a holder part, so the screen can move it to the middle
	 * of its picture by the holder's {@code y} without touching the head's own pose; a fresh bake, so nothing a
	 * screen sets reaches the worlds' workers.
	 */
	public static Model.Simple guiHead(EntityModelSet models) {
		ModelPart head = models.bakeLayer(ShiftWorkerRenderer.CLASSIC_LAYER).getChild("head");
		for (String hair : List.of("tail", "braid", "bob", "curls")) {
			head.getChild(hair).visible = false;
		}
		return new Model.Simple(new ModelPart(List.of(), Map.of("head", head)), RenderTypes::entityTranslucent);
	}

	@Override
	public void setupAnim(ShiftWorkerRenderState state) {
		super.setupAnim(state);
		boolean open = state.helmet == ShiftWorkerLook.Helmet.OPEN;
		tail.visible = open && state.hair == ShiftWorkerLook.Hair.TAIL;
		braid.visible = open && state.hair == ShiftWorkerLook.Hair.BRAID;
		bob.visible = open && state.hair == ShiftWorkerLook.Hair.BOB;
		curls.visible = open && state.hair == ShiftWorkerLook.Hair.CURLS;
		if (state.hibernating || state.isPassenger || state.isCrouching || state.isVisuallySwimming
				|| state.isFallFlying || state.hasPose(Pose.SLEEPING)) {
			return;
		}
		motion.compute(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.greetTicks,
				state.watching);
		head.xRot += motion.headX;
		head.yRot += motion.headY;
		head.zRot += motion.headZ;
		body.yRot += motion.bodyY;
		body.zRot += motion.bodyZ;
		rightArm.xRot += motion.rightArmX;
		rightArm.zRot += motion.rightArmZ;
		leftArm.xRot += motion.leftArmX;
		leftArm.zRot += motion.leftArmZ;
		rightLeg.xRot += motion.rightLegX;
		leftLeg.xRot += motion.leftLegX;
		if (motion.greet > 0.0F) {
			rightArm.xRot += (motion.greetArmX - rightArm.xRot) * motion.greet;
			rightArm.yRot -= rightArm.yRot * motion.greet;
			rightArm.zRot += (motion.greetArmZ - rightArm.zRot) * motion.greet;
		}
		head.y += motion.lift;
		body.y += motion.lift;
		rightArm.y += motion.lift;
		leftArm.y += motion.lift;
		pack.xRot = motion.packX;
		pack.zRot = motion.packZ;
	}
}
