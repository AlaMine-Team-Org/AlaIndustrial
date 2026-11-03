package dev.alaindustrial.mixin.client;

import dev.alaindustrial.client.tablet.ReactorTabletLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A tooltip asked for inside the scaled reactor tablet appears where the mouse is (MOD-946).
 *
 * <p>The open tablet draws the reactor panel under a scale and a shift, and hands the panel the mouse mapped back into
 * its own layout. A tooltip is not drawn there and then: {@code setTooltipForNextFrame*} only records the position, and
 * {@code extractDeferredElements} draws it after the screen, with no pose — so a position in panel space would land
 * beside the mouse. While {@link ReactorTabletLayout#tooltipsInPose} is set (only around the tablet's own drawing),
 * the recorded position is taken through the current pose into screen space. Every other tooltip passes untouched.
 * The tablet's pose has no rotation, so each axis maps on its own.
 *
 * <p>Every overload ends in the one private {@code setTooltipForNextFrameInternal(…, boolean, boolean)} (javap, 26.3).
 * Cosmetic, so it lives in the optional config.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorReactorTabletTooltipMixin {

	private static final String INTERNAL = "setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;"
			+ "Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;"
			+ "Lnet/minecraft/resources/Identifier;ZZ)V";

	@Shadow
	@Final
	private Matrix3x2fStack pose;

	@ModifyVariable(method = INTERNAL, at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int alaindustrial$tabletTooltipX(int x) {
		return ReactorTabletLayout.tooltipsInPose ? Math.round(pose.m00() * x + pose.m20()) : x;
	}

	@ModifyVariable(method = INTERNAL, at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int alaindustrial$tabletTooltipY(int y) {
		return ReactorTabletLayout.tooltipsInPose ? Math.round(pose.m11() * y + pose.m21()) : y;
	}
}
