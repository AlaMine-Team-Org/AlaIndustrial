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
	// --- Rotor / wheel wear (MOD-189) — the wind mill rotor and water mill wheel are consumables ---
	/**
	 * Wind mill rotor max durability (wear shown as a vanilla durability bar). Total rotor life is
	 * {@code windMillRotorMaxDamage × windMillRotorEuPerDamage} EU of production. NOTE: the max_damage
	 * component is baked when the item is registered, so a change here takes effect only after a restart
	 * (and only on newly obtained rotors); tune the calendar life through the EU-per-damage rate below,
	 * which is read live every tick. Shared by all three wind mills (T1 + both T2 branches).
	 */
	public static int windMillRotorMaxDamage = 1000;
	/**
	 * EU of production per one durability point of the wind mill rotor. Default 480: with the 1000-point
	 * bar that is 480 000 EU of life ≈ 5 in-game days at a typical 4 EU/t T1 mill (faster on a stronger
	 * high-altitude/storm mill — wear is proportional to output). Read live every tick.
	 */
	public static int windMillRotorEuPerDamage = 480;
	/**
	 * Extra rotor-wear multiplier while a wind mill runs in rain or thunder — mechanical stress on top of
	 * the already-higher storm output. 1.0 disables the weather bonus. Applies to all three wind mills.
	 */
	public static float windMillStormWearFactor = 1.5f;
	/**
	 * Water mill wheel max durability (durability bar). Total wheel life is
	 * {@code waterMillWheelMaxDamage × waterMillWheelEuPerDamage} EU. Like the rotor the max_damage
	 * component is registration-time (restart to change); tune life via the rate below.
	 */
	public static int waterMillWheelMaxDamage = 1000;
	/**
	 * EU of production per one durability point of the water mill wheel. Default 320: 320 000 EU of life
	 * ≈ 6–7 in-game days at a typical 2 EU/t setup (the wheel runs 24/7 but at a lower rate than the
	 * weather-dependent rotor, so a slightly longer calendar life). Read live every tick.
	 */
	public static int waterMillWheelEuPerDamage = 320;
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
	/** Shared buffer for ordinary LV processing machines: electric furnace, compressor, extractor,
	 * sawmill, polymerizer and vulcanizer. */
	public static int machineBuffer = 800;
	/** Electric Heater EU buffer. At the default 2 EU/t it holds two complete 200-tick
	 * vulcanization operations and smooths a thin LV supply without becoming bulk storage. */
	public static int electricHeaterBuffer = 800;
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

	// --- Fluid pipes (MOD-151) ---
	/**
	 * Working fluid buffer of one pipe segment, in mB. As with {@link #cableBuffer}, this doubles as the
	 * segment's real throughput: fluid physically flows THROUGH the buffer, one hop per tick, so a line
	 * carries at most this much per tick regardless of how much the endpoints want to move.
	 *
	 * <p><b>Why 50.</b> Anchored to what actually feeds a line: the pump costs
	 * {@link #pumpEuPerBucket} EU per bucket and draws at LV (32 EU/tick), so it produces about
	 * 32 mB/tick. A pipe at 50 mB/tick (1 bucket/s) keeps the pump as the bottleneck rather than
	 * becoming one itself, while staying deliberately modest — like the item pipe, the basic tier earns
	 * its keep by reaching across a base, not by raw speed. Later tiers should raise this number; the
	 * one-hop-per-tick rule stays.
	 *
	 * <p>Kept small on purpose for the same reason as the cable buffer: a wall of pipes must not become
	 * bulk storage. 1000 segments hold 50 buckets, well under a single portable tank.
	 */
	public static int fluidPipeSegmentBuffer = 50;

	/**
	 * Fluid networks processed per server tick; the remainder round-robins to later ticks. Mirrors
	 * {@link #networksPerTick} for energy — a base with hundreds of separate pipe runs must not be able
	 * to spike the tick.
	 */
	public static int fluidNetworksPerTick = 512;

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
	// Fluxweave armour (MOD-127). Only EU numbers live here: defense/toughness/enchantability are built
	// into ArmorMaterial at item-registration time, BEFORE the config file is read, so exposing those
	// would be dead knobs. See ModArmorMaterials.
	/** EU buffer of each Fluxweave piece — between the drill (10k) and the Energy Pack (20k). */
	public static int fluxweaveBuffer = 15_000;
	/** Max EU/t a Fluxweave piece accepts while charging in a slot (LV ceiling). */
	public static int fluxweaveInputRate = 32;
	/** EU/second a charged, worn piece burns to keep its bonuses on. 1 EU/s = ~4 h per full buffer. */
	public static int fluxweaveUpkeepEuPerSecond = 1;
	/** Boots: percent of fall damage absorbed while charged. Clamped to 90 in code — never a full cancel. */
	public static int fluxweaveFallDamageReductionPercent = 50;
	/** Leggings: percent added to run speed while charged. */
	public static int fluxweaveRunSpeedPercent = 12;
	/** Helmet: OXYGEN_BONUS levels while charged (Respiration's mechanic: 3 = ~75 % of air ticks skipped). */
	public static int fluxweaveOxygenBonus = 3;
	/** Helmet: percent added to water movement efficiency while charged (attribute caps at 100). */
	public static int fluxweaveSwimEfficiency = 50;
	/** Chestplate: extra armour toughness while charged. */
	public static int fluxweaveChargedToughness = 2;
	/** Chestplate: percent of knockback resisted while charged. */
	public static int fluxweaveKnockbackResistance = 10;
	/** Leggings: extra step height (in hundredths of a block) while charged AND the assist is toggled on.
	 * 60 = +0.6, which takes the player from the vanilla 0.6 to 1.2 — a full block step. */
	public static int fluxweaveStepHeightBonus = 60;
	/** Set bonus: EU the helmet spends per half-heart healed at 4/4 while below full health. */
	public static int fluxweaveRegenEuPerHeal = 200;
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

	// --- Scythe bonus seed drop (MOD-315) ---
	/**
	 * Global multiplier on the scythe's per-tier bonus-seed chance. The per-tier base lives in
	 * {@code ScytheTiers} together with the rest of the scythe's balance (area, cap, attack bias) —
	 * this is the one server-side knob over the whole ladder, so a server can soften or disable the
	 * mechanic without rebalancing eight tiers.
	 *
	 * <p>{@code 1.0} keeps the shipped ladder (wood 0 % → netherite 35 %); {@code 0.0} disables the
	 * bonus entirely; anything that pushes a tier past 1.0 is clamped, so a large value means "every
	 * ripe crop drops the extra seed". The multiplication and the clamp live in
	 * {@code ScytheItem.Profile.effectiveBonusChance}.
	 */
	public static double scytheBonusSeedMultiplier = 1.0;

	// --- Machines: shared EU/tick + per-machine duration (ticks) -> E_op = euPerTick × duration ---
	public static int machineEuPerTick = 2;
	public static int maceratorDuration = 150;
	public static int electricFurnaceDuration = 100;
	public static int compressorDuration = 130;
	public static int extractorDuration = 120;
	/** Sawmill (MOD-150): ticks per cut at 1.0 speed. 80 → 160 EU/op — the cheapest machine op (wood
	 * saws easier than ore mills): furnace 100, extractor 120, compressor 130, macerator 150. */
	public static int sawmillDuration = 80;
	/** Polymerizer (MOD-019): ticks per bucket of oil at 1.0 speed. 200 → 400 EU/op — the most expensive
	 * op of the LV processing family, because rubber is the material gate into MV and a bucket of oil is
	 * a whole pumping cycle's worth of input, not a single ore. */
	public static int polymerizerDuration = 200;
	/** Vulcanizer (MOD-258): ticks per operation at 1.0 speed. The shipped recipe costs 400 EU, so at
	 * the ordinary-machine rate of 2 EU/t the operation takes 200 ticks at every heat tier. */
	public static int vulcanizerDuration = 200;
	/** Galvanic Bath (MOD-127): fallback ticks per operation at 1.0 speed. The shipped recipe costs
	 * 1000 EU, so at the ordinary-machine rate of 2 EU/t the operation takes 500 ticks (25 s) — by far
	 * the slowest of the LV processing family, because plating silver onto fibre is the gate into the
	 * Fluxweave line and its armour. */
	public static int galvanicBathDuration = 500;

	// --- MOD-275 assembler. The first MV machine: six times the LV rate, but a short operation.
	// 12 EU/t x 40 ticks = 480 EU per craft — dearer than crafting by hand, cheaper than a processing
	// step, so the machine buys time rather than resources. Raised from 8 EU/t after the playtest:
	// automation was reading as too cheap for what it removes. The buffer follows the rate so it still
	// holds 25 operations.
	public static int assemblerEuPerTick = 12;
	public static int assemblerDuration = 40;
	public static int assemblerBuffer = 12000;
	/** Galvanic Bath (MOD-127): mB of water one operation consumes from the internal tank. Deliberately
	 * NOT part of the recipe JSON (no recipe family in the mod takes items and a fluid at once — see
	 * GalvanicBathBlockEntity), so this is the one knob for the water price. 4000 mB = four buckets per
	 * thread: the bath is meant to be thirsty, so a full 10-bucket tank yields only two threads and the
	 * player is pushed to pipe water in from a pump rather than carry it. */
	public static int galvanicBathWaterPerOp = 4000;
	/** Electric Heater (MOD-258): EU/t spent only while a Vulcanizer directly above it advances.
	 * Idle heating is free; the speed multiplier scales this rate together with processing duration. */
	public static int electricHeaterEuPerTick = 2;

	// --- Incubator (MOD-118): the mod's most energy-hungry LV machine. ---
	/**
	 * EU/t the incubator draws while running — four times the machine standard. Irradiating matter is
	 * deliberately far pricier than grinding it: a routine macerator op costs 300 EU, the cheapest
	 * incubator op costs 2400.
	 */
	public static int incubatorEuPerTick = 8;
	/** Internal EU buffer; holds the costliest operation (create, 8000 EU) in full. */
	public static int incubatorBuffer = 8000;
	/** Ticks per transform attempt at 1.0 speed (300 x 8 EU/t = 2400 EU). */
	public static int mutationDurationTransform = 300;
	/** Ticks per duplicate attempt at 1.0 speed (500 x 8 EU/t = 4000 EU). */
	public static int mutationDurationDuplicate = 500;
	/** Ticks per create attempt at 1.0 speed (1000 x 8 EU/t = 8000 EU). */
	public static int mutationDurationCreate = 1000;
	/** Uranium ingots are spent as a charge: one ingot powers this many attempts, then becomes ash. */
	public static int mutationAttemptsPerIngot = 3;
	/** Base success chance of a transform mutation. */
	public static double mutationChanceTransform = 0.75;
	/** Base success chance of a duplicate mutation. */
	public static double mutationChanceDuplicate = 0.45;
	/** Base success chance of a create mutation. */
	public static double mutationChanceCreate = 0.25;
	/** Ceiling on the total success chance (base + gene bonus) — a mutation is never guaranteed. */
	public static double mutationChanceCap = 0.95;
	/** Share of attempts that yield irradiated slag; carved out of the failure share, not the success. */
	public static double mutationSlagChance = 0.05;
	/** Share of successes that roll the rare grade. */
	public static double mutationGradeRare = 0.20;
	/** Share of successes that roll the epic grade. */
	public static double mutationGradeEpic = 0.08;
	/** Share of successes that roll the legendary grade. */
	public static double mutationGradeLegendary = 0.02;

	// --- Garden Drone Station (MOD-277): zone-scan farm caretaker, one BER-drawn drone per station. ---
	/** Internal EU buffer. Sized like {@link #pumpBuffer}/{@link #magnetBuffer} — a modest LV
	 * reservoir for a per-action (not per-tick) consumer. */
	public static int gardenDroneBuffer = 4000;
	/** EU spent per completed action (till / plant / fertilize / harvest). Demand-driven: an idle
	 * station (nothing to do) spends nothing, same pattern as {@link #electricHeaterEuPerTick}. */
	public static int gardenDroneEuPerAction = 8;
	/**
	 * Scan radius in blocks around the station.
	 *
	 * <p>Four, not the nine an edge-placed 9x9 plot would need: this is the tier-1 drone, and a machine
	 * that tends a 9-wide field the moment it is crafted leaves nothing for a later tier to improve.
	 * A radius-4 zone is a comfortable plot around the dock and keeps the flights short enough to watch.
	 */
	public static int gardenDroneRange = 4;
	/** Ticks between zone re-scans; same cadence as {@link #pumpScanCooldownTicks}. The scan result
	 * is cached and invalidated by block updates inside the zone, so this interval only bounds the
	 * cost of a full rebuild after cache invalidation, not every tick's work. */
	public static int gardenDroneScanIntervalTicks = 20;
	/**
	 * Ticks the drone spends flying per block of distance to its target. The action lands when the
	 * drone arrives, not when the target is chosen — without this the whole farm is tended in a couple
	 * of ticks, which reads as teleportation rather than as a machine doing work.
	 */
	public static int gardenDroneFlightTicksPerBlock = 11;

	// --- Cotton trellis (MOD-280): the mod's first crop. ---
	/**
	 * Chance divisor for one rooting stage of the trellis: on each random tick of a moist, lit
	 * plant there is a 1-in-this chance of advancing. Growth is deliberately random rather than a timer —
	 * the plant carries no block entity, so a field of any size costs nothing to tick (MOD-280).
	 *
	 * <p>Rooting runs three stages and happens <b>once per plant</b>, so this is the slow knob: raising it
	 * lengthens the initial wait without touching how fast an established plant re-fruits.
	 */
	public static int cottonRootingChanceDivisor = 12;
	/**
	 * Chance divisor for one fruiting stage — the two-stage cycle an established plant repeats forever
	 * after every harvest. Much smaller than the rooting divisor: waiting once is the price of the plant,
	 * waiting every harvest would be tedium.
	 */
	public static int cottonFruitingChanceDivisor = 4;

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
	 * Fraction of throughput attenuated per cable block traversed (copper LV). The retained fraction
	 * compounds over cable-distance {@code d}: a consumer receives the gross flow minus
	 * {@code floor(gross × (1 - (1 - copperCableLossPerBlock)^d))}. Loss is capped below the whole packet,
	 * so a positive flow always delivers at least 1 EU and a small top-off packet still reaches exact
	 * capacity on a long line.
	 *
	 * <p>{@code 0.02} = 2%/block (finalized in MOD-073, source of truth PERFORMANCE.md): a full 32 EU
	 * LV packet loses 5 EU over 10 cables and 10 over 20; an 8 EU/t fuel-generator stream loses 1 EU
	 * over 10 cables, and a 1 EU trickle floors to zero over any distance. Tuned to penalize long runs
	 * across the base without ever turning a sufficiently long cable into a silent 100% cutoff,
	 * pushing players to keep a BatteryBox near the load — copper stays the only cable in the release,
	 * so 0.02 sits at the comfortable top of its band (0.025/0.03 are reserved for when glass fibre
	 * gives a real upgrade path).
	 */
	public static double copperCableLossPerBlock = 0.02;
	/** Master safety switch for contact damage and its particles/sound on energized bare cables. */
	public static boolean bareCableShockEnabled = true;
	/** Contact damage from an energized bare LV (tin/copper) cable, in half-hearts. */
	public static float bareCableShockLvDamage = 2.0f;
	/** Contact damage from an energized bare MV (gold) cable, in half-hearts. */
	public static float bareCableShockMvDamage = 6.0f;
	/**
	 * Extra blocks the shock hazard reaches beyond the bare cable segment's own cell, in every
	 * direction (proximity check, on top of the direct-contact shape). {@code 0} keeps the original
	 * direct-touch-only behaviour.
	 */
	public static double bareCableShockProximityRadius = 0.5;
	/**
	 * Multiplier applied to the matching bare cable's attenuation when the whole governing cable grade
	 * is insulated. {@code 0.5} makes rubber insulation halve loss without changing tier, packet cap or
	 * segment throughput (MOD-259). Keeping this as one live knob preserves the exact relationship for
	 * tin and copper instead of duplicating two independently drifting rates.
	 */
	public static double insulationLossMultiplier = 0.5;

	// --- Insulating stands under bare cable, see core.energy.ShockGuardMaterial (MOD-279) ---
	/**
	 * Probability (0..1) that a shock still lands on a player standing <b>on top of</b> a wood-stood
	 * segment. These chances only govern the from-above case: a stand blocks the side/below hazard
	 * outright, so they are deliberately mild — the stand's main value is that it makes a cable run
	 * safe to walk <em>past</em>, not safe to walk <em>on</em>. {@code 1.0} removes the from-above
	 * benefit entirely, {@code 0.0} makes the stand as good as rubber insulation.
	 */
	public static double shockGuardWoodHitChance = 0.7;
	/** Probability (0..1) a shock still lands from above through a <b>wool</b> stand — the weakest of the three. */
	public static double shockGuardWoolHitChance = 0.9;
	/** Probability (0..1) a shock still lands from above through a <b>glass</b> stand — the strongest of the three. */
	public static double shockGuardGlassHitChance = 0.5;
	/**
	 * Contact ticks a player is left alone for after a stand absorbs a shock. Without this the roll would
	 * repeat every tick the player stays in contact (20×/second), and even a strong stand would let a hit
	 * through almost immediately — the reduced chance would be per-tick rather than per-contact, which is
	 * not what a player reads it as. Matches vanilla's own post-hit invulnerability window
	 * ({@code LivingEntity.INVULNERABLE_DURATION}, 20 ticks), so a blocked shock and a landed one pace
	 * the same. That constant is {@code protected} and cannot be referenced from here, hence the literal.
	 */
	public static int shockGuardGraceTicks = 20;

	// --- Cable grades: tin (cheap/narrow) and gold (MV/wide), see core.energy.CableType (MOD-219) ---
	/**
	 * Per-segment buffer of a tin cable — and therefore its real throughput (MOD-070: a cable carries its
	 * buffer per tick). 8 EU/t is deliberately below copper's 12: tin is the cheap wire, narrower than
	 * copper but far cheaper to lose energy in. Comfortably above a solar farm's needs (one panel is
	 * {@link #solarEuPerTick} = 1 EU/t) and below a fuel generator's 8 EU/t burst, so the choice
	 * tin-vs-copper is a real one.
	 */
	public static int tinCableBuffer = 8;
	/**
	 * Per-tick ceiling on EU drawn from one source through a tin cable. Matches its buffer (8): unlike
	 * copper — whose LV tier voltage (32) sits well above its 12 EU buffer — tin is capped by design, so
	 * it cannot be used as a cheap stand-in for a full LV line. There is no sub-LV entry in
	 * {@link dev.alaindustrial.core.energy.EnergyTier}; tin is an LV cable with its own lower cap.
	 */
	public static int tinCablePacketCap = 8;
	/**
	 * Fraction of throughput attenuated per tin cable block traversed. {@code 0.006} (research §3) is ~3.3×
	 * gentler than copper's 0.02 — this is tin's whole point. A 1 EU/t solar trickle loses nothing at
	 * any distance because the attenuation model always preserves at least 1 EU of a positive packet;
	 * at larger flows tin also attenuates more slowly than copper, which remains the wider choice for a
	 * dense, high-flow line.
	 */
	public static double tinCableLossPerBlock = 0.006;
	/**
	 * Per-segment buffer of a gold cable — its real throughput. 48 EU/t is 4× copper's 12, mirroring the
	 * ×4 LV→MV voltage step, so the MV cable is felt as a genuinely wider pipe rather than a recoloured
	 * copper one. Note the "no battery from wires" ceiling is a copper-scale invariant
	 * ({@link #cableBuffer} × 1000 &lt; {@link #batteryBoxBuffer}); gold's cost (gold ingots) is what keeps
	 * a 1000-segment gold grid out of reach rather than the buffer size.
	 */
	public static int goldCableBuffer = 48;
	/**
	 * Fraction of throughput lost per gold cable block traversed. {@code 0.03} is deliberately WORSE than
	 * copper's 0.02 (research §3, IC2 canon): gold buys throughput (4× buffer, 128 EU/t packet cap) and
	 * pays for it in distance, making the choice "wide pipe up close" vs "thin pipe far away" instead of
	 * a strict upgrade. Its packet cap is the shared {@link #tierMvVoltage}, not a private knob.
	 */
	public static double goldCableLossPerBlock = 0.03;

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
	 * MOD-238: when {@code true}, oil blocks ignite from adjacent fire/soul fire/lava and the burn
	 * spreads across the pool (see {@code OilLiquidBlock}). Set {@code false} to make oil inert.
	 */
	public static boolean oilBurns = true;

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
			new IntField("windMillRotorMaxDamage", "Wind mill rotor max durability (bar). Applies at registration (restart); tune life via the EU-per-damage rate. Shared by all three wind mills.",
				() -> windMillRotorMaxDamage, v -> windMillRotorMaxDamage = v, 1),
			new IntField("windMillRotorEuPerDamage", "EU of production per 1 durability point of the wind mill rotor (life = maxDamage × this). Read live every tick.",
				() -> windMillRotorEuPerDamage, v -> windMillRotorEuPerDamage = v, 1),
			new FloatField("windMillStormWearFactor", "Extra rotor wear multiplier while running in rain/thunder (1.0 = off). Applies to all three wind mills.",
				() -> windMillStormWearFactor, v -> windMillStormWearFactor = v, 1.0f),
			new IntField("waterMillWheelMaxDamage", "Water mill wheel max durability (bar). Applies at registration (restart); tune life via the EU-per-damage rate.",
				() -> waterMillWheelMaxDamage, v -> waterMillWheelMaxDamage = v, 1),
			new IntField("waterMillWheelEuPerDamage", "EU of production per 1 durability point of the water mill wheel (life = maxDamage × this). Read live every tick.",
				() -> waterMillWheelEuPerDamage, v -> waterMillWheelEuPerDamage = v, 1),
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
			new IntField("machineBuffer", "Shared EU buffer for ordinary LV processing machines: electric furnace, compressor, extractor, sawmill, polymerizer and vulcanizer. Applies to newly placed blocks.",
				() -> machineBuffer, v -> machineBuffer = v, 1),
			new IntField("electricHeaterBuffer", "Electric Heater EU buffer. Applies to newly placed blocks.",
				() -> electricHeaterBuffer, v -> electricHeaterBuffer = v, 1),
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
			new IntField("fluidPipeSegmentBuffer", "Per-segment fluid buffer in mB — also the segment's throughput, since fluid flows through the buffer one hop per tick (MOD-151). Applies to newly placed pipes.",
				() -> fluidPipeSegmentBuffer, v -> fluidPipeSegmentBuffer = v, 1),
			new IntField("fluidNetworksPerTick", "Fluid networks processed per server tick; the rest round-robin to later ticks.",
				() -> fluidNetworksPerTick, v -> fluidNetworksPerTick = v, 1),
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
			new IntField("fluxweaveBuffer", "EU buffer of each Fluxweave armour piece.",
				() -> fluxweaveBuffer, v -> fluxweaveBuffer = v, 1),
			new IntField("fluxweaveInputRate", "Max EU/t a Fluxweave piece accepts while charging in a slot.",
				() -> fluxweaveInputRate, v -> fluxweaveInputRate = v, 1),
			new IntField("fluxweaveUpkeepEuPerSecond", "EU/second a charged, worn Fluxweave piece burns to keep its bonuses on.",
				() -> fluxweaveUpkeepEuPerSecond, v -> fluxweaveUpkeepEuPerSecond = v, 0),
			new IntField("fluxweaveFallDamageReductionPercent", "Percent of fall damage Fluxweave boots absorb while charged (clamped to 90 in code).",
				() -> fluxweaveFallDamageReductionPercent, v -> fluxweaveFallDamageReductionPercent = v, 0),
			new IntField("fluxweaveRunSpeedPercent", "Percent added to run speed by charged Fluxweave leggings.",
				() -> fluxweaveRunSpeedPercent, v -> fluxweaveRunSpeedPercent = v, 0),
			new IntField("fluxweaveOxygenBonus", "OXYGEN_BONUS levels granted by a charged Fluxweave helmet.",
				() -> fluxweaveOxygenBonus, v -> fluxweaveOxygenBonus = v, 0),
			new IntField("fluxweaveSwimEfficiency", "Percent of water movement efficiency granted by a charged Fluxweave helmet.",
				() -> fluxweaveSwimEfficiency, v -> fluxweaveSwimEfficiency = v, 0),
			new IntField("fluxweaveChargedToughness", "Extra armour toughness on a charged Fluxweave chestplate.",
				() -> fluxweaveChargedToughness, v -> fluxweaveChargedToughness = v, 0),
			new IntField("fluxweaveKnockbackResistance", "Percent of knockback resisted by a charged Fluxweave chestplate.",
				() -> fluxweaveKnockbackResistance, v -> fluxweaveKnockbackResistance = v, 0),
			new IntField("fluxweaveStepHeightBonus", "Extra step height (hundredths of a block) from charged Fluxweave leggings with the assist toggled on.",
				() -> fluxweaveStepHeightBonus, v -> fluxweaveStepHeightBonus = v, 0),
			new IntField("fluxweaveRegenEuPerHeal", "EU the Fluxweave helmet spends per half-heart healed by the 4/4 set bonus.",
				() -> fluxweaveRegenEuPerHeal, v -> fluxweaveRegenEuPerHeal = v, 1),
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
			new DoubleField("scytheBonusSeedMultiplier", "Global multiplier on the scythe's per-tier bonus-seed chance (1.0 = shipped ladder, 0.0 = mechanic off; a tier is clamped to 1.0).",
				() -> scytheBonusSeedMultiplier, v -> scytheBonusSeedMultiplier = v, 0.0, 0.0),
			new IntField("machineEuPerTick", "Base EU/t a processing machine draws while running (energy per operation = this x its duration).",
				() -> machineEuPerTick, v -> machineEuPerTick = v, 1),
			new IntField("maceratorDuration", "Ticks a macerator takes per operation at 1.0 speed.",
				() -> maceratorDuration, v -> maceratorDuration = v, 1),
			new IntField("incubatorEuPerTick", "EU/t the incubator draws while running (4x the machine standard).",
				() -> incubatorEuPerTick, v -> incubatorEuPerTick = v, 1),
			new IntField("incubatorBuffer", "Incubator internal EU buffer.",
				() -> incubatorBuffer, v -> incubatorBuffer = v, 1),
			new IntField("mutationDurationTransform", "Ticks an incubator transform attempt takes at 1.0 speed.",
				() -> mutationDurationTransform, v -> mutationDurationTransform = v, 1),
			new IntField("mutationDurationDuplicate", "Ticks an incubator duplicate attempt takes at 1.0 speed.",
				() -> mutationDurationDuplicate, v -> mutationDurationDuplicate = v, 1),
			new IntField("mutationDurationCreate", "Ticks an incubator create attempt takes at 1.0 speed.",
				() -> mutationDurationCreate, v -> mutationDurationCreate = v, 1),
			new IntField("mutationAttemptsPerIngot", "Mutation attempts one uranium ingot powers before it burns to ash.",
				() -> mutationAttemptsPerIngot, v -> mutationAttemptsPerIngot = v, 1),
			new DoubleField("mutationChanceTransform", "Base success chance of a transform mutation (0..1).",
				() -> mutationChanceTransform, v -> mutationChanceTransform = v, 0.0, 0.0),
			new DoubleField("mutationChanceDuplicate", "Base success chance of a duplicate mutation (0..1).",
				() -> mutationChanceDuplicate, v -> mutationChanceDuplicate = v, 0.0, 0.0),
			new DoubleField("mutationChanceCreate", "Base success chance of a create mutation (0..1).",
				() -> mutationChanceCreate, v -> mutationChanceCreate = v, 0.0, 0.0),
			new DoubleField("mutationChanceCap", "Ceiling on the total mutation success chance (base + gene bonus).",
				() -> mutationChanceCap, v -> mutationChanceCap = v, 0.0, 0.0),
			new DoubleField("mutationSlagChance", "Share of attempts yielding irradiated slag instead of an empty miss.",
				() -> mutationSlagChance, v -> mutationSlagChance = v, 0.0, 0.0),
			new DoubleField("mutationGradeRare", "Share of successful mutations rolling the rare grade.",
				() -> mutationGradeRare, v -> mutationGradeRare = v, 0.0, 0.0),
			new DoubleField("mutationGradeEpic", "Share of successful mutations rolling the epic grade.",
				() -> mutationGradeEpic, v -> mutationGradeEpic = v, 0.0, 0.0),
			new DoubleField("mutationGradeLegendary", "Share of successful mutations rolling the legendary grade.",
				() -> mutationGradeLegendary, v -> mutationGradeLegendary = v, 0.0, 0.0),
			new IntField("gardenDroneBuffer", "Garden Drone station internal EU buffer.",
				() -> gardenDroneBuffer, v -> gardenDroneBuffer = v, 1),
			new IntField("gardenDroneEuPerAction", "EU the Garden Drone spends per completed action (till/plant/fertilize/harvest); idle costs nothing.",
				() -> gardenDroneEuPerAction, v -> gardenDroneEuPerAction = v, 1),
			new IntField("gardenDroneRange", "Garden Drone zone-scan radius in blocks around the station.",
				() -> gardenDroneRange, v -> gardenDroneRange = v, 1),
			new IntField("gardenDroneScanIntervalTicks", "Ticks between Garden Drone zone rebuilds after cache invalidation.",
				() -> gardenDroneScanIntervalTicks, v -> gardenDroneScanIntervalTicks = v, 1),
			new IntField("gardenDroneFlightTicksPerBlock", "Ticks the Garden Drone flies per block of distance before its action lands.",
				() -> gardenDroneFlightTicksPerBlock, v -> gardenDroneFlightTicksPerBlock = v, 0),
			// Minimum 1 on both: a 0 divisor would divide by zero inside the plant's random tick (MOD-169).
			new IntField("cottonRootingChanceDivisor", "Cotton trellis: 1-in-this chance of advancing one rooting stage per random tick (higher = longer initial growth).",
				() -> cottonRootingChanceDivisor, v -> cottonRootingChanceDivisor = v, 1),
			new IntField("cottonFruitingChanceDivisor", "Cotton trellis: 1-in-this chance of advancing one fruiting stage per random tick (the repeating harvest cycle).",
				() -> cottonFruitingChanceDivisor, v -> cottonFruitingChanceDivisor = v, 1),
			new IntField("electricFurnaceDuration", "Ticks an electric furnace takes per smelt at 1.0 speed.",
				() -> electricFurnaceDuration, v -> electricFurnaceDuration = v, 1),
			new IntField("compressorDuration", "Ticks a compressor takes per operation at 1.0 speed.",
				() -> compressorDuration, v -> compressorDuration = v, 1),
			new IntField("extractorDuration", "Ticks an extractor takes per operation at 1.0 speed.",
				() -> extractorDuration, v -> extractorDuration = v, 1),
			new IntField("sawmillDuration", "Ticks a sawmill takes per cut at 1.0 speed (all four modes).",
				() -> sawmillDuration, v -> sawmillDuration = v, 1),
			new IntField("polymerizerDuration", "Ticks a polymerizer takes to turn one bucket of oil into raw rubber at 1.0 speed.",
				() -> polymerizerDuration, v -> polymerizerDuration = v, 1),
			new IntField("vulcanizerDuration", "Fallback ticks a vulcanizer operation takes at 1.0 speed; shipped recipe energy 400 / machineEuPerTick 2 = 200.",
				() -> vulcanizerDuration, v -> vulcanizerDuration = v, 1),
			new IntField("galvanicBathDuration", "Fallback ticks a galvanic bath operation takes at 1.0 speed; shipped recipe energy 1000 / machineEuPerTick 2 = 500.",
				() -> galvanicBathDuration, v -> galvanicBathDuration = v, 1),
			new IntField("assemblerEuPerTick", "EU/tick the assembler draws while crafting (MOD-275). MV rate: six times an LV machine.",
				() -> assemblerEuPerTick, v -> assemblerEuPerTick = v, 1),
			new IntField("assemblerDuration", "Ticks one assembler craft takes at 1.0 speed (MOD-275). 40 = 2 seconds, the pace of the genre.",
				() -> assemblerDuration, v -> assemblerDuration = v, 1),
			new IntField("assemblerBuffer", "EU buffer of the assembler (MOD-275) — 25 operations at 480 EU each.",
				() -> assemblerBuffer, v -> assemblerBuffer = v, 1),
			new IntField("galvanicBathWaterPerOp", "mB of water a galvanic bath consumes per completed operation (not part of the recipe JSON).",
				() -> galvanicBathWaterPerOp, v -> galvanicBathWaterPerOp = v, 1),
			new IntField("electricHeaterEuPerTick", "EU/t an Electric Heater spends while the Vulcanizer directly above it advances; idle heater draws nothing.",
				() -> electricHeaterEuPerTick, v -> electricHeaterEuPerTick = v, 1),
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
			new DoubleField("copperCableLossPerBlock", "Fraction of throughput attenuated per copper cable block (0.02 = 2% of the remaining flow per block).",
				() -> copperCableLossPerBlock, v -> copperCableLossPerBlock = v, 0.0, 0.0),
			new BoolField("bareCableShockEnabled", "When true, energized bare cables damage players on direct contact and emit shock feedback. false disables the entire mechanic.",
				() -> bareCableShockEnabled, v -> bareCableShockEnabled = v),
			new FloatField("bareCableShockLvDamage", "Damage from direct contact with an energized bare LV cable, in half-hearts.",
				() -> bareCableShockLvDamage, v -> bareCableShockLvDamage = v, 0.0f),
			new FloatField("bareCableShockMvDamage", "Damage from direct contact with an energized bare MV cable, in half-hearts.",
				() -> bareCableShockMvDamage, v -> bareCableShockMvDamage = v, 0.0f),
			new DoubleField("bareCableShockProximityRadius", "Extra blocks the shock hazard reaches beyond a bare cable segment's own cell in every direction (0 = direct-touch only).",
				() -> bareCableShockProximityRadius, v -> bareCableShockProximityRadius = v, 0.0, 0.0),
			new DoubleField("insulationLossMultiplier", "Multiplier applied to bare-cable attenuation for rubber-insulated tin/copper cables (0.5 = half the loss; throughput and packet cap are unchanged).",
				() -> insulationLossMultiplier, v -> insulationLossMultiplier = v, 0.0, 0.0),
			new DoubleField("shockGuardWoodHitChance", "Chance (0..1) a shock still lands through a plank insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).",
				() -> shockGuardWoodHitChance, v -> shockGuardWoodHitChance = v, 0.0, 0.0),
			new DoubleField("shockGuardWoolHitChance", "Chance (0..1) a shock still lands through a wool insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).",
				() -> shockGuardWoolHitChance, v -> shockGuardWoolHitChance = v, 0.0, 0.0),
			new DoubleField("shockGuardGlassHitChance", "Chance (0..1) a shock still lands through a glass insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).",
				() -> shockGuardGlassHitChance, v -> shockGuardGlassHitChance = v, 0.0, 0.0),
			new IntField("shockGuardGraceTicks", "Contact ticks a player is spared after an insulating stand absorbs a shock, so the reduced chance is per contact rather than re-rolled every tick.",
				() -> shockGuardGraceTicks, v -> shockGuardGraceTicks = v, 0),
			new IntField("tinCableBuffer", "Per-segment working EU buffer of a tin cable = its real throughput (8 EU/t, narrower than copper's 12).",
				() -> tinCableBuffer, v -> tinCableBuffer = v, 1),
			new IntField("tinCablePacketCap", "Per-tick ceiling on EU drawn from one source through a tin cable (8 EU/t, below the LV tier voltage by design).",
				() -> tinCablePacketCap, v -> tinCablePacketCap = v, 1),
			new DoubleField("tinCableLossPerBlock", "Fraction of throughput attenuated per tin cable block (0.006 = 0.6% of the remaining flow per block; a 1 EU/t solar trickle floors to zero loss).",
				() -> tinCableLossPerBlock, v -> tinCableLossPerBlock = v, 0.0, 0.0),
			new IntField("goldCableBuffer", "Per-segment working EU buffer of a gold (MV) cable = its real throughput (48 EU/t, 4x copper).",
				() -> goldCableBuffer, v -> goldCableBuffer = v, 1),
			new DoubleField("goldCableLossPerBlock", "Fraction of throughput attenuated per gold cable block (0.03 = 3% of the remaining flow per block; worse than copper by design - gold buys throughput, not distance).",
				() -> goldCableLossPerBlock, v -> goldCableLossPerBlock = v, 0.0, 0.0),
			new IntField("networksPerTick", "Max awake energy networks processed per server tick; the rest are deferred round-robin.",
				() -> networksPerTick, v -> networksPerTick = v, 1),
			new IntField("networkAnalyzerMaxTraversedNetworks", "Cap on networks the Network Analyzer's Traverse mode walks (visualization only, never affects energy).",
				() -> networkAnalyzerMaxTraversedNetworks, v -> networkAnalyzerMaxTraversedNetworks = v, 1),
			new BoolField("bonusChestEnabled", "When true, mod starter items are injected into the vanilla bonus chest at world creation (vanilla loot kept). false = purely vanilla bonus chest.",
				() -> bonusChestEnabled, v -> bonusChestEnabled = v),
			new BoolField("oilBurns", "When true, oil ignites from adjacent fire or flint-and-steel and the burn spreads across the pool; lava alone does not ignite it. false = oil is inert.",
				() -> oilBurns, v -> oilBurns = v));

	/** Effective machine drain per tick after the speed multiplier (E_op stays ~constant). */
	public static int machineEuPerTickEffective() {
		return Math.max(1, Math.round(machineEuPerTick * globalMachineSpeedMultiplier));
	}

	/** Effective Electric Heater drain after the machine speed multiplier (heater E_op stays ~constant). */
	public static int electricHeaterEuPerTickEffective() {
		return Math.max(1, Math.round(electricHeaterEuPerTick * globalMachineSpeedMultiplier));
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
