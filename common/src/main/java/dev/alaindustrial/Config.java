package dev.alaindustrial;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.GsonHelper;

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

	// --- Pump (LV, EU-powered fluid mover) ---
	/** EU spent per bucket of fluid the pump moves (extract + push). The pump is one of the most
	 * energy-hungry machines — at 1000 EU/bucket it is a noticeable consumer, while a bucket of lava
	 * still yields 16 000 EU in the geothermal generator (16× payback on the pump's own tax). */
	public static int pumpEuPerBucket = 1000;

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
	 * energy network can keep the pump fed across its 32 EU/t LV intake without stalling just below the
	 * per-bucket threshold. */
	public static int pumpBuffer = 4000;
	public static int generatorBuffer = 4000;
	public static int geothermalBuffer = 4000;
	public static int waterMillBuffer = 4000;
	public static int windMillBuffer = 4000;
	/** Shared buffer for both T2 wind mills (high-altitude + storm). */
	public static int t2WindMillBuffer = 8000;
	public static int solarBuffer = 8000;
	public static int cableBuffer = 64;

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
		torch is a comfort action, cheaper than mining a block ({@link #electricDrillEuPerBlock} = 50). The
		drain is skipped gracefully when the drill holds less than this — the torch still places, matching the
		drill's "degrades but keeps working" philosophy for a flat battery. */
	public static int electricDrillTorchEuCost = 5;

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

	/** Load the config file at {@code path}, or write the v0.2 defaults if it does not exist yet. */
	public static void loadFrom(Path path) {
		try {
			if (Files.exists(path)) {
				try (BufferedReader reader = Files.newBufferedReader(path)) {
					JsonObject o = GsonHelper.parse(reader);
					globalEuRateMultiplier = GsonHelper.getAsFloat(o, "globalEuRateMultiplier", globalEuRateMultiplier);
					if (globalEuRateMultiplier <= 0) {
						globalEuRateMultiplier = 1.0f;
					}
					globalMachineSpeedMultiplier = GsonHelper.getAsFloat(o, "globalMachineSpeedMultiplier", globalMachineSpeedMultiplier);
					if (globalMachineSpeedMultiplier <= 0) {
						globalMachineSpeedMultiplier = 1.0f;
					}
					solarEuPerTick = GsonHelper.getAsInt(o, "solarEuPerTick", solarEuPerTick);
					daylightEuPerTick = GsonHelper.getAsInt(o, "daylightEuPerTick", daylightEuPerTick);
					moonlitEuPerTick = GsonHelper.getAsInt(o, "moonlitEuPerTick", moonlitEuPerTick);
					moonlitWeatherEuPerTick = GsonHelper.getAsInt(o, "moonlitWeatherEuPerTick", moonlitWeatherEuPerTick);
					fuelEuPerTick = GsonHelper.getAsInt(o, "fuelEuPerTick", fuelEuPerTick);
					geothermalEuPerTick = GsonHelper.getAsInt(o, "geothermalEuPerTick", geothermalEuPerTick);
					geothermalBurnTicks = GsonHelper.getAsInt(o, "geothermalBurnTicks", geothermalBurnTicks);
					waterMillEuPerTick = GsonHelper.getAsInt(o, "waterMillEuPerTick", waterMillEuPerTick);
					windMillMaxBaseEuPerTick = GsonHelper.getAsInt(o, "windMillMaxBaseEuPerTick", windMillMaxBaseEuPerTick);
					windMillMaxEuPerTick = GsonHelper.getAsInt(o, "windMillMaxEuPerTick", windMillMaxEuPerTick);
					windMillRainFactor = GsonHelper.getAsFloat(o, "windMillRainFactor", windMillRainFactor);
					windMillThunderFactor = GsonHelper.getAsFloat(o, "windMillThunderFactor", windMillThunderFactor);
					windMillSampleTicks = GsonHelper.getAsInt(o, "windMillSampleTicks", windMillSampleTicks);
					if (windMillSampleTicks <= 0) {
						windMillSampleTicks = 40;
					}
					windMillEvolveTicks = GsonHelper.getAsInt(o, "windMillEvolveTicks", windMillEvolveTicks);
					if (windMillEvolveTicks <= 0) {
						windMillEvolveTicks = 33_600;
					}
					highAltWindMillMaxBaseEuPerTick = GsonHelper.getAsInt(o, "highAltWindMillMaxBaseEuPerTick", highAltWindMillMaxBaseEuPerTick);
					highAltWindMillBlocksPerBase = GsonHelper.getAsInt(o, "highAltWindMillBlocksPerBase", highAltWindMillBlocksPerBase);
					if (highAltWindMillBlocksPerBase <= 0) {
						highAltWindMillBlocksPerBase = 8;
					}
					highAltWindMillMaxEuPerTick = GsonHelper.getAsInt(o, "highAltWindMillMaxEuPerTick", highAltWindMillMaxEuPerTick);
					stormWindMillMaxBaseEuPerTick = GsonHelper.getAsInt(o, "stormWindMillMaxBaseEuPerTick", stormWindMillMaxBaseEuPerTick);
					stormWindMillRainFactor = GsonHelper.getAsFloat(o, "stormWindMillRainFactor", stormWindMillRainFactor);
					stormWindMillThunderFactor = GsonHelper.getAsFloat(o, "stormWindMillThunderFactor", stormWindMillThunderFactor);
					stormWindMillMaxEuPerTick = GsonHelper.getAsInt(o, "stormWindMillMaxEuPerTick", stormWindMillMaxEuPerTick);
					solarTransparentFactor = GsonHelper.getAsFloat(o, "solarTransparentFactor", solarTransparentFactor);
					solarSnowFactor = GsonHelper.getAsFloat(o, "solarSnowFactor", solarSnowFactor);
					solarEvolveTicks = GsonHelper.getAsInt(o, "solarEvolveTicks", solarEvolveTicks);
					if (solarEvolveTicks <= 0) {
						solarEvolveTicks = 33_600;
					}
					pumpEuPerBucket = GsonHelper.getAsInt(o, "pumpEuPerBucket", pumpEuPerBucket);
					teleporterBuffer = GsonHelper.getAsInt(o, "teleporterBuffer", teleporterBuffer);
					if (teleporterBuffer <= 0) {
						teleporterBuffer = 500_000;
					}
					teleporterBaseCost = GsonHelper.getAsInt(o, "teleporterBaseCost", teleporterBaseCost);
					if (teleporterBaseCost < 0) {
						teleporterBaseCost = 5000;
					}
					teleporterCostPerBlock = GsonHelper.getAsInt(o, "teleporterCostPerBlock", teleporterCostPerBlock);
					if (teleporterCostPerBlock < 0) {
						teleporterCostPerBlock = 5;
					}
					teleporterWarmupTicks = GsonHelper.getAsInt(o, "teleporterWarmupTicks", teleporterWarmupTicks);
					if (teleporterWarmupTicks < 0) {
						teleporterWarmupTicks = 100;
					}
					teleporterCooldownTicks = GsonHelper.getAsInt(o, "teleporterCooldownTicks", teleporterCooldownTicks);
					if (teleporterCooldownTicks < 0) {
						teleporterCooldownTicks = 1200;
					}
					teleporterWarmupCancelRadius = GsonHelper.getAsInt(o, "teleporterWarmupCancelRadius",
							teleporterWarmupCancelRadius);
					if (teleporterWarmupCancelRadius <= 0) {
						teleporterWarmupCancelRadius = 2;
					}
					teleporterMaxPoints = GsonHelper.getAsInt(o, "teleporterMaxPoints", teleporterMaxPoints);
					if (teleporterMaxPoints <= 0) {
						teleporterMaxPoints = 16;
					}
					batteryBoxBuffer = GsonHelper.getAsInt(o, "batteryBoxBuffer", batteryBoxBuffer);
					maceratorBuffer = GsonHelper.getAsInt(o, "maceratorBuffer", maceratorBuffer);
					machineBuffer = GsonHelper.getAsInt(o, "machineBuffer", machineBuffer);
					pumpBuffer = GsonHelper.getAsInt(o, "pumpBuffer", pumpBuffer);
					generatorBuffer = GsonHelper.getAsInt(o, "generatorBuffer", generatorBuffer);
					geothermalBuffer = GsonHelper.getAsInt(o, "geothermalBuffer", geothermalBuffer);
					waterMillBuffer = GsonHelper.getAsInt(o, "waterMillBuffer", waterMillBuffer);
					windMillBuffer = GsonHelper.getAsInt(o, "windMillBuffer", windMillBuffer);
					t2WindMillBuffer = GsonHelper.getAsInt(o, "t2WindMillBuffer", t2WindMillBuffer);
					solarBuffer = GsonHelper.getAsInt(o, "solarBuffer", solarBuffer);
					cableBuffer = GsonHelper.getAsInt(o, "cableBuffer", cableBuffer);
					// MOD-108 renamed the pipe throughput keys (itemPipeItemsPerTick → PerTransfer + an
					// interval). The old key is deliberately NOT read: it meant "per tick", and silently
					// reusing that number as a per-transfer batch would keep an existing config at the very
					// speed this rebalance removes. An old config simply falls back to the new defaults.
					itemPipeItemsPerTransfer =
							GsonHelper.getAsInt(o, "itemPipeItemsPerTransfer", itemPipeItemsPerTransfer);
					if (itemPipeItemsPerTransfer <= 0) {
						itemPipeItemsPerTransfer = 2;
					}
					itemPipeTransferIntervalTicks =
							GsonHelper.getAsInt(o, "itemPipeTransferIntervalTicks", itemPipeTransferIntervalTicks);
					if (itemPipeTransferIntervalTicks <= 0) {
						itemPipeTransferIntervalTicks = 20;
					}
					lvPouchCapacity = GsonHelper.getAsInt(o, "lvPouchCapacity", lvPouchCapacity);
					if (lvPouchCapacity <= 0) {
						lvPouchCapacity = 128;
					}
					lvPouchBuffer = GsonHelper.getAsInt(o, "lvPouchBuffer", lvPouchBuffer);
					if (lvPouchBuffer <= 0) {
						lvPouchBuffer = 2000;
					}
					lvPouchDrainPerSecond = GsonHelper.getAsInt(o, "lvPouchDrainPerSecond", lvPouchDrainPerSecond);
					if (lvPouchDrainPerSecond < 0) {
						lvPouchDrainPerSecond = 1;
					}
					energyPackBuffer = GsonHelper.getAsInt(o, "energyPackBuffer", energyPackBuffer);
					if (energyPackBuffer <= 0) {
						energyPackBuffer = 20_000;
					}
					energyPackInputRate = GsonHelper.getAsInt(o, "energyPackInputRate", energyPackInputRate);
					if (energyPackInputRate <= 0) {
						energyPackInputRate = 32;
					}
					energyPackOutputRate = GsonHelper.getAsInt(o, "energyPackOutputRate", energyPackOutputRate);
					if (energyPackOutputRate <= 0) {
						energyPackOutputRate = 32;
					}
					electricDrillBuffer = GsonHelper.getAsInt(o, "electricDrillBuffer", electricDrillBuffer);
					if (electricDrillBuffer <= 0) {
						electricDrillBuffer = 10_000;
					}
					electricDrillEuPerBlock = GsonHelper.getAsInt(o, "electricDrillEuPerBlock", electricDrillEuPerBlock);
					if (electricDrillEuPerBlock <= 0) {
						electricDrillEuPerBlock = 50;
					}
					electricDrillInputRate = GsonHelper.getAsInt(o, "electricDrillInputRate", electricDrillInputRate);
					if (electricDrillInputRate <= 0) {
						electricDrillInputRate = 32;
					}
					electricDrillTorchEuCost = GsonHelper.getAsInt(o, "electricDrillTorchEuCost", electricDrillTorchEuCost);
					if (electricDrillTorchEuCost < 0) {
						electricDrillTorchEuCost = 5;
					}
					stockFrameScanIntervalTicks = GsonHelper.getAsInt(o, "stockFrameScanIntervalTicks", stockFrameScanIntervalTicks);
					if (stockFrameScanIntervalTicks <= 0) {
						stockFrameScanIntervalTicks = 20;
					}
					machineEuPerTick = GsonHelper.getAsInt(o, "machineEuPerTick", machineEuPerTick);
					maceratorDuration = GsonHelper.getAsInt(o, "maceratorDuration", maceratorDuration);
					electricFurnaceDuration = GsonHelper.getAsInt(o, "electricFurnaceDuration", electricFurnaceDuration);
					compressorDuration = GsonHelper.getAsInt(o, "compressorDuration", compressorDuration);
					extractorDuration = GsonHelper.getAsInt(o, "extractorDuration", extractorDuration);
					copperCableLossPerBlock = GsonHelper.getAsFloat(o, "copperCableLossPerBlock", (float) copperCableLossPerBlock);
					if (copperCableLossPerBlock < 0) {
						copperCableLossPerBlock = 0.0;
					}
					networksPerTick = GsonHelper.getAsInt(o, "networksPerTick", networksPerTick);
					if (networksPerTick <= 0) {
						networksPerTick = 512;
					}
					networkAnalyzerMaxTraversedNetworks = GsonHelper.getAsInt(o, "networkAnalyzerMaxTraversedNetworks",
							networkAnalyzerMaxTraversedNetworks);
					if (networkAnalyzerMaxTraversedNetworks < 1) {
						networkAnalyzerMaxTraversedNetworks = 32;
					}
				}
				Industrialization.LOGGER.info("[config] loaded {}", path);
			} else {
				Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(snapshot()));
				Industrialization.LOGGER.info("[config] wrote defaults to {}", path);
			}
		} catch (Exception e) {
			Industrialization.LOGGER.error("[config] failed to load {}: {}", path, e.toString());
		}
	}

	private static JsonObject snapshot() {
		JsonObject o = new JsonObject();
		o.addProperty("globalEuRateMultiplier", globalEuRateMultiplier);
		o.addProperty("globalMachineSpeedMultiplier", globalMachineSpeedMultiplier);
		o.addProperty("solarEuPerTick", solarEuPerTick);
		o.addProperty("daylightEuPerTick", daylightEuPerTick);
		o.addProperty("moonlitEuPerTick", moonlitEuPerTick);
		o.addProperty("moonlitWeatherEuPerTick", moonlitWeatherEuPerTick);
		o.addProperty("fuelEuPerTick", fuelEuPerTick);
		o.addProperty("geothermalEuPerTick", geothermalEuPerTick);
		o.addProperty("geothermalBurnTicks", geothermalBurnTicks);
		o.addProperty("waterMillEuPerTick", waterMillEuPerTick);
		o.addProperty("windMillMaxBaseEuPerTick", windMillMaxBaseEuPerTick);
		o.addProperty("windMillMaxEuPerTick", windMillMaxEuPerTick);
		o.addProperty("windMillRainFactor", windMillRainFactor);
		o.addProperty("windMillThunderFactor", windMillThunderFactor);
		o.addProperty("windMillSampleTicks", windMillSampleTicks);
		o.addProperty("windMillEvolveTicks", windMillEvolveTicks);
		o.addProperty("highAltWindMillMaxBaseEuPerTick", highAltWindMillMaxBaseEuPerTick);
		o.addProperty("highAltWindMillBlocksPerBase", highAltWindMillBlocksPerBase);
		o.addProperty("highAltWindMillMaxEuPerTick", highAltWindMillMaxEuPerTick);
		o.addProperty("stormWindMillMaxBaseEuPerTick", stormWindMillMaxBaseEuPerTick);
		o.addProperty("stormWindMillRainFactor", stormWindMillRainFactor);
		o.addProperty("stormWindMillThunderFactor", stormWindMillThunderFactor);
		o.addProperty("stormWindMillMaxEuPerTick", stormWindMillMaxEuPerTick);
		o.addProperty("solarTransparentFactor", solarTransparentFactor);
		o.addProperty("solarSnowFactor", solarSnowFactor);
		o.addProperty("solarEvolveTicks", solarEvolveTicks);
		o.addProperty("pumpEuPerBucket", pumpEuPerBucket);
		o.addProperty("teleporterBuffer", teleporterBuffer);
		o.addProperty("teleporterBaseCost", teleporterBaseCost);
		o.addProperty("teleporterCostPerBlock", teleporterCostPerBlock);
		o.addProperty("teleporterWarmupTicks", teleporterWarmupTicks);
		o.addProperty("teleporterCooldownTicks", teleporterCooldownTicks);
		o.addProperty("teleporterWarmupCancelRadius", teleporterWarmupCancelRadius);
		o.addProperty("teleporterMaxPoints", teleporterMaxPoints);
		o.addProperty("batteryBoxBuffer", batteryBoxBuffer);
		o.addProperty("maceratorBuffer", maceratorBuffer);
		o.addProperty("machineBuffer", machineBuffer);
		o.addProperty("pumpBuffer", pumpBuffer);
		o.addProperty("generatorBuffer", generatorBuffer);
		o.addProperty("geothermalBuffer", geothermalBuffer);
		o.addProperty("waterMillBuffer", waterMillBuffer);
		o.addProperty("windMillBuffer", windMillBuffer);
		o.addProperty("t2WindMillBuffer", t2WindMillBuffer);
		o.addProperty("solarBuffer", solarBuffer);
		o.addProperty("cableBuffer", cableBuffer);
		o.addProperty("itemPipeItemsPerTransfer", itemPipeItemsPerTransfer);
		o.addProperty("itemPipeTransferIntervalTicks", itemPipeTransferIntervalTicks);
		o.addProperty("lvPouchCapacity", lvPouchCapacity);
		o.addProperty("lvPouchBuffer", lvPouchBuffer);
		o.addProperty("lvPouchDrainPerSecond", lvPouchDrainPerSecond);
		o.addProperty("energyPackBuffer", energyPackBuffer);
		o.addProperty("energyPackInputRate", energyPackInputRate);
		o.addProperty("energyPackOutputRate", energyPackOutputRate);
		o.addProperty("electricDrillBuffer", electricDrillBuffer);
		o.addProperty("electricDrillEuPerBlock", electricDrillEuPerBlock);
		o.addProperty("electricDrillInputRate", electricDrillInputRate);
		o.addProperty("electricDrillTorchEuCost", electricDrillTorchEuCost);
		o.addProperty("stockFrameScanIntervalTicks", stockFrameScanIntervalTicks);
		o.addProperty("machineEuPerTick", machineEuPerTick);
		o.addProperty("maceratorDuration", maceratorDuration);
		o.addProperty("electricFurnaceDuration", electricFurnaceDuration);
		o.addProperty("compressorDuration", compressorDuration);
		o.addProperty("extractorDuration", extractorDuration);
		o.addProperty("copperCableLossPerBlock", copperCableLossPerBlock);
		o.addProperty("networksPerTick", networksPerTick);
		o.addProperty("networkAnalyzerMaxTraversedNetworks", networkAnalyzerMaxTraversedNetworks);
		return o;
	}
}
