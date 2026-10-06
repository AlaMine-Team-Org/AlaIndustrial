package dev.alaindustrial.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches {@code RenderType.create(String, RenderSetup)} for the render types the mod builds itself
 * (MOD-777): the door glass of the teleporter capsule needs a translucent type that writes no depth, and
 * vanilla offers none with a lightmap. The factory is package-private on both Minecraft lines, with the
 * same signature, so this invoker is line-neutral; the version-specific part — the pipeline and the setup
 * handed to it — lives in the facade {@code compat.client.TranslucentTypes}.
 */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {

	@Invoker("create")
	static RenderType alaindustrial$create(String name, RenderSetup setup) {
		throw new AssertionError("mixin invoker not applied");
	}
}
