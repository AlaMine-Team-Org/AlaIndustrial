package dev.alaindustrial.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws the solar panel of the Solar Fluxweave Helmet as a real 3D model on the head (MOD-664).
 *
 * <p>The helmet itself is worn armor, drawn by {@code HumanoidArmorLayer}. The panel on top of it is an item
 * model that vanilla's {@code CustomHeadLayer} would draw in the {@code head} display context — but
 * {@code extractRenderState} skips filling that head item whenever {@code HumanoidArmorLayer.shouldRender}
 * says the head stack is armor ({@code !shouldRender(...)} guards the {@code updateForLiving} call). This
 * mixin answers "no" for this one item, so both layers draw: the armor layer the helmet, the head layer the
 * panel ({@code items/fluxweave_solar_helmet.json} selects a panel-only model for {@code head}). The armor
 * layer reads {@code shouldRender} on its own, through a different call, and is not affected.
 *
 * <p>The item is matched by registry id so this client mixin carries no dependency on the item class. The
 * call site and both signatures are identical on 26.2 and 26.3 (checked with {@code javap -c}).
 *
 * <p>Cosmetic, so it lives in the optional config ({@code defaultRequire 0}): a conflict with another mod
 * rewriting this method costs the panel model, never the game.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererSolarHelmetMixin {

	private static final String NAMESPACE = "alaindustrial";
	private static final String SOLAR_HELMET = "fluxweave_solar_helmet";

	@WrapOperation(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;"
			+ "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;"
					+ "shouldRender(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;)Z"))
	private boolean alaindustrial$solarPanelIsAHeadItem(ItemStack stack, EquipmentSlot slot,
			Operation<Boolean> original) {
		if (!stack.isEmpty()) {
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (NAMESPACE.equals(id.getNamespace()) && SOLAR_HELMET.equals(id.getPath())) {
				return false;
			}
		}
		return original.call(stack, slot);
	}
}
