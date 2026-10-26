package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.block.WarehouseShelfBlock;
import dev.alaindustrial.block.entity.WarehouseShelfBlockEntity;
import dev.alaindustrial.compat.client.Poses;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The warehouse shelf's bin cards (MOD-885, R157): the inventory icon of each of the four kinds the shelf holds the
 * most of, drawn on the cream card of its bin. The biggest kind is on the top-left card as the player faces the shelf,
 * then top-right, bottom-left, bottom-right; a card with no kind stays blank.
 *
 * <p>The list comes from {@link WarehouseShelfBlockEntity#cards()}, which the server sends in the update tag only
 * when it changes; this renderer only draws it. The icon is the inventory one ({@link ItemDisplayContext#GUI}) with
 * its depth flattened onto the card, as the monitor panel draws its watched item, and it is lit by the air in front of
 * the shelf.
 *
 * <p>The card centres are the model's ({@code tools/model_sources/warehouse_shelf/build_warehouse_shelf.py}, front =
 * north at z = 0): columns at x = 11.15 and 4.85 px (the viewer's left and right when the shelf faces north), rows at
 * y = 12.1 and 5.3 px, the card's face at z = 0.15 px.
 */
public final class WarehouseShelfBlockEntityRenderer implements
		BlockEntityRenderer<WarehouseShelfBlockEntity, WarehouseShelfBlockEntityRenderer.State> {

	/** Across the card centres, in blocks from the block's centre, for the viewer's left and right column. */
	private static final float[] COLUMN = {0.5F - 11.15F / 16.0F, 0.5F - 4.85F / 16.0F};
	/** Up the card centres, in blocks from the block's centre, for the top and the bottom row. */
	private static final float[] ROW = {12.1F / 16.0F - 0.5F, 5.3F / 16.0F - 0.5F};
	/** The card's face, in blocks from the block's centre toward the front. */
	private static final float CARD_FACE = 0.5F - 0.15F / 16.0F;
	/** How far in front of the card the flattened icon sits: half its squashed depth, so it never sinks in. */
	private static final float LIFT = 0.006F;
	/** A 16 px inventory icon drawn 3.2 px wide on a 4.6 x 4.0 px card. */
	private static final float ICON_SCALE = 3.2F / 16.0F;
	private static final float ICON_DEPTH = 0.008F;

	private final ItemModelResolver itemModelResolver;

	public WarehouseShelfBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WarehouseShelfBlockEntity shelf, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(shelf, state, partialTicks, cameraPosition, breakProgress);
		BlockState blockState = shelf.getBlockState();
		state.facing = blockState.hasProperty(WarehouseShelfBlock.FACING)
				? blockState.getValue(WarehouseShelfBlock.FACING) : Direction.NORTH;
		List<ItemStack> cards = shelf.cards();
		state.shown = Math.min(cards.size(), WarehouseShelfBlockEntity.CARDS);
		for (int i = 0; i < state.shown; i++) {
			itemModelResolver.updateForTopItem(state.icons[i], cards.get(i), ItemDisplayContext.GUI,
					shelf.getLevel(), null, (int) shelf.getBlockPos().asLong() + i);
		}
		// Lit by the air in front of the shelf: the cards face that way.
		if (shelf.getLevel() != null) {
			state.lightCoords = LightCoordsUtil.getLightCoords(shelf.getLevel(),
					shelf.getBlockPos().relative(state.facing));
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.shown == 0) {
			return;
		}
		poseStack.pushPose();
		// +Z onto the face the shelf looks out of.
		poseStack.translate(0.5F, 0.5F, 0.5F);
		Poses.rotate(poseStack, Axis.YP.rotationDegrees(-state.facing.toYRot()));
		for (int i = 0; i < state.shown; i++) {
			ItemStackRenderState icon = state.icons[i];
			if (icon.isEmpty()) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(COLUMN[i % 2], ROW[i / 2], CARD_FACE + LIFT);
			poseStack.scale(ICON_SCALE, ICON_SCALE, ICON_DEPTH);
			icon.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** One reusable render state: the four card icons, refilled from the block entity each frame. */
	public static final class State extends BlockEntityRenderState {
		private final ItemStackRenderState[] icons = new ItemStackRenderState[WarehouseShelfBlockEntity.CARDS];
		private int shown;
		private Direction facing = Direction.NORTH;

		private State() {
			for (int i = 0; i < icons.length; i++) {
				icons[i] = new ItemStackRenderState();
			}
		}
	}
}
