package dev.alaindustrial.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads how many layers a render state currently holds, and the layer array itself.
 *
 * <p>Needed by {@link dev.alaindustrial.client.render.BlueprintResultItemModel}: composing another
 * item into an icon goes through {@code ItemModelResolver.appendItemLayers}, which appends an
 * unknown number of layers and hands none of them back. Vanilla never needs to know which ones it
 * just added (the bundle draws its selected item full size); drawing the product <em>small, inside
 * the blueprint's frame</em> does — the scale lives on the layer, as
 * {@code LayerRenderState.setLocalTransform}.
 */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
	@Accessor("activeLayerCount")
	int alaindustrial$activeLayerCount();

	/** Re-read after every append: {@code ensureCapacity} replaces the array when it grows. */
	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] alaindustrial$layers();
}
