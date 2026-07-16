package dev.alaindustrial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.GoldChestBlock;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Block-entity renderer for the gold chest — a 3D chest model with an animated lid, textured with
 * the mod's own {@code textures/entity/chest/gold.png} (a 64×64 atlas matching the vanilla chest
 * layout, recoloured to gold). Structurally identical to
 * {@link IronChestBlockEntityRenderer} / {@link SilverChestBlockEntityRenderer}; only the model layer
 * + sprite id differ.
 *
 * <p>Texture source: the gold texture is submitted through the {@code chest} atlas
 * ({@link Sheets#CHEST_MAPPER}), sprite id {@code alaindustrial:gold}, picked up by the vanilla
 * {@code DirectoryLister} source of the chest atlas (it scans {@code entity/chest/*}). The atlas-sprite
 * overload is used (not the standalone {@code Identifier} one) to get the correct
 * {@code entityCutoutCull} {@code RenderType} with depth/cull/mipmaps — see
 * {@link IronChestBlockEntityRenderer} for the detailed rationale.
 */
public class GoldChestBlockEntityRenderer implements BlockEntityRenderer<GoldChestBlockEntity, ChestRenderState> {
	/** Single chest layer baked from {@link ChestModel#createSingleBodyLayer()}. */
	public static final ModelLayerLocation GOLD_CHEST_LAYER =
			new ModelLayerLocation(Industrialization.id("gold_chest"), "main");
	/** Sprite id for the gold chest texture inside the chest atlas (entity/chest/gold). */
	private static final SpriteId SPRITE = Sheets.CHEST_MAPPER.apply(Industrialization.id("gold"));

	private final ChestModel model;
	private final SpriteGetter sprites;

	public GoldChestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		ModelPart root = context.bakeLayer(GOLD_CHEST_LAYER);
		this.model = new ChestModel(root);
		this.sprites = context.sprites();
	}

	@Override
	public ChestRenderState createRenderState() {
		return new ChestRenderState();
	}

	@Override
	public void extractRenderState(GoldChestBlockEntity entity, ChestRenderState state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		// Base fills lightCoords + breakProgress from the entity's position; we add the chest fields.
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		// Fallback for a BE without a level (gizmo/preview/off-world render) — mirror vanilla
		// ChestRenderer, which substitutes a default-facing chest state when hasLevel==false so
		// getValue(FACING) never throws on a state that lacks the property.
		Direction facing = entity.hasLevel()
				? entity.getBlockState().getValue(GoldChestBlock.FACING)
				: Direction.NORTH;
		state.facing = facing;
		// The gold chest is never part of a double chest — always SINGLE.
		state.type = ChestType.SINGLE;
		state.open = entity.getOpenNess(partialTicks);
	}

	@Override
	public void submit(ChestRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(modelTransformation(state.facing));
		// Vanilla ease curve on the lid lift: open = 1 - (1 - open)^3.
		float open = state.open;
		open = 1.0F - open;
		open = 1.0F - open * open * open;
		// Same overload the vanilla ChestRenderer uses: the atlas-sprite path gives the right
		// RenderType (entityCutoutCull bound to the chest atlas) with correct depth/cull/mipmaps,
		// avoiding the 1px white edge the standalone-Identifier overload produces.
		submitNodeCollector.submitModel(model, open, poseStack,
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
				SPRITE, sprites, 0, state.breakProgress);
		poseStack.popPose();
	}

	/**
	 * The vanilla chest's per-facing rotation (yaw around the centre-top pivot). Re-declared here
	 * as a thin wrapper so this renderer is self-contained without reaching into
	 * {@link ChestRenderer}'s static {@code modelTransformation}.
	 */
	private static Transformation modelTransformation(Direction facing) {
		return ChestRenderer.modelTransformation(facing);
	}
}
