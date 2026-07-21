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

	/** 1 millibucket — the internal unit itself, named for readability at call sites. */
	public static final long MILLIBUCKET = 1L;

	/** Droplets per millibucket on Fabric (81000 droplets/bucket ÷ 1000 mB/bucket), exact with no remainder. */
	public static final long FABRIC_DROPLETS_PER_MB = 81L;
}
