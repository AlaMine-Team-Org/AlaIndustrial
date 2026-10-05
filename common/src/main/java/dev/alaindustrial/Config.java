package dev.alaindustrial;

import dev.alaindustrial.config.ConfigFile;
import dev.alaindustrial.config.Knob;
import dev.alaindustrial.config.KnobRegistry;
import dev.alaindustrial.config.Section;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.core.reactor.ReactorConfig;
import dev.alaindustrial.item.ToolConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Tunable balance knobs (v0.2 defaults), loaded from {@code config/alaindustrial.json}.
 * Generators/machines/storage/cables read these at runtime, so a server can rebalance without a
 * code change. A key missing from the file carries no override: on every load it takes the default
 * compiled into this build, never the value an earlier load left live (MOD-694, owner decision D8).
 * Each load therefore starts from the baseline of builtin defaults and lays the file's values over
 * it, so the effective balance is a function of the file alone, and an operator resets one knob by
 * deleting its line. The file stays forward/backward safe: a new knob absent from an old file is
 * simply at its default. An untouched knob follows a changed default through the {@code builtinDefaults}
 * block, an edited one never does (ADR-033, which replaced ADR-013).
 *
 * <p>Knobs live in subsystem holders (ADR-034): {@code RadiationConfig}, {@code GeneratorConfig}, {@code
 * ToolConfig}, {@code ReactorConfig}; {@link #REGISTRY} scans them with this class. The FILE is structured: since
 * MOD-402 it carries a {@link dev.alaindustrial.config.ConfigSchema#VERSION} and groups its keys into
 * {@link Section}s, and the load layer ({@code dev.alaindustrial.config}) migrates an older file into the
 * current shape before reading it. Adding a knob never needs a migration; changing the file's shape does.
 *
 * <p>The balance fields and the pure file read/write ({@link #loadFrom(Path)}) are loader-neutral
 * and live in {@code common}. Resolving the per-loader config directory and hooking the
 * datapack-reload event is a platform seam: Fabric wires it in
 * {@code dev.alaindustrial.FabricConfigLoader} and {@code dev.alaindustrial.NeoForgeConfigLoader} (MOD-022).
 */
public final class Config {
	private Config() {
	}

	// --- Global multipliers (v0.2-neutral defaults) ---
	/** Scales every generator's EU/t output. Applied once in AbstractGeneratorBlockEntity.serverTick. */
	@Knob(section = Section.GLOBAL, min = 0.0, exclusive = true,
			doc = "Multiplier on EVERY generator's EU/t output. 1.0 = unchanged; 2.0 = twice the generation server-wide.")
	public static float globalEuRateMultiplier = 1.0f;
	/** Scales machine speed (E_op-invariant): EU/t up, duration down by the same factor. */
	@Knob(section = Section.GLOBAL, clientVisible = true, min = 0.0, exclusive = true,
			doc = "Machine speed multiplier (energy-neutral): higher = machines draw more EU/t but finish proportionally faster. 1.0 = unchanged.")
	public static float globalMachineSpeedMultiplier = 1.0f;

	// --- Pump (LV, EU-powered fluid mover) ---
	/** EU spent per bucket of fluid the pump moves (extract + push). The pump is one of the most
	 * energy-hungry machines — at 1000 EU/bucket it is a noticeable consumer, while a bucket of lava
	 * still yields 16 000 EU in the geothermal generator (16× payback on the pump's own tax). */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 0,
			doc = "EU the pump spends per bucket of fluid it moves (extract + push).")
	public static int pumpEuPerBucket = 1000;
	/** How many ticks the pump waits after a BFS scan before scanning again. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "How many ticks the pump waits after a BFS scan before scanning again.")
	public static int pumpScanCooldownTicks = 20;
	/** Max Manhattan distance the pump BFS searches for a fluid source. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Max Manhattan distance the pump BFS searches for a fluid source.")
	public static int pumpScanMaxDistance = 32;
	/** Max blocks the pump BFS visits per scan, caps lag. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Max blocks the pump BFS visits per scan, caps lag.")
	public static int pumpScanMaxVisited = 512;
	/**
	 * How long {@code lit} and the hum stay on after the pump's last bucket (MOD-143); covers the loop length,
	 * so steady pumping reads as continuously lit.
	 */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "How many ticks lit (and the working hum) stays on after the pump's last bucket transfer.")
	public static int pumpLitHoldTicks = 60;

	/** Portable passive tank capacity (MOD-111): 8 buckets, intentionally below machine tanks (10). */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Portable fluid tank capacity in mB (1000 mB = 1 bucket). Applies to newly placed tanks.")
	public static int fluidTankCapacity = 8000;

	/**
	 * Advanced portable tank capacity (MOD-612): 16 buckets, twice the basic — the mod's x2 logistics step,
	 * and just above a machine tank on purpose.
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Advanced fluid tank capacity in mB (1000 mB = 1 bucket). Applies to newly placed tanks.")
	public static int fluidTankAdvancedCapacity = 16000;

	// --- Teleporter (HV anchor station, MOD-091) ---
	/**
	 * Teleporter EU buffer, x25 a battery box: the TARGET station pays each jump in one lump, so this holds
	 * about 25-50 home jumps while its chunk is unloaded.
	 */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 1,
			doc = "Teleporter station EU buffer. Applies to newly placed stations.")
	public static int teleporterBuffer = 500_000;
	/** Flat part of a jump's price — the "even next door is not free" floor (~17 macerator cycles). */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Flat EU part of a jump's price (paid even for a short hop).")
	public static int teleporterBaseCost = 5000;
	/** Added per block of euclidean distance to the target station. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Added EU per block of straight-line distance to the target station.")
	public static int teleporterCostPerBlock = 5;
	/**
	 * Warmup before a jump fires (5 s): long enough for the wind-up scene; escape is prevented by
	 * cancel-on-damage, not by the clock. Raise it for PvP.
	 */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Warmup before a jump fires (20 ticks = 1 second). Cancelled by damage.")
	public static int teleporterWarmupTicks = 100;
	/** Anti-spam lockout after landing, per player. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Per-player anti-spam lockout after landing (ticks).")
	public static int teleporterCooldownTicks = 1200;
	/** Moving further than this from where the warmup started cancels it. A step aside is fine. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Moving further than this many blocks from where warmup started cancels the jump.")
	public static int teleporterWarmupCancelRadius = 2;
	/**
	 * Max stations one remote holds (MOD-093); bounds the data component and the screen's list. Enforced
	 * server-side at bind time.
	 */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 1,
			doc = "Max stations one teleport remote can hold.")
	public static int teleporterMaxPoints = 16;
	/**
	 * Price of one random jump (MOD-116): flat so it can be read before the dice land; a tenth of {@link
	 * #teleporterBuffer}, dearer than a targeted jump for the convenience.
	 */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Flat EU a random jump costs, paid by the station the remote has selected.")
	public static int teleporterRtpCost = 50_000;
	/** How far a random jump may throw the player. The outer edge of the ring, in blocks. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Outer edge of the ring a random jump throws the player into, in blocks.")
	public static int teleporterRtpRadius = 5000;
	/** Inner edge of the random-jump ring, so an honest draw never lands the player where they stood. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "Inner edge of that ring: a random jump never leaves the player closer than this.")
	public static int teleporterRtpMinRadius = 500;
	/**
	 * Candidate spots a random jump tries before giving up (free for the player). Small: each candidate past
	 * the noise probe costs a synchronous chunk load.
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "How many candidate spots a random jump tries before giving up. Giving up costs nothing.")
	public static int teleporterRtpMaxAttempts = 8;
	/** Ticks the capsule door stays open after a click before shutting itself (MOD-112): 3 s, enough to step out. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Ticks a teleporter capsule door stays open after a click before it closes itself.")
	public static int teleporterCapsuleDoorOpenTicks = 60;
	/** How long the door waits before re-testing a doorway someone is still standing in. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Ticks a teleporter capsule door waits before re-testing a doorway that still has someone in it.")
	public static int teleporterCapsuleDoorRecheckTicks = 10;
	/**
	 * Ticks the door panels take to slide either way. Cosmetic: the {@code open} state and its collision flip
	 * in one tick.
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Ticks the capsule door takes to slide its full height; cosmetic only, the open state flips in one tick.")
	public static int teleporterCapsuleDoorSlideTicks = 12;

	// --- Storage / per-block buffers (EU) ---
	@Knob(section = Section.STORAGE, clientVisible = true, min = 1,
			doc = "Battery Box EU buffer. Applies to newly placed blocks (already-placed keep their capacity until the chunk reloads).")
	public static int batteryBoxBuffer = 20_000;
	/**
	 * Reinforced Energy Storage (MV) buffer, five Battery Boxes (MOD-350/351); a store is larger than an MV
	 * machine's {@code tierMvCapacity}. Its rate follows {@code EnergyTier.MV}.
	 */
	@Knob(section = Section.STORAGE, clientVisible = true, min = 1,
			doc = "Reinforced Energy Storage (MV) EU buffer. Applies to newly placed blocks (already-placed keep their capacity until the chunk reloads).")
	public static int cesuBuffer = 100_000;

	/**
	 * EU/t a store hands to a non-cascade sink over cable (MOD-353: Teleporter, Charging Station); the copper
	 * cable's throughput. 0 disables the channel.
	 */
	@Knob(section = Section.STORAGE, min = 0,
			doc = "EU/tick a storage block feeds a non-cascade sink (teleporter, charging station) over cable. 0 disables the channel.")
	public static int storageFeedRate = 12;

	/**
	 * Share of capacity a donor store keeps on that channel, 0..1 (MOD-353): below it the channel is shut, so
	 * a sink can never drain the bank. 0 is not recommended.
	 */
	@Knob(section = Section.STORAGE, min = 0.0, floorTo = 1.0,
			doc = "Share of its capacity a storage block keeps for itself when feeding a non-cascade sink over cable (0..1). Below this line the channel is shut, so a teleporter can never drain the base's bank.")
	public static double storageFeedReserveFraction = 0.5;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Macerator EU buffer. Applies to newly placed blocks.")
	public static int maceratorBuffer = 800;
	/** Shared buffer for ordinary LV processing machines: electric furnace, compressor, extractor,
	 * sawmill, polymerizer and vulcanizer. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Shared EU buffer for ordinary LV processing machines: electric furnace, compressor, extractor, sawmill, polymerizer and vulcanizer. Applies to newly placed blocks.")
	public static int machineBuffer = 800;
	/** Electric Heater EU buffer. At the default 6 EU/t it holds one cold start (1200 EU) plus one
	 * complete 200-tick vulcanization (1200 EU) and smooths a thin LV supply without becoming bulk
	 * storage — a fully charged heater can light itself and serve one batch with the cable cut. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Electric Heater EU buffer. Applies to newly placed blocks.")
	public static int electricHeaterBuffer = 2400;
	/**
	 * Pump EU buffer: several buckets' worth of {@link #pumpEuPerBucket}, smoothing a feed through one thin
	 * cable ({@link #cableBuffer} EU/t, MOD-070, ADR-001).
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Pump EU buffer. Applies to newly placed blocks.")
	public static int pumpBuffer = 4000;

	/**
	 * Per-cable live EU buffer, which is also the segment throughput (MOD-070, ADR-001). Tiny so cables are
	 * never storage; separate from the tier's machine capacity. New cables only.
	 */
	@Knob(section = Section.CABLES, min = 1,
			doc = "Per-cable working EU buffer — the live transport-segment buffer (MOD-070). Tiny by design so a wall of cables can't be used as bulk storage. Applies to newly placed cables.")
	public static int cableBuffer = 12;

	// --- Item pipes (MOD-104, rebalanced in MOD-108) ---
	/**
	 * Items a pipe network moves per transfer, every {@link #itemPipeTransferIntervalTicks}: 2 items/s, the
	 * passive starter tier (MOD-104). Grades step x2 (MOD-581).
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Items an item-pipe network moves per transfer. With the interval below this sets throughput.")
	public static int itemPipeItemsPerTransfer = 2;

	/**
	 * Ticks between transfers on one pipe network (20 = once per second); grades raise the batch, not this.
	 * See {@link #itemPipeItemsPerTransfer}.
	 */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 1,
			doc = "Server ticks between item-pipe transfers (20 = once per second).")
	public static int itemPipeTransferIntervalTicks = 20;

	/**
	 * Items an ADVANCED pipe network moves per transfer (MOD-581): 4/s. A network runs at its WEAKEST pipe,
	 * hence the visibly thicker pipe.
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Items an ADVANCED item-pipe network moves per transfer. A network runs at its weakest pipe.")
	public static int itemPipeAdvancedItemsPerTransfer = 4;

	// --- Fluid pipes (MOD-151) ---
	/**
	 * Working buffer of one fluid-pipe segment in mB, which is also its throughput (one hop per tick). 50
	 * keeps the pump (about 32 mB/t) the bottleneck; small so pipes are not storage.
	 */
	@Knob(section = Section.LOGISTICS, clientVisible = true, min = 1,
			doc = "Per-segment fluid buffer in mB — also the segment's throughput, since fluid flows through the buffer one hop per tick (MOD-151). Applies to newly placed pipes.")
	public static int fluidPipeSegmentBuffer = 50;

	/**
	 * Working buffer, and throughput, of one ADVANCED fluid-pipe segment (MOD-675): 100 mB/t. A line flows at
	 * its thinnest segment by physics (MOD-677), hence the thicker pipe.
	 */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "Per-segment buffer of the ADVANCED fluid pipe in mB — also its throughput. An ordinary pipe in the line slows it. Applies to newly placed pipes.")
	public static int fluidPipeAdvancedSegmentBuffer = 100;

	/**
	 * Fluid networks processed per server tick; the rest round-robin to later ticks. Mirrors {@link
	 * #networksPerTick}.
	 */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Fluid networks processed per server tick; the rest round-robin to later ticks.")
	public static int fluidNetworksPerTick = 512;

	// --- Charging Station (MOD-274, the pad the player stands on) ---
	/**
	 * Charging Station buffer, a Battery Box's worth: sized to a visit, not an operation — it fills slowly and
	 * empties fast, the only way {@link #chargePadOutputRate} is reached.
	 */
	@Knob(section = Section.STORAGE, clientVisible = true, min = 1,
			doc = "Charging Station EU buffer. Sized to a visit, not an operation: the station banks power while idle so it can charge a player's gear in one burst. Applies to newly placed blocks.")
	public static int chargePadBuffer = 20_000;
	/**
	 * Max EU/t the station accepts from the grid: the MV ceiling ({@link #tierMvVoltage}) as a ceiling, not a
	 * promise — it scales with the cable or store that feeds it.
	 */
	@Knob(section = Section.STORAGE, clientVisible = true, min = 1,
			doc = "Max EU/t the Charging Station accepts from the grid. A ceiling, not a promise - a copper cable delivers 12, a gold one 48, an adjacent Battery Box 32.")
	public static int chargePadInputRate = 128;
	/**
	 * Max EU/t the station hands, from its buffer, to everything the player carries; each item is still
	 * clamped by its own input rate (32).
	 */
	@Knob(section = Section.STORAGE, clientVisible = true, min = 1,
			doc = "Max EU/t the Charging Station hands to the player standing on it, shared across every powered item they carry (each item still capped by its own input rate).")
	public static int chargePadOutputRate = 128;

	// --- Upgrade Table (MOD-482, fits permanent upgrades onto powered tools) ---
	/** EU/t the Upgrade Table draws while fitting a module. Four times the shared machine rate: the
	 * table rewrites a tool rather than converting an item, and it should be felt on the grid. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU/t the Upgrade Table draws while fitting a module.")
	public static int upgradeTableEuPerTick = 8;
	/** Ticks one installation takes at {@link #upgradeTableEuPerTick}. 200 ticks (10 s) x 8 EU/t =
	 * 1 600 EU per upgrade — a visible piece of work next to a 200 EU smelt, but not a wait. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks the Upgrade Table takes to fit one module.")
	public static int upgradeTableDuration = 200;

	// --- Stock Display Frame (MOD-066, no energy) ---
	/** How often (ticks) a stock display frame rescans the container behind it. 20 = once a second;
	 * a 100-frame warehouse costs ~5 container sums per tick at the default. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "How often (ticks) a Stock Display Frame rescans the container behind it.")
	public static int stockFrameScanIntervalTicks = 20;

	// --- Monitoring wall (MOD-480) ---
	/** How often (ticks) a monitor core re-reads every container wired to it. One pass per interval
	 * for the whole wall, however many panels read the result. */
	@Knob(section = Section.LOGISTICS, min = 1,
			doc = "How often (ticks) a Monitor Core re-reads the containers wired to it.")
	public static int monitorScanIntervalTicks = 20;

	/** Upkeep of a monitor core itself, before any panel shows anything. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "EU per tick a Monitor Core costs on its own, with no panel showing a number.")
	public static int monitorCoreIdleEuPerTick = 2;

	/** Added upkeep per panel that is actually showing a number; an empty panel costs nothing. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "EU per tick added for each panel that is actually displaying a count.")
	public static int monitorPanelEuPerTick = 1;

	/** How many different item types one capacity card lets the wall watch at once. */
	@Knob(section = Section.LOGISTICS, min = 0,
			doc = "How many distinct item types one Capacity Card lets a monitoring wall track.")
	public static int monitorCardTrackedTypes = 6;

	// --- Machines: shared EU/tick + per-machine duration (ticks) -> E_op = euPerTick × duration ---
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Base EU/t a processing machine draws while running (energy per operation = this x its duration).")
	public static int machineEuPerTick = 2;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks a macerator takes per operation at 1.0 speed.")
	public static int maceratorDuration = 150;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks an electric furnace takes per smelt at 1.0 speed.")
	public static int electricFurnaceDuration = 100;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks a compressor takes per operation at 1.0 speed.")
	public static int compressorDuration = 130;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks an extractor takes per operation at 1.0 speed.")
	public static int extractorDuration = 120;
	/**
	 * Recycler (MOD-145): per ITEM ground; a briquette is cast once the batch reaches {@link
	 * #recyclerBatchMass}. Junk is never cheaper to process than ore is to mine.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks a recycler takes to grind one item at 1.0 speed, before the blade grade.")
	public static int recyclerDuration = 40;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU per tick a recycler draws while grinding.")
	public static int recyclerEuPerTick = 8;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Slag mass a recycler batch must reach before it casts a briquette.")
	public static int recyclerBatchMass = 64;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Items an iron recycler blade set grinds before it wears out.")
	public static int recyclerBladesIronMaxDamage = 256;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Items a tempered-iron recycler blade set grinds before it wears out.")
	public static int recyclerBladesTemperedMaxDamage = 768;
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Items a diamond recycler blade set grinds before it wears out.")
	public static int recyclerBladesDiamondMaxDamage = 2048;

	/**
	 * Ceramic plates per carbon briquette in the quench press (MOD-590/594), the only way to break one; four
	 * plates make a block.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Plates one carbon briquette yields when a piston fires into the water it floats in.")
	public static int ceramicPlatesFromPress = 4;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Redstone dust the press consumes per briquette, alongside the briquette itself.")
	public static int ceramicPressRedstoneCost = 2;
	/** Sawmill (MOD-150): ticks per cut at 1.0 speed. 80 → 160 EU/op — the cheapest machine op (wood
	 * saws easier than ore mills): furnace 100, extractor 120, compressor 130, macerator 150. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks a sawmill takes per cut at 1.0 speed (all four modes).")
	public static int sawmillDuration = 80;
	/** Polymerizer (MOD-019): ticks per bucket of oil at 1.0 speed. 200 → 400 EU/op — the most expensive
	 * op of the LV processing family, because rubber is the material gate into MV and a bucket of oil is
	 * a whole pumping cycle's worth of input, not a single ore. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks a polymerizer takes to turn one bucket of oil into raw rubber at 1.0 speed.")
	public static int polymerizerDuration = 200;
	/** Vulcanizer (MOD-258): ticks per operation at 1.0 speed. The shipped recipe costs 400 EU, so at
	 * the ordinary-machine rate of 2 EU/t the operation takes 200 ticks at every heat tier. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Fallback ticks a vulcanizer operation takes at 1.0 speed; shipped recipe energy 400 / machineEuPerTick 2 = 200.")
	public static int vulcanizerDuration = 200;
	/** Thermal Centrifuge (MOD-424) ticks per operation at 1.0 speed: 800 EU at its own 4 EU/t. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Fallback ticks a thermal centrifuge operation takes at 1.0 speed; shipped recipe energy 800 / thermalCentrifugeEuPerTick 4 = 200.")
	public static int thermalCentrifugeDuration = 200;
	/**
	 * Thermal Centrifuge EU/t while spinning up or processing: double the machine rate; with a heater (4 + 6)
	 * close to the copper cable's 12.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU/t a thermal centrifuge spends while spinning up or processing; with the heater below the pair draws 10 EU/t.")
	public static int thermalCentrifugeEuPerTick = 4;
	/**
	 * Ticks a stopped rotor needs to reach speed once powered: twice the heater's warm-up, so the machine
	 * visibly waits on its own mass (playtest 2026-08-16).
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a stopped centrifuge rotor needs to reach working speed after the redstone signal arrives; it sheds speed at half this rate.")
	public static int thermalCentrifugeSpinupTicks = 400;

	// ── MOD-505: the crystal greenhouse. Shares the reactor's room scanner, nothing else. ──
	/**
	 * How often a crystal-farm controller re-scans its greenhouse (as {@link ReactorConfig#reactorScanIntervalTicks});
	 * also
	 * refreshes the seedbed list and water check.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between full re-scans of a crystal greenhouse by its controller; also refreshes its seedbed list and water check.")
	public static int crystalFarmScanIntervalTicks = 40;
	/** Smallest greenhouse interior VOLUME, in blocks: the room is flood-filled and may be any shape (MOD-505). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Smallest interior volume of a crystal greenhouse, in blocks; the room may be any shape, so this is a volume rather than an edge length.")
	public static int crystalFarmRoomMinCells = 27;
	/**
	 * Largest greenhouse interior volume, the fill's budget: past it the room reads as unsealed. 4096 is a
	 * 16x16x16 hall.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Largest interior volume of a crystal greenhouse, in blocks; past it the room reads as unsealed.")
	public static int crystalFarmRoomMaxCells = 4096;
	/** How far from the controller the fill may reach: keeps a leaking room from reading unloaded terrain. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "How far from its controller a greenhouse fill may reach, in blocks; keeps a leaking room from scanning into unloaded terrain.")
	public static int crystalFarmRoomMaxSpan = 24;
	/** Ticks between growth attempts; far slower than the scan, as a crystal takes over an hour unaided. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between growth attempts on every seedbed in a greenhouse.")
	public static int crystalFarmGrowthIntervalTicks = 100;
	/**
	 * 1-in-N chance a seedbed advances on one unaided attempt: about 90 minutes per crystal. Lower it to a
	 * single digit to watch the farm in a test.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "1-in-this chance a seedbed advances on one attempt with no water and no power; lower it to single digits to watch a farm work during a test.")
	public static int crystalFarmGrowthChanceDivisor = 270;
	/** How much water in the room cuts the growth divisor by. Free to supply, so the smaller bonus. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Factor water in the room cuts the crystal growth divisor by.")
	public static int crystalFarmWaterSpeedup = 3;
	/** How much a powered attempt cuts the growth divisor by, on top of water. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Factor a powered attempt cuts the crystal growth divisor by, on top of water.")
	public static int crystalFarmPowerSpeedup = 2;
	/**
	 * Growth-divisor cut from a sprinkler in the room (MOD-525), the third axis beside water and power: the
	 * full stack is 270 / 3 / 2 / 2 = 22.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Factor a sprinkler in the room cuts the crystal growth divisor by, on top of water and power.")
	public static int crystalFarmSprinklerSpeedup = 2;
	/**
	 * mB of nutrient solution one boosted growth event costs, charged on delivery like {@link
	 * #crystalFarmEuPerGrowth}.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "mB of nutrient solution one sprinkler-boosted growth event costs; charged only when the event happens.")
	public static int crystalFarmSolutionPerGrowthMb = 50;
	/** EU one boosted growth event costs, charged on delivery, not per roll. Zero makes the power bonus free. */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "EU one boosted growth event costs; charged only when the event actually happens.")
	public static int crystalFarmEuPerGrowth = 64;
	/** Buffer of a farm controller, in EU. Power is optional here, so this only smooths the boost. */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Energy buffer of a crystal farm controller, in EU.")
	public static int crystalFarmBuffer = 4000;
	/**
	 * Ticks a hand-opened greenhouse door stays open before sealing itself (the room only grows closed).
	 * Redstone-held doors ignore it.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a hand-opened greenhouse door stays open before sealing itself; a door held open by redstone ignores this.")
	public static int crystalFarmDoorAutoCloseTicks = 100;
	/** Ticks the door waits before re-testing a doorway that still has somebody standing in it. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the greenhouse door waits before re-testing a doorway that still has someone standing in it.")
	public static int crystalFarmDoorOccupiedRecheckTicks = 10;
	/**
	 * Buds one amethyst shard buys in a seedbed (one shard per click): a shard in, a crystal out, which drops
	 * {@link dev.alaindustrial.core.crystal.CrystalGrowth#SHARDS_PER_RIPE_CRYSTAL}. A block counts as four.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Buds one amethyst shard buys when fed to a seedbed; shards go in one per click, so this is the exchange rate of the whole farm.")
	public static int crystalSeedbedChargesPerShard = 1;

	/**
	 * EU/t a spun-up centrifuge spends idle to hold its speed; small, a stand-by cost. Below it the rotor
	 * sheds speed at half the rate it gained it.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "EU/t a spun-up thermal centrifuge spends holding revolutions with nothing to process.")
	public static int thermalCentrifugeIdleEuPerTick = 1;
	/** Ticks the canning machine spends pressing one ration; × machineEuPerTick = 200 EU per ration. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks the canning machine takes per ration at 1.0 speed; x machineEuPerTick 2 = 200 EU per ration.")
	public static int canningMachineDuration = 100;
	/**
	 * Food value, in tenths, one ration costs (MOD-383); must stay above the ration's own 96, the loss that
	 * keeps this from duplicating food.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 97,
			doc = "Food value in tenths (nutrition + saturation) the canning machine consumes per ration. Must exceed the ration's own 96, or canning becomes a food duplicator.")
	public static int canningFoodValuePerCan = 120;
	/** Distillation Column (MOD-251): fallback ticks per distillation at 1.0 speed. The shipped recipe
	 * costs 400 EU, so at the ordinary-machine rate of 2 EU/t one run takes 200 ticks — deliberately
	 * the Polymerizer's exact tier: both turn one bucket of pumped crude into product. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Fallback ticks one distillation takes at 1.0 speed; shipped recipe energy 400 / machineEuPerTick 2 = 200.")
	public static int distillationColumnDuration = 200;
	/** Distillation Column (MOD-251): ticks a cold column heats before it can distil. It draws the
	 * ordinary machine rate while heating, so one cold start costs ~warmup × machineEuPerTick EU
	 * (~400 EU at defaults — one distillation's worth). Cooling runs at half this rate. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks a cold distillation column heats (at machineEuPerTick) before it can distil; cooling is twice as slow.")
	public static int distillationColumnWarmupTicks = 200;
	/**
	 * Galvanic Bath (MOD-127) fallback ticks per operation at 1.0 speed: 1000 EU at 2 EU/t, the slowest LV
	 * machine, as the gate into the Fluxweave line.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Fallback ticks a galvanic bath operation takes at 1.0 speed; shipped recipe energy 1000 / machineEuPerTick 2 = 500.")
	public static int galvanicBathDuration = 500;

	// --- The organic chain (MOD-146 / MOD-525): fermenter → biofuel → nutrient solution → sprinkler. ---
	/**
	 * Fermenter (MOD-146) fallback ticks per batch at 1.0 speed: 600 EU at 2 EU/t, 15 s — slow, but a batch
	 * stays watchable.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Fallback ticks one fermenter batch takes at 1.0 speed; shipped recipe energy 600 / machineEuPerTick 2 = 300.")
	public static int fermenterDuration = 300;
	/**
	 * mB of water one fermenter batch drinks: a fixed machine cost, not a recipe field (no recipe family mixes
	 * items and fluids), as {@link #galvanicBathWaterPerOp}.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of water a fermenter batch consumes (not part of the recipe JSON).")
	public static int fermenterWaterPerOp = 100;
	/**
	 * mB of biofuel one batch of the CHEAPEST organic tier brews; the tier is read from the input's tag. Same
	 * cost per batch for all tiers, so the feed is the only lever.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of biofuel a batch of the cheapest organic tier brews (seeds, grass, leaves, rot).")
	public static int fermenterBiofuelPoor = 20;
	/** mB per batch of ordinary harvest — wheat, carrots, melon slices, raw meat. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of biofuel a batch of ordinary harvest brews (wheat, carrots, melon slices, raw meat).")
	public static int fermenterBiofuelCommon = 60;
	/** mB per batch of processed or dense feedstock — golden carrots, cooked food, hay blocks. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of biofuel a batch of processed or dense feedstock brews (golden carrots, cooked food, hay blocks).")
	public static int fermenterBiofuelRich = 150;
	/** Sprinkler (MOD-525) spray radius, matching {@link #gardenDroneRange}: the two share one 9x9 plot. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Sprinkler spray radius in blocks around the block.")
	public static int sprinklerRange = 4;
	/**
	 * Ticks between sprinkler attempts, matching {@link #gardenDroneScanIntervalTicks} on the same plot.
	 * Indoors the greenhouse controller runs growth on its own timer.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between sprinkler spray attempts.")
	public static int sprinklerIntervalTicks = 20;
	/** mB of nutrient solution one successful spray on a vanilla crop costs. A bucket is 20 sprays. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of nutrient solution one successful spray on a vanilla crop costs.")
	public static int sprinklerSolutionPerActionMb = 50;
	/** Sprinkler tank size, in mB. Also its intake rate ceiling — a FluidTank has no separate rate. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Sprinkler tank size in mB; also caps how fast a pipe can fill it.")
	public static int sprinklerTankMb = 4000;

	// --- Overclocker chip (MOD-392): the per-chip speed/energy trade, applied per machine. ---
	/**
	 * Duration multiplier per overclocker chip; paired with a harsher {@link #overclockerEuFactor} so a chip,
	 * unlike {@link #globalMachineSpeedMultiplier}, costs +60 % energy per operation.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 0.0, exclusive = true,
			doc = "MOD-392: duration multiplier per overclocker chip (0.8 = each chip cuts one operation to 80% of its length).")
	public static float overclockerSpeedFactor = 0.8f;
	/**
	 * EU/t multiplier per overclocker chip: one chip buys 1.25x speed for 1.6x energy per op; a 2 EU/t machine
	 * reaches {@code tierLvVoltage} on its fourth chip.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1.0, exclusive = true,
			doc = "MOD-392: EU/t multiplier per overclocker chip (2.0 = each chip doubles the draw). With 0.8 speed this makes every chip cost 60% more energy per operation.")
	public static float overclockerEuFactor = 2.0f;
	/**
	 * Highest overclocker tier (I-III). The per-machine voltage cap usually bites first ({@code base x
	 * euFactor^n <= tier.maxVoltage()}).
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "MOD-392: absolute ceiling on overclocker chips in one machine; the tier cap (base EU/t x factor^n <= tier voltage) usually bites first.")
	public static int overclockerMaxPerMachine = 3;

	// --- Energy condenser (MOD-393): surplus grid power banked, then packed into an item. ---
	/**
	 * Condenser bank ceiling, equal to the tier-III threshold: past it nothing is left to reach, so the block
	 * stops drawing.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "MOD-393: ceiling of the energy condenser's bank; equals the tier-III clot threshold, so it stops drawing once nothing higher is reachable.")
	public static int condenserCapacity = 4_000_000;
	/**
	 * EU/t the condenser accepts: one MV packet ({@link #tierMvVoltage}), so about 32 basic panels saturate
	 * one. Machines are served first by the network's two passes; scale with more condensers.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "MOD-393: EU/t the energy condenser accepts — one MV packet, about 32 basic solar panels. Machines are protected by the network's serve order, not by this number; absorbing a bigger surplus means placing more condensers.")
	public static int condenserInputRate = 128;
	/** Banked EU at which the condenser can yield a tier-I clot; below this its output stays empty. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "MOD-393: banked EU needed before the condenser can yield a tier-I energy clot.")
	public static int clotThresholdI = 250_000;
	/** Banked EU for a tier-II clot — four times tier I. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-393: banked EU for a tier-II clot (four times tier I).")
	public static int clotThresholdII = 1_000_000;
	/** Banked EU for a tier-III clot — four times tier II, and the bank's ceiling. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-393: banked EU for a tier-III clot (four times tier II).")
	public static int clotThresholdIII = 4_000_000;

	// --- MOD-064 alloy smelter. Its own rate, like the incubator and the assembler: melting several
	// metals into one is a hotter job than milling a single ore. 8 EU/t x 150 ticks = 1200 EU per
	// operation (7.5 s) for every alloy — one price across the family, so the four alloys differ by what
	// they consume and yield, not by what the machine charges. 8 EU/t is exactly a coal generator's
	// output and 2/3 of a copper cable's throughput, so one smelter occupies a starter line by itself.
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU/t the alloy smelter draws while running (MOD-064). Four times the machine standard, like the incubator.")
	public static int alloySmelterEuPerTick = 8;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Fallback ticks one alloying operation takes at 1.0 speed (MOD-064); shipped recipe energy 1200 / alloySmelterEuPerTick 8 = 150.")
	public static int alloySmelterDuration = 150;

	// --- MOD-384 component repair bench. Restores a worn rotor/wheel instead of replacing it, at the
	// price of a permanently lower durability ceiling. Its own rate, like the alloy smelter above: at the
	// shared 2 EU/t a T3 repair would run 14 400 ticks (12 minutes) and read as broken rather than
	// expensive. At 8 EU/t the three grades take 1200 / 2400 / 3600 ticks — exactly 60 / 120 / 180 s
	// (MOD-465 raised these from 625/1250/2250: half a minute for a T1 repair read as an errand rather
	// than as a job, and the bench was finished before the player had walked back to it).
	/** EU/t the repair bench draws while repairing. Four times the machine standard. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU/t the component repair bench draws while repairing (MOD-384). Four times the machine standard, like the alloy smelter.")
	public static int repairBenchEuPerTick = 8;
	/**
	 * EU per repair of a T1 component; the material side is one plate, so energy is the real cost ({@code
	 * docs/PERFORMANCE.md}). 9600 = one minute at {@link #repairBenchEuPerTick}.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "MOD-384: EU one repair of a T1 rotor/wheel costs (material: 1 iron plate). 9600 EU / 8 EU-t = 1200 ticks (60 s).")
	public static int repairBenchTier1EuCost = 9600;
	/** EU one repair of a reinforced (T2) component costs — double T1 (2400 ticks, 2 min). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-384: EU one repair of a reinforced rotor/wheel costs (material: 1 tempered iron plate). 19200 EU / 8 EU-t = 2400 ticks (120 s).")
	public static int repairBenchTier2EuCost = 19200;
	/** EU one repair of an advanced (T3) component costs — triple T1 (3600 ticks, 3 min). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-384: EU one repair of an advanced rotor/wheel costs (material: 1 electronic circuit). 28800 EU / 8 EU-t = 3600 ticks (180 s).")
	public static int repairBenchTier3EuCost = 28800;
	/**
	 * Percent of the ORIGINAL durability ceiling one repair burns, linearly ({@code
	 * core.machine.ComponentRepair}); it also sets the repair count (four at 20). 0 = no decay, unlimited.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 0,
			doc = "MOD-384: percent of the ORIGINAL durability ceiling one repair burns, linear (1000 -> 800 -> 600 -> 400). Also sets how many repairs a part gets (four at 20%). 0 disables the decay and the limit with it.")
	public static int repairBenchMaxDamageDecayPercent = 20;

	// --- MOD-275 assembler. The first MV machine: six times the LV rate, but a short operation.
	// 12 EU/t x 40 ticks = 480 EU per craft — dearer than crafting by hand, cheaper than a processing
	// step, so the machine buys time rather than resources. Raised from 8 EU/t after the playtest:
	// automation was reading as too cheap for what it removes. The buffer follows the rate so it still
	// holds 25 operations.
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU/tick the assembler draws while crafting (MOD-275). MV rate: six times an LV machine.")
	public static int assemblerEuPerTick = 12;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Ticks one assembler craft takes at 1.0 speed (MOD-275). 40 = 2 seconds, the pace of the genre.")
	public static int assemblerDuration = 40;
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU buffer of the assembler (MOD-275) — 25 operations at 480 EU each.")
	public static int assemblerBuffer = 12000;
	/**
	 * Galvanic Bath (MOD-127) mB of water per operation, not in the recipe JSON: four buckets a thread, so the
	 * bath is thirsty enough to earn a pump.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of water a galvanic bath consumes per completed operation (not part of the recipe JSON).")
	public static int galvanicBathWaterPerOp = 4000;
	/**
	 * Electric Heater (MOD-258) EU/t while warming or while the Vulcanizer above advances: three machine
	 * tariffs; the pair (6 + 2) fits a copper cable.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU/t an Electric Heater spends while the Vulcanizer directly above it advances; idle heater draws nothing.")
	public static int electricHeaterEuPerTick = 6;
	/**
	 * Electric Heater (MOD-418) warm-up ticks before it supplies any heat; it only warms while a machine above
	 * waits on heat, cools at half rate. 1200 EU, one vulcanization.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "MOD-418: paid heat ticks a cold Electric Heater needs before it supplies tier-3 heat (x3 output); until then it supplies tier 2 (x2). Cooling is twice as slow.")
	public static int electricHeaterWarmupTicks = 200;

	// --- Incubator (MOD-118): the mod's most energy-hungry LV machine. ---
	/** Incubator EU/t while running: four machine standards; irradiating is far pricier than grinding. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU/t the incubator draws while running (4x the machine standard).")
	public static int incubatorEuPerTick = 8;
	/** Internal EU buffer; holds the costliest operation (create, 8000 EU) in full. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Incubator internal EU buffer.")
	public static int incubatorBuffer = 8000;
	/** Ticks per transform attempt at 1.0 speed (300 x 8 EU/t = 2400 EU). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks an incubator transform attempt takes at 1.0 speed.")
	public static int mutationDurationTransform = 300;
	/** Ticks per duplicate attempt at 1.0 speed (500 x 8 EU/t = 4000 EU). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks an incubator duplicate attempt takes at 1.0 speed.")
	public static int mutationDurationDuplicate = 500;
	/** Ticks per create attempt at 1.0 speed (1000 x 8 EU/t = 8000 EU). */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks an incubator create attempt takes at 1.0 speed.")
	public static int mutationDurationCreate = 1000;
	/**
	 * MOD-605: mB of water per incubator attempt, not in the recipe JSON (items and a fluid never share a
	 * side): a bucket per attempt.
	 */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "mB of water an incubator attempt drinks (not part of the recipe JSON).")
	public static int incubatorWaterPerOp = 1000;
	/**
	 * MOD-605: percent faster while the bath has water for the next attempt. Water is an upgrade, not a
	 * requirement; 0 switches the bath off.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Per cent faster an incubator runs while its nutrient bath has water (0 disables).")
	public static int incubatorWaterSpeedBonus = 50;
	/** Uranium ingots are spent as a charge: one ingot powers this many attempts, then becomes ash. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Mutation attempts one uranium ingot powers before it burns to ash.")
	public static int mutationAttemptsPerIngot = 3;
	/** Base success chance of a transform mutation. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Base success chance of a transform mutation (0..1).")
	public static double mutationChanceTransform = 0.75;
	/** Base success chance of a duplicate mutation. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Base success chance of a duplicate mutation (0..1).")
	public static double mutationChanceDuplicate = 0.45;
	/** Base success chance of a create mutation. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Base success chance of a create mutation (0..1).")
	public static double mutationChanceCreate = 0.25;
	/** Ceiling on the total success chance (base + gene bonus) — a mutation is never guaranteed. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 0.0, floorTo = 0.0,
			doc = "Ceiling on the total mutation success chance (base + gene bonus).")
	public static double mutationChanceCap = 0.95;
	/** Share of attempts that yield irradiated slag; carved out of the failure share, not the success. */
	@Knob(section = Section.MACHINES, min = 0.0, floorTo = 0.0,
			doc = "Share of attempts yielding irradiated slag instead of an empty miss.")
	public static double mutationSlagChance = 0.05;
	/** Share of successes that roll the rare grade. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 0.0, floorTo = 0.0,
			doc = "Share of successful mutations rolling the rare grade.")
	public static double mutationGradeRare = 0.20;
	/** Share of successes that roll the epic grade. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 0.0, floorTo = 0.0,
			doc = "Share of successful mutations rolling the epic grade.")
	public static double mutationGradeEpic = 0.08;
	/** Share of successes that roll the legendary grade. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 0.0, floorTo = 0.0,
			doc = "Share of successful mutations rolling the legendary grade.")
	public static double mutationGradeLegendary = 0.02;

	// --- Garden Drone Station (MOD-277): zone-scan farm caretaker, one BER-drawn drone per station. ---
	/** Internal EU buffer. Sized like {@link #pumpBuffer}/{@link ToolConfig#magnetBuffer} — a modest LV
	 * reservoir for a per-action (not per-tick) consumer. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Garden Drone station internal EU buffer.")
	public static int gardenDroneBuffer = 4000;
	/** EU spent per completed action (till / plant / fertilize / harvest). Demand-driven: an idle
	 * station (nothing to do) spends nothing, same pattern as {@link #electricHeaterEuPerTick}. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "EU the Garden Drone spends per completed action (till/plant/fertilize/harvest); idle costs nothing.")
	public static int gardenDroneEuPerAction = 8;
	/** Garden drone scan radius around the station: 4, a tier-1 plot, leaving a wider field to a later tier. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Garden Drone zone-scan radius in blocks around the station.")
	public static int gardenDroneRange = 4;
	/** Ticks between zone re-scans; same cadence as {@link #pumpScanCooldownTicks}. The scan result
	 * is cached and invalidated by block updates inside the zone, so this interval only bounds the
	 * cost of a full rebuild after cache invalidation, not every tick's work. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between Garden Drone zone rebuilds after cache invalidation.")
	public static int gardenDroneScanIntervalTicks = 20;
	/**
	 * Ticks the drone flies per block of distance; the action lands on arrival, so the farm is not tended by
	 * teleport.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "Ticks the Garden Drone flies per block of distance before its action lands.")
	public static int gardenDroneFlightTicksPerBlock = 11;

	// --- Mob Repeller (MOD-278): tiered guard field that expels hostile mobs for EU. ---
	/** Zone radius in blocks around the LV block (a cube, matching the highlight dome). */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Mob Repeller LV zone radius in blocks (a cube around the block).")
	public static int mobRepellerRange = 8;
	/** Zone radius of the MV tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller MV zone radius in blocks.")
	public static int mobRepellerRangeMv = 16;
	/** Zone radius of the HV tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller HV zone radius in blocks.")
	public static int mobRepellerRangeHv = 24;
	/**
	 * Mob repeller field upkeep in EU/t while enabled, a constant drain (the field is the work); four machine
	 * standards, retuned up after the first playtest.
	 */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Mob Repeller LV field upkeep in EU per tick while enabled (constant drain, not per expulsion).")
	public static int mobRepellerEuPerTick = 8;
	/** MV field upkeep — ×4 of LV, the same step the voltage ladder takes. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller MV field upkeep in EU per tick.")
	public static int mobRepellerEuPerTickMv = 32;
	/** HV field upkeep, x2 of MV rather than x4, so the top tier stays worth reaching. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller HV field upkeep in EU per tick.")
	public static int mobRepellerEuPerTickHv = 64;
	/** Internal EU buffer of the LV block. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Mob Repeller LV internal EU buffer.")
	public static int mobRepellerBuffer = 2000;
	/** Internal EU buffer of the MV tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller MV internal EU buffer.")
	public static int mobRepellerBufferMv = 8000;
	/** Internal EU buffer of the HV tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Mob Repeller HV internal EU buffer.")
	public static int mobRepellerBufferHv = 32000;
	/** Ticks between zone sweeps. Ten (half a second) keeps the boundary dance tight without paying
	 * the entity scan every tick; the upkeep drain still applies every tick regardless. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks between Mob Repeller zone sweeps (the upkeep drain still applies every tick).")
	public static int mobRepellerScanIntervalTicks = 10;
	/** Personal hostile kills a Soul Vessel must hold to evolve the LV block into the MV tier. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Personal hostile kills a Soul Vessel needs to evolve the LV repeller into MV.")
	public static int mobRepellerEvolveKillsMv = 80;
	/** Personal hostile kills to evolve the MV block into the HV tier; also the vessel's hard cap. */
	@Knob(section = Section.MACHINES, clientVisible = true, min = 1,
			doc = "Personal hostile kills to evolve the MV repeller into HV; also the vessel hard cap.")
	public static int mobRepellerEvolveKillsHv = 250;

	// --- Welcome message (MOD-596) ---
	/**
	 * Whether the mod greets a world on its first join with two chat lines and a Discord link; off for a
	 * public server that would rather not. Off never marks the world greeted.
	 */
	@Knob(section = Section.WORLD,
			doc = "When true, the mod writes a two-line welcome with its Discord link the first time anyone joins a world.")
	public static boolean welcomeMessageEnabled = true;

	// --- Cotton trellis (MOD-280): the mod's first crop. ---
	/**
	 * Chance divisor per trellis rooting stage on each random tick of a moist, lit plant (MOD-280); random, so
	 * a field costs nothing to tick. Once per plant: the slow knob.
	 */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Cotton trellis: 1-in-this chance of advancing one rooting stage per random tick (higher = longer initial growth).")
	public static int cottonRootingChanceDivisor = 12;
	/**
	 * Chance divisor per fruiting stage, the two-stage cycle repeated after every harvest; much smaller than
	 * rooting.
	 */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Cotton trellis: 1-in-this chance of advancing one fruiting stage per random tick (the repeating harvest cycle).")
	public static int cottonFruitingChanceDivisor = 4;

	// --- Kok sagyz (MOD-537): the rubber dandelion — root rubber without an oil rig. ---
	/**
	 * Kok sagyz chance divisors, one per life step (MOD-584); a rising ladder, and means of a geometric wait,
	 * not timers. Off tended ground {@link #kokSagyzWildGrowthDivisor} multiplies them.
	 */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Kok sagyz: 1-in-this chance per random tick of going rosette -> bud, on tended ground.")
	public static int kokSagyzStage1Divisor = 1;
	/** Bud → open flower. @see #kokSagyzStage1Divisor */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Kok sagyz: 1-in-this chance per random tick of going bud -> open flower, on tended ground.")
	public static int kokSagyzStage2Divisor = 2;
	/** Open flower → seed head, the stage that unlocks rooting. @see #kokSagyzStage1Divisor */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Kok sagyz: 1-in-this chance per random tick of going flower -> seed head, on tended ground.")
	public static int kokSagyzStage3Divisor = 3;
	/** The intermediate root under the flower: pays seeds only, so the cheaper underground step. */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Kok sagyz: 1-in-this chance per random tick of growing the intermediate root under the flower.")
	public static int kokSagyzRootUpperDivisor = 6;
	/**
	 * The harvestable tip, the step that mints the root item — priced by what it makes, not by depth, so
	 * shallow plots do not out-yield deep ones.
	 */
	@Knob(section = Section.WORLD, min = 1,
			doc = "Kok sagyz: 1-in-this chance per random tick of growing the harvestable root tip, at any depth.")
	public static int kokSagyzRootTipDivisor = 9;
	/**
	 * A step's time in sand as a percentage of other ground (MOD-584); below 100 sand is faster, 100 removes
	 * it. Tilling deliberately does nothing: a steppe weed, not a hoe crop.
	 */
	@Knob(section = Section.WORLD, clientVisible = true, min = 1,
			doc = "Kok sagyz: a step in sand takes this percent of the time it takes in other ground. 100 removes the sand bonus; values above 100 are clamped.")
	public static int kokSagyzSandGrowthPercent = 75;
	/**
	 * Whether landing on a kok sagyz flower knocks it back one stage (MOD-584); walking and sneaking always
	 * spare it. Off: cross any way you like.
	 */
	@Knob(section = Section.WORLD,
			doc = "Kok sagyz: landing on a flower from above knocks it back one growth stage. Walking through and sneaking never trample.")
	public static boolean kokSagyzTrampling = true;

	// --- Iron Furnace (fuel-based, MOD-115): ticks to smelt one item. Vanilla furnace = 200. ---
	/** Ticks the iron furnace needs to smelt one item on fuel. Between vanilla (200) and the
	 * electric furnace, so it reads as "a bit faster than stone" without devaluing the electric tier. */
	@Knob(section = Section.MACHINES, min = 1,
			doc = "Ticks the (fuel-based) iron furnace takes to smelt one item. Vanilla furnace = 200.")
	public static int ironFurnaceCookTime = 150;

	// --- Player stats / mod XP (MOD-133). Starting values — calibrate after playtest. ---
	/** Useful EU (from completed machine operations) that equals one point of mod XP. Higher = slower. */
	@Knob(section = Section.PLAYER, clientVisible = true, min = 1, floorTo = 1,
			doc = "MOD-133 player profile: useful EU (from completed machine operations) per 1 point of mod XP. Higher = slower progression. Starting value, tune after playtest.")
	public static int euPerXp = 1000;
	/**
	 * Produced EU (credited to a generator's buffer) per point of mod XP: far worse than {@link #euPerXp}, as
	 * a generator runs without the player. A full buffer credits nothing.
	 */
	@Knob(section = Section.PLAYER, clientVisible = true, min = 1, floorTo = 1,
			doc = "MOD-133 player profile: produced EU (actually credited into a generator buffer, never idle overflow) per 1 point of mod XP. Much higher than euPerXp on purpose - a generator runs unattended, so it only trickles. Starting value, tune after playtest.")
	public static int euPerXpGenerated = 20_000;
	/** XP cost of the first level (1→2); each later level costs {@link #levelXpMultiplier}× the previous. */
	@Knob(section = Section.PLAYER, clientVisible = true, min = 1, floorTo = 1,
			doc = "MOD-133: XP cost of the first level (1->2); each later level costs levelXpMultiplier x the previous. Starting value.")
	public static int xpLevelOneCost = 80;
	/** Per-level XP cost multiplier — the exponential curve over 40 levels. Must be &gt; 1.0. */
	@Knob(section = Section.PLAYER, clientVisible = true, min = 1.0, exclusive = true,
			doc = "MOD-133: per-level XP cost multiplier (exponential curve over 40 levels). Must be > 1.0.")
	public static float levelXpMultiplier = 1.18f;
	/** How often (server ticks) in-memory player stats are folded into the attachment and synced. */
	@Knob(section = Section.PLAYER, min = 1,
			doc = "MOD-133: how often (server ticks) in-memory player stats fold into the attachment and sync. 100 = every 5s.")
	public static int statsFlushTicks = 100;

	// --- MOD-483 Workstation: what the station itself costs to run. ---

	/**
	 * EU/t the assembled Workstation draws while powered, debited for the ticks that elapsed (at most a
	 * second's worth per debit).
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "MOD-483: EU/t the assembled Workstation draws while powered. Charged per elapsed tick, at most a second per debit.")
	public static int workstationEuPerTick = 6;
	/**
	 * EU to teach one skill, flat whatever the Fragment cost (depth is already priced in Fragments): most of
	 * an MV charge, so learning is an event.
	 */
	@Knob(section = Section.MACHINES, min = 0,
			doc = "MOD-483: EU the Workstation spends per skill learned. Flat - the Ala-Fragment cost already scales with depth.")
	public static int workstationSkillPurchaseEu = 30_000;

	// --- MOD-483 skill tree: what each node is worth. ---
	//
	// These live here rather than as constants beside the code that applies them for the reason every
	// balance number in this mod does: a value an operator cannot retune without rebuilding the jar is
	// not a balance knob, it is a decision. Each number is also the ONE place its node is priced —
	// the tree's own arithmetic (which nodes exist, what they cost in Ala-Fragments, which side of a
	// fork closes which) stays in SkillSlot and is not configurable, because it is the design.

	/** Frugal Stroke: what a powered-tool action costs, as a percentage of the listed price. */
	@Knob(section = Section.PLAYER, min = 1,
			doc = "MOD-483 Frugal Stroke: powered-tool action cost as a percent of the listed price (90 = 10% off). Rounded UP, so a 2 EU action is not halved.")
	public static int skillFrugalStrokePercent = 90;
	/** Frugal Armour: one second in this many is free. Counted in seconds — upkeep is 1 EU. */
	@Knob(section = Section.PLAYER, min = 1,
			doc = "MOD-483 Frugal Armour: one second of powered-armour upkeep in this many is free (3 = a third off). Counted in seconds because upkeep is 1 EU and a percent of one rounds to nothing.")
	public static int skillFrugalArmourEverySeconds = 3;
	/** Quick Docking: how much faster a powered item accepts charge. */
	@Knob(section = Section.PLAYER, min = 1.0,
			doc = "MOD-483 Quick Docking: multiplier on how fast powered items accept charge (1.5 = half again). The voltage tier ceiling still applies above it.")
	public static float skillQuickDockFactor = 1.5f;
	/** Recuperator: percent of a tool's spend that returns to the worn pack. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Recuperator: percent of a tool's spend returned to the worn energy pack. Never applies to a carrier's own payout - that would mint EU.")
	public static int skillRecuperatorPercent = 10;
	/** Field Circuit: a bagged pack hands out at this fraction of the worn rate. */
	@Knob(section = Section.PLAYER, min = 1,
			doc = "MOD-483 Field Circuit: divisor on the pack's output rate while it sits in a bag rather than worn (2 = half speed). Keeps wearing it worthwhile.")
	public static int skillFieldCircuitDivisor = 2;

	/** Respirator: how much slower radiation dose builds up, in percent. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Respirator: percent by which radiation dose builds slower. Deliberately below one suit piece (25%) - a skill must not replace the gear.")
	public static int skillRespiratorPercent = 10;
	/** Dielectric: how much weaker a bare cable hits, in percent. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Dielectric: percent by which a bare cable hits weaker.")
	public static int skillDielectricPercent = 20;
	/** Full Insulation: the stronger shock cut. Replaces Dielectric rather than stacking with it. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Full Insulation: percent by which a bare cable hits weaker. Together with a full insulating set it reaches the mod cap of 95%, never immunity.")
	public static int skillFullInsulationPercent = 60;
	/** Careful Wear: extra dose one point of suit durability absorbs, in percent. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Careful Wear: percent more dose absorbed per point of hazmat durability (25 = the suit lasts a quarter longer).")
	public static int skillCarefulWearPercent = 25;
	/** Dosimetrist: how far the Geiger counter reaches with the skill, in blocks. */
	@Knob(section = Section.PLAYER, min = 1,
			doc = "MOD-483 Dosimetrist: Geiger counter reach in blocks with the skill (base geigerRadius applies without it).")
	public static int skillDosimetristRadius = 20;
	/** Background Shift: radiation damage arrives this many times less often. */
	@Knob(section = Section.PLAYER, min = 1,
			doc = "MOD-483 Background Shift: multiplier on the interval between radiation hits (2 = half as often). Not immunity - dose and symptoms are untouched.")
	public static int skillBackgroundShiftFactor = 2;

	/** Tuned Drive: percent taken off an operation's length. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Tuned Drive: percent off machine operation length. Small on purpose next to an overclocker chip, which buys 25% speed for 60% more energy.")
	public static int skillTunedDrivePercent = 5;
	/** Fine Tuning: a second cut off an operation's length, on top of Tuned Drive. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Fine Tuning: further percent off machine operation length, added to skillTunedDrivePercent.")
	public static int skillFineTuningPercent = 5;
	/** Precise Draw: one operation tick in this many costs nothing. */
	@Knob(section = Section.PLAYER, min = 1,
			doc = "MOD-483 Precise Draw: one drain tick in this many is free (10 = 10% off the operation). Counted in ticks because a basic machine draws 2 EU/t.")
	public static int skillPreciseDrawEveryTicks = 10;
	/** Steady Hands: how much longer fuel burns, in percent. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Steady Hands: percent longer fuel burns in the owner's generators. Applied to burn LENGTH, not EU/t - a solar panel makes 1 EU/t and a percent of one is zero.")
	public static int skillSteadyHandsPercent = 10;
	/** Resilient Cycle: an operation past this share of its length may coast on the machine's charge. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Resilient Cycle: percent of an operation that must be done before it may finish on the machine's own buffer. Below 50 a switch on a timer becomes free energy.")
	public static int skillResilientFromPercent = 50;
	/** Selection: mutation chance added, as a fraction. The mod's own cap still applies. */
	@Knob(section = Section.PLAYER, min = 0.0,
			doc = "MOD-483 Selection: mutation chance added in the incubator (0.05 = five points). Applied to the base chance, so mutationChanceCap still has the last word.")
	public static double skillSelectionBonus = 0.05;
	/** Frugal Sprayer and Frugal Vat: percent less fluid one action takes. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Frugal Sprayer / Frugal Vat: percent less solution or water one action takes.")
	public static int skillFrugalFluidPercent = 10;
	/** Wide Watering: blocks added to the sprinkler's radius. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Wide Watering: blocks added to sprinkler radius. Area grows with the SQUARE of it, which is why skillWideWateringCost exists.")
	public static int skillWideWateringRadius = 1;
	/** Wide Watering: multiplier on the solution a wider pass costs. */
	@Knob(section = Section.PLAYER, min = 1.0,
			doc = "MOD-483 Wide Watering: multiplier on solution per pass that pays for the larger area (1.5 keeps the cost per tile roughly level; 1.0 hands out 49% free work).")
	public static double skillWideWateringCost = 1.5;
	/** Swift Drone: ticks shaved off each block of drone flight. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Swift Drone: ticks shaved off each block of garden-drone flight.")
	public static int skillSwiftDroneTicks = 1;
	/** Extended Round: the drone's radius with the skill, sized for exactly twice the area. */
	@Knob(section = Section.PLAYER, min = 1,
			doc = "MOD-483 Extended Round: garden-drone radius with the skill. 6 against a base of 4 is exactly double the area; doubling the radius would be four times.")
	public static int skillExtendedRoundRadius = 6;
	/** Crystal Care: percent off greenhouse growth time. */
	@Knob(section = Section.PLAYER, min = 0,
			doc = "MOD-483 Crystal Care: percent faster greenhouse growth, read through the sprinkler's owner (the greenhouse controller has no owner of its own).")
	public static int skillCrystalCarePercent = 10;

	// --- Energy tiers: per-tick voltage cap + default buffer capacity, configurable per tier ---
	/**
	 * Max packet voltage and per-tick transfer cap of the LV tier, the universal ceiling beside per-block
	 * buffers; read live by {@link dev.alaindustrial.core.energy.EnergyTier#maxVoltage()}.
	 */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Max packet voltage (EU) and per-tick transfer cap for the LV tier (cable, generator, machine, storage). EnergyTier.LV reads it live.")
	public static int tierLvVoltage = 32;
	/** Max packet voltage for the MV tier. 4× LV by convention. EnergyTier.MV reads it live. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Max packet voltage for the MV tier (4x LV by convention). EnergyTier.MV reads it live.")
	public static int tierMvVoltage = 128;
	/** Max packet voltage for the HV tier. 4× MV by convention. EnergyTier.HV reads it live. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Max packet voltage for the HV tier (4x MV by convention). EnergyTier.HV reads it live.")
	public static int tierHvVoltage = 512;
	/** Default internal buffer capacity for LV machines that do not override it. EnergyTier.LV reads it live. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Default internal buffer capacity for LV machines that do not override it. EnergyTier.LV reads it live.")
	public static int tierLvCapacity = 10_000;
	/** Default internal buffer capacity for MV machines. EnergyTier.MV reads it live. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Default internal buffer capacity for MV machines. EnergyTier.MV reads it live.")
	public static int tierMvCapacity = 40_000;
	/** Default internal buffer capacity for HV machines. EnergyTier.HV reads it live. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Default internal buffer capacity for HV machines. EnergyTier.HV reads it live.")
	public static int tierHvCapacity = 160_000;

	// --- Cable ---
	/**
	 * Fraction of throughput lost per copper cable block, compounding with distance and capped so a positive
	 * flow always delivers at least 1 EU (MOD-073, PERFORMANCE.md): 32 EU loses 5 over 10 cables.
	 */
	@Knob(section = Section.CABLES, min = 0.0, floorTo = 0.0,
			doc = "Fraction of throughput attenuated per copper cable block (0.02 = 2% of the remaining flow per block).")
	public static double copperCableLossPerBlock = 0.02;
	/** Master safety switch for contact damage and its particles/sound on energized bare cables. */
	@Knob(section = Section.SAFETY,
			doc = "When true, energized bare cables damage players on direct contact and emit shock feedback. false disables the entire mechanic.")
	public static boolean bareCableShockEnabled = true;
	/** Contact damage from an energized bare LV (tin/copper) cable, in half-hearts. */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Damage from direct contact with an energized bare LV cable, in half-hearts.")
	public static float bareCableShockLvDamage = 2.0f;
	/** Contact damage from an energized bare MV (gold) cable, in half-hearts. */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Damage from direct contact with an energized bare MV cable, in half-hearts.")
	public static float bareCableShockMvDamage = 6.0f;
	/**
	 * Contact damage of an energized bare HV (electrum) cable in half-hearts, continuing the 2 -> 6 -> 10
	 * ladder (MOD-358).
	 */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Damage from direct contact with an energized bare HV cable, in half-hearts.")
	public static float bareCableShockHvDamage = 10.0f;
	/**
	 * Extra blocks the shock hazard reaches beyond the bare segment's cell in every direction; 0 = direct
	 * touch only.
	 */
	@Knob(section = Section.SAFETY, min = 0.0, floorTo = 0.0,
			doc = "Extra blocks the shock hazard reaches beyond a bare cable segment's own cell in every direction (0 = direct-touch only).")
	public static double bareCableShockProximityRadius = 0.5;
	/**
	 * Multiplier on the bare grade's loss when the whole governing grade is insulated: 0.5 halves loss without
	 * changing tier, cap or throughput (MOD-259).
	 */
	@Knob(section = Section.CABLES, min = 0.0, floorTo = 0.0,
			doc = "Multiplier applied to bare-cable attenuation for rubber-insulated tin/copper cables (0.5 = half the loss; throughput and packet cap are unchanged).")
	public static double insulationLossMultiplier = 0.5;

	// --- Insulating stands under bare cable, see core.energy.ShockGuardMaterial (MOD-279) ---
	/**
	 * Chance (0..1) a shock still lands on a player ON a wood-stood segment; a stand blocks side and below
	 * outright. 1.0 removes the benefit, 0.0 equals rubber.
	 */
	@Knob(section = Section.SAFETY, min = 0.0, floorTo = 0.0,
			doc = "Chance (0..1) a shock still lands through a plank insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).")
	public static double shockGuardWoodHitChance = 0.7;
	/** Probability (0..1) a shock still lands from above through a <b>wool</b> stand — the weakest of the three. */
	@Knob(section = Section.SAFETY, min = 0.0, floorTo = 0.0,
			doc = "Chance (0..1) a shock still lands through a wool insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).")
	public static double shockGuardWoolHitChance = 0.9;
	/** Probability (0..1) a shock still lands from above through a <b>glass</b> stand — the strongest of the three. */
	@Knob(section = Section.SAFETY, min = 0.0, floorTo = 0.0,
			doc = "Chance (0..1) a shock still lands through a glass insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).")
	public static double shockGuardGlassHitChance = 0.5;
	/**
	 * Contact ticks a player is left alone after a stand absorbs a shock, so the chance is per contact, not
	 * per tick; matches vanilla's 20-tick invulnerability window.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Contact ticks a player is spared after an insulating stand absorbs a shock, so the reduced chance is per contact rather than re-rolled every tick.")
	public static int shockGuardGraceTicks = 20;
	/**
	 * Percent of a bare cable's shock one worn insulating piece cuts (MOD-466): four make a set immune,
	 * clamped at 100 by {@link dev.alaindustrial.core.energy.ShockInsulation#cutPercent}. 0 disables it.
	 */
	@Knob(section = Section.SAFETY, min = 0,
			doc = "Percent of a bare cable's shock cut by one worn piece of insulating armour (25 = a full four-piece set is immune; 0 disables the set's protection).")
	public static int bareCableShockInsulationPerPiecePercent = 25;
	/**
	 * Absorbed shock damage per durability point on each worn insulating piece (MOD-466): a rate, since
	 * contact repeats every second — a helmet lasts about 4.5 min on LV. At least one point per hit.
	 */
	@Knob(section = Section.SAFETY, min = 0.0, exclusive = true,
			doc = "Absorbed shock damage that costs one durability point on each worn insulating piece (higher = the set lasts longer; contact is once per second, so this is a rate).")
	public static float bareCableShockInsulationDamagePerDurability = 4.0f;

	// --- Cable grades: tin (cheap/narrow), gold (MV/wide) and electrum (HV/widest),
	// see core.energy.CableType (MOD-219, MOD-358) ---
	/**
	 * Tin cable segment buffer, which is its throughput (MOD-070): 8 EU/t, narrower than copper, above a solar
	 * farm and below a fuel generator's burst.
	 */
	@Knob(section = Section.CABLES, min = 1,
			doc = "Per-segment working EU buffer of a tin cable = its real throughput (8 EU/t, narrower than copper's 12).")
	public static int tinCableBuffer = 8;
	/**
	 * Per-tick ceiling on EU drawn from one source through tin: equal to its buffer, so tin is no cheap
	 * stand-in for a full LV line.
	 */
	@Knob(section = Section.CABLES, min = 1,
			doc = "Per-tick ceiling on EU drawn from one source through a tin cable (8 EU/t, below the LV tier voltage by design).")
	public static int tinCablePacketCap = 8;
	/**
	 * Fraction lost per tin cable block: about 3.3x gentler than copper, tin's whole point; a 1 EU trickle
	 * loses nothing.
	 */
	@Knob(section = Section.CABLES, min = 0.0, floorTo = 0.0,
			doc = "Fraction of throughput attenuated per tin cable block (0.006 = 0.6% of the remaining flow per block; a 1 EU/t solar trickle floors to zero loss).")
	public static double tinCableLossPerBlock = 0.006;
	/**
	 * Gold cable segment buffer, its throughput: 4x copper, mirroring the LV -> MV step. Gold's craft cost,
	 * not its buffer, keeps a grid from being storage.
	 */
	@Knob(section = Section.CABLES, min = 1,
			doc = "Per-segment working EU buffer of a gold (MV) cable = its real throughput (48 EU/t, 4x copper).")
	public static int goldCableBuffer = 48;
	/**
	 * Fraction lost per gold cable block: worse than copper by design, gold buys throughput, not distance. Its
	 * packet cap is {@link #tierMvVoltage}.
	 */
	@Knob(section = Section.CABLES, min = 0.0, floorTo = 0.0,
			doc = "Fraction of throughput attenuated per gold cable block (0.03 = 3% of the remaining flow per block; worse than copper by design - gold buys throughput, not distance).")
	public static double goldCableLossPerBlock = 0.03;
	/**
	 * Electrum cable segment buffer, its throughput: 4x gold, the ladder's next rung; its cap is {@link
	 * #tierHvVoltage}. The craft cost keeps a grid from being storage.
	 */
	@Knob(section = Section.CABLES, min = 1,
			doc = "Per-segment working EU buffer of an electrum (HV) cable = its real throughput (192 EU/t, 4x gold).")
	public static int electrumCableBuffer = 192;
	/**
	 * Fraction lost per electrum cable block, the lowest in the mod: electrum wins every axis and pays in
	 * craft cost. Halved again when insulated.
	 */
	@Knob(section = Section.CABLES, min = 0.0, floorTo = 0.0,
			doc = "Fraction of throughput attenuated per electrum cable block (0.005 = 0.5% of the remaining flow per block; the lowest in the mod - electrum pays in craft cost, not in distance).")
	public static double electrumCableLossPerBlock = 0.005;

	// --- Energy network ---
	/** Max awake energy networks processed per server tick; the rest are deferred round-robin. */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Max awake energy networks processed per server tick; the rest are deferred round-robin.")
	public static int networksPerTick = 512;
	/**
	 * Most {@code EnergyNetwork}s the Network Analyzer's Traverse mode (MOD-047) walks; visualization only,
	 * with an actionbar warning past the cap.
	 */
	@Knob(section = Section.NETWORK, min = 1,
			doc = "Cap on networks the Network Analyzer's Traverse mode walks (visualization only, never affects energy).")
	public static int networkAnalyzerMaxTraversedNetworks = 32;

	// --- World gen ---
	/**
	 * MOD-119: with the vanilla Bonus Chest option, add a starter pool to {@code
	 * minecraft:chests/spawn_bonus_chest}; false leaves it vanilla. Read by both loaders' loot hooks.
	 */
	@Knob(section = Section.WORLD,
			doc = "When true, mod starter items are injected into the vanilla bonus chest at world creation (vanilla loot kept). false = purely vanilla bonus chest.")
	public static boolean bonusChestEnabled = true;

	/**
	 * MOD-238: oil ignites from adjacent fire or lava and the burn spreads across the pool ({@code
	 * OilLiquidBlock}); false makes oil inert.
	 */
	@Knob(section = Section.WORLD,
			doc = "When true, oil ignites from adjacent fire or flint-and-steel and the burn spreads across the pool; lava alone does not ignite it. false = oil is inert.")
	public static boolean oilBurns = true;

	/**
	 * MOD-638: chance (0..1) that an oil fire that went out by itself leaves soot, once per burnt cell over a
	 * sturdy floor.
	 */
	@Knob(section = Section.WORLD, clientVisible = true, min = 0.0, floorTo = 0.0,
			doc = "Chance (0..1) that a burnt-out oil fire leaves a soot layer on a solid floor under it (0 = never, 1 = always).")
	public static double oilSootChance = 0.2;

	// ---------------------------------------------------------------------------------------------
	// The file mechanism lives in dev.alaindustrial.config (MOD-710): schema and migrations
	// (ConfigSchema), the knob registry (KnobRegistry), reading and writing (ConfigFile).
	// ---------------------------------------------------------------------------------------------

	/**
	 * Every knob of this class (public read-only view: gametest ConfigOverrides resolves knobs by key). It
	 * must stay BELOW the declarations: entries capture compiled defaults; static init runs in source order.
	 */
	public static final KnobRegistry REGISTRY = KnobRegistry.scan(List.of(Config.class, RadiationConfig.class,
			GeneratorConfig.class, ToolConfig.class, ReactorConfig.class));

	/** Reads and writes {@code config/alaindustrial.json} for {@link #REGISTRY}. */
	static final ConfigFile FILE = new ConfigFile(REGISTRY);

	/**
	 * Per-loader path to {@code config/alaindustrial.json}. Each loader binds this in its config-loader
	 * {@code register()} (Fabric via {@code FabricLoader.getConfigDir()}, NeoForge via {@code FMLPaths.CONFIGDIR}),
	 * so loader-neutral callers in {@code common} — notably the {@code /ala config reload} command — can reload
	 * without knowing which loader they run on. Same set-once-supplier idiom as {@code ModSounds}: the default
	 * throws loudly if read before a loader bound it, catching an ordering regression instead of a silent NPE.
	 */
	public static Supplier<Path> configPath = () -> {
		throw new IllegalStateException("Config.configPath read before its loader bound it");
	};

	/** Outcome of {@link #loadFrom(Path)} so callers (e.g. {@code /ala config reload}) can report precisely. */
	public enum LoadResult {
		/** File existed and was parsed; live balance now reflects it. */
		LOADED,
		/** File was absent; the live balance was reset to the built-in defaults and those were written. */
		DEFAULTS_WRITTEN,
		/**
		 * File existed but could not be parsed (bad JSON, or a value of the wrong type). The apply is
		 * atomic, so the live balance is left exactly as it was.
		 */
		ERROR,
		/**
		 * The file declares a {@code schemaVersion} newer than {@link dev.alaindustrial.config.ConfigSchema#VERSION}
		 * (MOD-402): this
		 * build cannot know what its keys mean. Nothing from it is applied, the file on disk is left
		 * untouched, and the live balance is reset to the mod's built-in defaults.
		 *
		 * <p><b>Why this is its own outcome and not {@link #ERROR}.</b> The two differ in the one fact an
		 * admin needs: {@code ERROR} leaves the running balance alone, this one replaces it. Folding them
		 * together made {@code /ala config reload} report "live balance unchanged" while the balance had in
		 * fact just been reset — the reader would go looking for a syntax error instead of for the version
		 * mismatch that actually happened.
		 */
		SCHEMA_TOO_NEW
	}

	/** Reload from the loader-bound {@link #configPath}. Thin wrapper for the reload command + reload listeners. */
	public static LoadResult reload() {
		return loadFrom(configPath.get());
	}

	/**
	 * Load the config file at {@code path}; if it is absent, reset to the compiled defaults and write them.
	 * Versioned, atomic and self-healing — see {@link ConfigFile#loadFrom}; a key the file does not carry
	 * takes this build's default (ADR-033).
	 */
	public static LoadResult loadFrom(Path path) {
		return FILE.loadFrom(path);
	}
}
