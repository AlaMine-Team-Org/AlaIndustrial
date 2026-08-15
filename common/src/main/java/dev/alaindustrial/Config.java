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
 * <p>The Java API is a flat set of {@code public static} fields and stays that way — hundreds of
 * call sites, every gametest and every command read {@code Config.<field>} directly. Only the FILE
 * is structured: since MOD-402 it carries a {@link #SCHEMA_VERSION} and groups its keys into
 * {@link Section}s, and the load layer migrates an older file into the current shape before reading
 * it. Adding a knob therefore never needs a migration; changing the file's shape does.
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
	// --- Wind altitude profile (MOD-347) — shared by all three mills AND the Wind Gauge ---
	/**
	 * The cloud deck: the windiest height in the world. Wind climbs to here and collapses above it.
	 * 192 is the vanilla Overworld cloud layer, so the rule the player learns ("the best wind is just
	 * under the clouds, above them there is nothing") is something they can see.
	 */
	public static int windCloudY = 192;
	/** Height above which only {@link #windTraceFactor} remains — too little to turn any rotor. */
	public static int windDeadY = 248;
	/**
	 * Fraction of full strength reached at a branch's ridge ({@code seaLevel + maxBase × blocksPerBase},
	 * where that branch used to hit its cap before MOD-347). The remaining {@code 1 − this} is gained
	 * over an accelerating shoulder up to {@link #windCloudY}. Deliberately well under 1: the lower it
	 * is, the narrower the band of heights that yields full output, and the more the Wind Gauge is
	 * worth carrying. At 0.45 a T1 mill runs at its cap over eleven blocks instead of sixty-seven.
	 */
	public static float windRidgeFactor = 0.45f;
	/** Fraction of full strength left above {@link #windDeadY}: readable on the gauge, 0 EU/t for mills. */
	public static float windTraceFactor = 0.06f;
	/** Clear-weather wind speed in km/h at the cloud deck — the Wind Gauge's full-scale reading. */
	public static float windGaugePeakKmh = 62.5f;
	// --- Wind mill (LV) — needs open sky; base scales with height, boosted by weather ---
	/** Base EU/t at the cloud deck (MOD-347); the height profile scales this. 0 at/below sea level. */
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
	/**
	 * Weather multipliers for the high-altitude variant (MOD-345). Deliberately weaker than T1's
	 * ×1.5/×2: this branch's identity is a steady income that barely cares about the sky, so it wins in
	 * clear weather and concedes the storm to the storm branch. Before MOD-345 it borrowed T1's factors
	 * and, with twice T1's height growth, beat the storm mill in almost every cell of the table — two
	 * equally priced evolution chips with only one correct answer.
	 */
	public static float highAltWindMillRainFactor = 1.25f;
	/** @see #highAltWindMillRainFactor */
	public static float highAltWindMillThunderFactor = 1.5f;
	/**
	 * Hard cap on high-altitude wind-mill EU/t after the weather multiplier. Equals the reachable peak
	 * (base 8 × thunder 1.5), so the branch tops out at 12 and leaves the 16+ band to the storm mill.
	 */
	public static int highAltWindMillMaxEuPerTick = 12;
	// --- Storm wind mill (T2, LV) — boosted by weather ---
	/**
	 * Clear-sky height cap for the storm variant: same height step as T1 (16 blocks/+1), but raised above T1
	 * so the thunder multiplier actually pays off. The base stays low on purpose — this branch is not
	 * supposed to earn a living in clear weather.
	 */
	public static int stormWindMillMaxBaseEuPerTick = 6;
	/** Weather multiplier for the storm variant when it is raining (not thundering). */
	public static float stormWindMillRainFactor = 2.0f;
	/**
	 * Weather multiplier for the storm variant when it is thundering (MOD-345: ×3 → ×3.5). This is the
	 * branch's whole identity — the highest burst EU/t in the mod's LV tier, paid for by producing the
	 * least of the three mills when the sky is clear.
	 */
	public static float stormWindMillThunderFactor = 3.5f;
	/**
	 * Hard cap on storm wind-mill EU/t after the weather multiplier (MOD-345: 16 → 24). The old 16 clipped
	 * the thunder peak down to the high-altitude branch's ceiling, erasing the difference between the two
	 * chips. Headroom, not a target: the reachable peak is base 6 × 3.5 = 21.
	 */
	public static int stormWindMillMaxEuPerTick = 24;
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
	 * Output scale of the plain wooden rotor — the baseline of the MOD-385 ladder, hence 1.0. It is a
	 * field rather than a hardcoded constant so all three grades read the same way in
	 * {@link dev.alaindustrial.core.machine.ComponentTier}; raising it buffs every wind mill in the
	 * world, which is a legitimate server knob but NOT what the reinforced/advanced grades are for.
	 */
	public static float windMillRotorOutputMultiplier = 1.0f;
	/**
	 * Extra rotor-wear multiplier while a wind mill runs in rain or thunder — mechanical stress on top of
	 * the already-higher storm output. 1.0 disables the weather bonus. Applies to all three wind mills.
	 */
	public static float windMillStormWearFactor = 1.5f;
	// --- Reinforced / advanced rotor and wheel (MOD-385) — the second and third grades. ---
	/** Reinforced rotor durability: ×3 the wooden one. Registration-time like every max_damage. */
	public static int windMillRotorReinforcedMaxDamage = 3000;
	/**
	 * EU per durability point of the reinforced rotor. Scaled by the same ×1.25 as its output on
	 * purpose: wear is charged on EU produced, so leaving this at 480 would make the stronger rotor
	 * wear 25 % faster and quietly eat a quarter of the promised life gain.
	 */
	public static int windMillRotorReinforcedEuPerDamage = 600;
	/** Reinforced rotor output scale. Applied before the mill's cap, so it cannot raise the ceiling. */
	public static float windMillRotorReinforcedOutputMultiplier = 1.25f;
	/** Advanced rotor durability: ×6 the wooden one, ×2 the reinforced one. */
	public static int windMillRotorAdvancedMaxDamage = 6000;
	/** EU per durability point of the advanced rotor — ×1.5, matching its output scale. */
	public static int windMillRotorAdvancedEuPerDamage = 720;
	/** Advanced rotor output scale. Applied before the mill's cap, so it cannot raise the ceiling. */
	public static float windMillRotorAdvancedOutputMultiplier = 1.5f;
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
	/**
	 * Output scale of the plain wooden wheel — the baseline of the MOD-385 ladder, hence 1.0. Kept a
	 * field for the same reason as {@link #windMillRotorOutputMultiplier}: uniform grade definitions.
	 */
	public static float waterMillWheelOutputMultiplier = 1.0f;
	/** Reinforced wheel durability: ×3 the wooden one. */
	public static int waterMillWheelReinforcedMaxDamage = 3000;
	/** EU per durability point of the reinforced wheel — ×1.25, matching its output scale. */
	public static int waterMillWheelReinforcedEuPerDamage = 400;
	/**
	 * Reinforced wheel output scale. The water mill has no {@code *MaxEuPerTick} of its own — its
	 * ceiling is structural (4 wheel-swept cells × {@link #waterMillEuPerTick}), and even at the
	 * advanced grade the result stays far under the LV tier voltage and under a copper cable's
	 * throughput, so no cap needed to be introduced for MOD-385.
	 */
	public static float waterMillWheelReinforcedOutputMultiplier = 1.25f;
	/** Advanced wheel durability: ×6 the wooden one, ×2 the reinforced one. */
	public static int waterMillWheelAdvancedMaxDamage = 6000;
	/** EU per durability point of the advanced wheel — ×1.5, matching its output scale. */
	public static int waterMillWheelAdvancedEuPerDamage = 480;
	/** Advanced wheel output scale. */
	public static float waterMillWheelAdvancedOutputMultiplier = 1.5f;
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
	/**
	 * Reinforced Energy Storage (MV) EU buffer — five times the Battery Box (MOD-350/MOD-351). Its own
	 * knob rather than the tier default: {@code tierMvCapacity} stays the default for MV
	 * <em>machines</em>, and a store is deliberately larger than one machine's buffer (the LV pair sits
	 * at the same ratio, 20 000 against 10 000). The transfer rate is NOT a knob here — it comes from
	 * {@code EnergyTier.MV.maxVoltage()} so retuning {@code tierMvVoltage} moves the store with the tier.
	 */
	public static int cesuBuffer = 100_000;

	/**
	 * EU/tick a store hands to a NON-CASCADE sink over cable (MOD-353) — the Teleporter and the Charging
	 * Station today. 0 disables the channel.
	 *
	 * <p>Named for the mechanism, not for the Teleporter, because the defect it fixes is a class: any
	 * block that is an {@code isEnergyStorageSink} but outside the cascade was unreachable from a store
	 * over cable. 12 is the copper cable's throughput, so the store feeds a sink no faster than the wire
	 * to it would carry anyway, and it is the number already printed in the Teleporter spec.
	 */
	public static int storageFeedRate = 12;

	/**
	 * Share of its capacity a donor store keeps for itself on that channel, 0..1 (MOD-353).
	 *
	 * <p>This is the guarantee that replaces "the fund is simply excluded from the cascade" (MOD-314 R3):
	 * below this line the channel is shut, so a Teleporter can never drain the base's bank. At 0.5 the
	 * player always keeps half their stored EU for machines. Setting it to 0 restores drain-dry
	 * behaviour and is NOT recommended — it makes this channel a slow version of the MOD-314 bug.
	 */
	public static double storageFeedReserveFraction = 0.5;
	public static int maceratorBuffer = 800;
	/** Shared buffer for ordinary LV processing machines: electric furnace, compressor, extractor,
	 * sawmill, polymerizer and vulcanizer. */
	public static int machineBuffer = 800;
	/** Electric Heater EU buffer. At the default 6 EU/t it holds one cold start (1200 EU) plus one
	 * complete 200-tick vulcanization (1200 EU) and smooths a thin LV supply without becoming bulk
	 * storage — a fully charged heater can light itself and serve one batch with the cable cut. */
	public static int electricHeaterBuffer = 2400;
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

	// --- Battery (MOD-083, the stackable EU carrier) ---
	/** EU one battery holds. Deliberately the same size as the pouch (a pouch's whole charge fits in one
	 * battery), so the battery reads as "the first EU you can carry" and does not compete with the
	 * 20 000 EU Energy Pack. Charge is stored PER ITEM: a full stack of 16 carries 32 000 EU. */
	public static int batteryBuffer = 2000;
	/** Max EU/tick one battery accepts in a charge slot. At the LV ceiling (32) a whole stack of 16
	 * still divides evenly — 2 EU per item per tick — which is what keeps stack charging exact. */
	public static int batteryInputRate = 32;
	/** EU one right-click hands from the battery to the item in the other hand. A full battery empties
	 * into a tool in four clicks, so a manual top-up stays a deliberate act rather than a reflex. */
	public static int batteryTransferPerUse = 500;

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

	// --- Charging Station (MOD-274, the pad the player stands on) ---
	/**
	 * Charging Station EU buffer — a Battery Box's worth ({@link #batteryBoxBuffer}), and the single
	 * number that makes the station feel instant.
	 *
	 * <p>This is the one machine in the mod whose buffer is not sized to "one operation": it is sized to
	 * a <em>visit</em>. The station is a capacitor. It fills slowly from the grid while nobody is on it
	 * and empties fast into whoever steps up, which is the only way {@link #chargePadOutputRate} can ever
	 * be reached — the grid itself cannot deliver that much (see the rate's own note). Cut this to a
	 * machine-sized 800 and the station still works, but hands out its whole buffer in six ticks and
	 * then crawls at whatever the cable feeds it, which is exactly the "stand here for three minutes"
	 * experience the block exists to remove.
	 */
	public static int chargePadBuffer = 20_000;
	/**
	 * Max EU/tick the station accepts from the grid. Set to the MV ceiling ({@link #tierMvVoltage}) on
	 * purpose, even though the block itself is LV: it is a ceiling, not a promise, and nothing in the
	 * mod can currently reach it. What actually arrives is set by the supply — 12 EU/t through a copper
	 * cable ({@link #cableBuffer}), 48 through a gold one ({@link #goldCableBuffer}), 32 from a Battery
	 * Box flush against it ({@code DirectAdjacencyDistributor} caps at the SOURCE's tier voltage). So
	 * the station simply scales with whatever grid the player has built instead of capping their good
	 * infrastructure at LV, and the MV supply it is ready for costs gold to lay.
	 */
	public static int chargePadInputRate = 128;
	/**
	 * Max EU/tick the station hands to the items on the player standing on it, drawn from its buffer.
	 * Every target is still clamped by its own {@link dev.alaindustrial.item.energy.ItemEnergy#inputRate}
	 * (32 for every powered item in the mod), so this is the station's total per-tick output shared
	 * across everything the player carries, not a per-item rate — a full set drains the buffer in
	 * roughly eight seconds and then continues at the speed of the incoming supply.
	 */
	public static int chargePadOutputRate = 128;

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

	// --- Electric Chainsaw (MOD-337, the drill's wood-side counterpart) ---
	/** Electric Chainsaw EU buffer — the same reservoir as the drill, so the two tools of the LV hand-tool
	 * line charge and last alike. At {@link #electricChainsawEuPerBlock} per block this is ~333 logs on a
	 * full charge. */
	public static int electricChainsawBuffer = 10_000;
	/** EU drained per block the chainsaw successfully cuts while it has at least this much charge. Below it
	 * the chainsaw still cuts (and drops), but at hand speed and free — see ElectricChainsawItem. Cheaper
	 * than the drill's 50 because wood is softer than stone, and well under the LV machine floor
	 * (200 EU/op). */
	public static int electricChainsawEuPerBlock = 30;
	/** Max EU/tick the chainsaw accepts while sitting in a charge slot — the LV ceiling, like the drill. */
	public static int electricChainsawInputRate = 32;

	// --- Electric Shovel (MOD-338, the earth-side member of the same hand-tool line) ---
	/** Electric Shovel EU buffer — the same reservoir as the drill and the chainsaw, so the whole LV
	 * hand-tool line charges and lasts alike. At {@link #electricShovelEuPerBlock} per block this is
	 * ~500 blocks of dirt on a full charge. */
	public static int electricShovelBuffer = 10_000;
	/** EU drained per block the shovel successfully digs while it has at least this much charge. Below it
	 * the shovel still digs (and drops), but at hand speed and free — see ElectricShovelItem. Cheaper
	 * than the chainsaw's 30 because loose earth is softer than wood, and far under the LV machine floor
	 * (200 EU/op). */
	public static int electricShovelEuPerBlock = 20;
	/** Max EU/tick the shovel accepts while sitting in a charge slot — the LV ceiling, like its siblings. */
	public static int electricShovelInputRate = 32;

	// --- Electric Hoe (MOD-342, the farming member of the same hand-tool line) ---
	/** Electric Hoe EU buffer — the same reservoir as the rest of the line, so all four powered hand
	 * tools charge and last alike. */
	public static int electricHoeBuffer = 10_000;
	/** EU drained per block the hoe successfully breaks while it has at least this much charge. Below it
	 * the hoe still breaks (and drops), but at hand speed and free — see ElectricHoeItem. Set to the
	 * drill's 50 by customer decision: the hoe's block set ({@code #minecraft:mineable/hoe} — hay,
	 * leaves, sponge, moss, nether wart block) is small, so a cheap rate would make the tool spend
	 * nothing at all. Tilling is billed separately, by {@link #electricHoeTillEuCost}. */
	public static int electricHoeEuPerBlock = 50;
	/** EU the hoe spends per successful right-click conversion (tilling soil, coarse dirt → dirt, …).
	 * Unlike the shovel's free dirt paths, tilling is powered: it is the hoe's whole job, and if it were
	 * free the tool would never spend a single EU in normal play. Below this the hoe tills nothing and
	 * says so, mirroring the drill's torch (`electricDrillTorchEuCost`). */
	public static int electricHoeTillEuCost = 50;
	/** Max EU/tick the hoe accepts while sitting in a charge slot — the LV ceiling, like its siblings. */
	public static int electricHoeInputRate = 32;

	// --- Electric Saber (MOD-149, the line's first weapon) ---
	/** Electric Saber EU buffer — the same reservoir as the four hand tools, so the whole LV line
	 * charges and lasts alike. At {@link #electricSaberEuPerHit} per hit this is 100 powered swings. */
	public static int electricSaberBuffer = 10_000;
	/** EU drained per hit on a living target while the saber is charged and switched on. Below it the
	 * saber still hits, but as a plain sword and for free — see ElectricSaberItem. Twice the drill's
	 * per-block 50 and half the LV machine floor (200 EU/op): a swing that outdamages every vanilla
	 * sword should cost more than breaking a block. */
	public static int electricSaberEuPerHit = 100;
	/** Max EU/tick the saber accepts while sitting in a charge slot — the LV ceiling, like its siblings. */
	public static int electricSaberInputRate = 32;
	/** Seconds of Slowness II the electric discharge leaves on a target struck by a live saber. Short on
	 * purpose: two seconds read as a jolt, and the same number lands on players in PvP. 0 disables the
	 * effect entirely, leaving the saber a pure damage weapon. */
	public static int electricSaberShockSeconds = 2;

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
	/** Ticks the canning machine spends pressing one ration; × machineEuPerTick = 200 EU per ration. */
	public static int canningMachineDuration = 100;
	/**
	 * Food value, in tenths of a point, that one ration costs (MOD-383). Must stay above the ration's
	 * own value of 96 — that gap is the processing loss, and it is the only thing standing between
	 * this machine and a food duplicator.
	 */
	public static int canningFoodValuePerCan = 120;
	/** Distillation Column (MOD-251): fallback ticks per distillation at 1.0 speed. The shipped recipe
	 * costs 400 EU, so at the ordinary-machine rate of 2 EU/t one run takes 200 ticks — deliberately
	 * the Polymerizer's exact tier: both turn one bucket of pumped crude into product. */
	public static int distillationColumnDuration = 200;
	/** Distillation Column (MOD-251): ticks a cold column heats before it can distil. It draws the
	 * ordinary machine rate while heating, so one cold start costs ~warmup × machineEuPerTick EU
	 * (~400 EU at defaults — one distillation's worth). Cooling runs at half this rate. */
	public static int distillationColumnWarmupTicks = 200;
	/** Galvanic Bath (MOD-127): fallback ticks per operation at 1.0 speed. The shipped recipe costs
	 * 1000 EU, so at the ordinary-machine rate of 2 EU/t the operation takes 500 ticks (25 s) — by far
	 * the slowest of the LV processing family, because plating silver onto fibre is the gate into the
	 * Fluxweave line and its armour. */
	public static int galvanicBathDuration = 500;

	// --- Overclocker chip (MOD-392): the per-chip speed/energy trade, applied per machine. ---
	/**
	 * Duration multiplier per installed overclocker chip: each chip shortens one operation to 80 % of
	 * its previous length. Deliberately paired with a HARSHER {@link #overclockerEuFactor}, so the
	 * chip is not a re-skin of {@link #globalMachineSpeedMultiplier} — that knob is energy-neutral by
	 * design (EU/t up, duration down, E_op unchanged), whereas a chip must make speed genuinely
	 * expensive: 0.8 × 2.0 means every chip raises the energy PER OPERATION by 60 %.
	 */
	public static float overclockerSpeedFactor = 0.8f;
	/**
	 * EU/t multiplier per installed overclocker chip — each chip doubles the draw. Together with
	 * {@link #overclockerSpeedFactor} one chip buys 1.25× speed for 1.6× the energy per operation, and
	 * four chips buy 2.46× speed for 6.55×. Doubling is also what makes the tier ceiling bite at a
	 * round number: a 2 EU/t machine reaches exactly {@code tierLvVoltage} (32) on its fourth chip.
	 */
	public static float overclockerEuFactor = 2.0f;
	/**
	 * Highest overclocker tier that exists — three chips are crafted (I, II, III), so three steps.
	 * On top of this sits the per-machine tier cap, and that is usually the one that bites: a machine
	 * may run no more steps than its voltage tier can actually feed
	 * ({@code base × euFactor^n ≤ tier.maxVoltage()}), so an 8 EU/t LV machine stops at II even holding
	 * a tier-III chip, while a 2 EU/t one runs all three.
	 */
	public static int overclockerMaxPerMachine = 3;

	// --- Energy condenser (MOD-393): surplus grid power banked, then packed into an item. ---
	/**
	 * Ceiling of the condenser's bank. Equals the tier-III threshold on purpose: past it there is
	 * nothing left to reach, so the block stops drawing instead of hoarding energy no one can spend.
	 */
	public static int condenserCapacity = 4_000_000;
	/**
	 * EU/t the condenser will accept — one MV packet ({@link #tierMvVoltage}), so a farm of ~32 basic
	 * solar panels saturates exactly one condenser and the ratio is something a player can eyeball.
	 *
	 * <p>It was copper scale (12) at first, on the belief that a narrow intake was the only thing
	 * keeping the condenser from outrunning a machine sitting further from the generator. That was
	 * simply wrong: {@code EnergyNetwork#tick} serves the two consumer classes in two separate passes,
	 * machines first, so a machine drinks from the line before any storage sink is offered a drop. The
	 * intake never was the guard — it was only a throttle, and at 12 EU/t it throttled the mechanic
	 * itself: an 85-panel farm producing 1580 EU/t still banked 12, so building more generation — the
	 * one thing this block is supposed to reward — changed nothing at all.
	 *
	 * <p>Scaling therefore belongs to the BANK of condensers, not to a hidden cap inside one: absorbing
	 * a bigger surplus means placing more of them.
	 */
	public static int condenserInputRate = 128;
	/** Banked EU at which the condenser can yield a tier-I clot; below this its output stays empty. */
	public static int clotThresholdI = 250_000;
	/** Banked EU for a tier-II clot — four times tier I. */
	public static int clotThresholdII = 1_000_000;
	/** Banked EU for a tier-III clot — four times tier II, and the bank's ceiling. */
	public static int clotThresholdIII = 4_000_000;

	// --- MOD-064 alloy smelter. Its own rate, like the incubator and the assembler: melting several
	// metals into one is a hotter job than milling a single ore. 8 EU/t x 150 ticks = 1200 EU per
	// operation (7.5 s) for every alloy — one price across the family, so the four alloys differ by what
	// they consume and yield, not by what the machine charges. 8 EU/t is exactly a coal generator's
	// output and 2/3 of a copper cable's throughput, so one smelter occupies a starter line by itself.
	public static int alloySmelterEuPerTick = 8;
	public static int alloySmelterDuration = 150;

	// --- MOD-384 component repair bench. Restores a worn rotor/wheel instead of replacing it, at the
	// price of a permanently lower durability ceiling. Its own rate, like the alloy smelter above: at the
	// shared 2 EU/t a T3 repair would run 9000 ticks (7.5 minutes) and read as broken rather than
	// expensive. At 8 EU/t the three grades take 625 / 1250 / 2250 ticks (~31 / 62 / 112 s).
	/** EU/t the repair bench draws while repairing. Four times the machine standard. */
	public static int repairBenchEuPerTick = 8;
	/**
	 * EU one repair of a T1 component costs (plain {@code windmill_rotor} / {@code water_mill_wheel}).
	 * The material side is deliberately cheap — ONE plate — so energy, not metal, is what a repair
	 * really spends; see {@code docs/PERFORMANCE.md} for the full economy.
	 */
	public static int repairBenchTier1EuCost = 5000;
	/** EU one repair of a reinforced (T2) component costs — double T1, matching its dearer material. */
	public static int repairBenchTier2EuCost = 10000;
	/** EU one repair of an advanced (T3) component costs. */
	public static int repairBenchTier3EuCost = 18000;
	/**
	 * How much of the component's ORIGINAL durability ceiling one repair burns, in percent. Linear in
	 * the original, not compounding on the current value: at 20 % that is 1000 → 800 → 600 → 400 → 200.
	 * Integer percent keeps it exact for every grade (see {@code core.machine.ComponentRepair}).
	 *
	 * <p><b>This single knob also sets how many repairs a component gets</b> — four here, because a
	 * fifth would leave nothing to restore. There is deliberately no separate count: a second knob
	 * could disagree with this one, and a bench refusing a part that still had most of its ceiling
	 * read as an arbitrary rule rather than as wear. 0 disables the decay, which also makes repairs
	 * unlimited.
	 */
	public static int repairBenchMaxDamageDecayPercent = 20;

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
	/**
	 * Electric Heater (MOD-258): EU/t it spends, both while warming up and while a Vulcanizer directly
	 * above it advances. Three times the ordinary machine tariff on purpose — heat is the block's whole
	 * product, and at the old 2 EU/t it was cheaper than the machine it served, which read as free.
	 *
	 * <p>The pair therefore draws {@code 6 + 2 = 8 EU/t}, still inside a copper cable's 12; the first
	 * overclocker chip is what pushes it onto gold, which is the intended progression rather than an
	 * accident.
	 */
	public static int electricHeaterEuPerTick = 6;
	/**
	 * Electric Heater (MOD-418): ticks a cold heater spends warming before it is a heat source at all.
	 *
	 * <p>Until it is hot it supplies NOTHING, so the machine above simply waits — the heater is a stove
	 * being lit, not a dial. Warming is its own idle draw at {@link #electricHeaterEuPerTick}; the block
	 * still costs nothing when there is no work waiting on it, because it only warms while a machine
	 * above is actually blocked on heat. Cooling runs at half this rate, so a brief pause in the input
	 * feed costs a slice of the ramp rather than all of it.
	 *
	 * <p>200 keeps the arithmetic memorable: warming costs 1200 EU, exactly what one vulcanization costs,
	 * so {@link #electricHeaterBuffer} is one cold start plus one batch.
	 */
	public static int electricHeaterWarmupTicks = 200;

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

	// --- Mob Repeller (MOD-278): tiered guard field that expels hostile mobs for EU. ---
	/** Zone radius in blocks around the LV block (a cube, matching the highlight dome). */
	public static int mobRepellerRange = 8;
	/** Zone radius of the MV tier. */
	public static int mobRepellerRangeMv = 16;
	/** Zone radius of the HV tier. */
	public static int mobRepellerRangeHv = 24;
	/**
	 * Field upkeep in EU/t while enabled — a constant drain, NOT a per-expulsion tariff. The field is
	 * the useful work (a base stays clean whether or not a mob wanders in tonight), so unlike the
	 * demand-driven heater this block pays around the clock; at 0 EU the field goes dark.
	 *
	 * <p>8 EU/t on the LV tier is four times an ordinary processing machine ({@link #machineEuPerTick}
	 * = 2) and eight times a bare solar panel: protection is meant to cost real generation — a fuel
	 * generator's whole output, or eight panels — rather than being a torch substitute you bolt on and
	 * forget. Retuned upward after the first playtest, where the original 2 EU/t read as free.
	 */
	public static int mobRepellerEuPerTick = 8;
	/** MV field upkeep — ×4 of LV, the same step the voltage ladder takes. */
	public static int mobRepellerEuPerTickMv = 32;
	/**
	 * HV field upkeep — ×2 of MV rather than ×4. The top tier already demands an HV line and 32 000 EU
	 * of buffer; another fourfold jump (128 EU/t) would put it past a geothermal pair for a radius
	 * that is only half again as wide, and the tier would stop being worth reaching.
	 */
	public static int mobRepellerEuPerTickHv = 64;
	/** Internal EU buffer of the LV block. */
	public static int mobRepellerBuffer = 2000;
	/** Internal EU buffer of the MV tier. */
	public static int mobRepellerBufferMv = 8000;
	/** Internal EU buffer of the HV tier. */
	public static int mobRepellerBufferHv = 32000;
	/** Ticks between zone sweeps. Ten (half a second) keeps the boundary dance tight without paying
	 * the entity scan every tick; the upkeep drain still applies every tick regardless. */
	public static int mobRepellerScanIntervalTicks = 10;
	/** Personal hostile kills a Soul Vessel must hold to evolve the LV block into the MV tier. */
	public static int mobRepellerEvolveKillsMv = 80;
	/** Personal hostile kills to evolve the MV block into the HV tier; also the vessel's hard cap. */
	public static int mobRepellerEvolveKillsHv = 250;

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
	 * Contact damage from an energized bare HV (electrum) cable, in half-hearts. Continues the
	 * 2 → 6 → 10 ladder (MOD-358). HV shared the MV number until the electrum cable existed, because
	 * no HV cable did; the teleporter station is an HV consumer but not a cable, so it never touched
	 * this path.
	 */
	public static float bareCableShockHvDamage = 10.0f;
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

	// --- Cable grades: tin (cheap/narrow), gold (MV/wide) and electrum (HV/widest),
	// see core.energy.CableType (MOD-219, MOD-358) ---
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
	/**
	 * Per-segment buffer of an electrum cable — its real throughput. 192 EU/t is 4× gold's 48, the same
	 * ×4 step every previous rung of the ladder takes, so HV is the next rung rather than a leap. Its
	 * packet cap is the shared {@link #tierHvVoltage} (512), not a private knob — same arrangement as
	 * copper and gold; only tin owns a cap below its tier.
	 *
	 * <p><b>The "no battery from wires" invariant does not scale here</b> and is not meant to: that
	 * ceiling ({@link #cableBuffer} × 1000 &lt; {@link #batteryBoxBuffer}) is a copper-scale rule about
	 * the wire players run by the thousand. A 1000-segment electrum grid would bank 192 000 EU, but it
	 * costs ~2 667 electrum ingots and ~1 333 diamonds to build — the craft, not the buffer, is what
	 * keeps it out of reach. Gold already sits the same way (48 000 EU per 1000 segments).
	 */
	public static int electrumCableBuffer = 192;
	/**
	 * Fraction of throughput lost per electrum cable block traversed. {@code 0.005} is the lowest in the
	 * mod — below even tin's 0.006 — and is a deliberate exception to the "wider pipe pays in distance"
	 * rule that gold follows (research §3): electrum beats every other grade on every axis at once, and
	 * pays for it purely in craft cost. Halved again by {@link #insulationLossMultiplier} on the
	 * insulated variant, giving 0.0025.
	 */
	public static double electrumCableLossPerBlock = 0.005;

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

	// ---------------------------------------------------------------------------------------------
	// MOD-402: file layout — schema version, thematic sections and the migration ladder.
	// NOTE: none of this touches the Java API above. Every consumer keeps reading `Config.<field>`;
	// only the shape of `config/alaindustrial.json` and the load/save layer changed.
	// ---------------------------------------------------------------------------------------------

	/**
	 * Layout version of {@code config/alaindustrial.json}, written into the file as
	 * {@code schemaVersion} and bumped whenever the file's SHAPE or the MEANING of a key changes —
	 * never for adding a knob (a new key is absent-safe and needs no migration).
	 *
	 * <p><b>How to bump it</b> (the whole recipe, deliberately one entry):
	 * <ol>
	 *   <li>Write a {@code private static void migrateVNtoVN1(JsonObject file)} that rewrites the
	 *       document IN PLACE from version {@code N} to {@code N+1} — rename a key, rescale a value,
	 *       move a knob between sections. It receives the document as it stands after every earlier
	 *       step, so each migration only has to know about its own hop.</li>
	 *   <li>Append {@code new Migration(N, Config::migrateVNtoVN1)} to {@link #MIGRATIONS}.</li>
	 *   <li>Raise this constant to {@code N+1}.</li>
	 * </ol>
	 * {@code ConfigSchemaTest} pins the ladder: {@code MIGRATIONS} must hold exactly one step per
	 * version hop, in ascending order, so a bump without a migration (or a migration without a bump)
	 * fails the build rather than silently skipping a file.
	 *
	 * <p>An OLDER file is migrated step by step and then rewritten in the current shape. A NEWER file
	 * is refused outright — see {@link #loadFrom}.
	 */
	static final int SCHEMA_VERSION = 1;

	/** Json key holding {@link #SCHEMA_VERSION}. Absent = a pre-MOD-402 flat file, i.e. version 0. */
	private static final String SCHEMA_VERSION_KEY = "schemaVersion";

	/** Inline doc written above {@link #SCHEMA_VERSION_KEY}, same {@code _comment_} idiom as a field. */
	private static final String SCHEMA_VERSION_DOC =
			"Layout version of this file. Written by the mod - do not raise it by hand. An older file is"
					+ " migrated automatically on load; a file from a NEWER mod version is refused (the"
					+ " server logs a warning and runs on built-in defaults instead of guessing).";

	/**
	 * Thematic group a tunable belongs to — and, on disk, the JSON object its key lives inside.
	 *
	 * <p>The section is declared ON THE FIELD's own {@code FIELDS} entry, right next to its doc string.
	 * That is the point: a second "key → section" table would be a parallel list to keep in sync by
	 * hand, which is the exact drift class this repo has already had to build gates against.
	 *
	 * <p>Declaration order below is the order the sections render in the file.
	 */
	enum Section {
		GLOBAL("global", "Server-wide multipliers applied on top of everything else."),
		GENERATORS("generators", "Generator output and the environment that scales it (sky, height, weather),"
				+ " plus rotor/wheel consumables and generator EU buffers."),
		MACHINES("machines", "Processing machines: EU/t, operation durations, internal buffers, overclocker"
				+ " chips, the incubator mutation table, the condenser and the drone station."),
		STORAGE("storage", "EU stores: battery box, reinforced storage, charging station, and the channel"
				+ " that feeds a non-cascade sink from a store."),
		// NB: keep equals signs and apostrophes OUT of a section doc. Gson html-escapes both, so they
		// land in the written file as bare unicode escapes and read as noise to the operator. The older
		// field docs already carry that scar; new text does not have to.
		CABLES("cables", "Cable grades: per-segment buffer (which is also the segment throughput), packet"
				+ " caps and per-block loss."),
		SAFETY("safety", "Bare-cable shock hazard and the insulating stands that soften it."),
		NETWORK("network", "Voltage tiers, buffer capacities per tier, and per-tick network budgets."),
		TOOLS("tools", "Powered items the player carries or wears: buffers, charge rates and running costs."),
		LOGISTICS("logistics", "Moving things around: item and fluid pipes, the pump, portable tanks,"
				+ " the teleporter and the stock display frame."),
		PLAYER("player", "Mod XP and the player profile curve."),
		WORLD("world", "World behaviour: bonus chest injection, burning oil, crop growth.");

		/** Json key of the section object. */
		final String id;
		/** One-line note written as {@code _comment_<id>} above the section. */
		final String doc;

		Section(String id, String doc) {
			this.id = id;
			this.doc = doc;
		}
	}

	/**
	 * One rung of the schema ladder: "a document stored at {@code fromVersion} becomes a document at
	 * {@code fromVersion + 1} after {@code apply} has rewritten it in place".
	 *
	 * <p>Kept as data rather than a chain of {@code if}s so adding the next hop is one list entry —
	 * see {@link #SCHEMA_VERSION} for the three-step recipe.
	 */
	private record Migration(int fromVersion, Consumer<JsonObject> apply) {
	}

	/**
	 * The ladder, ascending, one entry per version hop ({@code MIGRATIONS.get(i).fromVersion() == i},
	 * {@code MIGRATIONS.size() == SCHEMA_VERSION}). Pinned by {@code ConfigSchemaTest}.
	 */
	private static final List<Migration> MIGRATIONS = List.of(
			new Migration(0, Config::migrateFlatToSections));

	/**
	 * v0 → v1: the pre-MOD-402 flat file, where all 220 knobs sat at the top level, becomes the
	 * sectioned one. Every registered key found at the root is moved into its declared section, so a
	 * server that has been running since before MOD-402 keeps every value it had edited.
	 *
	 * <p>This step is load-bearing, not decorative: {@link #loadFrom} stages each field from its
	 * section object only. Remove this migration and every old install silently reverts to defaults.
	 */
	private static void migrateFlatToSections(JsonObject file) {
		for (ConfigField field : FIELDS) {
			JsonElement value = file.remove(field.key);
			if (value == null) {
				continue;
			}
			JsonElement existing = file.get(field.section.id);
			JsonObject body;
			if (existing != null && existing.isJsonObject()) {
				body = existing.getAsJsonObject();
			} else {
				body = new JsonObject();
				file.add(field.section.id, body);
			}
			body.add(field.key, value);
		}
	}

	/**
	 * Declarative description of every tunable above: json key, its section, doc text, and how the
	 * value is read, validated and written back. {@link #loadFrom} and {@link #snapshot} walk this list
	 * instead of repeating each field five times (declaration, staged read, clamp, commit, serialize) —
	 * adding a knob is now one declaration plus one entry here.
	 *
	 * <p><b>Order is load-bearing:</b> entries are serialized in list order <em>within their section</em>
	 * and each field's {@code _comment_} renders immediately before it, so this list must stay in the
	 * same order the fields are declared above. It must also stay textually BELOW the declarations: each
	 * entry captures its fallback by reading the field itself, and Java runs static initializers in
	 * source order, so a list moved above them would capture zeroes.
	 *
	 * <p>Fields whose declared default IS the recovery value pass no explicit fallback — the value is
	 * derived from the field, which is what removes the old "same literal typed twice" drift risk. The
	 * four fields that deliberately clamp to a range boundary rather than restore their default
	 * (euPerXp, euPerXpGenerated, xpLevelOneCost floor at 1; copperCableLossPerBlock floors at 0.0)
	 * pass that boundary explicitly, so the exception is visible rather than implied.
	 */
	private static final List<ConfigField> FIELDS = List.of(
			new FloatField("globalEuRateMultiplier", Section.GLOBAL, "Multiplier on EVERY generator's EU/t output. 1.0 = unchanged; 2.0 = twice the generation server-wide.",
				() -> globalEuRateMultiplier, v -> globalEuRateMultiplier = v, 0.0f),
			new FloatField("globalMachineSpeedMultiplier", Section.GLOBAL, "Machine speed multiplier (energy-neutral): higher = machines draw more EU/t but finish proportionally faster. 1.0 = unchanged.",
				() -> globalMachineSpeedMultiplier, v -> globalMachineSpeedMultiplier = v, 0.0f),
			new IntField("solarEuPerTick", Section.GENERATORS, "Solar panel output in EU/t under clear daytime sky. The energy system's baseline (1).",
				() -> solarEuPerTick, v -> solarEuPerTick = v, 0),
			new IntField("daylightEuPerTick", Section.GENERATORS, "Evolved daylight-panel output in EU/t during the day.",
				() -> daylightEuPerTick, v -> daylightEuPerTick = v, 0),
			new IntField("moonlitEuPerTick", Section.GENERATORS, "Evolved moonlit-panel output in EU/t at night under clear sky.",
				() -> moonlitEuPerTick, v -> moonlitEuPerTick = v, 0),
			new IntField("moonlitWeatherEuPerTick", Section.GENERATORS, "Moonlit-panel EU/t at night during rain/thunder (a weather trickle).",
				() -> moonlitWeatherEuPerTick, v -> moonlitWeatherEuPerTick = v, 0),
			new IntField("fuelEuPerTick", Section.GENERATORS, "Fuel (solid-burnable) generator output in EU/t while burning.",
				() -> fuelEuPerTick, v -> fuelEuPerTick = v, 0),
			new IntField("geothermalEuPerTick", Section.GENERATORS, "Geothermal (lava) generator output in EU/t while burning lava.",
				() -> geothermalEuPerTick, v -> geothermalEuPerTick = v, 0),
			new IntField("geothermalBurnTicks", Section.GENERATORS, "Ticks of burn a geothermal generator gets per bucket of lava (20 ticks = 1 second).",
				() -> geothermalBurnTicks, v -> geothermalBurnTicks = v, 1),
			new IntField("waterMillEuPerTick", Section.GENERATORS, "Water mill EU/t per adjacent water block on its four sides (0..4 EU/t total).",
				() -> waterMillEuPerTick, v -> waterMillEuPerTick = v, 0),
			new IntField("windCloudY", Section.GENERATORS, "Cloud deck Y: the windiest height. Wind climbs to here and collapses above it (shared by all wind mills and the Wind Gauge).",
				() -> windCloudY, v -> windCloudY = v, 0),
			new IntField("windDeadY", Section.GENERATORS, "Y above which only a trace of wind remains — too little to turn any rotor.",
				() -> windDeadY, v -> windDeadY = v, 0),
			new FloatField("windRidgeFactor", Section.GENERATORS, "Fraction of full wind strength reached at a mill branch's ridge; the rest is gained over the shoulder up to windCloudY.",
				() -> windRidgeFactor, v -> windRidgeFactor = v, 0.0f),
			new FloatField("windTraceFactor", Section.GENERATORS, "Fraction of full wind strength left above windDeadY (readable on the gauge, 0 EU/t for mills).",
				() -> windTraceFactor, v -> windTraceFactor = v, 0.0f),
			new FloatField("windGaugePeakKmh", Section.GENERATORS, "Clear-weather wind speed in km/h at the cloud deck — the Wind Gauge's full-scale reading.",
				() -> windGaugePeakKmh, v -> windGaugePeakKmh = v, 0.0f),
			new IntField("windMillMaxBaseEuPerTick", Section.GENERATORS, "Wind mill base EU/t at the cloud deck; the altitude profile scales it (MOD-347).",
				() -> windMillMaxBaseEuPerTick, v -> windMillMaxBaseEuPerTick = v, 0),
			new IntField("windMillMaxEuPerTick", Section.GENERATORS, "Hard cap on wind mill EU/t after the weather multiplier.",
				() -> windMillMaxEuPerTick, v -> windMillMaxEuPerTick = v, 0),
			new FloatField("windMillRainFactor", Section.GENERATORS, "Wind mill output multiplier while it is raining (not thundering).",
				() -> windMillRainFactor, v -> windMillRainFactor = v, 0.0f),
			new FloatField("windMillThunderFactor", Section.GENERATORS, "Wind mill output multiplier while it is thundering.",
				() -> windMillThunderFactor, v -> windMillThunderFactor = v, 0.0f),
			new IntField("windMillSampleTicks", Section.GENERATORS, "How often (ticks) a wind mill re-samples height/sky/weather; rate is cached in between.",
				() -> windMillSampleTicks, v -> windMillSampleTicks = v, 1),
			new IntField("windMillEvolveTicks", Section.GENERATORS, "Active open-sky ticks (with rotor + evolution chip) to evolve a base wind mill into its T2 branch.",
				() -> windMillEvolveTicks, v -> windMillEvolveTicks = v, 1),
			new IntField("highAltWindMillMaxBaseEuPerTick", Section.GENERATORS, "High-altitude wind mill (T2) clear-sky height cap in EU/t.",
				() -> highAltWindMillMaxBaseEuPerTick, v -> highAltWindMillMaxBaseEuPerTick = v, 0),
			new IntField("highAltWindMillBlocksPerBase", Section.GENERATORS, "Blocks of height above sea level per +1 EU/t of base on the high-altitude T2 variant.",
				() -> highAltWindMillBlocksPerBase, v -> highAltWindMillBlocksPerBase = v, 1),
			new FloatField("highAltWindMillRainFactor", Section.GENERATORS, "High-altitude T2 wind mill output multiplier while it is raining.",
				() -> highAltWindMillRainFactor, v -> highAltWindMillRainFactor = v, 0.0f),
			new FloatField("highAltWindMillThunderFactor", Section.GENERATORS, "High-altitude T2 wind mill output multiplier while it is thundering.",
				() -> highAltWindMillThunderFactor, v -> highAltWindMillThunderFactor = v, 0.0f),
			new IntField("highAltWindMillMaxEuPerTick", Section.GENERATORS, "Hard cap on high-altitude T2 wind mill EU/t after the weather multiplier.",
				() -> highAltWindMillMaxEuPerTick, v -> highAltWindMillMaxEuPerTick = v, 0),
			new IntField("stormWindMillMaxBaseEuPerTick", Section.GENERATORS, "Storm wind mill (T2) clear-sky height cap in EU/t before the weather multiplier.",
				() -> stormWindMillMaxBaseEuPerTick, v -> stormWindMillMaxBaseEuPerTick = v, 0),
			new FloatField("stormWindMillRainFactor", Section.GENERATORS, "Storm T2 wind mill output multiplier while it is raining.",
				() -> stormWindMillRainFactor, v -> stormWindMillRainFactor = v, 0.0f),
			new FloatField("stormWindMillThunderFactor", Section.GENERATORS, "Storm T2 wind mill output multiplier while it is thundering.",
				() -> stormWindMillThunderFactor, v -> stormWindMillThunderFactor = v, 0.0f),
			new IntField("stormWindMillMaxEuPerTick", Section.GENERATORS, "Hard cap on storm T2 wind mill EU/t after the weather multiplier.",
				() -> stormWindMillMaxEuPerTick, v -> stormWindMillMaxEuPerTick = v, 0),
			new IntField("windMillRotorMaxDamage", Section.GENERATORS, "Wind mill rotor max durability (bar). Applies at registration (restart); tune life via the EU-per-damage rate. Shared by all three wind mills.",
				() -> windMillRotorMaxDamage, v -> windMillRotorMaxDamage = v, 1),
			new IntField("windMillRotorEuPerDamage", Section.GENERATORS, "EU of production per 1 durability point of the wind mill rotor (life = maxDamage × this). Read live every tick.",
				() -> windMillRotorEuPerDamage, v -> windMillRotorEuPerDamage = v, 1),
			new FloatField("windMillStormWearFactor", Section.GENERATORS, "Extra rotor wear multiplier while running in rain/thunder (1.0 = off). Applies to all three wind mills.",
				() -> windMillStormWearFactor, v -> windMillStormWearFactor = v, 1.0f),
			new IntField("waterMillWheelMaxDamage", Section.GENERATORS, "Water mill wheel max durability (bar). Applies at registration (restart); tune life via the EU-per-damage rate.",
				() -> waterMillWheelMaxDamage, v -> waterMillWheelMaxDamage = v, 1),
			new IntField("waterMillWheelEuPerDamage", Section.GENERATORS, "EU of production per 1 durability point of the water mill wheel (life = maxDamage × this). Read live every tick.",
				() -> waterMillWheelEuPerDamage, v -> waterMillWheelEuPerDamage = v, 1),
			// MOD-385: the three-grade component ladder. Output multipliers are applied INSIDE
			// WindMillOutput/WaterMillOutput.euFor, before their clamp, so no value here can lift a
			// generator's *MaxEuPerTick ceiling. EU-per-damage tracks the multiplier so the life gain
			// stays exactly the durability gain.
			new FloatField("windMillRotorOutputMultiplier", Section.GENERATORS, "MOD-385: output scale of the plain wooden rotor — the ladder's 1.0 baseline. Raising it buffs every wind mill, not just upgraded ones.",
				() -> windMillRotorOutputMultiplier, v -> windMillRotorOutputMultiplier = v, 0.0f),
			new IntField("windMillRotorReinforcedMaxDamage", Section.GENERATORS, "MOD-385: reinforced rotor max durability (×3 the wooden one). Applies at registration (restart).",
				() -> windMillRotorReinforcedMaxDamage, v -> windMillRotorReinforcedMaxDamage = v, 1),
			new IntField("windMillRotorReinforcedEuPerDamage", Section.GENERATORS, "MOD-385: EU per 1 durability point of the reinforced rotor. Scaled by its ×1.25 output so the life gain is purely the durability gain.",
				() -> windMillRotorReinforcedEuPerDamage, v -> windMillRotorReinforcedEuPerDamage = v, 1),
			new FloatField("windMillRotorReinforcedOutputMultiplier", Section.GENERATORS, "MOD-385: reinforced rotor output scale. Applied before the mill's cap, so it cannot raise windMillMaxEuPerTick.",
				() -> windMillRotorReinforcedOutputMultiplier, v -> windMillRotorReinforcedOutputMultiplier = v, 0.0f),
			new IntField("windMillRotorAdvancedMaxDamage", Section.GENERATORS, "MOD-385: advanced rotor max durability (×6 the wooden one). Applies at registration (restart).",
				() -> windMillRotorAdvancedMaxDamage, v -> windMillRotorAdvancedMaxDamage = v, 1),
			new IntField("windMillRotorAdvancedEuPerDamage", Section.GENERATORS, "MOD-385: EU per 1 durability point of the advanced rotor, scaled by its ×1.5 output.",
				() -> windMillRotorAdvancedEuPerDamage, v -> windMillRotorAdvancedEuPerDamage = v, 1),
			new FloatField("windMillRotorAdvancedOutputMultiplier", Section.GENERATORS, "MOD-385: advanced rotor output scale. Applied before the mill's cap.",
				() -> windMillRotorAdvancedOutputMultiplier, v -> windMillRotorAdvancedOutputMultiplier = v, 0.0f),
			new FloatField("waterMillWheelOutputMultiplier", Section.GENERATORS, "MOD-385: output scale of the plain wooden wheel — the ladder's 1.0 baseline.",
				() -> waterMillWheelOutputMultiplier, v -> waterMillWheelOutputMultiplier = v, 0.0f),
			new IntField("waterMillWheelReinforcedMaxDamage", Section.GENERATORS, "MOD-385: reinforced wheel max durability (×3 the wooden one). Applies at registration (restart).",
				() -> waterMillWheelReinforcedMaxDamage, v -> waterMillWheelReinforcedMaxDamage = v, 1),
			new IntField("waterMillWheelReinforcedEuPerDamage", Section.GENERATORS, "MOD-385: EU per 1 durability point of the reinforced wheel, scaled by its ×1.25 output.",
				() -> waterMillWheelReinforcedEuPerDamage, v -> waterMillWheelReinforcedEuPerDamage = v, 1),
			new FloatField("waterMillWheelReinforcedOutputMultiplier", Section.GENERATORS, "MOD-385: reinforced wheel output scale. The water mill has no EU/t cap of its own — its ceiling is 4 wheel cells × waterMillEuPerTick.",
				() -> waterMillWheelReinforcedOutputMultiplier, v -> waterMillWheelReinforcedOutputMultiplier = v, 0.0f),
			new IntField("waterMillWheelAdvancedMaxDamage", Section.GENERATORS, "MOD-385: advanced wheel max durability (×6 the wooden one). Applies at registration (restart).",
				() -> waterMillWheelAdvancedMaxDamage, v -> waterMillWheelAdvancedMaxDamage = v, 1),
			new IntField("waterMillWheelAdvancedEuPerDamage", Section.GENERATORS, "MOD-385: EU per 1 durability point of the advanced wheel, scaled by its ×1.5 output.",
				() -> waterMillWheelAdvancedEuPerDamage, v -> waterMillWheelAdvancedEuPerDamage = v, 1),
			new FloatField("waterMillWheelAdvancedOutputMultiplier", Section.GENERATORS, "MOD-385: advanced wheel output scale.",
				() -> waterMillWheelAdvancedOutputMultiplier, v -> waterMillWheelAdvancedOutputMultiplier = v, 0.0f),
			new FloatField("solarTransparentFactor", Section.GENERATORS, "Output multiplier when a solar panel sees sky through a translucent block (leaves, cobweb).",
				() -> solarTransparentFactor, v -> solarTransparentFactor = v, 0.0f),
			new FloatField("solarSnowFactor", Section.GENERATORS, "Output multiplier under snow (a snow layer above, or snowfall in a cold biome).",
				() -> solarSnowFactor, v -> solarSnowFactor = v, 0.0f),
			new IntField("solarEvolveTicks", Section.GENERATORS, "Active sky-time ticks needed to evolve a base solar panel into its T2 branch.",
				() -> solarEvolveTicks, v -> solarEvolveTicks = v, 1),
			new IntField("solarSkySampleTicks", Section.GENERATORS, "How often (ticks) a solar panel re-samples sky access + weather; verdict is cached between samples.",
				() -> solarSkySampleTicks, v -> solarSkySampleTicks = v, 1),
			new IntField("pumpEuPerBucket", Section.LOGISTICS, "EU the pump spends per bucket of fluid it moves (extract + push).",
				() -> pumpEuPerBucket, v -> pumpEuPerBucket = v, 0),
			new IntField("pumpScanCooldownTicks", Section.LOGISTICS, "How many ticks the pump waits after a BFS scan before scanning again.",
				() -> pumpScanCooldownTicks, v -> pumpScanCooldownTicks = v, 1),
			new IntField("pumpScanMaxDistance", Section.LOGISTICS, "Max Manhattan distance the pump BFS searches for a fluid source.",
				() -> pumpScanMaxDistance, v -> pumpScanMaxDistance = v, 1),
			new IntField("pumpScanMaxVisited", Section.LOGISTICS, "Max blocks the pump BFS visits per scan, caps lag.",
				() -> pumpScanMaxVisited, v -> pumpScanMaxVisited = v, 1),
			new IntField("fluidTankCapacity", Section.LOGISTICS, "Portable fluid tank capacity in mB (1000 mB = 1 bucket). Applies to newly placed tanks.",
				() -> fluidTankCapacity, v -> fluidTankCapacity = v, 1),
			new IntField("teleporterBuffer", Section.LOGISTICS, "Teleporter station EU buffer. Applies to newly placed stations.",
				() -> teleporterBuffer, v -> teleporterBuffer = v, 1),
			new IntField("teleporterBaseCost", Section.LOGISTICS, "Flat EU part of a jump's price (paid even for a short hop).",
				() -> teleporterBaseCost, v -> teleporterBaseCost = v, 0),
			new IntField("teleporterCostPerBlock", Section.LOGISTICS, "Added EU per block of straight-line distance to the target station.",
				() -> teleporterCostPerBlock, v -> teleporterCostPerBlock = v, 0),
			new IntField("teleporterWarmupTicks", Section.LOGISTICS, "Warmup before a jump fires (20 ticks = 1 second). Cancelled by damage.",
				() -> teleporterWarmupTicks, v -> teleporterWarmupTicks = v, 0),
			new IntField("teleporterCooldownTicks", Section.LOGISTICS, "Per-player anti-spam lockout after landing (ticks).",
				() -> teleporterCooldownTicks, v -> teleporterCooldownTicks = v, 0),
			new IntField("teleporterWarmupCancelRadius", Section.LOGISTICS, "Moving further than this many blocks from where warmup started cancels the jump.",
				() -> teleporterWarmupCancelRadius, v -> teleporterWarmupCancelRadius = v, 1),
			new IntField("teleporterMaxPoints", Section.LOGISTICS, "Max stations one teleport remote can hold.",
				() -> teleporterMaxPoints, v -> teleporterMaxPoints = v, 1),
			new IntField("batteryBoxBuffer", Section.STORAGE, "Battery Box EU buffer. Applies to newly placed blocks (already-placed keep their capacity until the chunk reloads).",
				() -> batteryBoxBuffer, v -> batteryBoxBuffer = v, 1),
			new IntField("cesuBuffer", Section.STORAGE, "Reinforced Energy Storage (MV) EU buffer. Applies to newly placed blocks (already-placed keep their capacity until the chunk reloads).",
				() -> cesuBuffer, v -> cesuBuffer = v, 1),
			new IntField("storageFeedRate", Section.STORAGE, "EU/tick a storage block feeds a non-cascade sink (teleporter, charging station) over cable. 0 disables the channel.",
				() -> storageFeedRate, v -> storageFeedRate = v, 0),
			new DoubleField("storageFeedReserveFraction", Section.STORAGE, "Share of its capacity a storage block keeps for itself when feeding a non-cascade sink over cable (0..1). Below this line the channel is shut, so a teleporter can never drain the base's bank.",
				() -> storageFeedReserveFraction, v -> storageFeedReserveFraction = v, 0.0, 1.0),
			new IntField("maceratorBuffer", Section.MACHINES, "Macerator EU buffer. Applies to newly placed blocks.",
				() -> maceratorBuffer, v -> maceratorBuffer = v, 1),
			new IntField("machineBuffer", Section.MACHINES, "Shared EU buffer for ordinary LV processing machines: electric furnace, compressor, extractor, sawmill, polymerizer and vulcanizer. Applies to newly placed blocks.",
				() -> machineBuffer, v -> machineBuffer = v, 1),
			new IntField("electricHeaterBuffer", Section.MACHINES, "Electric Heater EU buffer. Applies to newly placed blocks.",
				() -> electricHeaterBuffer, v -> electricHeaterBuffer = v, 1),
			new IntField("chargePadBuffer", Section.STORAGE, "Charging Station EU buffer. Sized to a visit, not an operation: the station banks power while idle so it can charge a player's gear in one burst. Applies to newly placed blocks.",
				() -> chargePadBuffer, v -> chargePadBuffer = v, 1),
			new IntField("chargePadInputRate", Section.STORAGE, "Max EU/t the Charging Station accepts from the grid. A ceiling, not a promise - a copper cable delivers 12, a gold one 48, an adjacent Battery Box 32.",
				() -> chargePadInputRate, v -> chargePadInputRate = v, 1),
			new IntField("chargePadOutputRate", Section.STORAGE, "Max EU/t the Charging Station hands to the player standing on it, shared across every powered item they carry (each item still capped by its own input rate).",
				() -> chargePadOutputRate, v -> chargePadOutputRate = v, 1),
			new IntField("pumpBuffer", Section.LOGISTICS, "Pump EU buffer. Applies to newly placed blocks.",
				() -> pumpBuffer, v -> pumpBuffer = v, 1),
			new IntField("generatorBuffer", Section.GENERATORS, "Fuel generator EU buffer. Applies to newly placed blocks.",
				() -> generatorBuffer, v -> generatorBuffer = v, 1),
			new IntField("geothermalBuffer", Section.GENERATORS, "Geothermal generator EU buffer. Applies to newly placed blocks.",
				() -> geothermalBuffer, v -> geothermalBuffer = v, 1),
			new IntField("waterMillBuffer", Section.GENERATORS, "Water mill EU buffer. Applies to newly placed blocks.",
				() -> waterMillBuffer, v -> waterMillBuffer = v, 1),
			new IntField("windMillBuffer", Section.GENERATORS, "Wind mill (T1) EU buffer. Applies to newly placed blocks.",
				() -> windMillBuffer, v -> windMillBuffer = v, 1),
			new IntField("t2WindMillBuffer", Section.GENERATORS, "Shared EU buffer for both T2 wind mills (high-altitude + storm). Applies to newly placed blocks.",
				() -> t2WindMillBuffer, v -> t2WindMillBuffer = v, 1),
			new IntField("solarBuffer", Section.GENERATORS, "Solar panel EU buffer. Applies to newly placed blocks.",
				() -> solarBuffer, v -> solarBuffer = v, 1),
			new IntField("cableBuffer", Section.CABLES, "Per-cable working EU buffer — the live transport-segment buffer (MOD-070). Tiny by design so a wall of cables can't be used as bulk storage. Applies to newly placed cables.",
				() -> cableBuffer, v -> cableBuffer = v, 1),
			new IntField("itemPipeItemsPerTransfer", Section.LOGISTICS, "Items an item-pipe network moves per transfer. With the interval below this sets throughput.",
				() -> itemPipeItemsPerTransfer, v -> itemPipeItemsPerTransfer = v, 1),
			new IntField("itemPipeTransferIntervalTicks", Section.LOGISTICS, "Server ticks between item-pipe transfers (20 = once per second).",
				() -> itemPipeTransferIntervalTicks, v -> itemPipeTransferIntervalTicks = v, 1),
			new IntField("fluidPipeSegmentBuffer", Section.LOGISTICS, "Per-segment fluid buffer in mB — also the segment's throughput, since fluid flows through the buffer one hop per tick (MOD-151). Applies to newly placed pipes.",
				() -> fluidPipeSegmentBuffer, v -> fluidPipeSegmentBuffer = v, 1),
			new IntField("fluidNetworksPerTick", Section.NETWORK, "Fluid networks processed per server tick; the rest round-robin to later ticks.",
				() -> fluidNetworksPerTick, v -> fluidNetworksPerTick = v, 1),
			new IntField("lvPouchCapacity", Section.TOOLS, "Battery Pouch item-storage capacity in weight units (one ordinary item = 1).",
				() -> lvPouchCapacity, v -> lvPouchCapacity = v, 1),
			new IntField("lvPouchBuffer", Section.TOOLS, "Battery Pouch EU buffer.",
				() -> lvPouchBuffer, v -> lvPouchBuffer = v, 1),
			new IntField("lvPouchDrainPerSecond", Section.TOOLS, "EU the pouch drains per second while carried and holding items (locks at 0 EU until recharged).",
				() -> lvPouchDrainPerSecond, v -> lvPouchDrainPerSecond = v, 0),
			new IntField("batteryBuffer", Section.TOOLS, "Battery EU buffer, PER ITEM (a stack of 16 carries 16x this).",
				() -> batteryBuffer, v -> batteryBuffer = v, 1),
			new IntField("batteryInputRate", Section.TOOLS, "Max EU/t one battery accepts while charging in a slot.",
				() -> batteryInputRate, v -> batteryInputRate = v, 1),
			new IntField("batteryTransferPerUse", Section.TOOLS, "EU one right-click moves from the battery into the item in the other hand.",
				() -> batteryTransferPerUse, v -> batteryTransferPerUse = v, 1),
			new IntField("energyPackBuffer", Section.TOOLS, "Energy Pack (worn) EU buffer.",
				() -> energyPackBuffer, v -> energyPackBuffer = v, 1),
			new IntField("energyPackInputRate", Section.TOOLS, "Max EU/t the Energy Pack accepts while charging in a slot.",
				() -> energyPackInputRate, v -> energyPackInputRate = v, 1),
			new IntField("energyPackOutputRate", Section.TOOLS, "Max EU/t the worn Energy Pack hands out to powered items in the inventory.",
				() -> energyPackOutputRate, v -> energyPackOutputRate = v, 1),
			new IntField("electricDrillBuffer", Section.TOOLS, "Electric Drill EU buffer.",
				() -> electricDrillBuffer, v -> electricDrillBuffer = v, 1),
			new IntField("electricDrillEuPerBlock", Section.TOOLS, "EU the drill spends per block mined at powered speed (below this it mines at hand speed for free).",
				() -> electricDrillEuPerBlock, v -> electricDrillEuPerBlock = v, 1),
			new IntField("electricDrillInputRate", Section.TOOLS, "Max EU/t the drill accepts while charging in a slot.",
				() -> electricDrillInputRate, v -> electricDrillInputRate = v, 1),
			new IntField("electricDrillTorchEuCost", Section.TOOLS, "EU the drill spends to place a torch on right-click.",
				() -> electricDrillTorchEuCost, v -> electricDrillTorchEuCost = v, 0),
			new IntField("electricChainsawBuffer", Section.TOOLS, "Electric Chainsaw EU buffer.",
				() -> electricChainsawBuffer, v -> electricChainsawBuffer = v, 1),
			new IntField("electricChainsawEuPerBlock", Section.TOOLS, "EU the chainsaw spends per block cut at powered speed (below this it cuts at hand speed for free).",
				() -> electricChainsawEuPerBlock, v -> electricChainsawEuPerBlock = v, 1),
			new IntField("electricChainsawInputRate", Section.TOOLS, "Max EU/t the chainsaw accepts while charging in a slot.",
				() -> electricChainsawInputRate, v -> electricChainsawInputRate = v, 1),
			new IntField("electricShovelBuffer", Section.TOOLS, "Electric Shovel EU buffer.",
				() -> electricShovelBuffer, v -> electricShovelBuffer = v, 1),
			new IntField("electricShovelEuPerBlock", Section.TOOLS, "EU the shovel spends per block dug at powered speed (below this it digs at hand speed for free).",
				() -> electricShovelEuPerBlock, v -> electricShovelEuPerBlock = v, 1),
			new IntField("electricShovelInputRate", Section.TOOLS, "Max EU/t the shovel accepts while charging in a slot.",
				() -> electricShovelInputRate, v -> electricShovelInputRate = v, 1),
			new IntField("electricHoeBuffer", Section.TOOLS, "Electric Hoe EU buffer.",
				() -> electricHoeBuffer, v -> electricHoeBuffer = v, 1),
			new IntField("electricHoeEuPerBlock", Section.TOOLS, "EU the hoe spends per block broken at powered speed (below this it breaks at hand speed for free). Tilling is powered too and is billed separately by electricHoeTillEuCost.",
				() -> electricHoeEuPerBlock, v -> electricHoeEuPerBlock = v, 1),
			new IntField("electricHoeTillEuCost", Section.TOOLS, "EU the hoe spends per successful right-click conversion (tilling soil).",
				() -> electricHoeTillEuCost, v -> electricHoeTillEuCost = v, 0),
			new IntField("electricHoeInputRate", Section.TOOLS, "Max EU/t the hoe accepts while charging in a slot.",
				() -> electricHoeInputRate, v -> electricHoeInputRate = v, 1),
			new IntField("electricSaberBuffer", Section.TOOLS, "Electric Saber EU buffer.",
				() -> electricSaberBuffer, v -> electricSaberBuffer = v, 1),
			new IntField("electricSaberEuPerHit", Section.TOOLS, "EU the saber spends per powered hit (below this it hits as a plain sword for free).",
				() -> electricSaberEuPerHit, v -> electricSaberEuPerHit = v, 1),
			new IntField("electricSaberInputRate", Section.TOOLS, "Max EU/t the saber accepts while charging in a slot.",
				() -> electricSaberInputRate, v -> electricSaberInputRate = v, 1),
			new IntField("electricSaberShockSeconds", Section.TOOLS, "Seconds of Slowness II a powered saber hit leaves on the target (0 disables).",
				() -> electricSaberShockSeconds, v -> electricSaberShockSeconds = v, 0),
			new IntField("fluxweaveBuffer", Section.TOOLS, "EU buffer of each Fluxweave armour piece.",
				() -> fluxweaveBuffer, v -> fluxweaveBuffer = v, 1),
			new IntField("fluxweaveInputRate", Section.TOOLS, "Max EU/t a Fluxweave piece accepts while charging in a slot.",
				() -> fluxweaveInputRate, v -> fluxweaveInputRate = v, 1),
			new IntField("fluxweaveUpkeepEuPerSecond", Section.TOOLS, "EU/second a charged, worn Fluxweave piece burns to keep its bonuses on.",
				() -> fluxweaveUpkeepEuPerSecond, v -> fluxweaveUpkeepEuPerSecond = v, 0),
			new IntField("fluxweaveFallDamageReductionPercent", Section.TOOLS, "Percent of fall damage Fluxweave boots absorb while charged (clamped to 90 in code).",
				() -> fluxweaveFallDamageReductionPercent, v -> fluxweaveFallDamageReductionPercent = v, 0),
			new IntField("fluxweaveRunSpeedPercent", Section.TOOLS, "Percent added to run speed by charged Fluxweave leggings.",
				() -> fluxweaveRunSpeedPercent, v -> fluxweaveRunSpeedPercent = v, 0),
			new IntField("fluxweaveOxygenBonus", Section.TOOLS, "OXYGEN_BONUS levels granted by a charged Fluxweave helmet.",
				() -> fluxweaveOxygenBonus, v -> fluxweaveOxygenBonus = v, 0),
			new IntField("fluxweaveSwimEfficiency", Section.TOOLS, "Percent of water movement efficiency granted by a charged Fluxweave helmet.",
				() -> fluxweaveSwimEfficiency, v -> fluxweaveSwimEfficiency = v, 0),
			new IntField("fluxweaveChargedToughness", Section.TOOLS, "Extra armour toughness on a charged Fluxweave chestplate.",
				() -> fluxweaveChargedToughness, v -> fluxweaveChargedToughness = v, 0),
			new IntField("fluxweaveKnockbackResistance", Section.TOOLS, "Percent of knockback resisted by a charged Fluxweave chestplate.",
				() -> fluxweaveKnockbackResistance, v -> fluxweaveKnockbackResistance = v, 0),
			new IntField("fluxweaveStepHeightBonus", Section.TOOLS, "Extra step height (hundredths of a block) from charged Fluxweave leggings with the assist toggled on.",
				() -> fluxweaveStepHeightBonus, v -> fluxweaveStepHeightBonus = v, 0),
			new IntField("fluxweaveRegenEuPerHeal", Section.TOOLS, "EU the Fluxweave helmet spends per half-heart healed by the 4/4 set bonus.",
				() -> fluxweaveRegenEuPerHeal, v -> fluxweaveRegenEuPerHeal = v, 1),
			new IntField("jetpackBuffer", Section.TOOLS, "Jetpack EU buffer.",
				() -> jetpackBuffer, v -> jetpackBuffer = v, 1),
			new IntField("jetpackEuPerTick", Section.TOOLS, "EU the jetpack burns per tick of thrust (jump held while airborne).",
				() -> jetpackEuPerTick, v -> jetpackEuPerTick = v, 1),
			new IntField("jetpackInputRate", Section.TOOLS, "Max EU/t the jetpack accepts while charging in a slot.",
				() -> jetpackInputRate, v -> jetpackInputRate = v, 1),
			new IntField("jetpackMaxY", Section.TOOLS, "Altitude ceiling (block Y) above which the jetpack engine refuses to thrust.",
				() -> jetpackMaxY, v -> jetpackMaxY = v, 1),
			new IntField("jetpackFlightLightLevel", Section.TOOLS, "Light level (0-15) a thrusting jetpack casts around the flyer; 0 disables the glow.",
				() -> jetpackFlightLightLevel, v -> jetpackFlightLightLevel = v, 0),
			new IntField("magnetBuffer", Section.TOOLS, "Electromagnet EU buffer.",
				() -> magnetBuffer, v -> magnetBuffer = v, 1),
			new IntField("magnetInputRate", Section.TOOLS, "Max EU/t the electromagnet accepts while charging in a slot.",
				() -> magnetInputRate, v -> magnetInputRate = v, 1),
			new IntField("magnetRange", Section.TOOLS, "Electromagnet pull radius in blocks around the carrier.",
				() -> magnetRange, v -> magnetRange = v, 1),
			new IntField("magnetEuPerItem", Section.TOOLS, "EU the electromagnet spends per item pulled each scan tick (an idle scan is free).",
				() -> magnetEuPerItem, v -> magnetEuPerItem = v, 1),
			new IntField("magnetScanIntervalTicks", Section.TOOLS, "How often (ticks) the electromagnet scans for and pulls nearby drops.",
				() -> magnetScanIntervalTicks, v -> magnetScanIntervalTicks = v, 1),
			new IntField("stockFrameScanIntervalTicks", Section.LOGISTICS, "How often (ticks) a Stock Display Frame rescans the container behind it.",
				() -> stockFrameScanIntervalTicks, v -> stockFrameScanIntervalTicks = v, 1),
			new DoubleField("scytheBonusSeedMultiplier", Section.TOOLS, "Global multiplier on the scythe's per-tier bonus-seed chance (1.0 = shipped ladder, 0.0 = mechanic off; a tier is clamped to 1.0).",
				() -> scytheBonusSeedMultiplier, v -> scytheBonusSeedMultiplier = v, 0.0, 0.0),
			new IntField("machineEuPerTick", Section.MACHINES, "Base EU/t a processing machine draws while running (energy per operation = this x its duration).",
				() -> machineEuPerTick, v -> machineEuPerTick = v, 1),
			new IntField("maceratorDuration", Section.MACHINES, "Ticks a macerator takes per operation at 1.0 speed.",
				() -> maceratorDuration, v -> maceratorDuration = v, 1),
			new IntField("incubatorEuPerTick", Section.MACHINES, "EU/t the incubator draws while running (4x the machine standard).",
				() -> incubatorEuPerTick, v -> incubatorEuPerTick = v, 1),
			new IntField("incubatorBuffer", Section.MACHINES, "Incubator internal EU buffer.",
				() -> incubatorBuffer, v -> incubatorBuffer = v, 1),
			new IntField("mutationDurationTransform", Section.MACHINES, "Ticks an incubator transform attempt takes at 1.0 speed.",
				() -> mutationDurationTransform, v -> mutationDurationTransform = v, 1),
			new IntField("mutationDurationDuplicate", Section.MACHINES, "Ticks an incubator duplicate attempt takes at 1.0 speed.",
				() -> mutationDurationDuplicate, v -> mutationDurationDuplicate = v, 1),
			new IntField("mutationDurationCreate", Section.MACHINES, "Ticks an incubator create attempt takes at 1.0 speed.",
				() -> mutationDurationCreate, v -> mutationDurationCreate = v, 1),
			new IntField("mutationAttemptsPerIngot", Section.MACHINES, "Mutation attempts one uranium ingot powers before it burns to ash.",
				() -> mutationAttemptsPerIngot, v -> mutationAttemptsPerIngot = v, 1),
			new DoubleField("mutationChanceTransform", Section.MACHINES, "Base success chance of a transform mutation (0..1).",
				() -> mutationChanceTransform, v -> mutationChanceTransform = v, 0.0, 0.0),
			new DoubleField("mutationChanceDuplicate", Section.MACHINES, "Base success chance of a duplicate mutation (0..1).",
				() -> mutationChanceDuplicate, v -> mutationChanceDuplicate = v, 0.0, 0.0),
			new DoubleField("mutationChanceCreate", Section.MACHINES, "Base success chance of a create mutation (0..1).",
				() -> mutationChanceCreate, v -> mutationChanceCreate = v, 0.0, 0.0),
			new DoubleField("mutationChanceCap", Section.MACHINES, "Ceiling on the total mutation success chance (base + gene bonus).",
				() -> mutationChanceCap, v -> mutationChanceCap = v, 0.0, 0.0),
			new DoubleField("mutationSlagChance", Section.MACHINES, "Share of attempts yielding irradiated slag instead of an empty miss.",
				() -> mutationSlagChance, v -> mutationSlagChance = v, 0.0, 0.0),
			new DoubleField("mutationGradeRare", Section.MACHINES, "Share of successful mutations rolling the rare grade.",
				() -> mutationGradeRare, v -> mutationGradeRare = v, 0.0, 0.0),
			new DoubleField("mutationGradeEpic", Section.MACHINES, "Share of successful mutations rolling the epic grade.",
				() -> mutationGradeEpic, v -> mutationGradeEpic = v, 0.0, 0.0),
			new DoubleField("mutationGradeLegendary", Section.MACHINES, "Share of successful mutations rolling the legendary grade.",
				() -> mutationGradeLegendary, v -> mutationGradeLegendary = v, 0.0, 0.0),
			new IntField("gardenDroneBuffer", Section.MACHINES, "Garden Drone station internal EU buffer.",
				() -> gardenDroneBuffer, v -> gardenDroneBuffer = v, 1),
			new IntField("gardenDroneEuPerAction", Section.MACHINES, "EU the Garden Drone spends per completed action (till/plant/fertilize/harvest); idle costs nothing.",
				() -> gardenDroneEuPerAction, v -> gardenDroneEuPerAction = v, 1),
			new IntField("gardenDroneRange", Section.MACHINES, "Garden Drone zone-scan radius in blocks around the station.",
				() -> gardenDroneRange, v -> gardenDroneRange = v, 1),
			new IntField("gardenDroneScanIntervalTicks", Section.MACHINES, "Ticks between Garden Drone zone rebuilds after cache invalidation.",
				() -> gardenDroneScanIntervalTicks, v -> gardenDroneScanIntervalTicks = v, 1),
			new IntField("gardenDroneFlightTicksPerBlock", Section.MACHINES, "Ticks the Garden Drone flies per block of distance before its action lands.",
				() -> gardenDroneFlightTicksPerBlock, v -> gardenDroneFlightTicksPerBlock = v, 0),
			new IntField("mobRepellerRange", Section.MACHINES, "Mob Repeller LV zone radius in blocks (a cube around the block).",
				() -> mobRepellerRange, v -> mobRepellerRange = v, 1),
			new IntField("mobRepellerRangeMv", Section.MACHINES, "Mob Repeller MV zone radius in blocks.",
				() -> mobRepellerRangeMv, v -> mobRepellerRangeMv = v, 1),
			new IntField("mobRepellerRangeHv", Section.MACHINES, "Mob Repeller HV zone radius in blocks.",
				() -> mobRepellerRangeHv, v -> mobRepellerRangeHv = v, 1),
			new IntField("mobRepellerEuPerTick", Section.MACHINES, "Mob Repeller LV field upkeep in EU per tick while enabled (constant drain, not per expulsion).",
				() -> mobRepellerEuPerTick, v -> mobRepellerEuPerTick = v, 1),
			new IntField("mobRepellerEuPerTickMv", Section.MACHINES, "Mob Repeller MV field upkeep in EU per tick.",
				() -> mobRepellerEuPerTickMv, v -> mobRepellerEuPerTickMv = v, 1),
			new IntField("mobRepellerEuPerTickHv", Section.MACHINES, "Mob Repeller HV field upkeep in EU per tick.",
				() -> mobRepellerEuPerTickHv, v -> mobRepellerEuPerTickHv = v, 1),
			new IntField("mobRepellerBuffer", Section.MACHINES, "Mob Repeller LV internal EU buffer.",
				() -> mobRepellerBuffer, v -> mobRepellerBuffer = v, 1),
			new IntField("mobRepellerBufferMv", Section.MACHINES, "Mob Repeller MV internal EU buffer.",
				() -> mobRepellerBufferMv, v -> mobRepellerBufferMv = v, 1),
			new IntField("mobRepellerBufferHv", Section.MACHINES, "Mob Repeller HV internal EU buffer.",
				() -> mobRepellerBufferHv, v -> mobRepellerBufferHv = v, 1),
			new IntField("mobRepellerScanIntervalTicks", Section.MACHINES, "Ticks between Mob Repeller zone sweeps (the upkeep drain still applies every tick).",
				() -> mobRepellerScanIntervalTicks, v -> mobRepellerScanIntervalTicks = v, 1),
			new IntField("mobRepellerEvolveKillsMv", Section.MACHINES, "Personal hostile kills a Soul Vessel needs to evolve the LV repeller into MV.",
				() -> mobRepellerEvolveKillsMv, v -> mobRepellerEvolveKillsMv = v, 1),
			new IntField("mobRepellerEvolveKillsHv", Section.MACHINES, "Personal hostile kills to evolve the MV repeller into HV; also the vessel hard cap.",
				() -> mobRepellerEvolveKillsHv, v -> mobRepellerEvolveKillsHv = v, 1),
			// Minimum 1 on both: a 0 divisor would divide by zero inside the plant's random tick (MOD-169).
			new IntField("cottonRootingChanceDivisor", Section.WORLD, "Cotton trellis: 1-in-this chance of advancing one rooting stage per random tick (higher = longer initial growth).",
				() -> cottonRootingChanceDivisor, v -> cottonRootingChanceDivisor = v, 1),
			new IntField("cottonFruitingChanceDivisor", Section.WORLD, "Cotton trellis: 1-in-this chance of advancing one fruiting stage per random tick (the repeating harvest cycle).",
				() -> cottonFruitingChanceDivisor, v -> cottonFruitingChanceDivisor = v, 1),
			new IntField("electricFurnaceDuration", Section.MACHINES, "Ticks an electric furnace takes per smelt at 1.0 speed.",
				() -> electricFurnaceDuration, v -> electricFurnaceDuration = v, 1),
			new IntField("compressorDuration", Section.MACHINES, "Ticks a compressor takes per operation at 1.0 speed.",
				() -> compressorDuration, v -> compressorDuration = v, 1),
			new IntField("extractorDuration", Section.MACHINES, "Ticks an extractor takes per operation at 1.0 speed.",
				() -> extractorDuration, v -> extractorDuration = v, 1),
			new IntField("sawmillDuration", Section.MACHINES, "Ticks a sawmill takes per cut at 1.0 speed (all four modes).",
				() -> sawmillDuration, v -> sawmillDuration = v, 1),
			new IntField("polymerizerDuration", Section.MACHINES, "Ticks a polymerizer takes to turn one bucket of oil into raw rubber at 1.0 speed.",
				() -> polymerizerDuration, v -> polymerizerDuration = v, 1),
			new IntField("vulcanizerDuration", Section.MACHINES, "Fallback ticks a vulcanizer operation takes at 1.0 speed; shipped recipe energy 400 / machineEuPerTick 2 = 200.",
				() -> vulcanizerDuration, v -> vulcanizerDuration = v, 1),
			new IntField("canningMachineDuration", Section.MACHINES, "Ticks the canning machine takes per ration at 1.0 speed; x machineEuPerTick 2 = 200 EU per ration.",
				() -> canningMachineDuration, v -> canningMachineDuration = v, 1),
			new IntField("canningFoodValuePerCan", Section.MACHINES, "Food value in tenths (nutrition + saturation) the canning machine consumes per ration. Must exceed the ration's own 96, or canning becomes a food duplicator.",
				() -> canningFoodValuePerCan, v -> canningFoodValuePerCan = v, 97),
			new IntField("distillationColumnDuration", Section.MACHINES, "Fallback ticks one distillation takes at 1.0 speed; shipped recipe energy 400 / machineEuPerTick 2 = 200.",
				() -> distillationColumnDuration, v -> distillationColumnDuration = v, 1),
			new IntField("distillationColumnWarmupTicks", Section.MACHINES, "Ticks a cold distillation column heats (at machineEuPerTick) before it can distil; cooling is twice as slow.",
				() -> distillationColumnWarmupTicks, v -> distillationColumnWarmupTicks = v, 1),
			new IntField("galvanicBathDuration", Section.MACHINES, "Fallback ticks a galvanic bath operation takes at 1.0 speed; shipped recipe energy 1000 / machineEuPerTick 2 = 500.",
				() -> galvanicBathDuration, v -> galvanicBathDuration = v, 1),
			new FloatField("overclockerSpeedFactor", Section.MACHINES, "MOD-392: duration multiplier per overclocker chip (0.8 = each chip cuts one operation to 80% of its length).",
				() -> overclockerSpeedFactor, v -> overclockerSpeedFactor = v, 0.0f),
			new FloatField("overclockerEuFactor", Section.MACHINES, "MOD-392: EU/t multiplier per overclocker chip (2.0 = each chip doubles the draw). With 0.8 speed this makes every chip cost 60% more energy per operation.",
				() -> overclockerEuFactor, v -> overclockerEuFactor = v, 1.0f),
			new IntField("overclockerMaxPerMachine", Section.MACHINES, "MOD-392: absolute ceiling on overclocker chips in one machine; the tier cap (base EU/t x factor^n <= tier voltage) usually bites first.",
				() -> overclockerMaxPerMachine, v -> overclockerMaxPerMachine = v, 0),
			new IntField("condenserCapacity", Section.MACHINES, "MOD-393: ceiling of the energy condenser's bank; equals the tier-III clot threshold, so it stops drawing once nothing higher is reachable.",
				() -> condenserCapacity, v -> condenserCapacity = v, 1),
			new IntField("condenserInputRate", Section.MACHINES, "MOD-393: EU/t the energy condenser accepts — one MV packet, about 32 basic solar panels. Machines are protected by the network's serve order, not by this number; absorbing a bigger surplus means placing more condensers.",
				() -> condenserInputRate, v -> condenserInputRate = v, 1),
			new IntField("clotThresholdI", Section.MACHINES, "MOD-393: banked EU needed before the condenser can yield a tier-I energy clot.",
				() -> clotThresholdI, v -> clotThresholdI = v, 1),
			new IntField("clotThresholdII", Section.MACHINES, "MOD-393: banked EU for a tier-II clot (four times tier I).",
				() -> clotThresholdII, v -> clotThresholdII = v, 1),
			new IntField("clotThresholdIII", Section.MACHINES, "MOD-393: banked EU for a tier-III clot (four times tier II).",
				() -> clotThresholdIII, v -> clotThresholdIII = v, 1),
			new IntField("alloySmelterEuPerTick", Section.MACHINES, "EU/t the alloy smelter draws while running (MOD-064). Four times the machine standard, like the incubator.",
				() -> alloySmelterEuPerTick, v -> alloySmelterEuPerTick = v, 1),
			new IntField("alloySmelterDuration", Section.MACHINES, "Fallback ticks one alloying operation takes at 1.0 speed (MOD-064); shipped recipe energy 1200 / alloySmelterEuPerTick 8 = 150.",
				() -> alloySmelterDuration, v -> alloySmelterDuration = v, 1),
			new IntField("repairBenchEuPerTick", Section.MACHINES, "EU/t the component repair bench draws while repairing (MOD-384). Four times the machine standard, like the alloy smelter.",
				() -> repairBenchEuPerTick, v -> repairBenchEuPerTick = v, 1),
			new IntField("repairBenchTier1EuCost", Section.MACHINES, "MOD-384: EU one repair of a T1 rotor/wheel costs (material: 1 iron plate). 5000 EU / 8 EU-t = 625 ticks.",
				() -> repairBenchTier1EuCost, v -> repairBenchTier1EuCost = v, 1),
			new IntField("repairBenchTier2EuCost", Section.MACHINES, "MOD-384: EU one repair of a reinforced rotor/wheel costs (material: 1 tempered iron plate).",
				() -> repairBenchTier2EuCost, v -> repairBenchTier2EuCost = v, 1),
			new IntField("repairBenchTier3EuCost", Section.MACHINES, "MOD-384: EU one repair of an advanced rotor/wheel costs (material: 1 electronic circuit).",
				() -> repairBenchTier3EuCost, v -> repairBenchTier3EuCost = v, 1),
			new IntField("repairBenchMaxDamageDecayPercent", Section.MACHINES, "MOD-384: percent of the ORIGINAL durability ceiling one repair burns, linear (1000 -> 800 -> 600 -> 400). Also sets how many repairs a part gets (four at 20%). 0 disables the decay and the limit with it.",
				() -> repairBenchMaxDamageDecayPercent, v -> repairBenchMaxDamageDecayPercent = v, 0),
			new IntField("assemblerEuPerTick", Section.MACHINES, "EU/tick the assembler draws while crafting (MOD-275). MV rate: six times an LV machine.",
				() -> assemblerEuPerTick, v -> assemblerEuPerTick = v, 1),
			new IntField("assemblerDuration", Section.MACHINES, "Ticks one assembler craft takes at 1.0 speed (MOD-275). 40 = 2 seconds, the pace of the genre.",
				() -> assemblerDuration, v -> assemblerDuration = v, 1),
			new IntField("assemblerBuffer", Section.MACHINES, "EU buffer of the assembler (MOD-275) — 25 operations at 480 EU each.",
				() -> assemblerBuffer, v -> assemblerBuffer = v, 1),
			new IntField("galvanicBathWaterPerOp", Section.MACHINES, "mB of water a galvanic bath consumes per completed operation (not part of the recipe JSON).",
				() -> galvanicBathWaterPerOp, v -> galvanicBathWaterPerOp = v, 1),
			new IntField("electricHeaterEuPerTick", Section.MACHINES, "EU/t an Electric Heater spends while the Vulcanizer directly above it advances; idle heater draws nothing.",
				() -> electricHeaterEuPerTick, v -> electricHeaterEuPerTick = v, 1),
			new IntField("electricHeaterWarmupTicks", Section.MACHINES, "MOD-418: paid heat ticks a cold Electric Heater needs before it supplies tier-3 heat (x3 output); until then it supplies tier 2 (x2). Cooling is twice as slow.",
				() -> electricHeaterWarmupTicks, v -> electricHeaterWarmupTicks = v, 1),
			new IntField("ironFurnaceCookTime", Section.MACHINES, "Ticks the (fuel-based) iron furnace takes to smelt one item. Vanilla furnace = 200.",
				() -> ironFurnaceCookTime, v -> ironFurnaceCookTime = v, 1),
			new IntField("euPerXp", Section.PLAYER, "MOD-133 player profile: useful EU (from completed machine operations) per 1 point of mod XP. Higher = slower progression. Starting value, tune after playtest.",
				() -> euPerXp, v -> euPerXp = v, 1, 1),
			new IntField("euPerXpGenerated", Section.PLAYER, "MOD-133 player profile: produced EU (actually credited into a generator buffer, never idle overflow) per 1 point of mod XP. Much higher than euPerXp on purpose - a generator runs unattended, so it only trickles. Starting value, tune after playtest.",
				() -> euPerXpGenerated, v -> euPerXpGenerated = v, 1, 1),
			new IntField("xpLevelOneCost", Section.PLAYER, "MOD-133: XP cost of the first level (1->2); each later level costs levelXpMultiplier x the previous. Starting value.",
				() -> xpLevelOneCost, v -> xpLevelOneCost = v, 1, 1),
			new FloatField("levelXpMultiplier", Section.PLAYER, "MOD-133: per-level XP cost multiplier (exponential curve over 40 levels). Must be > 1.0.",
				() -> levelXpMultiplier, v -> levelXpMultiplier = v, 1.0f),
			new IntField("statsFlushTicks", Section.PLAYER, "MOD-133: how often (server ticks) in-memory player stats fold into the attachment and sync. 100 = every 5s.",
				() -> statsFlushTicks, v -> statsFlushTicks = v, 1),
			new IntField("tierLvVoltage", Section.NETWORK, "Max packet voltage (EU) and per-tick transfer cap for the LV tier (cable, generator, machine, storage). Mirrored into EnergyTier.LV.",
				() -> tierLvVoltage, v -> tierLvVoltage = v, 1),
			new IntField("tierMvVoltage", Section.NETWORK, "Max packet voltage for the MV tier (4x LV by convention). Mirrored into EnergyTier.MV.",
				() -> tierMvVoltage, v -> tierMvVoltage = v, 1),
			new IntField("tierHvVoltage", Section.NETWORK, "Max packet voltage for the HV tier (4x MV by convention). Mirrored into EnergyTier.HV.",
				() -> tierHvVoltage, v -> tierHvVoltage = v, 1),
			new IntField("tierLvCapacity", Section.NETWORK, "Default internal buffer capacity for LV machines that do not override it. Mirrored into EnergyTier.LV.",
				() -> tierLvCapacity, v -> tierLvCapacity = v, 1),
			new IntField("tierMvCapacity", Section.NETWORK, "Default internal buffer capacity for MV machines. Mirrored into EnergyTier.MV.",
				() -> tierMvCapacity, v -> tierMvCapacity = v, 1),
			new IntField("tierHvCapacity", Section.NETWORK, "Default internal buffer capacity for HV machines. Mirrored into EnergyTier.HV.",
				() -> tierHvCapacity, v -> tierHvCapacity = v, 1),
			new DoubleField("copperCableLossPerBlock", Section.CABLES, "Fraction of throughput attenuated per copper cable block (0.02 = 2% of the remaining flow per block).",
				() -> copperCableLossPerBlock, v -> copperCableLossPerBlock = v, 0.0, 0.0),
			new BoolField("bareCableShockEnabled", Section.SAFETY, "When true, energized bare cables damage players on direct contact and emit shock feedback. false disables the entire mechanic.",
				() -> bareCableShockEnabled, v -> bareCableShockEnabled = v),
			new FloatField("bareCableShockLvDamage", Section.SAFETY, "Damage from direct contact with an energized bare LV cable, in half-hearts.",
				() -> bareCableShockLvDamage, v -> bareCableShockLvDamage = v, 0.0f),
			new FloatField("bareCableShockMvDamage", Section.SAFETY, "Damage from direct contact with an energized bare MV cable, in half-hearts.",
				() -> bareCableShockMvDamage, v -> bareCableShockMvDamage = v, 0.0f),
			new FloatField("bareCableShockHvDamage", Section.SAFETY, "Damage from direct contact with an energized bare HV cable, in half-hearts.",
				() -> bareCableShockHvDamage, v -> bareCableShockHvDamage = v, 0.0f),
			new DoubleField("bareCableShockProximityRadius", Section.SAFETY, "Extra blocks the shock hazard reaches beyond a bare cable segment's own cell in every direction (0 = direct-touch only).",
				() -> bareCableShockProximityRadius, v -> bareCableShockProximityRadius = v, 0.0, 0.0),
			new DoubleField("insulationLossMultiplier", Section.CABLES, "Multiplier applied to bare-cable attenuation for rubber-insulated tin/copper cables (0.5 = half the loss; throughput and packet cap are unchanged).",
				() -> insulationLossMultiplier, v -> insulationLossMultiplier = v, 0.0, 0.0),
			new DoubleField("shockGuardWoodHitChance", Section.SAFETY, "Chance (0..1) a shock still lands through a plank insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).",
				() -> shockGuardWoodHitChance, v -> shockGuardWoodHitChance = v, 0.0, 0.0),
			new DoubleField("shockGuardWoolHitChance", Section.SAFETY, "Chance (0..1) a shock still lands through a wool insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).",
				() -> shockGuardWoolHitChance, v -> shockGuardWoolHitChance = v, 0.0, 0.0),
			new DoubleField("shockGuardGlassHitChance", Section.SAFETY, "Chance (0..1) a shock still lands through a glass insulating stand under a bare cable (1 = no protection, 0 = blocks every hit).",
				() -> shockGuardGlassHitChance, v -> shockGuardGlassHitChance = v, 0.0, 0.0),
			new IntField("shockGuardGraceTicks", Section.SAFETY, "Contact ticks a player is spared after an insulating stand absorbs a shock, so the reduced chance is per contact rather than re-rolled every tick.",
				() -> shockGuardGraceTicks, v -> shockGuardGraceTicks = v, 0),
			new IntField("tinCableBuffer", Section.CABLES, "Per-segment working EU buffer of a tin cable = its real throughput (8 EU/t, narrower than copper's 12).",
				() -> tinCableBuffer, v -> tinCableBuffer = v, 1),
			new IntField("tinCablePacketCap", Section.CABLES, "Per-tick ceiling on EU drawn from one source through a tin cable (8 EU/t, below the LV tier voltage by design).",
				() -> tinCablePacketCap, v -> tinCablePacketCap = v, 1),
			new DoubleField("tinCableLossPerBlock", Section.CABLES, "Fraction of throughput attenuated per tin cable block (0.006 = 0.6% of the remaining flow per block; a 1 EU/t solar trickle floors to zero loss).",
				() -> tinCableLossPerBlock, v -> tinCableLossPerBlock = v, 0.0, 0.0),
			new IntField("goldCableBuffer", Section.CABLES, "Per-segment working EU buffer of a gold (MV) cable = its real throughput (48 EU/t, 4x copper).",
				() -> goldCableBuffer, v -> goldCableBuffer = v, 1),
			new DoubleField("goldCableLossPerBlock", Section.CABLES, "Fraction of throughput attenuated per gold cable block (0.03 = 3% of the remaining flow per block; worse than copper by design - gold buys throughput, not distance).",
				() -> goldCableLossPerBlock, v -> goldCableLossPerBlock = v, 0.0, 0.0),
			new IntField("electrumCableBuffer", Section.CABLES, "Per-segment working EU buffer of an electrum (HV) cable = its real throughput (192 EU/t, 4x gold).",
				() -> electrumCableBuffer, v -> electrumCableBuffer = v, 1),
			new DoubleField("electrumCableLossPerBlock", Section.CABLES, "Fraction of throughput attenuated per electrum cable block (0.005 = 0.5% of the remaining flow per block; the lowest in the mod - electrum pays in craft cost, not in distance).",
				() -> electrumCableLossPerBlock, v -> electrumCableLossPerBlock = v, 0.0, 0.0),
			new IntField("networksPerTick", Section.NETWORK, "Max awake energy networks processed per server tick; the rest are deferred round-robin.",
				() -> networksPerTick, v -> networksPerTick = v, 1),
			new IntField("networkAnalyzerMaxTraversedNetworks", Section.NETWORK, "Cap on networks the Network Analyzer's Traverse mode walks (visualization only, never affects energy).",
				() -> networkAnalyzerMaxTraversedNetworks, v -> networkAnalyzerMaxTraversedNetworks = v, 1),
			new BoolField("bonusChestEnabled", Section.WORLD, "When true, mod starter items are injected into the vanilla bonus chest at world creation (vanilla loot kept). false = purely vanilla bonus chest.",
				() -> bonusChestEnabled, v -> bonusChestEnabled = v),
			new BoolField("oilBurns", Section.WORLD, "When true, oil ignites from adjacent fire or flint-and-steel and the burn spreads across the pool; lava alone does not ignite it. false = oil is inert.",
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
		/**
		 * File existed but could not be parsed (bad JSON, or a value of the wrong type). The apply is
		 * atomic, so the live balance is left exactly as it was.
		 */
		ERROR,
		/**
		 * The file declares a {@code schemaVersion} newer than {@link #SCHEMA_VERSION} (MOD-402): this
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
	 * Load the config file at {@code path}, or write the current defaults if it does not exist yet.
	 *
	 * <p><b>Versioned (MOD-402):</b> the file carries {@code schemaVersion}. An older one (including a
	 * pre-MOD-402 file, which has no such key and counts as version 0) is walked up the
	 * {@link #MIGRATIONS} ladder before anything is read, so an existing server keeps every value it had
	 * edited. A file from a NEWER mod build is refused: this build cannot know what its keys mean, so
	 * nothing is applied, the balance falls back to the compiled defaults, the file on disk is left
	 * alone, and the reason goes to the log at WARN. Silently reading a newer format is exactly the
	 * "config shadow" failure this task existed to end.
	 *
	 * <p><b>Atomic:</b> every field is parsed into locals first (a wrong-type value throws before anything is
	 * applied), then committed to the static fields in one block — a single typo in the file can never leave the
	 * live balance half-updated. <b>Self-healing on load:</b> after a successful parse the file is re-serialized in
	 * canonical form (sections + field comments + any newly added mod fields) and rewritten only when its content
	 * actually differs, so existing installs gain the inline comments and the write is idempotent (no churn on
	 * {@code /reload}).
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

			int fileVersion = readSchemaVersion(o);
			if (fileVersion > SCHEMA_VERSION) {
				Industrialization.LOGGER.warn("[config] {} declares schemaVersion {}, but this build only"
						+ " understands {}. NOTHING from the file was applied — the balance is now the mod's"
						+ " built-in defaults, and the file is left untouched. Downgrade the file or upgrade"
						+ " the mod.", path, fileVersion, SCHEMA_VERSION);
				resetToDefaults();
				return LoadResult.SCHEMA_TOO_NEW;
			}
			migrate(o, fileVersion, path);

			// --- staging: parse + validate every field into pending commits; a present-but-wrong-type
			//     key throws here, before any static field is touched (atomic all-or-nothing apply below).
			//     Each field is read out of its own section object, resolved once up front so a section
			//     holding something other than an object also fails before any commit. ---
			JsonObject[] bodies = new JsonObject[Section.values().length];
			for (Section section : Section.values()) {
				bodies[section.ordinal()] = sectionBody(o, section);
			}
			List<Runnable> pending = new ArrayList<>(FIELDS.size());
			for (ConfigField field : FIELDS) {
				pending.add(field.stage(bodies[field.section.ordinal()]));
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

	/**
	 * Layout version recorded in {@code file}. Absent means "written before MOD-402", i.e. the flat
	 * layout, which is version 0 — the one case where a missing key is information rather than a
	 * default. A present-but-not-a-number value throws, exactly like any other wrong-type key.
	 */
	private static int readSchemaVersion(JsonObject file) {
		JsonElement e = file.get(SCHEMA_VERSION_KEY);
		if (e == null || e.isJsonNull()) {
			return 0;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			return e.getAsInt();
		}
		throw new IllegalArgumentException("config key '" + SCHEMA_VERSION_KEY + "' must be a number, got " + e);
	}

	/**
	 * Walk {@code file} up the {@link #MIGRATIONS} ladder from {@code fileVersion} to
	 * {@link #SCHEMA_VERSION}, rewriting it in place. Called before staging, so the rest of the load
	 * only ever sees a current-shape document. Nothing is written back here — what lands on disk is
	 * {@link #canonicalJson()}, rebuilt from the live values by the self-heal step.
	 */
	private static void migrate(JsonObject file, int fileVersion, Path path) {
		if (fileVersion >= SCHEMA_VERSION) {
			return;
		}
		for (Migration step : MIGRATIONS) {
			if (step.fromVersion() >= fileVersion) {
				step.apply().accept(file);
			}
		}
		Industrialization.LOGGER.info("[config] migrated {} from schemaVersion {} to {}",
				path, fileVersion, SCHEMA_VERSION);
	}

	/**
	 * The section's object inside {@code file}, or an empty one when the section is absent (every key
	 * in it then falls back to its live value, the same contract a missing key has always had). A
	 * section key present with a non-object value is a typo in the operator's file and throws, so the
	 * load aborts before any commit instead of silently ignoring a whole group of knobs.
	 */
	private static JsonObject sectionBody(JsonObject file, Section section) {
		JsonElement e = file.get(section.id);
		if (e == null || e.isJsonNull()) {
			return new JsonObject();
		}
		if (e.isJsonObject()) {
			return e.getAsJsonObject();
		}
		throw new IllegalArgumentException("config section '" + section.id + "' must be an object, got " + e);
	}

	/**
	 * Restore every tunable to the value compiled into this build — the state a fresh install starts
	 * from. Used by {@link #loadFrom} when the file's schema is from the future: leaving whatever the
	 * previous load happened to apply would mean the server keeps running on a file nobody can read
	 * any more, which is the silent-corruption case this guard exists to prevent.
	 */
	private static void resetToDefaults() {
		for (ConfigField field : FIELDS) {
			field.resetToDefault();
		}
	}

	/** Ignore line-ending + surrounding-whitespace differences when deciding whether to rewrite the file. */
	private static String normalize(String s) {
		return s.replace("\r\n", "\n").strip();
	}

	/**
	 * Read an int by key from a section body with the {@link net.minecraft.util.GsonHelper}-equivalent contract,
	 * but on plain Gson so {@link Config} carries no {@code net.minecraft} dependency: return {@code def} if the
	 * key is absent/null, else the number — and <b>throw</b> if the key is present but not a number (this is what
	 * makes a typo abort the whole load instead of silently applying a partial file). {@code _comment_*} keys are
	 * never requested.
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

	/**
	 * The whole current balance as the canonical document: {@code schemaVersion} first, then one object
	 * per {@link Section} in enum order, each holding its fields in {@code FIELDS} order with their
	 * inline {@code _comment_} doc above them.
	 */
	private static JsonObject snapshot() {
		JsonObject root = new JsonObject();
		root.addProperty("_comment_" + SCHEMA_VERSION_KEY, SCHEMA_VERSION_DOC);
		root.addProperty(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
		for (Section section : Section.values()) {
			JsonObject body = new JsonObject();
			for (ConfigField field : FIELDS) {
				if (field.section == section) {
					field.write(body);
				}
			}
			root.addProperty("_comment_" + section.id, section.doc);
			root.add(section.id, body);
		}
		return root;
	}

	/** Add an inline {@code _comment_<field>} doc string immediately before its field, inside the field's
	 * section object (Gson keeps insertion order, so the comment renders on the line above). Ignored on
	 * read — {@link #getInt}/{@link #getFloat} only ever request the real field keys. */
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
		/** Which JSON object this key lives in (MOD-402); declared with the field, never in a side table. */
		final Section section;
		final String doc;

		ConfigField(String key, Section section, String doc) {
			this.key = key;
			this.section = section;
			this.doc = doc;
		}

		/**
		 * Parse and validate this field out of {@code body} — its own section's object — returning the
		 * action that commits it. Throws if the key is present with a wrong type: that is what aborts
		 * the whole load before any field is applied, keeping {@link #loadFrom} all-or-nothing.
		 */
		abstract Runnable stage(JsonObject body);

		/** Append the current live value (and its doc comment) to its section in the canonical snapshot. */
		abstract void write(JsonObject out);

		/**
		 * Put the field back to the value compiled into this build (captured when the registry was
		 * built, before any file could touch it). Used by {@link #resetToDefaults()}.
		 */
		abstract void resetToDefault();
	}

	private static final class IntField extends ConfigField {
		private final IntSupplier getter;
		private final IntConsumer setter;
		private final int fallback;
		private final Integer minimum;
		private final Integer floorTo;

		IntField(String key, Section section, String doc, IntSupplier getter, IntConsumer setter) {
			this(key, section, doc, getter, setter, null, null);
		}

		IntField(String key, Section section, String doc, IntSupplier getter, IntConsumer setter,
				Integer minimum) {
			this(key, section, doc, getter, setter, minimum, null);
		}

		IntField(String key, Section section, String doc, IntSupplier getter, IntConsumer setter,
				Integer minimum, Integer floorTo) {
			super(key, section, doc);
			this.getter = getter;
			this.setter = setter;
			this.fallback = getter.getAsInt();
			this.minimum = minimum;
			this.floorTo = floorTo;
		}

		@Override
		Runnable stage(JsonObject body) {
			int v = getInt(body, key, getter.getAsInt());
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

		@Override
		void resetToDefault() {
			setter.accept(fallback);
		}
	}

	private static final class FloatField extends ConfigField {
		private final FloatSupplier getter;
		private final FloatConsumer setter;
		private final float fallback;
		private final Float minimumExclusive;

		FloatField(String key, Section section, String doc, FloatSupplier getter, FloatConsumer setter) {
			this(key, section, doc, getter, setter, null);
		}

		FloatField(String key, Section section, String doc, FloatSupplier getter, FloatConsumer setter,
				Float minimumExclusive) {
			super(key, section, doc);
			this.getter = getter;
			this.setter = setter;
			this.fallback = getter.get();
			this.minimumExclusive = minimumExclusive;
		}

		@Override
		Runnable stage(JsonObject body) {
			float v = getFloat(body, key, getter.get());
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

		@Override
		void resetToDefault() {
			setter.accept(fallback);
		}
	}

	private static final class DoubleField extends ConfigField {
		private final DoubleSupplier getter;
		private final DoubleConsumer setter;
		private final double fallback;
		private final double minimum;
		private final double floorTo;

		DoubleField(String key, Section section, String doc, DoubleSupplier getter, DoubleConsumer setter,
				double minimum, double floorTo) {
			super(key, section, doc);
			this.getter = getter;
			this.setter = setter;
			this.fallback = getter.getAsDouble();
			this.minimum = minimum;
			this.floorTo = floorTo;
		}

		@Override
		Runnable stage(JsonObject body) {
			double v = getDouble(body, key, getter.getAsDouble());
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

		@Override
		void resetToDefault() {
			setter.accept(fallback);
		}
	}

	private static final class BoolField extends ConfigField {
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;
		private final boolean fallback;

		BoolField(String key, Section section, String doc, BooleanSupplier getter, Consumer<Boolean> setter) {
			super(key, section, doc);
			this.getter = getter;
			this.setter = setter;
			this.fallback = getter.getAsBoolean();
		}

		@Override
		Runnable stage(JsonObject body) {
			boolean applied = getBool(body, key, getter.getAsBoolean());
			return () -> setter.accept(applied);
		}

		@Override
		void write(JsonObject out) {
			c(out, key, doc);
			out.addProperty(key, getter.getAsBoolean());
		}

		@Override
		void resetToDefault() {
			setter.accept(fallback);
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
