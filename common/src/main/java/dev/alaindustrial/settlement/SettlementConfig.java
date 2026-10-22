package dev.alaindustrial.settlement;

import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.Section;

/**
 * Knobs of the ALA towns (epic MOD-824), in the holder of their own subsystem (ADR-034): the settlement core of
 * MOD-831 first; the shift workers (MOD-832) and the reception capsule (MOD-833) add theirs here. The json key of
 * each knob is its field name and its section is declared on the field; {@code Config.REGISTRY} scans this class
 * next to {@code Config}.
 *
 * <p><b>The tier ladder is four rows of the same four numbers</b> — territory radius, house limit, worker limit,
 * upkeep — one row per {@link SettlementTier}. Only the LV row is reachable in the first slice; the others are
 * declared now so that what a tier means is data an operator can read and tune, not a constant found in code
 * later. {@link SettlementTier} reads them and keeps the radius and both limits climbing whatever the file says.
 *
 * <p>Minecraft-free on purpose: the L1 lane loads it ({@code SettlementTierTest}).
 */
public final class SettlementConfig {

	private SettlementConfig() {
	}

	// ── MOD-831: the settlement core ─────────────────────────────────────────────────────────────
	/** The core's EU buffer: about four minutes of LV upkeep before the grace period even starts. */
	@Knob(section = Section.SETTLEMENTS, min = 1, clientVisible = true,
			doc = "EU buffer of the settlement core.")
	public static int settlementCoreBuffer = 2000;
	/**
	 * How long a powered core keeps its territory once it can no longer pay its upkeep, counted as upkeep owed
	 * ({@link SettlementCoreUpkeep}). A dark core also lights only once its buffer holds this many ticks of
	 * upkeep, so a supply that cannot carry it does not flicker the territory on and off every tick.
	 */
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "Ticks a powered settlement core keeps its territory after it stops being able to pay its upkeep;"
					+ " a dark core lights again only once its buffer holds this many ticks of upkeep.")
	public static int settlementCoreGraceTicks = 20;

	// LV — the first tier, the only one the first slice can run.
	/** LV upkeep: as much as an LV mob repeller — a whole fuel generator, or eight solar panels. */
	@Knob(section = Section.SETTLEMENTS, min = 1, clientVisible = true,
			doc = "EU per tick an LV settlement core draws while it holds its territory.")
	public static int settlementCoreEuPerTickLv = 8;
	@Knob(section = Section.SETTLEMENTS, min = 1, clientVisible = true,
			doc = "Territory radius in blocks of an LV settlement: the square column around the core, all heights, that"
					+ " every settlement holds; a town built by a structure also holds its buildings.")
	public static int settlementRadiusLv = 32;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Houses an LV settlement may have restored.")
	public static int settlementHouseLimitLv = 4;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Shift workers an LV settlement may hold.")
	public static int settlementWorkerLimitLv = 4;

	// MV
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "EU per tick an MV settlement core draws while it holds its territory.")
	public static int settlementCoreEuPerTickMv = 32;
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "Territory radius in blocks of an MV settlement; never below the LV radius.")
	public static int settlementRadiusMv = 48;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Houses an MV settlement may have restored; never below the LV limit.")
	public static int settlementHouseLimitMv = 8;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Shift workers an MV settlement may hold; never below the LV limit.")
	public static int settlementWorkerLimitMv = 8;

	// HV
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "EU per tick an HV settlement core draws while it holds its territory.")
	public static int settlementCoreEuPerTickHv = 64;
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "Territory radius in blocks of an HV settlement; never below the MV radius.")
	public static int settlementRadiusHv = 64;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Houses an HV settlement may have restored; never below the MV limit.")
	public static int settlementHouseLimitHv = 12;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Shift workers an HV settlement may hold; never below the MV limit.")
	public static int settlementWorkerLimitHv = 12;

	// Atomic — the top tier, fed by a reactor.
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "EU per tick an atomic settlement core draws while it holds its territory.")
	public static int settlementCoreEuPerTickAtomic = 256;
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "Territory radius in blocks of an atomic settlement; never below the HV radius.")
	public static int settlementRadiusAtomic = 96;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Houses an atomic settlement may have restored; never below the HV limit.")
	public static int settlementHouseLimitAtomic = 20;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Shift workers an atomic settlement may hold; never below the HV limit.")
	public static int settlementWorkerLimitAtomic = 20;

	// ── MOD-848: the territory of a town a structure built ──────────────────────────────────────
	/**
	 * Epic decision R59: a town built by a structure holds the footprint of all its buildings, grown by this many
	 * blocks on every side — room to walk round the outermost building and to see where the town ends before reaching
	 * its walls. A higher tier adds its radius growth on top ({@link SettlementTier#growth()}).
	 */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Blocks a town built by a structure holds past the footprint of its buildings, on every side; a"
					+ " higher tier adds what its radius has grown past the LV one.")
	public static int settlementTerritoryMargin = 8;

	// ── MOD-832: the shift workers ────────────────────────────────────────────────────────────────
	/**
	 * Epic decision R30: a dead shift worker's number is closed for good (true), or the worker waits to be brought
	 * back by the reception capsule (false, decision R39). Decided at the moment of death.
	 */
	@Knob(section = Section.SETTLEMENTS,
			doc = "Shift workers die for good: their record number is closed and never handed out again. False: a"
					+ " dead worker waits in the town's records to be brought back by the reception capsule.")
	public static boolean shiftWorkerPermadeath = true;
	/** A new worker's maximum health, as a player's (docs/mobs/shift_worker.md). */
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "Maximum health of a new shift worker (half-hearts); a player has 20.")
	public static int shiftWorkerMaxHealth = 20;
	/** How far from its anchor (the town's core) a worker walks at most; never past the territory. */
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "Blocks a shift worker walks away from its town's core at most (never past the territory); it"
					+ " walks back once it is further.")
	public static int shiftWorkerWanderRadius = 12;
	/** ADR-050 point 11: new paths one town grants per tick; the rest of its workers wait a tick. */
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "New paths the shift workers of one town may plan in one tick; the others wait for the next.")
	public static int shiftWorkerPathsPerTick = 2;

	// ── MOD-854: the shift worker's profile (battery, level, stats; epic decisions R67–R70) ──────
	/** Epic decision R68: the battery a new worker carries — over three game days of idle drain. */
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "EU a shift worker's battery holds before any point in the Battery stat.")
	public static int shiftWorkerBatteryBase = 20000;
	/** Epic decision R69: what one point in the «Battery» stat adds. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "EU of battery capacity one point in a shift worker's Battery stat adds.")
	public static int shiftWorkerBatteryPerPoint = 5000;
	/** Epic decision R67: the suit, the helmet and walking — what an awake worker drains while it has no work. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "EU per tick an awake shift worker drains from its battery while it has no work; its town's core"
					+ " recharges it and pays this on top of its own upkeep.")
	public static double shiftWorkerIdleEuPerTick = 0.25;
	/** Epic decision R67: the player's mastery rate — one XP for this many EU that left the battery. */
	@Knob(section = Section.SETTLEMENTS, min = 1, floorTo = 1,
			doc = "EU that must leave a shift worker's battery for one point of its experience; charging counts"
					+ " for nothing.")
	public static int shiftWorkerEuPerXp = 1000;
	/** Epic decision R70: XP of a worker's first level (1 to 2). */
	@Knob(section = Section.SETTLEMENTS, min = 1, floorTo = 1,
			doc = "Experience a shift worker needs for level 2; each next level costs shiftWorkerLevelXpMultiplier"
					+ " times the previous one, over 40 levels.")
	public static int shiftWorkerXpLevelOneCost = 15;
	/** Epic decision R70: the growth of a worker's level cost — level 5 at about 72 000 EU, level 40 at 10 million. */
	@Knob(section = Section.SETTLEMENTS, min = 1.0, exclusive = true,
			doc = "How many times each shift worker level costs more experience than the previous one. Must be > 1.0.")
	public static double shiftWorkerLevelXpMultiplier = 1.12;
	/** Epic decision R69: what one point in the «Strength» stat adds. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Maximum health (half-hearts) one point in a shift worker's Strength stat adds.")
	public static int shiftWorkerHealthPerPoint = 2;
	/** Epic decision R69: points one stat holds at most. */
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "Points one stat of a shift worker holds at most.")
	public static int shiftWorkerStatCap = 10;
	/** Epic decision R69: redistributing all points at the reception capsule, paid from its buffer. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "EU taking back all of a shift worker's invested points costs, paid from the reception capsule"
					+ " it stands at.")
	public static int shiftWorkerRespecCost = 50000;

	// ── MOD-833: the reception capsule ───────────────────────────────────────────────────────────
	/**
	 * EU one new shift worker costs, paid from the capsule's own buffer when the worker steps out. A worker is a
	 * member of the crew, not a machine's product (owner decision, epic R57): at a full LV packet of 32 EU/t the
	 * capsule fills in about 156 seconds of uninterrupted supply. The buffer holds twice this, so bringing a dead
	 * worker back (epic decision R39, double price) fits too.
	 */
	@Knob(section = Section.SETTLEMENTS, min = 1, clientVisible = true,
			doc = "EU one new shift worker costs at the reception capsule; the capsule's buffer holds twice this.")
	public static int capsuleEuPerWorker = 100000;
	/**
	 * How long restoring one worker takes once everything is paid for: one game day (owner decision, MOD-847). A
	 * development run takes ten seconds instead, so a tester need not wait twenty minutes ({@link Knob#devDefault()}).
	 */
	@Knob(section = Section.SETTLEMENTS, min = 1, clientVisible = true, devDefault = 200,
			doc = "Ticks the reception capsule takes to restore one shift worker once its price is in"
					+ " (24000 = one game day).")
	public static int capsuleRestoreTicks = 24000;
	/** The suit's electronics: one electronic circuit per worker. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Electronic circuits one new shift worker costs at the reception capsule (at most 32).")
	public static int capsuleCircuitsPerWorker = 1;
	/** The suit itself, a sealed overall: eight rubber per worker. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Rubber one new shift worker costs at the reception capsule (at most 32).")
	public static int capsuleRubberPerWorker = 8;
	/** The helmet's copper and the battery cylinder on the back: four copper plates per worker. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Copper plates one new shift worker costs at the reception capsule (at most 32).")
	public static int capsuleCopperPlatesPerWorker = 4;

	// ── MOD-858: the dispatcher, the drone nests and the tier-1 builder drone (epic decisions R91, R94) ──
	/** R91: drone nests that work in a town with an LV core; the rest stand idle and say why. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Drone nests that work in a town whose core is LV; extra nests stand idle.")
	public static int settlementDroneNestsLv = 1;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Drone nests that work in a town whose core is MV.")
	public static int settlementDroneNestsMv = 2;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Drone nests that work in a town whose core is HV.")
	public static int settlementDroneNestsHv = 3;
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Drone nests that work in a town whose core is atomic.")
	public static int settlementDroneNestsAtomic = 4;
	/** R91: a restored town hall finishes the landing pad on its roof — this many nests more. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "Extra drone nests that work once the town hall is restored.")
	public static int settlementDroneNestsRestoredBonus = 1;
	/** R94: the drone's own battery; it finishes a flight on it when the core goes dark. */
	@Knob(section = Section.SETTLEMENTS, min = 1, clientVisible = true,
			doc = "EU a builder drone's battery holds.")
	public static int settlementDroneBattery = 4000;
	/** R94: what a nest takes from its town's core per tick while its drone is docked. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "EU per tick a drone nest charges its drone's battery with from the settlement core, out of what"
					+ " the core holds above its upkeep reserve.")
	public static int settlementDroneChargePerTick = 16;
	/** R94: the core pays for every block a drone places and every block it takes away. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "EU the settlement core pays for every block a builder drone places or removes; the drone's"
					+ " battery pays it while the core is dark.")
	public static int settlementDroneEuPerBlock = 20;
	/** The flight itself is paid by the battery: per block of the path, out and back. */
	@Knob(section = Section.SETTLEMENTS, min = 0,
			doc = "EU a builder drone's battery spends per block of flight path, out and back.")
	public static int settlementDroneEuPerBlockFlown = 1;
	/** Tier 1's speed (R87): ticks per block of flight path. */
	@Knob(section = Section.SETTLEMENTS, min = 1,
			doc = "Ticks a tier-1 builder drone takes per block of flight path.")
	public static int settlementDroneFlightTicksPerBlock = 2;
}
