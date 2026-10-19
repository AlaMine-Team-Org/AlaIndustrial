package dev.alaindustrial.settlement;

import dev.alaindustrial.core.energy.EnergyTier;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * The tier of a settlement core (epic MOD-824, decision R5): the mod's own tiers, LV → MV → HV → atomic. A tier
 * sets how far the territory reaches and how many houses and shift workers the settlement may have, what the core
 * draws to hold it and what it takes from the network ({@link #intakeTier()}). The numbers are knobs of
 * {@link SettlementConfig}; this enum only reads them.
 *
 * <p><b>The ladder only climbs.</b> {@link #radius()}, {@link #houseLimit()} and {@link #workerLimit()} are this
 * tier's knob or the one beneath it, whichever is larger: a server that set an MV radius below the LV one would
 * otherwise have a core that shrinks its town when it is upgraded. The shipped defaults climb on their own
 * ({@code SettlementTierTest} pins that separately), so the floor only ever catches an operator's file. The upkeep
 * is read as written: a cheaper higher tier is an odd balance, not a broken town.
 *
 * <p>Only {@link #LV} is reachable in the first slice (MOD-831); how a core moves up a tier is decided later.
 * Minecraft-free, so the L1 lane checks the ladder directly.
 */
public enum SettlementTier {
	LV,
	MV,
	HV,
	ATOMIC;

	/** The tier a newly found or newly placed core starts at. */
	public static final SettlementTier FIRST = LV;

	/**
	 * Territory radius in blocks: the square column {@code 2r + 1} wide around the core every town holds, the least of
	 * its territory ({@link SettlementTerritory}).
	 */
	public int radius() {
		return climbing(SettlementTier::rawRadius);
	}

	/**
	 * How far this tier's radius has grown past the first tier's: the columns a town built by a structure adds round
	 * its buildings' footprint on top of the margin (epic decision R59), so a higher tier widens the town on every
	 * side. Never negative — the radius climbs.
	 */
	public int growth() {
		return radius() - FIRST.radius();
	}

	/** How many houses the settlement may have restored at this tier. */
	public int houseLimit() {
		return climbing(SettlementTier::rawHouseLimit);
	}

	/** How many shift workers the settlement may hold at this tier. */
	public int workerLimit() {
		return climbing(SettlementTier::rawWorkerLimit);
	}

	/** EU per tick the core draws while it holds the territory. */
	public int euPerTick() {
		return switch (this) {
			case LV -> SettlementConfig.settlementCoreEuPerTickLv;
			case MV -> SettlementConfig.settlementCoreEuPerTickMv;
			case HV -> SettlementConfig.settlementCoreEuPerTickHv;
			case ATOMIC -> SettlementConfig.settlementCoreEuPerTickAtomic;
		};
	}

	/**
	 * The energy tier whose packets a core of this tier takes from the network (epic decision R44): the one above its
	 * own, so a town with full houses, working shift workers and their night charging has headroom over its upkeep.
	 * The mod's energy tiers stop at HV, and 512 EU/t is the most one receiver can get at all — the electrum cable's
	 * packet, an HV source flush against it — so HV and atomic both take an HV packet: the atomic tier's own EV packet
	 * is not an energy tier of the mod yet.
	 */
	public EnergyTier intakeTier() {
		return switch (this) {
			case LV -> EnergyTier.MV;
			case MV, HV, ATOMIC -> EnergyTier.HV;
		};
	}

	/** The tier above, or this one at the top. */
	public SettlementTier next() {
		return this == ATOMIC ? ATOMIC : values()[ordinal() + 1];
	}

	/** The name the registry saves and a translation key ends with: {@code lv}, {@code mv}, {@code hv}, {@code atomic}.
	 */
	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The tier named {@code name} by {@link #serializedName()}, or {@link #FIRST} for a name this build lacks. */
	public static SettlementTier byName(String name) {
		for (SettlementTier tier : values()) {
			if (tier.serializedName().equals(name)) {
				return tier;
			}
		}
		return FIRST;
	}

	/** The tier with this ordinal, or {@link #FIRST} for one out of range — what a GUI channel sends. */
	public static SettlementTier byOrdinal(int ordinal) {
		SettlementTier[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : FIRST;
	}

	/** The largest {@code raw} value of this tier and every tier beneath it. */
	private int climbing(ToIntFunction<SettlementTier> raw) {
		int best = 0;
		for (SettlementTier tier : values()) {
			best = Math.max(best, raw.applyAsInt(tier));
			if (tier == this) {
				break;
			}
		}
		return best;
	}

	private static int rawRadius(SettlementTier tier) {
		return switch (tier) {
			case LV -> SettlementConfig.settlementRadiusLv;
			case MV -> SettlementConfig.settlementRadiusMv;
			case HV -> SettlementConfig.settlementRadiusHv;
			case ATOMIC -> SettlementConfig.settlementRadiusAtomic;
		};
	}

	private static int rawHouseLimit(SettlementTier tier) {
		return switch (tier) {
			case LV -> SettlementConfig.settlementHouseLimitLv;
			case MV -> SettlementConfig.settlementHouseLimitMv;
			case HV -> SettlementConfig.settlementHouseLimitHv;
			case ATOMIC -> SettlementConfig.settlementHouseLimitAtomic;
		};
	}

	private static int rawWorkerLimit(SettlementTier tier) {
		return switch (tier) {
			case LV -> SettlementConfig.settlementWorkerLimitLv;
			case MV -> SettlementConfig.settlementWorkerLimitMv;
			case HV -> SettlementConfig.settlementWorkerLimitHv;
			case ATOMIC -> SettlementConfig.settlementWorkerLimitAtomic;
		};
	}
}
