package dev.alaindustrial.item.fluid;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Translates between a filled bucket item and the fluid it carries (MOD-380), in both directions and
 * for <em>any</em> fluid — the mod's own, vanilla's, and another mod's.
 *
 * <p><b>Why this exists.</b> The two manual bucket routes ({@link FluidTankBucketInteractions} on a
 * plain right-click, {@link VanillaBucketDeposit} on a shift-right-click) each carried their own
 * hard-coded list of buckets they recognised — water and lava for the tank, lava alone for the
 * machine deposit. A bucket outside that list resolved to {@link Fluids#EMPTY}, the route returned
 * {@code PASS}, and vanilla finished the interaction by running {@code BucketItem#use}: the fluid
 * went onto the floor next to the tank instead of into it. That is exactly what happened to oil once
 * MOD-238 added it, because {@code OIL_BUCKET} is a plain vanilla {@link BucketItem} carrying
 * {@code OilFluid} and is indistinguishable from a water bucket by anything but item identity. The
 * tanks themselves were never the limit — the portable tank accepts any non-empty fluid, and the
 * Vacuum Capsule moved oil correctly all along, because it goes through the fluid-agnostic
 * {@code FluidPort}. Resolving the bucket generically removes the class of bug rather than the one
 * instance of it: the next fluid the mod ships works without touching either route.
 *
 * <p>Both directions are plain vanilla API — {@link BucketItem#getContent()} and
 * {@link Fluid#getBucket()} — so this needs no loader-specific fluid type on either side.
 */
public final class BucketFluids {
	private BucketFluids() {
	}

	/**
	 * The fluid a filled bucket carries, or {@link Fluids#EMPTY} if {@code stack} is not a bucket whose
	 * whole content is that fluid. An empty bucket also reports {@link Fluids#EMPTY}.
	 *
	 * <p><b>The round-trip check is a safety guard, not a formality.</b> A bucket of cod is a
	 * {@code MobBucketItem extends BucketItem} whose {@link BucketItem#getContent()} reports plain
	 * {@link Fluids#WATER} — the mob rides along in the stack's components. Accepting every
	 * {@link BucketItem} would therefore let a player deposit "water" from a bucket of cod, hand back
	 * an ordinary empty bucket, and destroy the fish. Requiring the fluid to name this very item as
	 * its own bucket ({@code fluid.getBucket() == stack.getItem()}) rejects every bucket that carries
	 * something besides the fluid — the four vanilla mob buckets today, and any modded variant later —
	 * without this class having to know they exist.
	 */
	public static Fluid content(ItemStack stack) {
		if (!(stack.getItem() instanceof BucketItem bucket)) {
			return Fluids.EMPTY;
		}
		Fluid fluid = bucket.getContent();
		if (fluid == Fluids.EMPTY || fluid.getBucket() != stack.getItem()) {
			return Fluids.EMPTY;
		}
		return fluid;
	}

	/**
	 * A filled bucket holding {@code fluid}, or {@link ItemStack#EMPTY} when the fluid has no bucket to
	 * fill. Fluids with no bucket item are real: {@code EmptyFluid#getBucket} returns {@link Items#AIR},
	 * and a modded fluid reachable only through pipes may do the same, so the caller must be able to
	 * tell "cannot be bottled" apart from a successful exchange.
	 */
	public static ItemStack filledBucket(Fluid fluid) {
		if (fluid == null || fluid == Fluids.EMPTY) {
			return ItemStack.EMPTY;
		}
		Item bucket = fluid.getBucket();
		return (bucket == null || bucket == Items.AIR) ? ItemStack.EMPTY : new ItemStack(bucket);
	}
}
