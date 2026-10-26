package dev.alaindustrial.client.render.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.render.entity.ShiftWorkerModel;
import dev.alaindustrial.client.render.entity.ShiftWorkerRenderState;
import dev.alaindustrial.compat.client.ModelSubmit;
import dev.alaindustrial.settlement.shift.Profession;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import org.jspecify.annotations.Nullable;

/**
 * The patch of a worker's trade on the outside of its left shoulder (MOD-888; epic MOD-824, decision R151), so the
 * trades read apart from a distance: a small badge, its own layer, drawn only for a worker with a trade. It follows the
 * arm's swing because it is drawn in the arm's coordinates. One texture a trade ({@link #texture}), drawn by
 * {@code tools/model_sources/shift_worker_patch/gen_patches.py}.
 *
 * <p>The geometry is not a registered model layer: one 3 × 3 × 1 box ({@link ShiftWorkerModel#createPatchLayer}),
 * baked here once.
 */
public class ShiftWorkerPatchLayer extends RenderLayer<ShiftWorkerRenderState, ShiftWorkerModel> {

	/** The outer face of the left arm, in its pixels: the classic arm is 4 wide, the slim one 3 (from x = -1). */
	private static final float CLASSIC_OUTSIDE = 3.0F;
	private static final float SLIM_OUTSIDE = 2.0F;
	/** How far below the arm's pivot the badge's middle sits: the shoulder end of the upper arm. */
	private static final float BADGE_Y = 0.5F;

	private final Model<Unit> patch;
	private final Map<Profession, Identifier> textures = new EnumMap<>(Profession.class);

	public ShiftWorkerPatchLayer(RenderLayerParent<ShiftWorkerRenderState, ShiftWorkerModel> parent) {
		super(parent);
		this.patch = new Model.Simple(ShiftWorkerModel.createPatchLayer().bakeRoot(), RenderTypes::entityCutout);
		for (Profession profession : Profession.values()) {
			Identifier texture = texture(profession);
			if (texture != null) {
				textures.put(profession, texture);
			}
		}
	}

	/** The patch texture of {@code profession}; null for a trade with no patch drawn yet. */
	public static @Nullable Identifier texture(Profession profession) {
		return switch (profession) {
			case STOREKEEPER -> Industrialization.id("textures/entity/shift_worker/patch_storekeeper.png");
			case NONE -> null;
		};
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			ShiftWorkerRenderState state, float yRot, float xRot) {
		Identifier texture = textures.get(state.profession);
		if (state.isInvisible || texture == null) {
			return;
		}
		poseStack.pushPose();
		getParentModel().root().translateAndRotate(poseStack);
		getParentModel().leftArm.translateAndRotate(poseStack);
		poseStack.translate((state.slim ? SLIM_OUTSIDE : CLASSIC_OUTSIDE) / 16.0F, BADGE_Y / 16.0F, 0.0F);
		ModelSubmit.typed(collector, patch, Unit.INSTANCE, poseStack, RenderTypes.entityCutout(texture),
				lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
		poseStack.popPose();
	}
}
