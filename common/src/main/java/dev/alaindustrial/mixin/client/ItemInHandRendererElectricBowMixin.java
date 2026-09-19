package dev.alaindustrial.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alaindustrial.item.tool.ElectricBowItem;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The first-person half of drawing a bow (MOD-363), which vanilla again keys on {@code Items.BOW} itself
 * and a fixed 20-tick draw:
 * <ul>
 * <li>which hands are drawn — while a bow is drawn only the drawing hand shows, so a torch in the other
 * hand does not float in front of the aim ({@code evaluateWhichHandsToRender} and its helper);</li>
 * <li>how far the drawing hand has pulled back ({@code submitArmWithItem}, the {@code BOW} branch) — over
 * the Electric Bow's own draw time, so the hand reaches full pull on the same tick the shot does.</li>
 * </ul>
 * Both loaders keep these checks and the single {@code 20.0F} in {@code submitArmWithItem} verbatim, so
 * one shared mixin serves both. The draw-time edit touches the constant only for an Electric Bow stack,
 * which renders through the {@code BOW} branch alone.
 *
 * <p>Cosmetic by nature, so it lives in the optional config: a conflict costs the pose, never the game.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererElectricBowMixin {

	@WrapOperation(method = {"evaluateWhichHandsToRender", "selectionUsingItemWhileHoldingBowLike"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
	private static boolean alaindustrial$electricBowIsABow(ItemStack stack, Item item, Operation<Boolean> original) {
		return original.call(stack, item) || (item == Items.BOW && stack.getItem() instanceof ElectricBowItem);
	}

	@ModifyExpressionValue(method = "submitArmWithItem", at = @At(value = "CONSTANT", args = "floatValue=20.0"))
	private float alaindustrial$electricBowDrawTicks(float vanillaDrawTicks, @Local(argsOnly = true) ItemStack stack) {
		return stack.getItem() instanceof ElectricBowItem ? ElectricBowItem.drawTicks(stack) : vanillaDrawTicks;
	}
}
