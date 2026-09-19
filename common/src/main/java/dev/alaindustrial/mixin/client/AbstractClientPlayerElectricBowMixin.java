package dev.alaindustrial.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.alaindustrial.item.tool.ElectricBowItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The camera zoom while drawing (MOD-363). Vanilla narrows the field of view as a bow is drawn, but only
 * for {@code Items.BOW} itself — an {@code is(Items.BOW)} identity check, not a class check — so without
 * this the Electric Bow would draw with no zoom at all. Both loaders keep that check and the 20-tick
 * divisor verbatim (NeoForge only moves the return through its FOV hook), so one shared mixin serves both.
 *
 * <p>Two edits: the Electric Bow answers "is this a bow" with yes, and the draw time the zoom scales over
 * is the bow's own ({@link ElectricBowItem#drawTicks}) — 16 ticks while charged, so the zoom completes
 * exactly when the shot does.
 *
 * <p>Cosmetic by nature, so it lives in the optional config: a conflict with a camera mod costs the zoom,
 * never the game.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerElectricBowMixin {

	@WrapOperation(method = "getFieldOfViewModifier",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
	private boolean alaindustrial$electricBowIsABow(ItemStack stack, Item item, Operation<Boolean> original) {
		return original.call(stack, item) || (item == Items.BOW && stack.getItem() instanceof ElectricBowItem);
	}

	@ModifyExpressionValue(method = "getFieldOfViewModifier", at = @At(value = "CONSTANT", args = "floatValue=20.0"))
	private float alaindustrial$electricBowDrawTicks(float vanillaDrawTicks) {
		ItemStack used = ((LivingEntity) (Object) this).getUseItem();
		return used.getItem() instanceof ElectricBowItem ? ElectricBowItem.drawTicks(used) : vanillaDrawTicks;
	}
}
