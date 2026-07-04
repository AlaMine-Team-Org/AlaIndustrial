package dev.alaindustrial.core;

/**
 * IC2-flavored voltage tiers layered on top of the Team Reborn Energy API.
 *
 * <p>{@code maxVoltage} is the per-tick transfer cap for this tier: the most EU a block of this
 * tier accepts or emits per tick. Energy offered beyond this cap is simply not transferred — there
 * is no overvoltage penalty. {@code capacity} is the default internal buffer size.
 *
 * <p>EU is the display unit; the underlying API unit maps 1:1 to EU (see {@link EnergyUnits}).
 */
public enum EnergyTier {
	LV(32L, 10_000L, 0xFFD24A),
	MV(128L, 40_000L, 0xFF8A3D),
	HV(512L, 160_000L, 0xFF3D5A);

	private final long maxVoltage;
	private final long capacity;
	private final int color;

	EnergyTier(long maxVoltage, long capacity, int color) {
		this.maxVoltage = maxVoltage;
		this.capacity = capacity;
		this.color = color;
	}

	/** Max packet voltage (EU) and per-tick transfer cap for this tier. */
	public long maxVoltage() {
		return maxVoltage;
	}

	/** Default internal buffer capacity (EU) for machines of this tier. */
	public long capacity() {
		return capacity;
	}

	/** Cable/identity colour (ARGB) for this tier. */
	public int color() {
		return color;
	}
}
