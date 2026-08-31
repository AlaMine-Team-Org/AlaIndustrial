package dev.alaindustrial.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code LivingEntityRenderer#addLayer} to {@code VillagerRendererMixin} (MOD-536).
 *
 * <p>{@code addLayer} is protected-final and declared on {@code LivingEntityRenderer}, so a
 * {@code @Shadow} on the villager renderer cannot see it (shadows only find members of the target
 * class itself) — an invoker on the declaring class is the mixin-idiomatic way to call an
 * inherited protected method.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {

	@Invoker("addLayer")
	boolean alaindustrial$invokeAddLayer(RenderLayer<?, ?> layer);
}
