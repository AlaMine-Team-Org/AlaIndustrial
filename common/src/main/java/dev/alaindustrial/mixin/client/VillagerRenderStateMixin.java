package dev.alaindustrial.mixin.client;

import dev.alaindustrial.client.render.entity.layers.ShieldingSuitRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds the four shielding slots to the villager render state (MOD-536) — see
 * {@link ShieldingSuitRenderState} for why they cannot live on a vanilla state class.
 */
@Mixin(VillagerRenderState.class)
public abstract class VillagerRenderStateMixin implements ShieldingSuitRenderState {

	@Unique
	private ItemStack alaindustrial$headStack = ItemStack.EMPTY;

	@Unique
	private ItemStack alaindustrial$chestStack = ItemStack.EMPTY;

	@Unique
	private ItemStack alaindustrial$legsStack = ItemStack.EMPTY;

	@Unique
	private ItemStack alaindustrial$feetStack = ItemStack.EMPTY;

	@Override
	public ItemStack alaindustrial$head() {
		return this.alaindustrial$headStack;
	}

	@Override
	public ItemStack alaindustrial$chest() {
		return this.alaindustrial$chestStack;
	}

	@Override
	public ItemStack alaindustrial$legs() {
		return this.alaindustrial$legsStack;
	}

	@Override
	public ItemStack alaindustrial$feet() {
		return this.alaindustrial$feetStack;
	}

	@Override
	public void alaindustrial$setSuit(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
		this.alaindustrial$headStack = head;
		this.alaindustrial$chestStack = chest;
		this.alaindustrial$legsStack = legs;
		this.alaindustrial$feetStack = feet;
	}
}
