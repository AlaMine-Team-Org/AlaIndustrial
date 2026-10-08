package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.client.ModelSubmit;
import dev.alaindustrial.compat.client.Poses;
import dev.alaindustrial.entity.ChestBoatVariants;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.model.object.boat.RaftModel;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import org.jspecify.annotations.Nullable;

/**
 * Renders a {@code ModChestBoat} (MOD-785) by composition: the vanilla model and texture of the plain
 * boat of its wood, plus the mod chest's own {@link ChestModel} textured from the chest atlas — the same
 * model and sprite the chest block uses. No per-pair entity texture exists: N woods × M chests are drawn
 * from the N boat textures vanilla ships and the M chest textures the mod already has.
 *
 * <p><b>Placement.</b> The vanilla chest boat draws a 12×12×12 px chest on the stern (model x −14…−2,
 * floor at y = 3 in the boat's y-down model space, latch facing the rider; the chest raft has it at
 * y = −2.1, on top of its thicker deck, and no water patch). The chest model is a
 * 14×14×14 block-space model, so it is scaled by 12/14, turned upright (the boat pose is flipped by a
 * 180° turn about Z) and turned so the latch faces the bow like the vanilla one.
 */
public class ModChestBoatRenderer extends AbstractBoatRenderer {
	/** Chest footprint the vanilla chest boat uses, in px; the mod chest model is 14 px wide. */
	private static final float CHEST_SCALE = 12.0F / 14.0F;
	/** Centre of the vanilla boat chest on the model x axis, and its floor on the y-down axis, in blocks. */
	private static final float CHEST_CENTRE_X = -8.0F / 16.0F;
	private static final float BOAT_CHEST_FLOOR_Y = 3.0F / 16.0F;
	private static final float RAFT_CHEST_FLOOR_Y = -2.1F / 16.0F;

	private final EntityModel<BoatRenderState> model;
	private final Model.@Nullable Simple waterPatchModel;
	private final float chestFloorY;
	private final ChestModel chestModel;
	private final SpriteGetter sprites;
	private final SpriteId chestSprite;

	private ModChestBoatRenderer(EntityRendererProvider.Context context, ModelLayerLocation boatLayer, boolean raft,
			ModelLayerLocation chestLayer, SpriteId chestSprite) {
		// The plain boat's texture, as vanilla derives it from the layer: textures/entity/boat/<wood>.png.
		super(context, boatLayer.model().withPath(p -> "textures/entity/" + p + ".png"));
		ModelPart boatRoot = context.bakeLayer(boatLayer);
		this.model = raft ? new RaftModel(boatRoot) : new BoatModel(boatRoot);
		this.waterPatchModel = raft ? null : new Model.Simple(context.bakeLayer(ModelLayers.BOAT_WATER_PATCH),
				t -> RenderTypes.waterMask());
		this.chestFloorY = raft ? RAFT_CHEST_FLOOR_Y : BOAT_CHEST_FLOOR_Y;
		this.chestModel = new ChestModel(context.bakeLayer(chestLayer));
		this.sprites = context.getSprites();
		this.chestSprite = chestSprite;
	}

	/** The renderer of one wood × chest pair: the chest's own single-chest layer, baked for its block too. */
	public static ModChestBoatRenderer create(EntityRendererProvider.Context context,
			ChestBoatVariants.Variant variant) {
		ChestBoatVariants.Wood wood = variant.wood();
		// Vanilla registers the plain boat layer of every wood as boat/<wood> (the bamboo raft too).
		ModelLayerLocation boatLayer = new ModelLayerLocation(
				Identifier.withDefaultNamespace("boat/" + wood.path()), "main");
		String chest = variant.chest().path();
		ModelLayerLocation chestLayer = new ModelLayerLocation(Industrialization.id(chest + "_chest"), "main");
		return new ModChestBoatRenderer(context, boatLayer, wood.raft(), chestLayer,
				Sheets.CHEST_MAPPER.apply(Industrialization.id(chest)));
	}

	@Override
	protected EntityModel<BoatRenderState> model() {
		return model;
	}

	@Override
	protected void submitTypeAdditions(BoatRenderState state, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, int lightCoords) {
		// Same as vanilla BoatRenderer: the water mask keeps the surface from showing inside the hull.
		if (waterPatchModel != null && !state.isUnderWater) {
			ModelSubmit.textured(submitNodeCollector, waterPatchModel, Unit.INSTANCE, poseStack, texture, lightCoords,
					OverlayTexture.NO_OVERLAY, state.outlineColor);
		}
		poseStack.pushPose();
		poseStack.translate(CHEST_CENTRE_X, chestFloorY, 0.0F);
		Poses.rotate(poseStack, Axis.ZP.rotationDegrees(180.0F));
		poseStack.scale(CHEST_SCALE, CHEST_SCALE, CHEST_SCALE);
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(-90.0F));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		// Lid closed, as on the vanilla chest boat.
		ModelSubmit.withCrumbling(submitNodeCollector, chestModel, 0.0F, poseStack, lightCoords,
				OverlayTexture.NO_OVERLAY, -1, chestSprite, sprites, state.outlineColor, null);
		poseStack.popPose();
	}
}
