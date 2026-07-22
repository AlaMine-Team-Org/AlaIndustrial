package dev.alaindustrial;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Tunable balance knobs (v0.2 defaults), loaded from {@code config/alaindustrial.json}.
 * Generators/machines/storage/cables read these at runtime, so a server can rebalance without a
 * code change. Missing keys fall back to the v0.2 default, so the file is forward/backward safe.
 *
 * <p>The balance fields and the pure file read/write ({@link #loadFrom(Path)}) are loader-neutral
 * and live in {@code common}. Resolving the per-loader config directory and hooking the
 * datapack-reload event is a platform seam: Fabric wires it in
 * {@code dev.alaindustrial.FabricConfigLoader}; NeoForge will do the same on its side (MOD-022).
 */
public final class Config {
	private Config() {
	}

	// --- Global multipliers (v0.2-neutral defaults) ---
	/** Scales every generator's EU/t output. Applied once in AbstractGeneratorBlockEntity.serverTick. */
	public static float globalEuRateMultiplier = 1.0f;
	/** Scales machine speed (E_op-invariant): EU/t up, duration down by the same factor. */
	public static float globalMachineSpeedMultiplier = 1.0f;

	// --- Generators (EU/tick) ---
	public static int solarEuPerTick = 1;
	public static int daylightEuPerTick = 4;
	public static int moonlitEuPerTick = 3;
	/** Flat EU/t the moonlit panel still produces at night during rain/thunder (a weather trickle). */
	public static int moonlitWeatherEuPerTick = 1;
	public static int fuelEuPerTick = 8;
	public static int geothermalEuPerTick = 16;
	public static int geothermalBurnTicks = 1000;
	/**
	 * EU/t a water mill produces per adjacent vanilla-water block (source or flowing) on its four
	 * horizontal sides — so 0..4 EU/t, continuous while water is present, no fuel. Reads the world
	 * directly; never touches the fluid/tank system (Phases 4–5).
	 */
	public static int waterMillEuPerTick = 1;
	// --- Wind mill (LV) — needs open sky; base scales with height, boosted by weather ---
	/** Clear-sky height cap: base EU/t = min((y − seaLevel) / 16, this). 0 at/below sea level. */
	public static int windMillMaxBaseEuPerTick = 4;
	/** Hard cap on wind-mill EU/t after the weather multiplier (thunder can otherwise push past base). */
	public static int windMillMaxEuPerTick = 8;
	/** Weather multiplier applied to the height base when it is raining (not thundering). */
	public static float windMillRainFactor = 1.5f;
	/** Weather multiplier applied to the height base when it is thundering. */
	public static float windMillThunderFactor = 2.0f;
	/** How often (ticks) the wind mill re-samples height/sky/weather; the rate is cached between samples. */
	public static int windMillSampleTicks = 40;
	/**
	 * Active open-sky ticks (with a rotor installed and an evolution chip in the slot) needed to evolve
	 * a base wind mill into its T2 branch (high-altitude / storm). Mirrors {@link #solarEvolveTicks}.
	 */
	public static int windMillEvolveTicks = 33_600;
	// --- High-altitude wind mill (T2, LV) — boosted by height ---
	/** Clear-sky height cap for the high-altitude variant: base EU/t = min((y − seaLevel) / blocksPerBase, this). */
	public static int highAltWindMillMaxBaseEuPerTick = 8;
	/** Blocks of height above sea level needed for +1 EU/t of base on the high-altitude variant (half the T1 16). */
	public static int highAltWindMillBlocksPerBase = 8;
	/** Hard cap on high-altitude wind-mill EU/t after the weather multiplier. */
	public static int highAltWindMillMaxEuPerTick = 16;
	// --- Storm wind mill (T2, LV) — boosted by weather ---
	/**
	 * Clear-sky height cap for the storm variant: same height step as T1 (16 blocks/+1), but raised above T1
	 * so the thunder multiplier (×3) actually reaches the T2 cap: base 6 × thunder 3 = 18 → clamped to 16.
	 * At 4 (the old value) the peak was only 12, leaving the T2 cap dead and the storm mill strictly weaker
	 * than the high-altitude T2. Now both T2 mills reach 16, but via different reliability profiles.
	 */
	public static int stormWindMillMaxBaseEuPerTick = 6;
	/** Weather multiplier for the storm variant when it is raining (not thundering). */
	public static float stormWindMillRainFactor = 2.0f;
	/** Weather multiplier for the storm variant when it is thundering. */
	public static float stormWindMillThunderFactor = 3.0f;
	/** Hard cap on storm wind-mill EU/t after the weather multiplier. */
	public static int stormWindMillMaxEuPerTick = 16;
	/** Output multiplier when a solar panel sees the sky through a translucent block (leaves, cobweb). MOD-004. */
	public static float solarTransparentFactor = 0.5f;
	/** Output multiplier under snow: a snow layer above the panel, or snowfall in a cold biome — MODE_SNOW. */
	public static float solarSnowFactor = 0.2f;
	/**
	 * Active sky-time ticks (at the chip's time of day, i.e. only while its half of the day/night
	 * cycle is active) needed to evolve a base solar panel into its T2 branch. 33 600 = ~2.8
	 * active half-days (~12 000 ticks/half-day) of continuous clear-weather generation, ≈28 real
	 * minutes, ≈3 in-game days accounting for weather/night gaps.
	 */
	public static int solarEvolveTicks = 33_600;
	/**
	 * How often (ticks) a solar panel re-samples sky access + weather; the verdict is cached between
	 * samples to avoid scanning the column above the panel every tick. Mirrors {@link #windMillSampleTicks};
	 * 40 ticks (2 s) is imperceptible against the day/night and weather transitions a panel reacts to,
	 * and at 100 panels cuts the per-tick column-scan cost from 100/tick to ~2/tick on average.
	 */
	public static int solarSkySampleTicks = 40;

	// --- Pump (LV, EU-powered fluid mover) ---
	/** EU spent per bucket of fluid the pump moves (extract + push). The pump is one of the most
	 * energy-hungry machines — at 1000 EU/bucket it is a noticeable consumer, while a bucket of lava
	 * still yields 16 000 EU in the geothermal generator (16× payback on the pump's own tax). */
	public static int pumpEuPerBucket = 1000;
	/** How many ticks the pump waits after a BFS scan before scanning again. */
	public static int pumpScanCooldownTicks = 20;
	/** Max Manhattan distance the pump BFS searches for a fluid source. */
	public static int pumpScanMaxDistance = 32;
	/** Max blocks the pump BFS visits per scan, caps lag. */
	public static int pumpScanMaxVisited = 512;

	/** Portable passive tank capacity (MOD-111): 8 buckets, intentionally below machine tanks (10). */
	public static int fluidTankCapacity = 8000;

	// --- Teleporter (HV anchor station, MOD-091) ---
	/** Teleporter station EU buffer. Oversized (×25 the battery box) because a jump is paid in one
	 * lump sum by the TARGET station: at ~10 000–20 000 EU for a typical "home from the mine" jump
	 * this holds ~25–50 jumps, which is what makes the station usable while its chunk is unloaded
	 * (an unloaded station does not recharge — see docs/blocks/advanced-machines/teleporter.md). */
	public static int teleporterBuffer = 500_000;
	/** Flat part of a jump's price — the "even next door is not free" floor (~17 macerator cycles). */
	public static int teleporterBaseCost = 5000;
	/** Added per block of euclidean distance to the target station. */
	public static int teleporterCostPerBlock = 5;
	/**
	 * Warmup before a jump fires (100 t = 5 s).
	 *
	 * <p>Short on purpose: the feature's whole job is "get me home from the mine", and the original
	 * fifteen seconds of standing still made that a chore. The anti-escape guarantee rests on the
	 * cancel-on-damage rule rather than on the clock — a player under fire still cannot leave, but a
	 * player who is simply done mining does not wait around. Five seconds is what the wind-up needs
	 * to read as a scene (particles → rising sound → the screen going dark), which is why it is not
	 * shorter. Raise it on a PvP server if you want the jump interruptible by reaction, not by a hit.
	 */
	public static int teleporterWarmupTicks = 100;
	/** Anti-spam lockout after landing, per player. */
	public static int teleporterCooldownTicks = 1200;
	/** Moving further than this from where the warmup started cancels it. A step aside is fine. */
	public static int teleporterWarmupCancelRadius = 2;
	/**
	 * Max stations one remote can hold (MOD-093). Bounds the data component (each point is a
	 * dimension + pos + a name up to 32 chars) and keeps the screen's list finite without paging.
	 * Enforced server-side at bind time — never by the screen.
	 */
	public static int teleporterMaxPoints = 16;

	// --- Storage / per-block buffers (EU) ---
	public static int batteryBoxBuffer = 20_000;
	public static int maceratorBuffer = 800;
	/** Shared buffer for electric furnace / compressor / extractor. */
	public static int machineBuffer = 800;
	/** Pump EU buffer. Sized to hold several buckets' worth of pump cost (pumpEuPerBucket = 1000) so the
	 * energy network can keep the pump fed without stalling just below the per-bucket threshold. NOTE
	 * (MOD-070): a single copper cable now carries {@link #cableBuffer} EU/tick (the segment buffer, e.g.
	 * 12), not the LV tier voltage (32) — so a pump fed through one thin cable refills ~2.7× slower than a
	 * directly-adjacent source. This large buffer smooths that out; feed a high-draw pump from an adjacent
	 * source or several parallel cables if intake speed matters. */
	public static int pumpBuffer = 4000;
	public static int generatorBuffer = 4000;
	public static int geothermalBuffer = 4000;
	public static int waterMillBuffer = 4000;
	public static int windMillBuffer = 4000;
	/** Shared buffer for both T2 wind mills (high-altitude + storm). */
	public static int t2WindMillBuffer = 8000;
	public static int solarBuffer = 8000;
	/**
	 * Per-cable working EU buffer (MOD-070). A cable is a real transport segment with a small live
	 * buffer: energy flows segment-to-segment through these buffers (inertia) instead of teleporting
	 * producer→consumer, and on a line break the remainder is retained in the source-side cables.
	 * Deliberately tiny so a wall of cables can never be used as bulk storage — the balance ceiling is
	 * {@code cableBuffer × realistic-network-size ≪ batteryBoxBuffer}: a 1000-cable network holds
	 * 12 000 EU &lt; one Battery Box ({@link #batteryBoxBuffer} = 20 000). Kept separate from
	 * {@link dev.alaindustrial.core.energy.EnergyTier#capacity()} (the machine buffer, 10 000 EU for LV) on
	 * purpose. Applies to newly placed cables ({@code EnergyBuffer.capacity} is final per block entity).
	 */
	public static int cableBuffer = 12;

	// --- Item pipes (MOD-104, rebalanced in MOD-108) ---
	/**
	 * Items a pipe network moves per transfer, once every {@link #itemPipeTransferIntervalTicks}.
	 * Together they set the throughput: 2 items / 20 ticks = <b>2 items per second</b>.
	 *
	 * <p><b>Why this number.</b> MOD-104 shipped 1 item <i>every tick</i> — 20/s, which is 8× a vanilla
	 * hopper (1 item / 8 ticks = 2.5/s) and faster than the starter tier of every comparable mod:
	 * BuildCraft wooden pipe 1.0/s, Mekanism Basic Transporter 2.0/s, Thermal basic servo 2.67/s,
	 * AE2 import bus 4.0/s, EnderIO conduit 8.0/s. A player emptied a stack between chests in ~3
	 * seconds and, worse, there was nowhere left to upgrade to. 2/s matches Mekanism's Basic
	 * Logistical Transporter exactly — a passive starter tier sits slightly below the hopper, and earns
	 * its keep by routing and range rather than raw speed.
	 *
	 * <p><b>Why an interval instead of a smaller batch.</b> A batch cannot go below 1 item, so the only
	 * way down from 20/s is to stop moving every tick. Fixing the interval and growing the batch is
	 * also what MI, AE2, Mekanism and EnderIO all do, which leaves a clean ladder for later tiers
	 * (batch 2 → 8 → 32 → 64 at the same interval = 2 → 8 → 32 → 64 items/s).
	 */
	public static int itemPipeItemsPerTransfer = 2;

	/**
	 * Server ticks between transfers on one pipe network (20 = once per second). See
	 * {@link #itemPipeItemsPerTransfer} for the balance rationale; upgrades are expected to raise the
	 * batch, not shorten this.
	 */
	public static int itemPipeTransferIntervalTicks = 20;

	// --- Battery Pouch (MOD-052, powered item) ---
	/** Pouch storage capacity in weight units (vanilla-bundle math: one item weighs 64/maxStackSize).
	 * 128 = exactly twice a vanilla bundle, ≈ two stacks of ordinary items. */
	public static int lvPouchCapacity = 128;
	/** Pouch EU buffer. At the 1 EU/s passive drain this is ~33 min of carrying items — well past a
	 * single mining trip; charging at the LV ceiling (32 EU/t) refills it in ~63 ticks. */
	public static int lvPouchBuffer = 2000;
	/** EU drained per second while the pouch is in a player inventory AND holds items. At 0 EU the
	 * pouch locks (no insert, no extract) until recharged in the Battery Box slot. */
	public static int lvPouchDrainPerSecond = 1;

	// --- Energy Pack (MOD-065, worn LV buffer) ---
	/** Energy Pack EU buffer — 10 pouches' worth, the same size as the Battery Box (LV tier). Charging
	 * it from a Battery Box at the LV ceiling (32 EU/t) takes ~625 ticks (~31 s). */
	public static int energyPackBuffer = 20_000;
	/** Max EU/tick the pack accepts while sitting in a charge slot. At the LV ceiling this is what a
	 * Battery Box can push anyway; the knob exists so a future MV charger can feed the pack faster. */
	public static int energyPackInputRate = 32;
	/** Max EU/tick the worn pack hands out to powered items in the player's inventory. The transfer
	 * runs once per second in batches of {@code energyPackOutputRate × 20} EU (see EnergyPackItem). */
	public static int energyPackOutputRate = 32;

	// --- Electric Drill (MOD-079, first powered hand tool) ---
	/** Electric Drill EU buffer — half an Energy Pack, five pouches' worth. At {@link #electricDrillEuPerBlock}
	 * per block this is ~200 blocks on a full charge. */
	public static int electricDrillBuffer = 10_000;
	/** EU drained per block the drill successfully mines while it has at least this much charge. Below it the
	 * drill still mines (and drops), but at hand speed and free — see ElectricDrillItem. Kept under the LV
	 * machine floor (200 EU/op): breaking a block is cheaper than smelting one. */
	public static int electricDrillEuPerBlock = 50;
	/** Max EU/tick the drill accepts while sitting in a charge slot. At the LV ceiling a full charge from a
		Battery Box takes ~313 ticks (~16 s). */
	public static int electricDrillInputRate = 32;
	/** EU drained when the drill places a torch from the inventory on right-click (MOD-089). Placing a
		torch is a comfort action, cheaper than mining a block ({@link #electricDrillEuPerBlock} = 50). Below
		this charge the drill refuses to place and notifies the player instead of giving a free torch
		(MOD-097) — the torch is powered, not free. */
	public static int electricDrillTorchEuCost = 5;

	// --- Electromagnet (MOD-132, item-pull convenience) ---
	/** Electromagnet EU buffer (tier 1). A modest LV reservoir: at {@link #magnetEuPerItem} per pulled
	 * item·tick it reaps hundreds of drops before a recharge, and tops up in ~8 s at an LV charger. */
	public static int magnetBuffer = 5_000;
	/** Max EU/tick the magnet accepts while sitting in a charge slot (LV ceiling, like the drill). */
	public static int magnetInputRate = 32;
	/** Pull radius in blocks around the carrier (a sphere — up, down and sideways). Tier 1 covers 5
	 * blocks; higher tiers (larger radius) are a later task. */
	public static int magnetRange = 5;
	/** EU spent per item actually pulled, each tick it is being drawn in. An idle scan (nothing in range)
	 * is free, so the magnet is a consumable and not a free vacuum. Small next to the large buffer. */
	public static int magnetEuPerItem = 2;
	/** How often (ticks) the magnet scans for and pulls nearby drops. 1 = every tick, for a smooth, fast
	 * XP-orb-like pull that visibly flies items in (a coarser interval read as "barely pulling"). */
	public static int magnetScanIntervalTicks = 1;

	// --- Jetpack (MOD-148, worn EU flight) ---
	/** Jetpack EU buffer — 1.5 Energy Packs. At {@link #jetpackEuPerTick} per tick of thrust this is
	 * ~30 s of continuous flight; charging at the LV ceiling (32 EU/t) refills it in ~938 ticks (~47 s). */
	public static int jetpackBuffer = 30_000;
	/** EU burned per tick the jetpack engine actually thrusts (jump held while airborne, charge left).
	 * Matches the drill's per-block cost: a second of flight ≈ 20 mined blocks' worth of EU. */
	public static int jetpackEuPerTick = 50;
	/** Max EU/tick the jetpack accepts while sitting in a charge slot (LV ceiling, like the pack). */
	public static int jetpackInputRate = 32;
	/** Altitude ceiling (block Y) above which the engine refuses to thrust — the jetpack glides
	 * instead. 320 = the overworld build limit; server owners can lower it. */
	public static int jetpackMaxY = 320;
	/** Light level (0–15) of the torch-like glow a thrusting jetpack casts around the flyer — a
	 * moving {@code minecraft:light} block (see JetpackLight). 0 disables the effect entirely; 10 is
	 * a bit under a torch (14), a "small glow". Values above 15 are clamped. */
	public static int jetpackFlightLightLevel = 10;

	// --- Stock Display Frame (MOD-066, no energy) ---
	/** How often (ticks) a stock display frame rescans the container behind it. 20 = once a second;
	 * a 100-frame warehouse costs ~5 container sums per tick at the default. */
	public static int stockFrameScanIntervalTicks = 20;

	// --- Machines: shared EU/tick + per-machine duration (ticks) -> E_op = euPerTick × duration ---
	public static int machineEuPerTick = 2;
	public static int maceratorDuration = 150;
	public static int electricFurnaceDuration = 100;
	public static int compressorDuration = 130;
	public static int extractorDuration = 120;

	// --- Iron Furnace (fuel-based, MOD-115): ticks to smelt one item. Vanilla furnace = 200. ---
	/** Ticks the iron furnace needs to smelt one item on fuel. Between vanilla (200) and the
	 * electric furnace, so it reads as "a bit faster than stone" without devaluing the electric tier. */
	public static int ironFurnaceCookTime = 150;

	// --- Player stats / mod XP (MOD-133). Starting values — calibrate after playtest. ---
	/** Useful EU (from completed machine operations) that equals one point of mod XP. Higher = slower. */
	public static int euPerXp = 1000;
	/**
	 * Produced EU (actually credited into a generator's buffer) that equals one point of mod XP —
	 * deliberately far worse than {@link #euPerXp}, because a generator runs without the player.
	 * The token trickle keeps a big power farm from feeling unrewarded while leaving hands-on machine
	 * work the dominant source; idle production into a full buffer credits nothing at all.
	 */
	public static int euPerXpGenerated = 20_000;
	/** XP cost of the first level (1→2); each later level costs {@link #levelXpMultiplier}× the previous. */
	public static int xpLevelOneCost = 80;
	/** Per-level XP cost multiplier — the exponential curve over 40 levels. Must be &gt; 1.0. */
	public static float levelXpMultiplier = 1.18f;
	/** How often (server ticks) in-memory player stats are folded into the attachment and synced. */
	public static int statsFlushTicks = 100;

	// --- Energy tiers: per-tick voltage cap + default buffer capacity, configurable per tier ---
	/**
	 * Max packet voltage (EU) and per-tick transfer cap for the LV tier. Applies to every LV block
	 * (cable, generator, machine, storage) — i.e. the most-used tier in the mod. The other LV-rate
	 * fields (cableBuffer, generatorBuffer, …) are per-block overrides; this is the universal tier ceiling.
	 * Mirrored into {@link dev.alaindustrial.core.energy.EnergyTier#LV} at class init.
	 */
	public static int tierLvVoltage = 32;
	/** Max packet voltage for the MV tier. 4× LV by convention. Mirrored into EnergyTier.MV. */
	public static int tierMvVoltage = 128;
	/** Max packet voltage for the HV tier. 4× MV by convention. Mirrored into EnergyTier.HV. */
	public static int tierHvVoltage = 512;
	/** Default internal buffer capacity for LV machines that do not override it. Mirrored into EnergyTier.LV. */
	public static int tierLvCapacity = 10_000;
	/** Default internal buffer capacity for MV machines. Mirrored into EnergyTier.MV. */
	public static int tierMvCapacity = 40_000;
	/** Default internal buffer capacity for HV machines. Mirrored into EnergyTier.HV. */
	public static int tierHvCapacity = 160_000;

	// --- Cable ---
	/**
	 * Fraction of throughput lost per cable block traversed (copper LV). Resistive/proportional model
	 * (MOD-021): a consumer at cable-distance {@code d} from the nearest producer receives
	 * {@code floor(gross × copperCableLossPerBlock × d)} fewer EU, capped at {@code gross} so delivery
	 * never goes negative. Proportional — not the flat per-tick toll removed in MOD-009 — so a small
	 * top-off packet floors to zero loss and a buffer still reaches its exact capacity on a long line.
	 *
	 * <p>{@code 0.02} = 2%/block (finalized in MOD-073, source of truth PERFORMANCE.md): a full 32 EU
	 * LV packet loses 6 EU over 10 cables, 12 over 20, and the whole packet over 50; an 8 EU/t
	 * fuel-generator stream still loses only 1 EU over 10 cables, and a 1 EU trickle floors to zero
	 * over any distance. Tuned to penalize long runs across the base rather than the LV tier ceiling,
	 * pushing players to keep a BatteryBox near the load — copper stays the only cable in the release,
	 * so 0.02 sits at the comfortable top of its band (0.025/0.03 are reserved for when glass fibre
	 * gives a real upgrade path).
	 */
	public static double copperCableLossPerBlock = 0.02;

	// --- Energy network ---
	/** Max awake energy networks processed per server tick; the rest are deferred round-robin. */
	public static int networksPerTick = 512;
	/**
	 * Cap on how many distinct {@code EnergyNetwork}s the Network Analyzer's Traverse mode (MOD-047)
	 * will walk through storage sinks before stopping. Visualization-only — never affects energy
	 * distribution. Generous default so realistic factories stitch fully; absurd megabases cap out
	 * with an actionbar warning instead of freezing the client.
	 */
	public static int networkAnalyzerMaxTraversedNetworks = 32;

	// --- World gen ---
	/**
	 * MOD-119: when {@code true} and the player creates a world with the vanilla "Bonus Chest" option on,
	 * the mod injects a pool of starter items into {@code minecraft:chests/spawn_bonus_chest} (vanilla loot
	 * is kept). Set {@code false} to leave the bonus chest purely vanilla. Read by the Fabric
	 * {@code LootTableEvents.MODIFY} handler and the NeoForge {@code alaindustrial:bonus_chest_enabled}
	 * loot condition.
	 */
	public static boolean bonusChestEnabled = true;

	/**
	 * Declarative description of every tunable above: json key, doc text, and how the value is read,
	 * validated and written back. {@link #loadFrom} and {@link #snapshot} walk this list instead of
	 * repeating each field five times (declaration, staged read, clamp, commit, serialize) — adding a
	 * knob is now one declaration plus one entry here.
	 *
	 * <p><b>Order is load-bearing:</b> entries are serialized in list order and each field's
	 * {@code _comment_} renders immediately before it, so this list must stay in the same order the
	 * fields are declared above. It must also stay textually BELOW the declarations: each entry
	 * captures its fallback by reading the field itself, and Java runs static initializers in source
	 * order, so a list moved above them would capture zeroes.
	 *
	 * <p>Fields whose declared default IS the recovery value pass no explicit fallback — the value is
	 * derived from the field, which is what removes the old "same literal typed twice" drift risk. The
	 * four fields that deliberately clamp to a range boundary rather than restore their default
	 * (euPerXp, euPerXpGenerated, xpLevelOneCost floor at 1; copperCableLossPerBlock floors at 0.0)
	 * pass that boundary explicitly, so the exception is visible rather than implied.
	 */
	private static final List<ConfigField> FIELDS = List.of(
			new FloatField("globalEuRateMultiplier", "Multiplier on EVERY generator's EU/t output. 1.0 = unchanged; 2.0 = twice the generation server-wide.",
				() -> globalEuRateMultiplier, v -> globalEuRateMultiplier = v, 0.0f),
			new FloatField("globalMachineSpeedMultiplier", "Machine speed multiplier (energy-neutral): higher = machines draw more EU/t but finish proportionally faster. 1.0 = unchanged.",
				() -> globalMachineSpeedMultiplier, v -> globalMachineSpeedMultiplier = v, 0.0f),
			new IntField("solarEuPerTick", "Solar panel output in EU/t under clear daytime sky. The energy system's baseline (1).",
				() -> solarEuPerTick, v -> solarEuPerTick = v, 0),
			new IntField("daylightEuPerTick", "Evolved daylight-panel output in EU/t during the day.",
				() -> daylightEuPerTick, v -> daylightEuPerTick = v, 0),
			new IntField("moonlitEuPerTick", "Evolved moonlit-panel output in EU/t at night under clear sky.",
				() -> moonlitEuPerTick, v -> moonlitEuPerTick = v, 0),
			new IntField("moonlitWeatherEuPerTick", "Moonlit-panel EU/t at night during rain/thunder (a weather trickle).",
				() -> moonlitWeatherEuPerTick, v -> moonlitWeatherEuPerTick = v, 0),
			new IntField("fuelEuPerTick", "Fuel (solid-burnable) generator output in EU/t while burning.",
				() -> fuelEuPerTick, v -> fuelEuPerTick = v, 0),
			new IntField("geothermalEuPerTick", "Geothermal (lava) generator output in EU/t while burning lava.",
				() -> geothermalEuPerTick, v -> geothermalEuPerTick = v, 0),
			new IntField("geothermalBurnTicks", "Ticks of burn a geothermal generator gets per bucket of lava (20 ticks = 1 second).",
				() -> geothermalBurnTicks, v -> geothermalBurnTicks = v, 1),
			new IntField("waterMillEuPerTick", "Water mill EU/t per adjacent water block on its four sides (0..4 EU/t total).",
				() -> waterMillEuPerTick, v -> waterMillEuPerTick = v, 0),
			new IntField("windMillMaxBaseEuPerTick", "Wind mill clear-sky height cap in EU/t (base grows with altitude up to this).",
				() -> windMillMaxBaseEuPerTick, v -> windMillMaxBaseEuPerTick = v, 0),
			new IntField("windMillMaxEuPerTick", "Hard cap on wind mill EU/t after the weather multiplier.",
				() -> windMillMaxEuPerTick, v -> windMillMaxEuPerTick = v, 0),
			new FloatField("windMillRainFactor", "Wind mill output multiplier while it is raining (not thundering).",
				() -> windMillRainFactor, v -> windMillRainFactor = v, 0.0f),
			new FloatField("windMillThunderFactor", "Wind mill output multiplier while it is thundering.",
				() -> windMillThunderFactor, v -> windMillThunderFactor = v, 0.0f),
			new IntField("windMillSampleTicks", "How often (ticks) a wind mill re-samples height/sky/weather; rate is cached in between.",
				() -> windMillSampleTicks, v -> windMillSampleTicks = v, 1),
			new IntField("windMillEvolveTicks", "Active open-sky ticks (with rotor + evolution chip) to evolve a base wind mill into its T2 branch.",
				() -> windMillEvolveTicks, v -> windMillEvolveTicks = v, 1),
			new IntField("highAltWindMillMaxBaseEuPerTick", "High-altitude wind mill (T2) clear-sky height cap in EU/t.",
				() -> highAltWindMillMaxBaseEuPerTick, v -> highAltWindMillMaxBaseEuPerTick = v, 0),
			new IntField("highAltWindMillBlocksPerBase", "Blocks of height above sea level per +1 EU/t of base on the high-altitude T2 variant.",
				() -> highAltWindMillBlocksPerBase, v -> highAltWindMillBlocksPerBase = v, 1),
			new IntField("highAltWindMillMaxEuPerTick", "Hard cap on high-altitude T2 wind mill EU/t after the weather multiplier.",
				() -> highAltWindMillMaxEuPerTick, v -> highAltWindMillMaxEuPerTick = v, 0),
			new IntField("stormWindMillMaxBaseEuPerTick", "Storm wind mill (T2) clear-sky height cap in EU/t before the weather multiplier.",
				() -> stormWindMillMaxBaseEuPerTick, v -> stormWindMillMaxBaseEuPerTick = v, 0),
			new FloatField("stormWindMillRainFactor", "Storm T2 wind mill output multiplier while it is raining.",
				() -> stormWindMillRainFactor, v -> stormWindMillRainFactor = v, 0.0f),
			new FloatField("stormWindMillThunderFactor", "Storm T2 wind mill output multiplier while it is thundering.",
				() -> stormWindMillThunderFactor, v -> stormWindMillThunderFactor = v, 0.0f),
			new IntField("stormWindMillMaxEuPerTick", "Hard cap on storm T2 wind mill EU/t after the weather multiplier.",
				() -> stormWindMillMaxEuPerTick, v -> stormWindMillMaxEuPerTick = v, 0),
			new FloatField("solarTransparentFactor", "Output multiplier when a solar panel sees sky through a translucent block (leaves, cobweb).",
				() -> solarTransparentFactor, v -> solarTransparentFactor = v, 0.0f),
			new FloatField("solarSnowFactor", "Output multiplier under snow (a snow layer above, or snowfall in a cold biome).",
				() -> solarSnowFactor, v -> solarSnowFactor = v, 0.0f),
			new IntField("solarEvolveTicks", "Active sky-time ticks needed to evolve a base solar panel into its T2 branch.",
				() -> solarEvolveTicks, v -> solarEvolveTicks = v, 1),
			new IntField("solarSkySampleTicks", "How often (ticks) a solar panel re-samples sky access + weather; verdict is cached between samples.",
				() -> solarSkySampleTicks, v -> solarSkySampleTicks = v, 1),
			new IntField("pumpEuPerBucket", "EU the pump spends per bucket of fluid it moves (extract + push).",
				() -> pumpEuPerBucket, v -> pumpEuPerBucket = v, 0),
			new IntField("pumpScanCooldownTicks", "How many ticks the pump waits after a BFS scan before scanning again.",
				() -> pumpScanCooldownTicks, v -> pumpScanCooldownTicks = v, 1),
			new IntField("pumpScanMaxDistance", "Max Manhattan distance the pump BFS searches for a fluid source.",
				() -> pumpScanMaxDistance, v -> pumpScanMaxDistance = v, 1),
			new IntField("pumpScanMaxVisited", "Max blocks the pump BFS visits per scan, caps lag.",
				() -> pumpScanMaxVisited, v -> pumpScanMaxVisited = v, 1),
			new IntField("fluidTankCapacity", "Portable fluid tank capacity in mB (1000 mB = 1 bucket). Applies to newly placed tanks.",
				() -> fluidTankCapacity, v -> fluidTankCapacity = v, 1),
			new IntField("teleporterBuffer", "Teleporter station EU buffer. Applies to newly placed stations.",
				() -> teleporterBuffer, v -> teleporterBuffer = v, 1),
			new IntField("teleporterBaseCost", "Flat EU part of a jump's price (paid even for a short hop).",
				() -> teleporterBaseCost, v -> teleporterBaseCost = v, 0),
			new IntField("teleporterCostPerBlock", "Added EU per block of straight-line distance to the target station.",
				() -> teleporterCostPerBlock, v -> teleporterCostPerBlock = v, 0),
			new IntField("teleporterWarmupTicks", "Warmup before a jump fires (20 ticks = 1 second). Cancelled by damage.",
				() -> teleporterWarmupTicks, v -> teleporterWarmupTicks = v, 0),
			new IntField("teleporterCooldownTicks", "Per-player anti-spam lockout after landing (ticks).",
				() -> teleporterCooldownTicks, v -> teleporterCooldownTicks = v, 0),
			new IntField("teleporterWarmupCancelRadius", "Moving further than this many blocks from where warmup started cancels the jump.",
				() -> teleporterWarmupCancelRadius, v -> teleporterWarmupCancelRadius = v, 1),
			new IntField("teleporterMaxPoints", "Max stations one teleport remote can hold.",
				() -> teleporterMaxPoints, v -> teleporterMaxPoints = v, 1),
			new IntField("batteryBoxBuffer", "Battery Box EU buffer. Applies to newly placed blocks (already-placed keep their capacity until the chunk reloads).",
				() -> batteryBoxBuffer, v -> batteryBoxBuffer = v, 1),
			new IntField("maceratorBuffer", "Macerator EU buffer. Applies to newly placed blocks.",
				() -> maceratorBuffer, v -> maceratorBuffer = v, 1),
			new IntField("machineBuffer", "Shared EU buffer for electric furnace / compressor / extractor. Applies to newly placed blocks.",
				() -> machineBuffer, v -> machineBuffer = v, 1),
			new IntField("pumpBuffer", "Pump EU buffer. Applies to newly placed blocks.",
				() -> pumpBuffer, v -> pumpBuffer = v, 1),
			new IntField("generatorBuffer", "Fuel generator EU buffer. Applies to newly placed blocks.",
				() -> generatorBuffer, v -> generatorBuffer = v, 1),
			new IntField("geothermalBuffer", "Geothermal generator EU buffer. Applies to newly placed blocks.",
				() -> geothermalBuffer, v -> geothermalBuffer = v, 1),
			new IntField("waterMillBuffer", "Water mill EU buffer. Applies to newly placed blocks.",
				() -> waterMillBuffer, v -> waterMillBuffer = v, 1),
			new IntField("windMillBuffer", "Wind mill (T1) EU buffer. Applies to newly placed blocks.",
				() -> windMillBuffer, v -> windMillBuffer = v, 1),
			new IntField("t2WindMillBuffer", "Shared EU buffer for both T2 wind mills (high-altitude + storm). Applies to newly placed blocks.",
				() -> t2WindMillBuffer, v -> t2WindMillBuffer = v, 1),
			new IntField("solarBuffer", "Solar panel EU buffer. Applies to newly placed blocks.",
				() -> solarBuffer, v -> solarBuffer = v, 1),
			new IntField("cableBuffer", "Per-cable working EU buffer — the live transport-segment buffer (MOD-070). Tiny by design so a wall of cables can't be used as bulk storage. Applies to newly placed cables.",
				() -> cableBuffer, v -> cableBuffer = v, 1),
			new IntField("itemPipeItemsPerTransfer", "Items an item-pipe network moves per transfer. With the interval below this sets throughput.",
				() -> itemPipeItemsPerTransfer, v -> itemPipeItemsPerTransfer = v, 1),
			new IntField("itemPipeTransferIntervalTicks", "Server ticks between item-pipe transfers (20 = once per second).",
				() -> itemPipeTransferIntervalTicks, v -> itemPipeTransferIntervalTicks = v, 1),
			new IntField("lvPouchCapacity", "Battery Pouch item-storage capacity in weight units (one ordinary item = 1).",
				() -> lvPouchCapacity, v -> lvPouchCapacity = v, 1),
			new IntField("lvPouchBuffer", "Battery Pouch EU buffer.",
				() -> lvPouchBuffer, v -> lvPouchBuffer = v, 1),
			new IntField("lvPouchDrainPerSecond", "EU the pouch drains per second while carried and holding items (locks at 0 EU until recharged).",
				() -> lvPouchDrainPerSecond, v -> lvPouchDrainPerSecond = v, 0),
			new IntField("energyPackBuffer", "Energy Pack (worn) EU buffer.",
				() -> energyPackBuffer, v -> energyPackBuffer = v, 1),
			new IntField("energyPackInputRate", "Max EU/t the Energy Pack accepts while charging in a slot.",
				() -> energyPackInputRate, v -> energyPackInputRate = v, 1),
			new IntField("energyPackOutputRate", "Max EU/t the worn Energy Pack hands out to powered items in the inventory.",
				() -> energyPackOutputRate, v -> energyPackOutputRate = v, 1),
			new IntField("electricDrillBuffer", "Electric Drill EU buffer.",
				() -> electricDrillBuffer, v -> electricDrillBuffer = v, 1),
			new IntField("electricDrillEuPerBlock", "EU the drill spends per block mined at powered speed (below this it mines at hand speed for free).",
				() -> electricDrillEuPerBlock, v -> electricDrillEuPerBlock = v, 1),
			new IntField("electricDrillInputRate", "Max EU/t the drill accepts while charging in a slot.",
				() -> electricDrillInputRate, v -> electricDrillInputRate = v, 1),
			new IntField("electricDrillTorchEuCost", "EU the drill spends to place a torch on right-click.",
				() -> electricDrillTorchEuCost, v -> electricDrillTorchEuCost = v, 0),
			new IntField("jetpackBuffer", "Jetpack EU buffer.",
				() -> jetpackBuffer, v -> jetpackBuffer = v, 1),
			new IntField("jetpackEuPerTick", "EU the jetpack burns per tick of thrust (jump held while airborne).",
				() -> jetpackEuPerTick, v -> jetpackEuPerTick = v, 1),
			new IntField("jetpackInputRate", "Max EU/t the jetpack accepts while charging in a slot.",
				() -> jetpackInputRate, v -> jetpackInputRate = v, 1),
			new IntField("jetpackMaxY", "Altitude ceiling (block Y) above which the jetpack engine refuses to thrust.",
				() -> jetpackMaxY, v -> jetpackMaxY = v, 1),
			new IntField("jetpackFlightLightLevel", "Light level (0-15) a thrusting jetpack casts around the flyer; 0 disables the glow.",
				() -> jetpackFlightLightLevel, v -> jetpackFlightLightLevel = v, 0),
			new IntField("magnetBuffer", "Electromagnet EU buffer.",
				() -> magnetBuffer, v -> magnetBuffer = v, 1),
			new IntField("magnetInputRate", "Max EU/t the electromagnet accepts while charging in a slot.",
				() -> magnetInputRate, v -> magnetInputRate = v, 1),
			new IntField("magnetRange", "Electromagnet pull radius in blocks around the carrier.",
				() -> magnetRange, v -> magnetRange = v, 1),
			new IntField("magnetEuPerItem", "EU the electromagnet spends per item pulled each scan tick (an idle scan is free).",
				() -> magnetEuPerItem, v -> magnetEuPerItem = v, 1),
			new IntField("magnetScanIntervalTicks", "How often (ticks) the electromagnet scans for and pulls nearby drops.",
				() -> magnetScanIntervalTicks, v -> magnetScanIntervalTicks = v, 1),
			new IntField("stockFrameScanIntervalTicks", "How often (ticks) a Stock Display Frame rescans the container behind it.",
				() -> stockFrameScanIntervalTicks, v -> stockFrameScanIntervalTicks = v, 1),
			new IntField("machineEuPerTick", "Base EU/t a processing machine draws while running (energy per operation = this x its duration).",
				() -> machineEuPerTick, v -> machineEuPerTick = v, 1),
			new IntField("maceratorDuration", "Ticks a macerator takes per operation at 1.0 speed.",
				() -> maceratorDuration, v -> maceratorDuration = v, 1),
			new IntField("electricFurnaceDuration", "Ticks an electric furnace takes per smelt at 1.0 speed.",
				() -> electricFurnaceDuration, v -> electricFurnaceDuration = v, 1),
			new IntField("compressorDuration", "Ticks a compressor takes per operation at 1.0 speed.",
				() -> compressorDuration, v -> compressorDuration = v, 1),
			new IntField("extractorDuration", "Ticks an extractor takes per operation at 1.0 speed.",
				() -> extractorDuration, v -> extractorDuration = v, 1),
			new IntField("ironFurnaceCookTime", "Ticks the (fuel-based) iron furnace takes to smelt one item. Vanilla furnace = 200.",
				() -> ironFurnaceCookTime, v -> ironFurnaceCookTime = v, 1),
			new IntField("euPerXp", "MOD-133 player profile: useful EU (from completed machine operations) per 1 point of mod XP. Higher = slower progression. Starting value, tune after playtest.",
				() -> euPerXp, v -> euPerXp = v, 1, 1),
			new IntField("euPerXpGenerated", "MOD-133 player profile: produced EU (actually credited into a generator buffer, never idle overflow) per 1 point of mod XP. Much higher than euPerXp on purpose - a generator runs unattended, so it only trickles. Starting value, tune after playtest.",
				() -> euPerXpGenerated, v -> euPerXpGenerated = v, 1, 1),
			new IntField("xpLevelOneCost", "MOD-133: XP cost of the first level (1->2); each later level costs levelXpMultiplier x the previous. Starting value.",
				() -> xpLevelOneCost, v -> xpLevelOneCost = v, 1, 1),
			new FloatField("levelXpMultiplier", "MOD-133: per-level XP cost multiplier (exponential curve over 40 levels). Must be > 1.0.",
				() -> levelXpMultiplier, v -> levelXpMultiplier = v, 1.0f),
			new IntField("statsFlushTicks", "MOD-133: how often (server ticks) in-memory player stats fold into the attachment and sync. 100 = every 5s.",
				() -> statsFlushTicks, v -> statsFlushTicks = v, 1),
			new IntField("tierLvVoltage", "Max packet voltage (EU) and per-tick transfer cap for the LV tier (cable, generator, machine, storage). Mirrored into EnergyTier.LV.",
				() -> tierLvVoltage, v -> tierLvVoltage = v, 1),
			new IntField("tierMvVoltage", "Max packet voltage for the MV tier (4x LV by convention). Mirrored into EnergyTier.MV.",
				() -> tierMvVoltage, v -> tierMvVoltage = v, 1),
			new IntField("tierHvVoltage", "Max packet voltage for the HV tier (4x MV by convention). Mirrored into EnergyTier.HV.",
				() -> tierHvVoltage, v -> tierHvVoltage = v, 1),
			new IntField("tierLvCapacity", "Default internal buffer capacity for LV machines that do not override it. Mirrored into EnergyTier.LV.",
				() -> tierLvCapacity, v -> tierLvCapacity = v, 1),
			new IntField("tierMvCapacity", "Default internal buffer capacity for MV machines. Mirrored into EnergyTier.MV.",
				() -> tierMvCapacity, v -> tierMvCapacity = v, 1),
			new IntField("tierHvCapacity", "Default internal buffer capacity for HV machines. Mirrored into EnergyTier.HV.",
				() -> tierHvCapacity, v -> tierHvCapacity = v, 1),
			new DoubleField("copperCableLossPerBlock", "Fraction of throughput lost per copper cable block traversed (0.02 = 2% per block).",
				() -> copperCableLossPerBlock, v -> copperCableLossPerBlock = v, 0.0, 0.0),
			new IntField("networksPerTick", "Max awake energy networks processed per server tick; the rest are deferred round-robin.",
				() -> networksPerTick, v -> networksPerTick = v, 1),
			new IntField("networkAnalyzerMaxTraversedNetworks", "Cap on networks the Network Analyzer's Traverse mode walks (visualization only, never affects energy).",
				() -> networkAnalyzerMaxTraversedNetworks, v -> networkAnalyzerMaxTraversedNetworks = v, 1),
			new BoolField("bonusChestEnabled", "When true, mod starter items are injected into the vanilla bonus chest at world creation (vanilla loot kept). false = purely vanilla bonus chest.",
				() -> bonusChestEnabled, v -> bonusChestEnabled = v));

	/** Effective machine drain per tick after the speed multiplier (E_op stays ~constant). */
	public static int machineEuPerTickEffective() {
		return Math.max(1, Math.round(machineEuPerTick * globalMachineSpeedMultiplier));
	}

	/** Scale a base duration (ticks) by the speed multiplier: faster machine -> fewer ticks. */
	public static int scaledDuration(int baseTicks) {
		return Math.max(1, Math.round(baseTicks / globalMachineSpeedMultiplier));
	}

	/**
	 * EU the electric furnace spends on one vanilla smelt — its scaled duration times its effective
	 * per-tick drain, i.e. exactly what {@code ElectricFurnaceBlockEntity} ticks away.
	 *
	 * <p>Lives here so the recipe-viewer mirrors (MOD-086) quote the same number the machine spends
	 * under any {@link #globalMachineSpeedMultiplier}: both factors round separately, so multiplying
	 * the raw fields would only agree at the default 1.0 (e.g. x3 really costs
	 * round(100/3) x round(2*3) = 198 EU, not 200).
	 */
	public static int electricFurnaceVanillaSmeltEu() {
		return Math.max(1, scaledDuration(electricFurnaceDuration) * machineEuPerTickEffective());
	}

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
		/** File was absent; the current defaults were written to it. */
		DEFAULTS_WRITTEN,
		/** File existed but could not be parsed (bad JSON / wrong value type); live balance is unchanged. */
		ERROR
	}

	/** Reload from the loader-bound {@link #configPath}. Thin wrapper for the reload command + reload listeners. */
	public static LoadResult reload() {
		return loadFrom(configPath.get());
	}

	/**
	 * Load the config file at {@code path}, or write the current defaults if it does not exist yet.
	 *
	 * <p><b>Atomic:</b> every field is parsed into locals first (a wrong-type value throws before anything is
	 * applied), then committed to the static fields in one block — a single typo in the file can never leave the
	 * live balance half-updated. <b>Self-healing on load:</b> after a successful parse the file is re-serialized in
	 * canonical form (field comments + any newly added mod fields) and rewritten only when its content actually
	 * differs, so existing installs gain the inline comments and the write is idempotent (no churn on {@code /reload}).
	 * <b>Minecraft-free:</b> uses plain Gson (not {@code net.minecraft.GsonHelper}) so the loader-neutral L1 test
	 * suite, which runs without the Minecraft jar, can exercise the file logic directly.
	 */
	public static LoadResult loadFrom(Path path) {
		try {
			if (!Files.exists(path)) {
				Files.writeString(path, canonicalJson());
				Industrialization.LOGGER.info("[config] wrote defaults to {}", path);
				return LoadResult.DEFAULTS_WRITTEN;
			}
			String raw = Files.readString(path);
			JsonObject o = JsonParser.parseString(raw).getAsJsonObject();

			// --- staging: parse + validate every field into pending commits; a present-but-wrong-type
			//     key throws here, before any static field is touched (atomic all-or-nothing apply below). ---
			List<Runnable> pending = new ArrayList<>(FIELDS.size());
			for (ConfigField field : FIELDS) {
				pending.add(field.stage(o));
			}

			// --- commit: apply all staged values at once (nothing above threw, so this is all-or-nothing). ---
			for (Runnable commit : pending) {
				commit.run();
			}
			Industrialization.LOGGER.info("[config] loaded {}", path);

			// --- self-heal: rewrite in canonical form (comments + new fields) only when content differs. This
			//     is how an existing comment-less file gains its inline docs, and it stays idempotent on /reload.
			//     A write failure must not fail the (already successful) load, so it is caught separately. ---
			String canonical = canonicalJson();
			if (!normalize(raw).equals(normalize(canonical))) {
				try {
					Files.writeString(path, canonical);
					Industrialization.LOGGER.info("[config] canonicalized {}", path);
				} catch (Exception writeError) {
					Industrialization.LOGGER.error("[config] canonicalize write failed {}: {}", path, writeError.toString());
				}
			}
			return LoadResult.LOADED;
		} catch (Exception e) {
			Industrialization.LOGGER.error("[config] failed to load {}: {}", path, e.toString());
			return LoadResult.ERROR;
		}
	}

	/** Pretty-printed canonical form of the current values, including the {@code _comment_*} field docs. */
	private static String canonicalJson() {
		return new GsonBuilder().setPrettyPrinting().create().toJson(snapshot());
	}

	/** Ignore line-ending + surrounding-whitespace differences when deciding whether to rewrite the file. */
	private static String normalize(String s) {
		return s.replace("\r\n", "\n").strip();
	}

	/**
	 * Read an int by key with the {@link net.minecraft.util.GsonHelper}-equivalent contract, but on plain Gson so
	 * {@link Config} carries no {@code net.minecraft} dependency: return {@code def} if the key is absent/null,
	 * else the number — and <b>throw</b> if the key is present but not a number (this is what makes a typo abort
	 * the whole load instead of silently applying a partial file). {@code _comment_*} keys are never requested.
	 */
	private static int getInt(JsonObject o, String key, int def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsInt();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a number, got " + e);
	}

	/** Float counterpart of {@link #getInt} — same absent-default / wrong-type-throws contract. */
	private static float getFloat(JsonObject o, String key, float def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsFloat();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a number, got " + e);
	}

	/**
	 * Double counterpart of {@link #getInt} — same absent-default / wrong-type-throws contract. Reads at
	 * double precision on purpose: the previous code funnelled the one double knob through
	 * {@link #getFloat}, so a file holding {@code 0.02} was widened back as {@code 0.019999999552965164}
	 * and the self-heal then rewrote the file with that noise.
	 */
	private static double getDouble(JsonObject o, String key, double def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsDouble();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a number, got " + e);
	}

	/** Boolean counterpart of {@link #getInt} — same absent-default / wrong-type-throws contract. */
	private static boolean getBool(JsonObject o, String key, boolean def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
			return e.getAsBoolean();
		}
		throw new IllegalArgumentException("config key '" + key + "' must be a boolean, got " + e);
	}

	private static JsonObject snapshot() {
		JsonObject o = new JsonObject();
		for (ConfigField field : FIELDS) {
			field.write(o);
		}
		return o;
	}

	/** Add an inline {@code _comment_<field>} doc string immediately before its field (Gson keeps insertion
	 * order, so the comment renders on the line above). Ignored on read — {@link #getInt}/{@link #getFloat}
	 * only ever request the real field keys. */
	private static void c(JsonObject o, String field, String text) {
		o.addProperty("_comment_" + field, text);
	}

	/**
	 * One tunable's read/validate/write behaviour. Subclasses exist per primitive type so the
	 * serialized json keeps the exact numeric form it had when every field was written out by hand
	 * (an int must not start rendering as a double).
	 */
	private abstract static class ConfigField {
		final String key;
		final String doc;

		ConfigField(String key, String doc) {
			this.key = key;
			this.doc = doc;
		}

		/**
		 * Parse and validate this field out of {@code file}, returning the action that commits it.
		 * Throws if the key is present with a wrong type — that is what aborts the whole load before
		 * any field is applied, keeping {@link #loadFrom} all-or-nothing.
		 */
		abstract Runnable stage(JsonObject file);

		/** Append the current live value (and its doc comment) to the canonical snapshot. */
		abstract void write(JsonObject out);
	}

	private static final class IntField extends ConfigField {
		private final IntSupplier getter;
		private final IntConsumer setter;
		private final int fallback;
		private final Integer minimum;
		private final Integer floorTo;

		IntField(String key, String doc, IntSupplier getter, IntConsumer setter) {
			this(key, doc, getter, setter, null, null);
		}

		IntField(String key, String doc, IntSupplier getter, IntConsumer setter, Integer minimum) {
			this(key, doc, getter, setter, minimum, null);
		}

		IntField(String key, String doc, IntSupplier getter, IntConsumer setter, Integer minimum,
				Integer floorTo) {
			super(key, doc);
			this.getter = getter;
			this.setter = setter;
			this.fallback = getter.getAsInt();
			this.minimum = minimum;
			this.floorTo = floorTo;
		}

		@Override
		Runnable stage(JsonObject file) {
			int v = getInt(file, key, getter.getAsInt());
			if (minimum != null && v < minimum) {
				v = floorTo != null ? floorTo : fallback;
			}
			int applied = v;
			return () -> setter.accept(applied);
		}

		@Override
		void write(JsonObject out) {
			c(out, key, doc);
			out.addProperty(key, getter.getAsInt());
		}
	}

	private static final class FloatField extends ConfigField {
		private final FloatSupplier getter;
		private final FloatConsumer setter;
		private final float fallback;
		private final Float minimumExclusive;

		FloatField(String key, String doc, FloatSupplier getter, FloatConsumer setter) {
			this(key, doc, getter, setter, null);
		}

		FloatField(String key, String doc, FloatSupplier getter, FloatConsumer setter,
				Float minimumExclusive) {
			super(key, doc);
			this.getter = getter;
			this.setter = setter;
			this.fallback = getter.get();
			this.minimumExclusive = minimumExclusive;
		}

		@Override
		Runnable stage(JsonObject file) {
			float v = getFloat(file, key, getter.get());
			if (minimumExclusive != null && v <= minimumExclusive) {
				v = fallback;
			}
			float applied = v;
			return () -> setter.accept(applied);
		}

		@Override
		void write(JsonObject out) {
			c(out, key, doc);
			out.addProperty(key, getter.get());
		}
	}

	private static final class DoubleField extends ConfigField {
		private final DoubleSupplier getter;
		private final DoubleConsumer setter;
		private final double minimum;
		private final double floorTo;

		DoubleField(String key, String doc, DoubleSupplier getter, DoubleConsumer setter,
				double minimum, double floorTo) {
			super(key, doc);
			this.getter = getter;
			this.setter = setter;
			this.minimum = minimum;
			this.floorTo = floorTo;
		}

		@Override
		Runnable stage(JsonObject file) {
			double v = getDouble(file, key, getter.getAsDouble());
			if (v < minimum) {
				v = floorTo;
			}
			double applied = v;
			return () -> setter.accept(applied);
		}

		@Override
		void write(JsonObject out) {
			c(out, key, doc);
			out.addProperty(key, getter.getAsDouble());
		}
	}

	private static final class BoolField extends ConfigField {
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;

		BoolField(String key, String doc, BooleanSupplier getter, Consumer<Boolean> setter) {
			super(key, doc);
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		Runnable stage(JsonObject file) {
			boolean applied = getBool(file, key, getter.getAsBoolean());
			return () -> setter.accept(applied);
		}

		@Override
		void write(JsonObject out) {
			c(out, key, doc);
			out.addProperty(key, getter.getAsBoolean());
		}
	}

	/** {@code java.util.function} has no float primitive pair; these avoid boxing every float knob. */
	@FunctionalInterface
	private interface FloatSupplier {
		float get();
	}

	@FunctionalInterface
	private interface FloatConsumer {
		void accept(float value);
	}

}
