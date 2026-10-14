package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.block.entity.AdvancedIndustrialWorkbenchBlockEntity;
import dev.alaindustrial.compat.client.Poses;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Advanced Industrial Workbench's lid (MOD-810): the nine grid stacks lying on the 3x3 top, and the craft
 * they would make floating and turning above the middle.
 *
 * <p>Nothing here is sent by this renderer's own packet: the grid stacks and the result ride the block entity's
 * update tag ({@code AdvancedIndustrialWorkbenchBlockEntity#getUpdateTag}), which the server sends only when the
 * drawn picture changes. An empty bench draws nothing at all, and every item is lit by the air above the block -
 * the block itself is a solid cube whose own light level is zero.
 */
public final class AdvancedIndustrialWorkbenchBlockEntityRenderer implements
		BlockEntityRenderer<AdvancedIndustrialWorkbenchBlockEntity,
				AdvancedIndustrialWorkbenchBlockEntityRenderer.State> {

	/** Centre of a grid cell on the top face, per row/column index: three equal cells across the 16 px top. */
	private static final float CELL_STEP = 0.25F;
	private static final float TOP = 1.0F;
	/** Scale of a stack lying in its cell - a cell is 4 px, the item is drawn a little under that. */
	private static final float CELL_ITEM_SCALE = 0.2F;
	/** A flat sprite is lifted by half its thickness so it rests ON the lid, not in it. */
	private static final float LIE_LIFT = 0.012F;
	private static final float RESULT_HEIGHT = 1.38F;
	private static final float RESULT_SCALE = 0.4F;
	private static final float BOB_AMPLITUDE = 0.03F;
	private static final float BOB_SPEED = 0.06F;
	private static final float SPIN_SPEED = 0.03F;
	/** The game time is wrapped before it reaches a float, so the animation never starts stepping. */
	private static final long TIME_WRAP = 24000L;

	private final ItemModelResolver itemModelResolver;

	public AdvancedIndustrialWorkbenchBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(AdvancedIndustrialWorkbenchBlockEntity entity, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		Level level = entity.getLevel();
		BlockPos pos = entity.getBlockPos();
		state.anything = false;
		state.hasResult = false;
		for (int i = 0; i < AdvancedIndustrialWorkbenchBlockEntity.GRID_SIZE; i++) {
			ItemStack stack = entity.getItem(i);
			state.cellFilled[i] = !stack.isEmpty();
			if (!stack.isEmpty()) {
				// The seed keeps an item's random-looking variation (a model's weighted choice) stable per cell.
				this.itemModelResolver.updateForTopItem(state.cells[i], stack, ItemDisplayContext.FIXED, level, null,
						(int) pos.asLong() + i);
				state.anything = true;
			}
		}
		ItemStack result = entity.previewResult();
		if (!result.isEmpty()) {
			this.itemModelResolver.updateForTopItem(state.result, result, ItemDisplayContext.GROUND, level, null,
					(int) pos.asLong());
			state.hasResult = true;
			state.anything = true;
		}
		if (!state.anything) {
			return;
		}
		state.light = level == null ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.getLightCoords(level, pos.above());
		state.time = (level == null ? 0L : level.getGameTime() % TIME_WRAP) + partialTicks;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.anything) {
			return;
		}
		for (int i = 0; i < AdvancedIndustrialWorkbenchBlockEntity.GRID_SIZE; i++) {
			if (!state.cellFilled[i] || state.cells[i].isEmpty()) {
				continue;
			}
			ItemStackRenderState item = state.cells[i];
			poseStack.pushPose();
			poseStack.translate(0.5F + (i % 3 - 1) * CELL_STEP, TOP + LIE_LIFT, 0.5F + (i / 3 - 1) * CELL_STEP);
			// A gentle, fixed twist per cell so the nine do not look stamped from one mould.
			Poses.rotate(poseStack, Axis.YP.rotationDegrees(((i * 37) % 60) - 30));
			if (!item.usesBlockLight()) {
				// A flat item lies on the lid: its front turns to face up.
				Poses.rotate(poseStack, Axis.XP.rotationDegrees(-90.0F));
			}
			poseStack.scale(CELL_ITEM_SCALE, CELL_ITEM_SCALE, CELL_ITEM_SCALE);
			item.submit(poseStack, collector, state.light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
		if (state.hasResult && !state.result.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0.5F, RESULT_HEIGHT + Mth.sin(state.time * BOB_SPEED) * BOB_AMPLITUDE, 0.5F);
			Poses.rotate(poseStack, Axis.YP.rotation(state.time * SPIN_SPEED));
			poseStack.scale(RESULT_SCALE, RESULT_SCALE, RESULT_SCALE);
			state.result.submit(poseStack, collector, state.light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		// The result floats above the block's own cell, so the renderer says it draws beyond it.
		return true;
	}

	/**
	 * The box NeoForge tests against the view frustum before it draws this renderer (MOD-633): the block and
	 * the space above it where the result floats. No {@code @Override}: only NeoForge declares this hook.
	 */
	public AABB getRenderBoundingBox(AdvancedIndustrialWorkbenchBlockEntity blockEntity) {
		return new AABB(blockEntity.getBlockPos()).expandTowards(0.0, 1.0, 0.0);
	}

	private static final int GRID = AdvancedIndustrialWorkbenchBlockEntity.GRID_SIZE;

	/** One reusable render state: nine cell models and the result, refilled from the block entity each frame. */
	public static final class State extends BlockEntityRenderState {
		private final ItemStackRenderState[] cells = new ItemStackRenderState[GRID];
		private final boolean[] cellFilled = new boolean[GRID];
		private final ItemStackRenderState result = new ItemStackRenderState();
		private boolean anything;
		private boolean hasResult;
		private int light = LightCoordsUtil.FULL_BRIGHT;
		private float time;

		private State() {
			for (int i = 0; i < cells.length; i++) {
				cells[i] = new ItemStackRenderState();
			}
		}
	}
}
