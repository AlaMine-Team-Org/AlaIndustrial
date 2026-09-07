package dev.alaindustrial.mixin.client;

import dev.alaindustrial.client.render.RootInspection;
import dev.alaindustrial.client.render.RootInspectionState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelRenderState.class)
public abstract class RootInspectionStateMixin implements RootInspectionState {
	@Unique private RootInspection.Frame alaindustrial$rootFrame = RootInspection.Frame.EMPTY;
	@Override public RootInspection.Frame alaindustrial$roots() { return alaindustrial$rootFrame; }
	@Override public void alaindustrial$roots(RootInspection.Frame frame) { alaindustrial$rootFrame = frame; }
}
