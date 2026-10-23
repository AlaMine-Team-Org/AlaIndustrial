package dev.alaindustrial.client.render.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.render.entity.ShiftWorkerModel;
import dev.alaindustrial.client.render.entity.ShiftWorkerRenderState;
import dev.alaindustrial.compat.client.ModelSubmit;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

/**
 * The balloon battery on a shift worker's back and its hose to the helmet (MOD-832, variant E of MOD-828), drawn from
 * one texture shared by every look: the suit's equipment is the same for every worker, only the face differs. It
 * follows the body — crouching, turning — because it is drawn in the body's coordinates.
 *
 * <p>Culled faces: the balloon's front sits on the body's back face (z = 2), and only one of two coplanar faces may be
 * drawn there.
 */
public class ShiftWorkerGearLayer extends RenderLayer<ShiftWorkerRenderState, ShiftWorkerModel> {

	public static final Identifier TEXTURE = Industrialization.id("textures/entity/shift_worker/gear.png");

	private final Model<Unit> gear;

	public ShiftWorkerGearLayer(RenderLayerParent<ShiftWorkerRenderState, ShiftWorkerModel> parent, ModelPart gear) {
		super(parent);
		this.gear = new Model.Simple(gear, RenderTypes::entityCutoutCull);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			ShiftWorkerRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		poseStack.pushPose();
		getParentModel().root().translateAndRotate(poseStack);
		getParentModel().body.translateAndRotate(poseStack);
		// The balloon swings about its hinge on the back (MOD-882); its geometry stays in the body's pixels.
		getParentModel().pack().translateAndRotate(poseStack);
		poseStack.translate(0.0F, 0.0F, -ShiftWorkerModel.PACK_Z / 16.0F);
		ModelSubmit.typed(collector, gear, Unit.INSTANCE, poseStack, RenderTypes.entityCutoutCull(TEXTURE),
				lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
		poseStack.popPose();
	}
}
