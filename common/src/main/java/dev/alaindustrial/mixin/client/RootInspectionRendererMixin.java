package dev.alaindustrial.mixin.client;

import dev.alaindustrial.client.render.RootInspection;
import dev.alaindustrial.client.render.RootInspectionRenderer;
import dev.alaindustrial.client.render.RootInspectionState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The same frame boundary on both loaders, after terrain and before the hand changes projection. */
@Mixin(GameRenderer.class)
public abstract class RootInspectionRendererMixin {
	@Inject(method = "extract", at = @At("RETURN"))
	private void extractRoots(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
		var state = ((GameRenderer) (Object) this).gameRenderState().levelRenderState;
		((RootInspectionState) state).alaindustrial$roots(renderLevel ? RootInspection.extract(delta) : RootInspection.Frame.EMPTY);
	}
	// 26.3: renderLevel takes no arguments any more (the DeltaTracker moved into the extracted state),
	// and LevelRenderer.render dropped its DeltaTracker and Matrix4fc parameters while GpuBufferSlice
	// moved to com.mojang.renderpearl.api.buffers — the whole descriptor below is the 26.3 one.
	@Inject(method = "renderLevel()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V", shift = At.Shift.AFTER))
	private void drawRoots(CallbackInfo ci) {
		GameRenderer renderer = (GameRenderer) (Object) this;
		var state = renderer.gameRenderState().levelRenderState;
		RootInspectionRenderer.draw(((RootInspectionState) state).alaindustrial$roots(), state.cameraRenderState, renderer.mainRenderTarget());
	}
	@Inject(method = "close", at = @At("HEAD"))
	private void closeRoots(CallbackInfo ci) { RootInspectionRenderer.close(); }
}
