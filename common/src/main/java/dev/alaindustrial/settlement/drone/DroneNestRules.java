package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementTier;
import java.util.Locale;

/**
 * The rules of a town's drone nests and their tier-1 builder drones (MOD-858; epic MOD-824, decisions R87, R91, R94):
 * how many nests of a town work, how long a flight takes and what it costs, whether a drone may take off, and the
 * status a nest shows. The nest's block entity feeds it what it sees and acts on the answer.
 *
 * <p><b>The flight path</b> is a route through open cells only (owner's round 2, {@code DroneAirspace}); its length,
 * rounded up to whole blocks ({@link #routeBlocks}), sets how long a leg takes and what it costs. A flight saved before
 * routes existed is drawn the old way — up to {@link #cruiseY}, across, down.
 *
 * <p><b>The energy (R94).</b> The battery pays the flight, out and back, per block of path. The core pays every block
 * placed or removed; while the core is dark the battery pays that too. A drone takes off only when its battery holds
 * the whole round trip AND one block's price, so a core that goes dark mid-flight never strands it: it finishes the
 * flight on its battery and comes home.
 *
 * <p>Minecraft-free on purpose: the L1 lane checks it directly ({@code DroneNestRulesTest}).
 */
public final class DroneNestRules {

	/** Blocks the cruise height stands above the higher of the nest and the target. */
	public static final int CRUISE_CLEARANCE = 2;
	/** Shortest leg, in ticks: a neighbouring cell would otherwise be reached faster than the eye follows. */
	public static final int MIN_FLIGHT_TICKS = 10;
	/** Ticks the drone stands on the cell it worked before it flies home. */
	public static final int LANDING_PAUSE_TICKS = 6;
	/** Ticks a drone stays docked between two flights — the time its nest charges it in. */
	public static final int DOCK_TICKS = 6;
	/** Ticks between two tries of a job this nest had to stop: the cause may be gone. */
	public static final int RETRY_TICKS = 100;
	/** Ticks a cell with a creature in it is left alone before the drone looks at it again. */
	public static final int SKIP_RETRY_TICKS = 40;

	private DroneNestRules() {
	}

	/** What a nest is doing, or why it stands still — the line a right-click shows. */
	public enum Status {
		/** No town's territory holds the nest. */
		NO_TOWN,
		/** The nest stands nowhere over its town's town hall. */
		OUTSIDE_HALL,
		/** The town already has as many working nests as its core's tier allows (R91). */
		EXTRA,
		/** The town has no working dispatcher, so no plan and no materials. */
		NO_DISPATCHER,
		/** The town's core is dark: no charge and no new flights (R94). */
		CORE_DARK,
		/** The battery cannot pay the next flight and the core cannot charge it. */
		NO_ENERGY,
		/** Nothing to do: no job in the queue it may take. */
		IDLE,
		/** The drone works on a job. */
		WORKING,
		/** The job is held up: a cell with a creature in it, or a cause the core's tab shows. */
		WAITING,
		/** No drone docked: the nest is an empty platform until a builder drone item is put in. */
		NO_DRONE,
		/** The town's dispatcher holds no restoration plan of the building (owner's round 2): the drones stand. */
		NO_PLAN,
		/** No route through the air reaches the cells left to work: they wait and are tried again. */
		NO_ROUTE;

		/** Whether the nest stands still because of this. */
		public boolean blocking() {
			return this != WORKING && this != IDLE;
		}

		/** The translation key of the status line. */
		public String key() {
			return "settlement.alaindustrial.drone_nest.status." + name().toLowerCase(Locale.ROOT);
		}

		/** The status with this ordinal, or {@link #NO_TOWN} for one out of range. */
		public static Status byOrdinal(int ordinal) {
			Status[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NO_TOWN;
		}
	}

	/** How many nests of a town work at {@code tier}; a restored town hall adds its bonus (R91). Never negative. */
	public static int nestLimit(SettlementTier tier, boolean hallRestored) {
		int base = switch (tier) {
			case LV -> SettlementConfig.settlementDroneNestsLv;
			case MV -> SettlementConfig.settlementDroneNestsMv;
			case HV -> SettlementConfig.settlementDroneNestsHv;
			case ATOMIC -> SettlementConfig.settlementDroneNestsAtomic;
		};
		int bonus = hallRestored ? SettlementConfig.settlementDroneNestsRestoredBonus : 0;
		return Math.max(0, base) + Math.max(0, bonus);
	}

	/** Whether the nest that came {@code rank}-th (0 first) to work in its town works at {@code limit}. */
	public static boolean works(int rank, int limit) {
		return rank >= 0 && rank < limit;
	}

	/** The cruise height of a flight between a nest at height {@code nestY} and a target at {@code targetY}. */
	public static int cruiseY(int nestY, int targetY) {
		return Math.max(nestY, targetY) + CRUISE_CLEARANCE;
	}

	/** Whole blocks of a route {@code length} blocks long, one way: rounded up, never negative. */
	public static int routeBlocks(double length) {
		return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) Math.ceil(length - 1.0E-6)));
	}

	/** Ticks one leg of {@code pathBlocks} takes at tier 1, at least {@link #MIN_FLIGHT_TICKS}. */
	public static int flightTicks(int pathBlocks) {
		long ticks = (long) Math.max(0, pathBlocks) * Math.max(1, SettlementConfig.settlementDroneFlightTicksPerBlock);
		return (int) Math.max(MIN_FLIGHT_TICKS, Math.min(Integer.MAX_VALUE, ticks));
	}

	/** EU the battery spends on a flight of {@code pathBlocks} one way: out and back. */
	public static long flightCost(int pathBlocks) {
		return 2L * Math.max(0, pathBlocks) * Math.max(0, SettlementConfig.settlementDroneEuPerBlockFlown);
	}

	/** EU one placed or removed block costs (R94): the core pays it, or the battery while the core is dark. */
	public static long blockPrice() {
		return Math.max(0, SettlementConfig.settlementDroneEuPerBlock);
	}

	/**
	 * Whether a drone with {@code battery} EU may take off on a flight of {@code pathBlocks}: the round trip plus one
	 * block's price, whatever the core does meanwhile.
	 */
	public static boolean canTakeOff(long battery, int pathBlocks) {
		return battery >= flightCost(pathBlocks) + blockPrice();
	}

	/** The drone's battery. */
	public static long batteryCapacity() {
		return Math.max(1, SettlementConfig.settlementDroneBattery);
	}

	/** EU a nest takes from the core this tick: its charge rate, never more than the battery has room for. */
	public static long chargeWanted(long battery) {
		long room = Math.max(0L, batteryCapacity() - Math.max(0L, battery));
		return Math.min(room, Math.max(0, SettlementConfig.settlementDroneChargePerTick));
	}
}
