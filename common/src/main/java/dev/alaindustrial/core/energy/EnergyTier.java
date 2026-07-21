package dev.alaindustrial.core.energy;

import dev.alaindustrial.Config;

/**
 * IC2-flavored voltage tiers layered on top of the Team Reborn Energy API.
 *
 * <p>{@code maxVoltage} is the per-tick transfer cap for this tier: the most EU a block of this
 * tier accepts or emits per tick. Energy offered beyond this cap is simply not transferred — there
 * is no overvoltage penalty. {@code capacity} is the default internal buffer size.
 *
 * <p><b>Config-backed.</b> The per-tier voltage/capacity numbers live in {@link Config}
 * ({@code tierLvVoltage}, {@code tierMvVoltage}, {@code tierHvVoltage} and the matching capacity
 * knobs), so a server operator can retune them from {@code config/alaindustrial.json} without a
 * code change — the same contract every other balance number in the mod already follows. The enum
 * still carries the compile-time default in its constructor so the tier is self-describing before
 * {@link Config} is loaded (the {@link #maxVoltage()}/{@link #capacity()} methods read Config live
 * at runtime, where it is guaranteed to be initialised).
 *
 * <p>The {@code color} ARGB value is the per-tier cable/UI tint (yellow/orange/red) — a visual
 * constant, NOT balance — so it stays in the enum.
 *
 * <p>EU is the display unit; the underlying API unit maps 1:1 to EU (see {@link EnergyUnits}).
 */
public enum EnergyTier {
	LV(32L, 10_000L, 0xFFD24A),
	MV(128L, 40_000L, 0xFF8A3D),
	HV(512L, 160_000L, 0xFF3D5A);

	private final long defaultMaxVoltage;
	private final long defaultCapacity;
	private final int color;

	EnergyTier(long defaultMaxVoltage, long defaultCapacity, int color) {
		this.defaultMaxVoltage = defaultMaxVoltage;
		this.defaultCapacity = defaultCapacity;
		this.color = color;
	}

	/**
	 * Max packet voltage (EU) and per-tick transfer cap for this tier — read live from {@link Config}
	 * so {@code config/alaindustrial.json} can retune it without a code change. Falls back to the
	 * enum's compile-time default if Config has not been loaded yet (defensive — Config is initialised
	 * at mod init, well before any runtime call here).
	 */
	public long maxVoltage() {
		return switch (this) {
			case LV -> Config.tierLvVoltage;
			case MV -> Config.tierMvVoltage;
			case HV -> Config.tierHvVoltage;
		};
	}

	/**
	 * Default internal buffer capacity (EU) for machines of this tier — read live from {@link Config}.
	 * Most blocks override this with their own {@code Config.<block>Buffer} knob (e.g.
	 * {@link Config#maceratorBuffer}); this is the fallback used when a tier is queried in isolation
	 * (e.g. tests, recipes that scale by tier).
	 */
	public long capacity() {
		return switch (this) {
			case LV -> Config.tierLvCapacity;
			case MV -> Config.tierMvCapacity;
			case HV -> Config.tierHvCapacity;
		};
	}

	/** The compile-time default {@link #maxVoltage()} — used by tests that pin the canonical value. */
	public long defaultMaxVoltage() {
		return defaultMaxVoltage;
	}

	/** The compile-time default {@link #capacity()} — used by tests that pin the canonical value. */
	public long defaultCapacity() {
		return defaultCapacity;
	}

	/** Cable/identity colour (ARGB) for this tier. */
	public int color() {
		return color;
	}
}
