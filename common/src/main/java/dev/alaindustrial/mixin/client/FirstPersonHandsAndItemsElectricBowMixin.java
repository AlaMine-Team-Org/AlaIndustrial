package dev.alaindustrial.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.alaindustrial.item.tool.ElectricBowItem;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Which hands are drawn while a bow is being drawn (MOD-363). Vanilla keys this on {@code Items.BOW}
 * itself, so without the mixin a torch in the other hand would float in front of the aim whenever the
 * Electric Bow is drawn.
 *
 * <p><b>26.3 moved and merged the target.</b> The decision used to live in {@code ItemInHandRenderer}
 * as two methods, {@code evaluateWhichHandsToRender} and {@code selectionUsingItemWhileHoldingBowLike};
 * 26.3 moved it to {@link FirstPersonHandsAndItems} and inlined the helper, so all of the
 * {@code ItemStack.is(Item)} tests that used to span both methods now sit in the one that remains.
 * Wrapping every such call in it therefore covers exactly what the pair covered before; the crossbow
 * tests it also sees are unaffected, because the answer only ever widens for {@code Items.BOW}.
 *
 * <p>Cosmetic by nature, so it lives in the optional config: a conflict costs the pose, never the game.
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsElectricBowMixin {

	@WrapOperation(method = "evaluateWhichHandsToRender",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
	private static boolean alaindustrial$electricBowIsABow(ItemStack stack, Item item, Operation<Boolean> original) {
		return original.call(stack, item) || (item == Items.BOW && stack.getItem() instanceof ElectricBowItem);
	}
}
