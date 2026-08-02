package dev.alaindustrial.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a layer's own local transform so a new one can be <em>composed</em> onto it rather than
 * overwrite it.
 *
 * <p>{@code setLocalTransform} is a plain {@code Matrix4f.set} (checked in the 26.2 bytecode), so
 * writing the blueprint's shrink matrix straight onto a composed layer would silently discard a
 * transform the product's own model had baked in — a foreign item whose definition wraps its model
 * in {@code minecraft:composite} with a {@code transformation} would render displaced, and only on
 * a blueprint.
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
	@Accessor("localTransform")
	Matrix4f alaindustrial$localTransform();

	/**
	 * The display transform this layer's own model asked for.
	 *
	 * <p>Read for two reasons. It is the only way to learn how a composed layer will be oriented — the
	 * blueprint has to re-express the product in the <em>sheet's</em> frame instead, or the two halves
	 * of the icon fly apart the moment the item leaves a GUI slot, each obeying its own model's
	 * {@code display} block. And it has no getter: {@code setItemTransform} is write-only in 26.2.
	 */
	@Accessor("itemTransform")
	ItemTransform alaindustrial$itemTransform();

	/**
	 * Whether this layer wants block lighting ({@code gui_light: side}) rather than flat item lighting.
	 *
	 * <p>{@code ItemStackRenderState.usesBlockLight()} answers only for layer 0 (checked in the 26.2
	 * bytecode: it is {@code firstLayer().usesBlockLight}), and the GUI picks ONE lighting rig for the
	 * whole stack from it. A composed product is never layer 0, so its own answer is unreachable
	 * without this.
	 */
	@Accessor("usesBlockLight")
	boolean alaindustrial$usesBlockLight();
}
