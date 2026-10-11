package dev.alaindustrial.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * MOD-797: the blueprint screen hands its 3D scene to the GUI's picture-in-picture queue, which lives on
 * this private field. Neither vanilla nor Fabric exposes it (NeoForge adds a submit method of its own), so
 * one shared accessor keeps the screen loader-neutral; the field has the same name on 26.2 and 26.3.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {
	@Accessor("guiRenderState")
	GuiRenderState alaindustrial$guiRenderState();
}
