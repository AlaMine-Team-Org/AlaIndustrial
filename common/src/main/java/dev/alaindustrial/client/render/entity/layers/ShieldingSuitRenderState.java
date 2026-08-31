package dev.alaindustrial.client.render.entity.layers;

import net.minecraft.world.item.ItemStack;

/**
 * The worn shielding pieces, carried on {@code VillagerRenderState} via a mixin (MOD-536).
 *
 * <p>Villagers are not humanoid: {@code VillagerRenderState} has none of the four armor fields
 * {@code HumanoidRenderState} carries, and no vanilla layer would consume them anyway. The duck
 * interface plus {@code VillagerRenderStateMixin} add exactly the four slots the suit needs, and
 * {@code VillagerRendererMixin} fills them in {@code extractRenderState} — only pieces tagged
 * {@code #alaindustrial:radiation_shielding}, because only those have overlay textures here.
 */
public interface ShieldingSuitRenderState {

	ItemStack alaindustrial$head();

	ItemStack alaindustrial$chest();

	ItemStack alaindustrial$legs();

	ItemStack alaindustrial$feet();

	void alaindustrial$setSuit(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet);
}
