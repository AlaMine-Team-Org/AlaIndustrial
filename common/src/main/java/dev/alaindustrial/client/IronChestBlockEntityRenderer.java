package dev.alaindustrial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.IronChestBlock;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Block-entity renderer for the iron chest — a 3D chest model with an animated lid, textured with
 * the mod's own {@code textures/entity/chest/iron.png} (a 64×64 atlas matching the vanilla chest
 * layout, recoloured to iron). Built on the vanilla 26.2 chest rendering pieces:
 * <ul>
 *   <li>{@link ChestModel} — the same lid/lock/bottom model the wooden chest uses, baked from our
 *       own {@link ModelLayerLocation} so the layer can be registered per-loader.</li>
 *   <li>{@link ChestRenderer#modelTransformation} — the per-facing rotation so the chest's front
 *       points the way the block faces.</li>
 *   <li>The {@code 1 - (1-open)³} ease curve on the openness, matching the vanilla chest's lift.</li>
 * </ul>
 *
 * <p>Texture source: the iron texture is submitted exactly like the vanilla chest — through the
 * {@code chest} atlas ({@link Sheets#CHEST_MAPPER}), not as a standalone texture. The 64×64 PNG at
 * {@code textures/entity/chest/iron.png} is picked up by the vanilla {@code DirectoryLister} source
 * of the chest atlas (it scans {@code entity/chest/*} in every namespace), so the sprite id is
 * {@code alaindustrial:iron}. Submitting via the atlas-sprite overload ({@code SpriteId} +
 * {@code SpriteGetter}) is what the vanilla {@link ChestRenderer#submit} does — it yields the same
 * {@code entityCutoutCull} {@code RenderType} bound to the chest atlas, which has correct depth/cull
 * and mipmap behaviour. Using the standalone
 * {@code Identifier} overload instead renders the raw PNG directly, which on 26.2 leaves a 1px
 * white edge bleeding through overlapping geometry (visible through the player/blocks).
 *
 * <p>This class references client-only types ({@link PoseStack}, render state, …) but is loaded
 * only on the physical client — each loader registers it via a {@code dist = CLIENT} entrypoint
 * (Fabric {@code IndustrializationClient} / NeoForge {@code IndustrializationNeoForgeClient}).
 */
public class IronChestBlockEntityRenderer implements BlockEntityRenderer<IronChestBlockEntity, ChestRenderState> {
	/** Single chest layer baked from {@link ChestModel#createSingleBodyLayer()}. */
	public static final ModelLayerLocation IRON_CHEST_LAYER =
			new ModelLayerLocation(Industrialization.id("iron_chest"), "main");
	/** Sprite id for the iron chest texture inside the chest atlas (entity/chest/iron). */
	private static final SpriteId SPRITE = Sheets.CHEST_MAPPER.apply(Industrialization.id("iron"));

	private final ChestModel model;
	private final SpriteGetter sprites;

	public IronChestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		ModelPart root = context.bakeLayer(IRON_CHEST_LAYER);
		this.model = new ChestModel(root);
		this.sprites = context.sprites();
	}

	@Override
	public ChestRenderState createRenderState() {
		return new ChestRenderState();
	}

	@Override
	public void extractRenderState(IronChestBlockEntity entity, ChestRenderState state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		// Base fills lightCoords + breakProgress from the entity's position; we add the chest fields.
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		// Fallback for a BE without a level (gizmo/preview/off-world render) — mirror vanilla
		// ChestRenderer, which substitutes a default-facing chest state when hasLevel==false so
		// getValue(FACING) never throws on a state that lacks the property.
		Direction facing = entity.hasLevel()
				? entity.getBlockState().getValue(IronChestBlock.FACING)
				: Direction.NORTH;
		state.facing = facing;
		// The iron chest is never part of a double chest — always SINGLE.
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

