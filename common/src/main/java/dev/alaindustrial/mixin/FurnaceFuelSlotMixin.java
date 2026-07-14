package dev.alaindustrial.mixin;

import dev.alaindustrial.item.CapsuleFuel;
import net.minecraft.world.inventory.FurnaceFuelSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Caps a lava-filled Vacuum Capsule to one item in a furnace fuel slot (MOD-077), exactly as vanilla does for
 * buckets. The furnace only writes the emptied-fuel remainder ({@code craftRemainder} → an empty capsule) when
 * the fuel stack reaches zero; a capsule stacks to sixteen, so without this a stack of sixteen would burn and
 * return a single empty capsule, losing the other fifteen. Forcing the fuel-slot stack size to one — the same
 * fix vanilla applies to buckets in {@link FurnaceFuelSlot#getMaxStackSize} — makes each capsule return its own
 * empty container. Loader-neutral (config in {@code common/alaindustrial.mixins.json}).
 */
@Mixin(FurnaceFuelSlot.class)
public abstract class FurnaceFuelSlotMixin {

	@Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"),
			cancellable = true)
	private void alaindustrial$lavaCapsuleStacksToOne(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (CapsuleFuel.isLavaCapsule(stack)) {
			cir.setReturnValue(1);
		}
	}
}
