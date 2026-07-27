package dev.alaindustrial.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.alaindustrial.client.OilScreenEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the oil screen overlay (MOD-248). Vanilla's {@code submit} decides what to draw from a
 * hard-coded {@code isEyeInFluid(FluidTags.WATER)} test, and neither loader exposes a working hook
 * in 26.2 — see {@link OilScreenEffects} for the full reasoning and for why this mixin is in the
 * optional config rather than the required one.
 *
 * <p>{@code TAIL} rather than a targeted injection point: the vanilla water branch is guarded by
 * conditions this mixin does not want to inherit, and the item-activation animation that runs at the
 * end of the method submits into the same collector, so ordering between the two is decided by the
 * collector rather than by call order anyway.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "submit", at = @At("TAIL"))
	private void alaindustrial$oilOverlay(boolean isFirstPerson, boolean isSleeping, float partialTicks,
			SubmitNodeCollector collector, boolean hideGui, CallbackInfo ci) {
		if (!isFirstPerson || isSleeping) {
			return;
		}
		Player player = this.minecraft.player;
		if (player == null || player.isSpectator() || !OilScreenEffects.isEyeInOil(player)) {
			return;
		}
		OilScreenEffects.submitOverlay(player, new PoseStack(), collector);
	}
}
