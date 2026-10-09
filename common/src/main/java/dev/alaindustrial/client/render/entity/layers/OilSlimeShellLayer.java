package dev.alaindustrial.client.render.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.compat.client.ModelSubmit;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The translucent shell of a cube mob drawn from a given texture (MOD-767, the oil slime). Vanilla's own shell
 * layer is fixed to the green slime's texture, so a slime of another colour needs this one. An invisible
 * slime shows no shell — only its outline while it glows.
 */
public class OilSlimeShellLayer extends RenderLayer<SlimeRenderState, SlimeModel> {

	private final SlimeModel shell;
	private final Identifier texture;

	public OilSlimeShellLayer(RenderLayerParent<SlimeRenderState, SlimeModel> parent, EntityModelSet models,
			Identifier texture) {
		super(parent);
		this.shell = new SlimeModel(models.bakeLayer(ModelLayers.SLIME_OUTER));
		this.texture = texture;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, SlimeRenderState state,
			float yRot, float xRot) {
		boolean outlineOnly = state.isInvisible && state.appearsGlowing();
		if (state.isInvisible && !outlineOnly) {
			return;
		}
		RenderType type = outlineOnly ? RenderTypes.outline(texture) : RenderTypes.entityTranslucent(texture);
		ModelSubmit.typed(collector.order(1), shell, state, poseStack, type, lightCoords,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
	}
}
