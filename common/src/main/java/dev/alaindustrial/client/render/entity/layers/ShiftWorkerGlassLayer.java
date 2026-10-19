package dev.alaindustrial.client.render.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.render.entity.ShiftWorkerModel;
import dev.alaindustrial.client.render.entity.ShiftWorkerRenderState;
import dev.alaindustrial.compat.client.ModelSubmit;
import dev.alaindustrial.settlement.shift.ShiftWorkerLook;
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
 * The glass of the «window» helmet (MOD-832): its own face of the model, drawn in the translucent pass from its own
 * texture — not half-transparent pixels in the skin, which a player model's hat layer does not promise to draw
 * (MOD-828 research.md). It follows the head. Submitted on a later order than the body, like the oil slime's shell, so
 * the face behind it is already drawn when the glass goes over it.
 */
public class ShiftWorkerGlassLayer extends RenderLayer<ShiftWorkerRenderState, ShiftWorkerModel> {

	public static final Identifier TEXTURE = Industrialization.id("textures/entity/shift_worker/window_glass.png");

	private final Model<Unit> glass;

	public ShiftWorkerGlassLayer(RenderLayerParent<ShiftWorkerRenderState, ShiftWorkerModel> parent, ModelPart glass) {
		super(parent);
		this.glass = new Model.Simple(glass, RenderTypes::entityTranslucent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			ShiftWorkerRenderState state, float yRot, float xRot) {
		if (state.isInvisible || state.helmet != ShiftWorkerLook.Helmet.WINDOW) {
			return;
		}
		poseStack.pushPose();
		getParentModel().root().translateAndRotate(poseStack);
		getParentModel().head.translateAndRotate(poseStack);
		ModelSubmit.typed(collector.order(1), glass, Unit.INSTANCE, poseStack, RenderTypes.entityTranslucent(TEXTURE),
				lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
		poseStack.popPose();
	}
}
