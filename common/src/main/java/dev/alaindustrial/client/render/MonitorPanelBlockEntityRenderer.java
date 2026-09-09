package dev.alaindustrial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.alaindustrial.block.MonitorPanelBlock;
import dev.alaindustrial.block.entity.MonitorPanelBlockEntity;
import dev.alaindustrial.core.monitor.MonitorReadout;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a monitoring panel's face (MOD-480): the watched item, and under it the number.
 *
 * <p>The text idiom is the sign renderer's — {@code submitText} straight onto the collector this
 * renderer is handed, with {@code POLYGON_OFFSET} so glyphs lying on a surface do not fight it for
 * depth. Two things differ from the stock display frame, which draws the same readout as an ENTITY:
 * the font comes from the renderer context (a block entity renderer has no {@code getFont()}), and
 * the distance to the camera is measured here, because a block entity's render state does not carry
 * one.
 *
 * <p>A wall can be hundreds of blocks; the text is dropped past {@link #MAX_TEXT_DISTANCE} while the
 * icon keeps rendering, so a wall still reads as a wall from far away.
 */
public class MonitorPanelBlockEntityRenderer
		implements BlockEntityRenderer<MonitorPanelBlockEntity, MonitorPanelBlockEntityRenderer.State> {

	/** Same reading distance the stock display frame uses, for the same reason: it is legible there. */
	private static final double MAX_TEXT_DISTANCE = 24.0;

	private static final float TEXT_SCALE = 0.022F;

	/** Face of the recessed screen: half a block, less the one-pixel bezel, plus a hair of clearance. */
	private static final float SCREEN_DEPTH = 0.5F - 1.0F / 16.0F + 0.002F;

	private final ItemModelResolver itemModelResolver;
	private final Font font;

	public MonitorPanelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
		this.font = context.font();
	}

	/** Per-frame snapshot; the item state is reused rather than rebuilt every frame. */
	public static final class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		Direction facing = Direction.NORTH;
		MonitorReadout readout = MonitorReadout.IDLE;
		long count;
		boolean showText;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MonitorPanelBlockEntity panel, State state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(panel, state, partialTicks, cameraPosition,
				breakProgress);
		BlockState blockState = panel.getBlockState();
		state.facing = blockState.hasProperty(MonitorPanelBlock.FACING)
				? blockState.getValue(MonitorPanelBlock.FACING) : Direction.NORTH;
		state.readout = panel.getReadout();
		state.count = panel.getCount();
		state.showText = cameraPosition.distanceToSqr(Vec3.atCenterOf(panel.getBlockPos()))
				< MAX_TEXT_DISTANCE * MAX_TEXT_DISTANCE;
		// Light is sampled in FRONT of the panel, never at the panel itself: this is a full cube, and
		// inside a solid block the light level is zero — which is what painted the icon black.
		if (panel.getLevel() != null) {
			state.lightCoords = LightCoordsUtil.getLightCoords(panel.getLevel(),
					panel.getBlockPos().relative(state.facing));
		}
		// GUI, deliberately: this is the pose the player already knows every item by, because it is
		// the one the inventory draws — blocks as little isometric cubes, flat items flat. A screen
		// showing "the inventory icon" should show exactly that icon, not a second interpretation.
		itemModelResolver.updateForTopItem(state.item, panel.getFilter(), ItemDisplayContext.GUI,
				panel.getLevel(), null, (int) panel.getBlockPos().asLong());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (state.item.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		// Put +Z on the face the panel was placed looking out of, then step just clear of the surface.
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
		// The screen is recessed one pixel behind the bezel, so everything drawn on it sits there too.
		poseStack.translate(0.0F, 0.0F, SCREEN_DEPTH);

		poseStack.pushPose();
		// Lifted out of the recess so the cube floats in the niche rather than sinking into the wall.
		poseStack.translate(0.0F, 0.16F, 0.06F);
		poseStack.scale(0.38F, 0.38F, 0.38F);
		state.item.submit(poseStack, collector, state.lightCoords,
				net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();

		if (state.showText) {
			poseStack.pushPose();
			poseStack.translate(0.0F, -0.12F, 0.004F);
			// Negative Y: glyph space grows downwards.
			poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
			FormattedCharSequence text = Component.literal(label(state)).getVisualOrderText();
			collector.submitText(poseStack, -font.width(text) / 2.0F, 0.0F, text, false,
					Font.DisplayMode.POLYGON_OFFSET, state.lightCoords, colour(state.readout), 0,
					0xFF000000);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static String label(State state) {
		return switch (state.readout) {
			case OK -> formatCount(state.count);
			case NO_CORE -> "?";
			case NO_POWER -> "!";
			case NO_CAPACITY -> "x";
			case IDLE -> "";
		};
	}

	/** Grey "no core", red "no power", amber "out of capacity" — three causes, three marks. */
	private static int colour(MonitorReadout readout) {
		return switch (readout) {
			case NO_CORE -> 0xFF9E9E9E;
			case NO_POWER -> 0xFFFF5555;
			case NO_CAPACITY -> 0xFFFFAA00;
			default -> 0xFFFFFFFF;
		};
	}

	/**
	 * Exact up to ten thousand, then {@code 12.3k} / {@code 1.2M} — the stock display frame's format,
	 * so one warehouse never shows the same number two ways.
	 */
	public static String formatCount(long count) {
		if (count < 10_000L) {
			return Long.toString(count);
		}
		if (count < 1_000_000L) {
			return String.format(Locale.ROOT, "%.1fk", count / 1_000.0);
		}
		return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
	}
}
