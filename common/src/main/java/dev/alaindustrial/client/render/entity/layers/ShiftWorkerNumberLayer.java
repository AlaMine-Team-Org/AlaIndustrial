package dev.alaindustrial.client.render.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.client.render.entity.ShiftWorkerModel;
import dev.alaindustrial.client.render.entity.ShiftWorkerRenderState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The number on a shift worker's back (MOD-832, R3b): {@code <letter>-<NNN>} on the balloon's plate in two lines,
 * {@code W-} over {@code 117}, as the concept of MOD-828 lays it out — its own layer, not part of the skin, because
 * every worker's number is its own (R3a). The text idiom is the sign's and the monitor panel's: {@code submitText}
 * with {@code POLYGON_OFFSET}, so the glyphs lying on the plate do not fight it for depth.
 *
 * <p><b>Which way the text faces.</b> In the model's coordinates the body faces -z, +x is the worker's left and +y
 * points down (the renderer flips y). A viewer behind the worker sees +z coming at them, their right is -x and their
 * down is +y; the glyph space of a font is right +x, down +y, toward the viewer +z. So the glyphs are put on the plate
 * with {@code scale(-s, s, s)} — the same mapping of the glyph frame onto the viewer's frame that the sign's
 * {@code scale(s, -s, s)} is in world coordinates.
 *
 * <p><b>Size.</b> The plate is 6 × 6 pixels of the model (rows 3 to 8 of the balloon's back face). The wider line,
 * three digits, is made 5.5 pixels wide; two lines of the font's height then take about 5.5 pixels — about 0.15 of a
 * block per line, which reads from 5 blocks (MOD-832 research.md, "Number").
 */
public class ShiftWorkerNumberLayer extends RenderLayer<ShiftWorkerRenderState, ShiftWorkerModel> {

	/** Plate centre in the body's pixels: on the balloon's back face (z = 5), 6 below the shoulders. */
	private static final float PLATE_X = 0.0F;
	private static final float PLATE_Y = 6.0F;
	private static final float PLATE_Z = 5.02F;
	/** Width in model pixels the widest line is fitted to. */
	private static final float LINE_WIDTH = 5.5F;
	/** Stencil black, as on the concept's plate. */
	private static final int INK = 0xFF16191C;

	private final Font font;

	public ShiftWorkerNumberLayer(RenderLayerParent<ShiftWorkerRenderState, ShiftWorkerModel> parent, Font font) {
		super(parent);
		this.font = font;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			ShiftWorkerRenderState state, float yRot, float xRot) {
		if (state.isInvisible || state.number.length() < 3) {
			return;
		}
		FormattedCharSequence top = Component.literal(state.number.substring(0, 2)).getVisualOrderText();
		FormattedCharSequence bottom = Component.literal(state.number.substring(2)).getVisualOrderText();
		int widest = Math.max(1, Math.max(font.width(top), font.width(bottom)));
		float scale = LINE_WIDTH / widest / 16.0F;
		poseStack.pushPose();
		getParentModel().root().translateAndRotate(poseStack);
		getParentModel().body.translateAndRotate(poseStack);
		// The balloon swings about its hinge on the back (MOD-882); its geometry stays in the body's pixels.
		getParentModel().pack().translateAndRotate(poseStack);
		poseStack.translate(0.0F, 0.0F, -ShiftWorkerModel.PACK_Z / 16.0F);
		poseStack.translate(PLATE_X / 16.0F, PLATE_Y / 16.0F, PLATE_Z / 16.0F);
		poseStack.scale(-scale, scale, scale);
		float lineHeight = font.lineHeight;
		collector.submitText(poseStack, -font.width(top) / 2.0F, -lineHeight, top, false,
				Font.DisplayMode.POLYGON_OFFSET, lightCoords, INK, 0, state.outlineColor);
		collector.submitText(poseStack, -font.width(bottom) / 2.0F, 0.0F, bottom, false,
				Font.DisplayMode.POLYGON_OFFSET, lightCoords, INK, 0, state.outlineColor);
		poseStack.popPose();
	}
}
