package dev.alaindustrial.item.fluid;

import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * Shared rule for treating a lava-filled Vacuum Capsule as furnace fuel, on par with a vanilla lava bucket
 * (MOD-077). Only lava capsules are fuel; a capsule holding any other fluid is not, which is why the rule
 * keys on the stored fluid rather than on the item alone.
 *
 * <p><b>26.3 moved this from a mixin to a data component.</b> Fuel used to be a lookup keyed purely by item
 * type ({@code FuelValues}), which could not tell a lava capsule from a water one — hence the stack-aware
 * {@code FuelValuesMixin} that answered for both {@code isFuel} and {@code burnDuration}. In 26.3 fuel IS a
 * per-stack property: the furnace asks whether the stack carries {@code minecraft:cooking_fuel} and reads
 * the burn time out of it. So the capsule now carries that component exactly while it holds lava, which
 * makes the mixin unnecessary — and makes the answer right everywhere a stack travels, not only in the
 * methods a mixin happened to cover.
 *
 * <p>The values are not hardcoded: {@link #LAVA_FUEL} names the same two providers
 * {@code Items.LAVA_BUCKET} is registered with, so a datapack that retunes the lava bucket retunes the
 * capsule with it — which is what the mixin's re-entrant lookup was for.
 */
public final class CapsuleFuel {

	/** Burn time and speed of a lava bucket, by reference rather than by value. */
	private static final CookingFuel LAVA_FUEL = new CookingFuel(
			ContextIntProviders.COOKING_TIME_LAVA_BUCKET,
			ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER);

	private CapsuleFuel() {
	}

	/** Whether {@code stack} is a Vacuum Capsule currently holding lava. */
	public static boolean isLavaCapsule(ItemStack stack) {
		return stack.is(ModContent.FILLED_VACUUM_CAPSULE.get()) && ItemFluid.get(stack) == Fluids.LAVA;
	}

	/**
	 * Bring the stack's {@code minecraft:cooking_fuel} component in line with the fluid it is about to
	 * hold. Called from {@link ItemFluid#set} so every filling route — the two in common and the two
	 * loader-side storage adapters — is covered by one rule and none of them can forget it.
	 *
	 * <p>Emptying removes the component again, so an emptied capsule is component-identical to a freshly
	 * crafted one and the two still stack, exactly as {@link ItemFluid} promises.
	 */
	static void applyTo(ItemStack stack, Fluid fluid) {
		if (fluid == Fluids.LAVA) {
			stack.set(DataComponents.COOKING_FUEL, LAVA_FUEL);
		} else {
			stack.remove(DataComponents.COOKING_FUEL);
		}
	}

	/**
	 * Give a lava capsule saved by 26.2 the {@code cooking_fuel} component 26.3 reads (owner decision,
	 * 2026-09-22). Until 26.3 fuel was a per-level lookup a stack-aware mixin answered for, so a capsule
	 * filled on 26.2 carries its fluid but no component — and on 26.3 that means it does not burn, in any
	 * furnace, until it is emptied and filled again.
	 *
	 * <p>The heal runs where every saved stack materialises: the {@code ItemStack(Holder, int,
	 * DataComponentPatch)} constructor, the one choke point of the disk codec and the network decode
	 * alike (see {@code mixin/ItemStackLavaCapsuleHealMixin}). A capsule therefore heals the moment it
	 * loads wherever it was saved — a chest, a machine, an item entity, an offline player file — and the
	 * next save persists the component, so the migration converges instead of recurring.
	 *
	 * <p>Conservative by construction: only a capsule that HOLDS lava and does NOT already carry the
	 * component is touched. A water capsule, an emptied capsule and a freshly filled one (which
	 * {@link #applyTo} stamped already) all stay exactly as they are, and a second heal of the same
	 * stack is a no-op.
	 */
	public static void heal262LavaCapsule(ItemStack stack) {
		if (!stack.has(DataComponents.COOKING_FUEL) && isLavaCapsule(stack)) {
			stack.set(DataComponents.COOKING_FUEL, LAVA_FUEL);
		}
	}
}
