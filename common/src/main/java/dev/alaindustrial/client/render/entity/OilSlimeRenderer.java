package dev.alaindustrial.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.render.entity.layers.OilSlimeShellLayer;
import dev.alaindustrial.entity.OilSlime;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.entity.AbstractCubeMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;

/**
 * The oil slime (MOD-767): vanilla's slime geometry — the {@code SLIME} layer for the core and eyes, the
 * {@code SLIME_OUTER} layer for the shell — with the mod's own texture. It extends the cube-mob renderer rather
 * than {@code SlimeRenderer} because the vanilla slime renderer adds a shell layer whose texture is the green
 * slime's; {@link OilSlimeShellLayer} draws the shell from this texture instead.
 */
public class OilSlimeRenderer extends AbstractCubeMobRenderer<OilSlime, SlimeRenderState, SlimeModel> {

	/** One 64 × 32 sheet for both layers, laid out like the vanilla slime's. */
	public static final Identifier TEXTURE = Industrialization.id("textures/entity/oil_slime.png");

	public OilSlimeRenderer(EntityRendererProvider.Context context) {
		super(context, new SlimeModel(context.bakeLayer(ModelLayers.SLIME)));
		addLayer(new OilSlimeShellLayer(this, context.getModelSet(), TEXTURE));
	}

	@Override
	protected void scale(SlimeRenderState state, PoseStack poseStack) {
		downscaleSlightly(poseStack);
		super.scale(state, poseStack);
	}

	@Override
	public Identifier getTextureLocation(SlimeRenderState state) {
		return TEXTURE;
	}

	@Override
	public SlimeRenderState createRenderState() {
		return new SlimeRenderState();
	}
}
