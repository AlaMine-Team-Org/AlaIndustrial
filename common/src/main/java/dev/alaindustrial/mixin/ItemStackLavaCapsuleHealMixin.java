package dev.alaindustrial.mixin;

import dev.alaindustrial.item.fluid.CapsuleFuel;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Heals 26.2-era lava capsules at the one boundary every saved stack crosses (MOD-226). In 26.3 fuel is
 * the {@code minecraft:cooking_fuel} component on the stack, and a capsule filled before the upgrade
 * carries only its fluid — so in any furnace it is inert until emptied and refilled. Worlds are lazy,
 * chunks load years apart, so no start-up sweep can find them all; but every stack from disk and from
 * the network funnels through the public {@code ItemStack(Holder, int, DataComponentPatch)} constructor,
 * and that is where the heal lives (the rule itself is {@link CapsuleFuel#heal262LavaCapsule}).
 *
 * <p>The check is one item comparison for every stack but a lava capsule, and the heal is idempotent,
 * so a healed stack re-saving and re-loading is a no-op. Required config ({@code alaindustrial.mixins.json}):
 * without it the lava-capsule feature is silently broken for every existing world, which is the
 * "functionally broken" category that config exists for.
 *
 * <p>Initialization order is safe: this constructor only runs for stacks parsed from data — the codec
 * and the stream decoder — and both parse after item registration has frozen, so the content lookup in
 * the heal always resolves.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackLavaCapsuleHealMixin {

	@Inject(method = "<init>(Lnet/minecraft/core/Holder;ILnet/minecraft/core/component/DataComponentPatch;)V",
			at = @At("RETURN"))
	private void alaindustrial$heal262LavaCapsule(Holder<Item> item, int count, DataComponentPatch patch,
			CallbackInfo ci) {
		CapsuleFuel.heal262LavaCapsule((ItemStack)(Object)this);
	}
}
