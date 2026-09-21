package dev.alaindustrial.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.alaindustrial.item.tool.ElectricBowItem;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * How far the drawing hand has pulled back in first person (MOD-363). Vanilla keys the pull on a fixed
 * 20-tick draw inside {@code submitArmWithItem}'s {@code BOW} branch; the Electric Bow draws over its
 * own time, so the hand reaches full pull on the same tick the shot does.
 *
 * <p>Both loaders keep that single {@code 20.0F} verbatim, so one shared mixin serves both. The edit
 * touches the constant only for an Electric Bow stack, which renders through the {@code BOW} branch
 * alone.
 *
 * <p><b>26.3 renamed the target class.</b> {@code ItemInHandRenderer} became
 * {@link FirstPersonHandsAndItemsRenderer} and the hand-<em>selection</em> half of MOD-363 moved out of
 * it entirely, into {@code net.minecraft.client.player.FirstPersonHandsAndItems} — see
 * {@link FirstPersonHandsAndItemsElectricBowMixin}, which is why this pair is now two classes.
 *
 * <p>Cosmetic by nature, so it lives in the optional config: a conflict costs the pose, never the game.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererElectricBowMixin {

	@ModifyExpressionValue(method = "submitArmWithItem", at = @At(value = "CONSTANT", args = "floatValue=20.0"))
	private float alaindustrial$electricBowDrawTicks(float vanillaDrawTicks, @Local(argsOnly = true) ItemStack stack) {
		return stack.getItem() instanceof ElectricBowItem ? ElectricBowItem.drawTicks(stack) : vanillaDrawTicks;
	}
}
