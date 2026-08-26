package dev.alaindustrial.core.fluid;

/**
 * Fluid-volume unit constants (MOD-028): the single source of truth for the internal unit every
 * {@link FluidPort}/{@link FluidTank} amount is expressed in.
 *
 * <p><b>Internal unit = millibucket (mB), 1 bucket = 1000.</b> The two loaders disagree on their native
 * fluid unit — NeoForge's {@code ResourceHandler<FluidResource>} already counts in mB
 * ({@code FluidType.BUCKET_VOLUME == 1000}), while Fabric's {@code Storage<FluidVariant>} counts in
 * droplets ({@code FluidConstants.BUCKET == 81000}). Pinning mB as the common unit means the NeoForge
 * adapter is a 1:1 pass-through and only the Fabric adapter converts, exactly mirroring the MOD-022
 * decision to pin a 1:1 EU:native-unit ratio for energy. 81000 droplets/bucket divides evenly by 1000 mB
 * (= 81 droplets per mB), so the conversion is exact whenever amounts are bucket-multiples — the invariant
 * every fluid transaction in this mod upholds (machines move whole buckets at a time).
 */
public final class FluidAmounts {
	private FluidAmounts() {
	}

	/** 1 bucket, in the internal unit (millibuckets). */
	public static final long BUCKET = 1000L;

	/** Droplets per millibucket on Fabric (81000 droplets/bucket ÷ 1000 mB/bucket), exact with no remainder. */
	public static final long FABRIC_DROPLETS_PER_MB = 81L;

	/**
	 * Millibuckets as droplets. Exact — mB is the coarser unit, so scaling up never loses anything.
	 * Overflows loudly ({@link ArithmeticException}) rather than wrapping, so a caller must pass a real
	 * bound and never {@code Long.MAX_VALUE} as shorthand for "everything".
	 */
	public static long toDroplets(long mb) {
		return Math.multiplyExact(mb, FABRIC_DROPLETS_PER_MB);
	}

	/**
	 * Droplets as whole millibuckets, rounding DOWN. Anything below 81 droplets is not representable in
	 * mB at all and floors to zero.
	 *
	 * <p><b>MOD-284.</b> Rounding down is only safe when the leftover from {@link #remainderDroplets} is
	 * dealt with. A foreign Fabric storage may accept or yield an amount that is not a multiple of 81
	 * (a mod working in ninths of a bucket yields 9000 droplets, a bottle is 27000 — neither divides by
	 * 81), and silently flooring such a value makes the two sides of a transfer disagree: the mB figure
	 * under-reports what actually crossed the boundary, which creates fluid on an insert and destroys it
	 * on an extract.
	 */
	public static long toMbFloor(long droplets) {
		return droplets / FABRIC_DROPLETS_PER_MB;
	}

	/**
	 * The sub-millibucket tail of a droplet amount — what {@link #toMbFloor} drops. Always in
	 * {@code [0, 80]} for a non-negative input. A caller that floors an amount must move this tail back
	 * where it came from inside the same transaction, or that fluid is created/destroyed (MOD-284).
	 */
	public static long remainderDroplets(long droplets) {
		return droplets % FABRIC_DROPLETS_PER_MB;
	}
}
