package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.AbstractModChestBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.block.entity.ElectrumChestBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.MultiblockChestResources;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Block-entity renderer shared by every storage-chest tier — a 3D chest model with an animated lid,
 * textured from the mod's own {@code textures/entity/chest/<tier>[_left|_right].png} (64×64 atlases
 * matching the vanilla chest layout). Since MOD-391 a tier renders in three variants — single, and
 * the left/right halves of a double — held in the same vanilla triple the real {@code ChestRenderer}
 * uses, {@link MultiblockChestResources}, for both the baked models and the sprites; one
 * parameterised renderer still serves all three tiers via the {@code iron}/{@code silver}/
 * {@code gold} factories below.
 *
 * <p>Built on the vanilla 26.2 chest rendering pieces:
 * <ul>
 *   <li>{@link ChestModel} — {@code createSingleBodyLayer} plus the 15-wide
 *       {@code createDoubleBodyLeft/RightLayer} halves, baked from our own
 *       {@link ModelLayerLocation}s so the layers can be registered per-loader.</li>
 *   <li>{@link ChestRenderer#modelTransformation} — the per-facing rotation.</li>
 *   <li>The {@code 1 - (1-open)³} ease curve on the openness.</li>
 *   <li>{@link BrightnessCombiner} — a double's light coords come from the brighter half, so the
 *       long chest does not change brightness at the seam.</li>
 * </ul>
 *
 * <p><b>Openness of a double is the max of both halves</b> — the vanilla
 * {@code ChestBlock.opennessCombiner} cannot be reused (it is typed to the vanilla
 * {@code ChestBlockEntity}, which our tiers do not extend), so {@link #opennessCombiner} is the same
 * five lines over {@link AbstractChestBlockEntity}.
 *
 * <p><b>Texture source.</b> Each variant is submitted through the {@code chest} atlas
 * ({@link Sheets#CHEST_MAPPER}) exactly like the vanilla chest; the {@code _left}/{@code _right}
 * PNGs under {@code textures/entity/chest/} are picked up by the vanilla {@code DirectoryLister}
 * source automatically, like the singles always were. The atlas-sprite overload gives the correct
 * {@code entityCutoutCull} depth/cull/mipmap behaviour (the standalone-Identifier overload bleeds a
 * 1px white edge — see git history).
 *
 * <p>This class references client-only types but is loaded only on the physical client — each loader
 * registers it via a {@code dist = CLIENT} entrypoint.
 *
 * @param <T> the chest tier's block entity this instance renders
 */
public class ChestBlockEntityRenderer<T extends AbstractChestBlockEntity>
		implements BlockEntityRenderer<T, ChestRenderState> {
	public static final ModelLayerLocation IRON_CHEST_LAYER =
			new ModelLayerLocation(Industrialization.id("iron_chest"), "main");
	public static final ModelLayerLocation IRON_CHEST_LEFT_LAYER =
			new ModelLayerLocation(Industrialization.id("iron_chest_left"), "main");
	public static final ModelLayerLocation IRON_CHEST_RIGHT_LAYER =
			new ModelLayerLocation(Industrialization.id("iron_chest_right"), "main");
	public static final ModelLayerLocation SILVER_CHEST_LAYER =
			new ModelLayerLocation(Industrialization.id("silver_chest"), "main");
	public static final ModelLayerLocation SILVER_CHEST_LEFT_LAYER =
			new ModelLayerLocation(Industrialization.id("silver_chest_left"), "main");
	public static final ModelLayerLocation SILVER_CHEST_RIGHT_LAYER =
			new ModelLayerLocation(Industrialization.id("silver_chest_right"), "main");
	public static final ModelLayerLocation GOLD_CHEST_LAYER =
			new ModelLayerLocation(Industrialization.id("gold_chest"), "main");
	public static final ModelLayerLocation GOLD_CHEST_LEFT_LAYER =
			new ModelLayerLocation(Industrialization.id("gold_chest_left"), "main");
	public static final ModelLayerLocation GOLD_CHEST_RIGHT_LAYER =
			new ModelLayerLocation(Industrialization.id("gold_chest_right"), "main");
	public static final ModelLayerLocation ELECTRUM_CHEST_LAYER =
			new ModelLayerLocation(Industrialization.id("electrum_chest"), "main");
	public static final ModelLayerLocation ELECTRUM_CHEST_LEFT_LAYER =
			new ModelLayerLocation(Industrialization.id("electrum_chest_left"), "main");
	public static final ModelLayerLocation ELECTRUM_CHEST_RIGHT_LAYER =
			new ModelLayerLocation(Industrialization.id("electrum_chest_right"), "main");
	public static final ModelLayerLocation SHIELDING_CHEST_LAYER =
			new ModelLayerLocation(Industrialization.id("shielding_chest"), "main");
	public static final ModelLayerLocation SHIELDING_CHEST_LEFT_LAYER =
			new ModelLayerLocation(Industrialization.id("shielding_chest_left"), "main");
	public static final ModelLayerLocation SHIELDING_CHEST_RIGHT_LAYER =
			new ModelLayerLocation(Industrialization.id("shielding_chest_right"), "main");

	private static final MultiblockChestResources<SpriteId> IRON_SPRITES = sprites("iron");
	private static final MultiblockChestResources<SpriteId> SILVER_SPRITES = sprites("silver");
	private static final MultiblockChestResources<SpriteId> GOLD_SPRITES = sprites("gold");
	private static final MultiblockChestResources<SpriteId> ELECTRUM_SPRITES = sprites("electrum");
	// NOTE the argument is the bare tier name, NOT the block id: it names the PNG under
	// textures/entity/chest/, while the ModelLayerLocations above carry the "_chest" id.
	private static final MultiblockChestResources<SpriteId> SHIELDING_SPRITES = sprites("shielding");

	private final MultiblockChestResources<ChestModel> models;
	private final SpriteGetter spriteGetter;
	private final MultiblockChestResources<SpriteId> sprites;

	private static MultiblockChestResources<SpriteId> sprites(String tier) {
		return new MultiblockChestResources<>(
				Sheets.CHEST_MAPPER.apply(Industrialization.id(tier)),
				Sheets.CHEST_MAPPER.apply(Industrialization.id(tier + "_left")),
				Sheets.CHEST_MAPPER.apply(Industrialization.id(tier + "_right")));
	}

	private ChestBlockEntityRenderer(BlockEntityRendererProvider.Context context,
			MultiblockChestResources<ModelLayerLocation> layers, MultiblockChestResources<SpriteId> sprites) {
		this.models = layers.map(layer -> new ChestModel(context.bakeLayer(layer)));
		this.spriteGetter = context.sprites();
		this.sprites = sprites;
	}

	public static ChestBlockEntityRenderer<IronChestBlockEntity> iron(BlockEntityRendererProvider.Context context) {
		return new ChestBlockEntityRenderer<>(context,
				new MultiblockChestResources<>(IRON_CHEST_LAYER, IRON_CHEST_LEFT_LAYER, IRON_CHEST_RIGHT_LAYER),
				IRON_SPRITES);
	}

	public static ChestBlockEntityRenderer<SilverChestBlockEntity> silver(BlockEntityRendererProvider.Context context) {
		return new ChestBlockEntityRenderer<>(context,
				new MultiblockChestResources<>(SILVER_CHEST_LAYER, SILVER_CHEST_LEFT_LAYER, SILVER_CHEST_RIGHT_LAYER),
				SILVER_SPRITES);
	}

	public static ChestBlockEntityRenderer<GoldChestBlockEntity> gold(BlockEntityRendererProvider.Context context) {
		return new ChestBlockEntityRenderer<>(context,
				new MultiblockChestResources<>(GOLD_CHEST_LAYER, GOLD_CHEST_LEFT_LAYER, GOLD_CHEST_RIGHT_LAYER),
				GOLD_SPRITES);
	}

	public static ChestBlockEntityRenderer<ElectrumChestBlockEntity> electrum(
			BlockEntityRendererProvider.Context context) {
		return new ChestBlockEntityRenderer<>(context,
				new MultiblockChestResources<>(ELECTRUM_CHEST_LAYER, ELECTRUM_CHEST_LEFT_LAYER,
						ELECTRUM_CHEST_RIGHT_LAYER),
				ELECTRUM_SPRITES);
	}

	public static ChestBlockEntityRenderer<ShieldingChestBlockEntity> shielding(
			BlockEntityRendererProvider.Context context) {
		return new ChestBlockEntityRenderer<>(context,
				new MultiblockChestResources<>(SHIELDING_CHEST_LAYER, SHIELDING_CHEST_LEFT_LAYER,
						SHIELDING_CHEST_RIGHT_LAYER),
				SHIELDING_SPRITES);
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
		boolean hasLevel = entity.hasLevel();
		// Fallback for a BE without a level (gizmo/preview/off-world render) — mirror vanilla
		// ChestRenderer, which substitutes a default-facing single chest so getValue never throws.
		BlockState blockState = hasLevel ? entity.getBlockState() : null;
		state.facing = hasLevel ? blockState.getValue(HorizontalMachineBlock.FACING) : Direction.NORTH;
		state.type = hasLevel && blockState.hasProperty(AbstractModChestBlock.TYPE)
				? blockState.getValue(AbstractModChestBlock.TYPE)
				: ChestType.SINGLE;

		DoubleBlockCombiner.NeighborCombineResult<? extends AbstractChestBlockEntity> combineResult;
		if (hasLevel && blockState.getBlock() instanceof AbstractModChestBlock chestBlock) {
			combineResult = chestBlock.combine(blockState, entity.getLevel(), entity.getBlockPos());
		} else {
			combineResult = DoubleBlockCombiner.Combiner::acceptNone;
		}
		state.open = combineResult.apply(opennessCombiner(entity)).get(partialTicks);
		if (state.type != ChestType.SINGLE) {
			// A double is one long model — light it from the brighter half or the seam flickers.
			state.lightCoords = combineResult.apply(new BrightnessCombiner<AbstractChestBlockEntity>())
					.applyAsInt(state.lightCoords);
		}
	}

	/**
	 * Both lids of a double lift together: the pair's openness is the max of the halves. Our own
	 * five lines because the vanilla {@code ChestBlock.opennessCombiner} is typed to the vanilla
	 * {@code ChestBlockEntity}.
	 */
	private static DoubleBlockCombiner.Combiner<AbstractChestBlockEntity, Float2FloatFunction> opennessCombiner(
			LidBlockEntity fallback) {
		return new DoubleBlockCombiner.Combiner<>() {
			@Override
			public Float2FloatFunction acceptDouble(AbstractChestBlockEntity first, AbstractChestBlockEntity second) {
				return partialTicks -> Math.max(first.getOpenNess(partialTicks), second.getOpenNess(partialTicks));
			}

			@Override
			public Float2FloatFunction acceptSingle(AbstractChestBlockEntity single) {
				return single::getOpenNess;
			}

			@Override
			public Float2FloatFunction acceptNone() {
				return fallback::getOpenNess;
			}
		};
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
		// RenderType (entityCutoutCull bound to the chest atlas) with correct depth/cull/mipmaps.
		submitNodeCollector.submitModel(models.select(state.type), open, poseStack,
				state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
				sprites.select(state.type), spriteGetter, 0, state.breakProgress);
		poseStack.popPose();
	}
}
