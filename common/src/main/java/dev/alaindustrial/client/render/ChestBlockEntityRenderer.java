package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
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
 * Block-entity renderer shared by every storage-chest tier — a 3D chest model with an animated lid,
 * textured from the mod's own {@code textures/entity/chest/<tier>.png} (64×64 atlases matching the
 * vanilla chest layout). The tiers render identically and differ only in baked model layer and
 * sprite, so one parameterised renderer serves all three; each tier is registered through its
 * {@code iron}/{@code silver}/{@code gold} factory below.
 *
 * <p>Built on the vanilla 26.2 chest rendering pieces:
 * <ul>
 *   <li>{@link ChestModel} — the same lid/lock/bottom model the wooden chest uses, baked from our
 *       own {@link ModelLayerLocation} so the layer can be registered per-loader.</li>
 *   <li>{@link ChestRenderer#modelTransformation} — the per-facing rotation so the chest's front
 *       points the way the block faces.</li>
 *   <li>The {@code 1 - (1-open)³} ease curve on the openness, matching the vanilla chest's lift.</li>
 * </ul>
 *
 * <p><b>Texture source.</b> Each tier texture is submitted exactly like the vanilla chest — through
 * the {@code chest} atlas ({@link Sheets#CHEST_MAPPER}), not as a standalone texture. The 64×64 PNGs
 * under {@code textures/entity/chest/} are picked up by the vanilla {@code DirectoryLister} source of
 * the chest atlas (it scans {@code entity/chest/*} in every namespace), so the sprite ids are
 * {@code alaindustrial:iron} and friends. Submitting via the atlas-sprite overload ({@code SpriteId} +
 * {@code SpriteGetter}) is what the vanilla {@link ChestRenderer#submit} does — it yields the same
 * {@code entityCutoutCull} {@code RenderType} bound to the chest atlas, which has correct depth/cull
 * and mipmap behaviour. Using the standalone {@code Identifier} overload instead renders the raw PNG
 * directly, which on 26.2 leaves a 1px white edge bleeding through overlapping geometry.
 *
 * <p>This class references client-only types ({@link PoseStack}, render state, …) but is loaded only
 * on the physical client — each loader registers it via a {@code dist = CLIENT} entrypoint (Fabric
 * {@code IndustrializationClient} / NeoForge {@code IndustrializationNeoForgeClient}).
 *
 * @param <T> the chest tier's block entity this instance renders
 */
public class ChestBlockEntityRenderer<T extends AbstractChestBlockEntity>
		implements BlockEntityRenderer<T, ChestRenderState> {
	/** Single chest layer baked from {@link ChestModel#createSingleBodyLayer()}, one per tier. */
	public static final ModelLayerLocation IRON_CHEST_LAYER =
			new ModelLayerLocation(Industrialization.id("iron_chest"), "main");
	public static final ModelLayerLocation SILVER_CHEST_LAYER =
			new ModelLayerLocation(Industrialization.id("silver_chest"), "main");
	public static final ModelLayerLocation GOLD_CHEST_LAYER =
			new ModelLayerLocation(Industrialization.id("gold_chest"), "main");

	private static final SpriteId IRON_SPRITE = Sheets.CHEST_MAPPER.apply(Industrialization.id("iron"));
	private static final SpriteId SILVER_SPRITE = Sheets.CHEST_MAPPER.apply(Industrialization.id("silver"));
	private static final SpriteId GOLD_SPRITE = Sheets.CHEST_MAPPER.apply(Industrialization.id("gold"));

	private final ChestModel model;
	private final SpriteGetter sprites;
	private final SpriteId sprite;

	private ChestBlockEntityRenderer(BlockEntityRendererProvider.Context context, ModelLayerLocation layer,
			SpriteId sprite) {
		ModelPart root = context.bakeLayer(layer);
		this.model = new ChestModel(root);
		this.sprites = context.sprites();
		this.sprite = sprite;
	}

	public static ChestBlockEntityRenderer<IronChestBlockEntity> iron(BlockEntityRendererProvider.Context context) {
		return new ChestBlockEntityRenderer<>(context, IRON_CHEST_LAYER, IRON_SPRITE);
	}

	public static ChestBlockEntityRenderer<SilverChestBlockEntity> silver(BlockEntityRendererProvider.Context context) {
		return new ChestBlockEntityRenderer<>(context, SILVER_CHEST_LAYER, SILVER_SPRITE);
	}

	public static ChestBlockEntityRenderer<GoldChestBlockEntity> gold(BlockEntityRendererProvider.Context context) {
		return new ChestBlockEntityRenderer<>(context, GOLD_CHEST_LAYER, GOLD_SPRITE);
	}

	@Override
	public ChestRenderState createRenderState() {
		return new ChestRenderState();
	}

	@Override
	public void extractRenderState(T entity, ChestRenderState state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		// Base fills lightCoords + breakProgress from the entity's position; we add the chest fields.
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		// Fallback for a BE without a level (gizmo/preview/off-world render) — mirror vanilla
		// ChestRenderer, which substitutes a default-facing chest state when hasLevel==false so
		// getValue(FACING) never throws on a state that lacks the property.
		state.facing = entity.hasLevel()
				? entity.getBlockState().getValue(HorizontalMachineBlock.FACING)
				: Direction.NORTH;
		// The mod's chests are never part of a double chest — always SINGLE.
		state.type = ChestType.SINGLE;
		state.open = entity.getOpenNess(partialTicks);
	}

	@Override
	public void submit(ChestRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(ChestRenderer.modelTransformation(state.facing));
		// Vanilla ease curve on the lid lift: open = 1 - (1 - open)^3.
		float open = state.open;
		open = 1.0F - open;
		open = 1.0F - open * open * open;
		// Same overload the vanilla ChestRenderer uses: the atlas-sprite path gives the right
		// RenderType (entityCutoutCull bound to the chest atlas) with correct depth/cull/mipmaps,
		// avoiding the 1px white edge the standalone-Identifier overload produces.
		submitNodeCollector.submitModel(model, open, poseStack,
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
				sprite, sprites, 0, state.breakProgress);
		poseStack.popPose();
	}
}
