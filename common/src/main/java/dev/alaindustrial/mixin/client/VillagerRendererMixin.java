package dev.alaindustrial.mixin.client;

import dev.alaindustrial.client.render.entity.layers.ShieldingSuitLayer;
import dev.alaindustrial.client.render.entity.layers.ShieldingSuitRenderState;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Teaches {@code VillagerRenderer} the shielding suit (MOD-536): registers
 * {@link ShieldingSuitLayer} next to the profession layer, and copies the worn suit pieces into
 * the render state on extraction.
 *
 * <p>Applies identically on both loaders — it rides the shared client mixin config, with no
 * per-loader registration at all. Only pieces tagged {@code #alaindustrial:radiation_shielding}
 * are copied: the layer has textures for those alone, and a villager in vanilla armor must keep
 * looking the way vanilla makes it look (bare — but that is vanilla's story, not ours to repaint).
 */
@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererMixin {

	@Inject(method = "<init>", at = @At("TAIL"))
	private void alaindustrial$addSuitLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
		// addLayer is protected on LivingEntityRenderer (inherited), so it goes through an invoker
		// rather than a shadow — shadows only see members declared on the target class itself.
		((LivingEntityRendererInvoker) this).alaindustrial$invokeAddLayer(
				new ShieldingSuitLayer((VillagerRenderer) (Object) this));
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void alaindustrial$copySuit(Villager villager, VillagerRenderState state, float tick,
			CallbackInfo ci) {
		if (!(state instanceof ShieldingSuitRenderState suit)) {
			return;
		}
		suit.alaindustrial$setSuit(shielding(villager, EquipmentSlot.HEAD),
				shielding(villager, EquipmentSlot.CHEST), shielding(villager, EquipmentSlot.LEGS),
				shielding(villager, EquipmentSlot.FEET));
	}

	private static ItemStack shielding(Villager villager, EquipmentSlot slot) {
		ItemStack stack = villager.getItemBySlot(slot);
		return stack.is(ModTags.Items.RADIATION_SHIELDING) ? stack : ItemStack.EMPTY;
	}
}
