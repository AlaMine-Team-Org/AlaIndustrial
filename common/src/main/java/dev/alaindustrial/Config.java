package dev.alaindustrial;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
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

	// --- Pump (LV, EU-powered fluid mover) ---
	/** EU spent per bucket of fluid the pump moves (extract + push). The pump is one of the most
	 * energy-hungry machines — at 1000 EU/bucket it is a noticeable consumer, while a bucket of lava
	 * still yields 16 000 EU in the geothermal generator (16× payback on the pump's own tax). */
	public static int pumpEuPerBucket = 1000;

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
		torch is a comfort action, cheaper than mining a block ({@link #electricDrillEuPerBlock} = 50). Below
		this charge the drill refuses to place and notifies the player instead of giving a free torch
		(MOD-097) — the torch is powered, not free. */
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

	// --- Iron Furnace (fuel-based, MOD-115): ticks to smelt one item. Vanilla furnace = 200. ---
	/** Ticks the iron furnace needs to smelt one item on fuel. Between vanilla (200) and the
	 * electric furnace, so it reads as "a bit faster than stone" without devaluing the electric tier. */
	public static int ironFurnaceCookTime = 150;

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

			// --- staging: read + validate every field into locals; a present-but-wrong-type key throws here,
			//     before any static field is touched (atomic all-or-nothing apply below). ---
			float sGlobalEuRateMultiplier = getFloat(o, "globalEuRateMultiplier", globalEuRateMultiplier);
			if (sGlobalEuRateMultiplier <= 0) {
				sGlobalEuRateMultiplier = 1.0f;
			}
			float sGlobalMachineSpeedMultiplier = getFloat(o, "globalMachineSpeedMultiplier", globalMachineSpeedMultiplier);
			if (sGlobalMachineSpeedMultiplier <= 0) {
				sGlobalMachineSpeedMultiplier = 1.0f;
			}
			int sSolarEuPerTick = getInt(o, "solarEuPerTick", solarEuPerTick);
			int sDaylightEuPerTick = getInt(o, "daylightEuPerTick", daylightEuPerTick);
			int sMoonlitEuPerTick = getInt(o, "moonlitEuPerTick", moonlitEuPerTick);
			int sMoonlitWeatherEuPerTick = getInt(o, "moonlitWeatherEuPerTick", moonlitWeatherEuPerTick);
			int sFuelEuPerTick = getInt(o, "fuelEuPerTick", fuelEuPerTick);
			int sGeothermalEuPerTick = getInt(o, "geothermalEuPerTick", geothermalEuPerTick);
			int sGeothermalBurnTicks = getInt(o, "geothermalBurnTicks", geothermalBurnTicks);
			int sWaterMillEuPerTick = getInt(o, "waterMillEuPerTick", waterMillEuPerTick);
			int sWindMillMaxBaseEuPerTick = getInt(o, "windMillMaxBaseEuPerTick", windMillMaxBaseEuPerTick);
			int sWindMillMaxEuPerTick = getInt(o, "windMillMaxEuPerTick", windMillMaxEuPerTick);
			float sWindMillRainFactor = getFloat(o, "windMillRainFactor", windMillRainFactor);
			float sWindMillThunderFactor = getFloat(o, "windMillThunderFactor", windMillThunderFactor);
			int sWindMillSampleTicks = getInt(o, "windMillSampleTicks", windMillSampleTicks);
			if (sWindMillSampleTicks <= 0) {
				sWindMillSampleTicks = 40;
			}
			int sWindMillEvolveTicks = getInt(o, "windMillEvolveTicks", windMillEvolveTicks);
			if (sWindMillEvolveTicks <= 0) {
				sWindMillEvolveTicks = 33_600;
			}
			int sHighAltWindMillMaxBaseEuPerTick = getInt(o, "highAltWindMillMaxBaseEuPerTick", highAltWindMillMaxBaseEuPerTick);
			int sHighAltWindMillBlocksPerBase = getInt(o, "highAltWindMillBlocksPerBase", highAltWindMillBlocksPerBase);
			if (sHighAltWindMillBlocksPerBase <= 0) {
				sHighAltWindMillBlocksPerBase = 8;
			}
			int sHighAltWindMillMaxEuPerTick = getInt(o, "highAltWindMillMaxEuPerTick", highAltWindMillMaxEuPerTick);
			int sStormWindMillMaxBaseEuPerTick = getInt(o, "stormWindMillMaxBaseEuPerTick", stormWindMillMaxBaseEuPerTick);
			float sStormWindMillRainFactor = getFloat(o, "stormWindMillRainFactor", stormWindMillRainFactor);
			float sStormWindMillThunderFactor = getFloat(o, "stormWindMillThunderFactor", stormWindMillThunderFactor);
			int sStormWindMillMaxEuPerTick = getInt(o, "stormWindMillMaxEuPerTick", stormWindMillMaxEuPerTick);
			float sSolarTransparentFactor = getFloat(o, "solarTransparentFactor", solarTransparentFactor);
			float sSolarSnowFactor = getFloat(o, "solarSnowFactor", solarSnowFactor);
			int sSolarEvolveTicks = getInt(o, "solarEvolveTicks", solarEvolveTicks);
			if (sSolarEvolveTicks <= 0) {
				sSolarEvolveTicks = 33_600;
			}
			int sPumpEuPerBucket = getInt(o, "pumpEuPerBucket", pumpEuPerBucket);
			int sFluidTankCapacity = getInt(o, "fluidTankCapacity", fluidTankCapacity);
			if (sFluidTankCapacity <= 0) {
				sFluidTankCapacity = 8000;
			}
			int sTeleporterBuffer = getInt(o, "teleporterBuffer", teleporterBuffer);
			if (sTeleporterBuffer <= 0) {
				sTeleporterBuffer = 500_000;
			}
			int sTeleporterBaseCost = getInt(o, "teleporterBaseCost", teleporterBaseCost);
			if (sTeleporterBaseCost < 0) {
				sTeleporterBaseCost = 5000;
			}
			int sTeleporterCostPerBlock = getInt(o, "teleporterCostPerBlock", teleporterCostPerBlock);
			if (sTeleporterCostPerBlock < 0) {
				sTeleporterCostPerBlock = 5;
			}
			int sTeleporterWarmupTicks = getInt(o, "teleporterWarmupTicks", teleporterWarmupTicks);
			if (sTeleporterWarmupTicks < 0) {
				sTeleporterWarmupTicks = 100;
			}
			int sTeleporterCooldownTicks = getInt(o, "teleporterCooldownTicks", teleporterCooldownTicks);
			if (sTeleporterCooldownTicks < 0) {
				sTeleporterCooldownTicks = 1200;
			}
			int sTeleporterWarmupCancelRadius = getInt(o, "teleporterWarmupCancelRadius", teleporterWarmupCancelRadius);
			if (sTeleporterWarmupCancelRadius <= 0) {
				sTeleporterWarmupCancelRadius = 2;
			}
			int sTeleporterMaxPoints = getInt(o, "teleporterMaxPoints", teleporterMaxPoints);
			if (sTeleporterMaxPoints <= 0) {
				sTeleporterMaxPoints = 16;
			}
			int sBatteryBoxBuffer = getInt(o, "batteryBoxBuffer", batteryBoxBuffer);
			int sMaceratorBuffer = getInt(o, "maceratorBuffer", maceratorBuffer);
			int sMachineBuffer = getInt(o, "machineBuffer", machineBuffer);
			int sPumpBuffer = getInt(o, "pumpBuffer", pumpBuffer);
			int sGeneratorBuffer = getInt(o, "generatorBuffer", generatorBuffer);
			int sGeothermalBuffer = getInt(o, "geothermalBuffer", geothermalBuffer);
			int sWaterMillBuffer = getInt(o, "waterMillBuffer", waterMillBuffer);
			int sWindMillBuffer = getInt(o, "windMillBuffer", windMillBuffer);
			int sT2WindMillBuffer = getInt(o, "t2WindMillBuffer", t2WindMillBuffer);
			int sSolarBuffer = getInt(o, "solarBuffer", solarBuffer);
			int sCableBuffer = getInt(o, "cableBuffer", cableBuffer);
			// MOD-108 renamed the pipe throughput keys (itemPipeItemsPerTick → PerTransfer + an
			// interval). The old key is deliberately NOT read: it meant "per tick", and silently
			// reusing that number as a per-transfer batch would keep an existing config at the very
			// speed this rebalance removes. An old config simply falls back to the new defaults.
			int sItemPipeItemsPerTransfer = getInt(o, "itemPipeItemsPerTransfer", itemPipeItemsPerTransfer);
			if (sItemPipeItemsPerTransfer <= 0) {
				sItemPipeItemsPerTransfer = 2;
			}
			int sItemPipeTransferIntervalTicks = getInt(o, "itemPipeTransferIntervalTicks", itemPipeTransferIntervalTicks);
			if (sItemPipeTransferIntervalTicks <= 0) {
				sItemPipeTransferIntervalTicks = 20;
			}
			int sLvPouchCapacity = getInt(o, "lvPouchCapacity", lvPouchCapacity);
			if (sLvPouchCapacity <= 0) {
				sLvPouchCapacity = 128;
			}
			int sLvPouchBuffer = getInt(o, "lvPouchBuffer", lvPouchBuffer);
			if (sLvPouchBuffer <= 0) {
				sLvPouchBuffer = 2000;
			}
			int sLvPouchDrainPerSecond = getInt(o, "lvPouchDrainPerSecond", lvPouchDrainPerSecond);
			if (sLvPouchDrainPerSecond < 0) {
				sLvPouchDrainPerSecond = 1;
			}
			int sEnergyPackBuffer = getInt(o, "energyPackBuffer", energyPackBuffer);
			if (sEnergyPackBuffer <= 0) {
				sEnergyPackBuffer = 20_000;
			}
			int sEnergyPackInputRate = getInt(o, "energyPackInputRate", energyPackInputRate);
			if (sEnergyPackInputRate <= 0) {
				sEnergyPackInputRate = 32;
			}
			int sEnergyPackOutputRate = getInt(o, "energyPackOutputRate", energyPackOutputRate);
			if (sEnergyPackOutputRate <= 0) {
				sEnergyPackOutputRate = 32;
			}
			int sElectricDrillBuffer = getInt(o, "electricDrillBuffer", electricDrillBuffer);
			if (sElectricDrillBuffer <= 0) {
				sElectricDrillBuffer = 10_000;
			}
			int sElectricDrillEuPerBlock = getInt(o, "electricDrillEuPerBlock", electricDrillEuPerBlock);
			if (sElectricDrillEuPerBlock <= 0) {
				sElectricDrillEuPerBlock = 50;
			}
			int sElectricDrillInputRate = getInt(o, "electricDrillInputRate", electricDrillInputRate);
			if (sElectricDrillInputRate <= 0) {
				sElectricDrillInputRate = 32;
			}
			int sElectricDrillTorchEuCost = getInt(o, "electricDrillTorchEuCost", electricDrillTorchEuCost);
			if (sElectricDrillTorchEuCost < 0) {
				sElectricDrillTorchEuCost = 5;
			}
			int sStockFrameScanIntervalTicks = getInt(o, "stockFrameScanIntervalTicks", stockFrameScanIntervalTicks);
			if (sStockFrameScanIntervalTicks <= 0) {
				sStockFrameScanIntervalTicks = 20;
			}
			int sMachineEuPerTick = getInt(o, "machineEuPerTick", machineEuPerTick);
			int sMaceratorDuration = getInt(o, "maceratorDuration", maceratorDuration);
			int sElectricFurnaceDuration = getInt(o, "electricFurnaceDuration", electricFurnaceDuration);
			int sCompressorDuration = getInt(o, "compressorDuration", compressorDuration);
			int sExtractorDuration = getInt(o, "extractorDuration", extractorDuration);
			int sIronFurnaceCookTime = getInt(o, "ironFurnaceCookTime", ironFurnaceCookTime);
			if (sIronFurnaceCookTime <= 0) {
				sIronFurnaceCookTime = 150;
			}
			double sCopperCableLossPerBlock = getFloat(o, "copperCableLossPerBlock", (float) copperCableLossPerBlock);
			if (sCopperCableLossPerBlock < 0) {
				sCopperCableLossPerBlock = 0.0;
			}
			int sNetworksPerTick = getInt(o, "networksPerTick", networksPerTick);
			if (sNetworksPerTick <= 0) {
				sNetworksPerTick = 512;
			}
			int sNetworkAnalyzerMaxTraversedNetworks = getInt(o, "networkAnalyzerMaxTraversedNetworks",
					networkAnalyzerMaxTraversedNetworks);
			if (sNetworkAnalyzerMaxTraversedNetworks < 1) {
				sNetworkAnalyzerMaxTraversedNetworks = 32;
			}
			boolean sBonusChestEnabled = getBool(o, "bonusChestEnabled", bonusChestEnabled);

			// --- commit: apply all staged values at once (nothing above threw, so this is all-or-nothing). ---
			globalEuRateMultiplier = sGlobalEuRateMultiplier;
			globalMachineSpeedMultiplier = sGlobalMachineSpeedMultiplier;
			solarEuPerTick = sSolarEuPerTick;
			daylightEuPerTick = sDaylightEuPerTick;
			moonlitEuPerTick = sMoonlitEuPerTick;
			moonlitWeatherEuPerTick = sMoonlitWeatherEuPerTick;
			fuelEuPerTick = sFuelEuPerTick;
			geothermalEuPerTick = sGeothermalEuPerTick;
			geothermalBurnTicks = sGeothermalBurnTicks;
			waterMillEuPerTick = sWaterMillEuPerTick;
			windMillMaxBaseEuPerTick = sWindMillMaxBaseEuPerTick;
			windMillMaxEuPerTick = sWindMillMaxEuPerTick;
			windMillRainFactor = sWindMillRainFactor;
			windMillThunderFactor = sWindMillThunderFactor;
			windMillSampleTicks = sWindMillSampleTicks;
			windMillEvolveTicks = sWindMillEvolveTicks;
			highAltWindMillMaxBaseEuPerTick = sHighAltWindMillMaxBaseEuPerTick;
			highAltWindMillBlocksPerBase = sHighAltWindMillBlocksPerBase;
			highAltWindMillMaxEuPerTick = sHighAltWindMillMaxEuPerTick;
			stormWindMillMaxBaseEuPerTick = sStormWindMillMaxBaseEuPerTick;
			stormWindMillRainFactor = sStormWindMillRainFactor;
			stormWindMillThunderFactor = sStormWindMillThunderFactor;
			stormWindMillMaxEuPerTick = sStormWindMillMaxEuPerTick;
			solarTransparentFactor = sSolarTransparentFactor;
			solarSnowFactor = sSolarSnowFactor;
			solarEvolveTicks = sSolarEvolveTicks;
			pumpEuPerBucket = sPumpEuPerBucket;
			fluidTankCapacity = sFluidTankCapacity;
			teleporterBuffer = sTeleporterBuffer;
			teleporterBaseCost = sTeleporterBaseCost;
			teleporterCostPerBlock = sTeleporterCostPerBlock;
			teleporterWarmupTicks = sTeleporterWarmupTicks;
			teleporterCooldownTicks = sTeleporterCooldownTicks;
			teleporterWarmupCancelRadius = sTeleporterWarmupCancelRadius;
			teleporterMaxPoints = sTeleporterMaxPoints;
			batteryBoxBuffer = sBatteryBoxBuffer;
			maceratorBuffer = sMaceratorBuffer;
			machineBuffer = sMachineBuffer;
			pumpBuffer = sPumpBuffer;
			generatorBuffer = sGeneratorBuffer;
			geothermalBuffer = sGeothermalBuffer;
			waterMillBuffer = sWaterMillBuffer;
			windMillBuffer = sWindMillBuffer;
			t2WindMillBuffer = sT2WindMillBuffer;
			solarBuffer = sSolarBuffer;
			cableBuffer = sCableBuffer;
			itemPipeItemsPerTransfer = sItemPipeItemsPerTransfer;
			itemPipeTransferIntervalTicks = sItemPipeTransferIntervalTicks;
			lvPouchCapacity = sLvPouchCapacity;
			lvPouchBuffer = sLvPouchBuffer;
			lvPouchDrainPerSecond = sLvPouchDrainPerSecond;
			energyPackBuffer = sEnergyPackBuffer;
			energyPackInputRate = sEnergyPackInputRate;
			energyPackOutputRate = sEnergyPackOutputRate;
			electricDrillBuffer = sElectricDrillBuffer;
			electricDrillEuPerBlock = sElectricDrillEuPerBlock;
			electricDrillInputRate = sElectricDrillInputRate;
			electricDrillTorchEuCost = sElectricDrillTorchEuCost;
			stockFrameScanIntervalTicks = sStockFrameScanIntervalTicks;
			machineEuPerTick = sMachineEuPerTick;
			maceratorDuration = sMaceratorDuration;
			electricFurnaceDuration = sElectricFurnaceDuration;
			compressorDuration = sCompressorDuration;
			extractorDuration = sExtractorDuration;
			ironFurnaceCookTime = sIronFurnaceCookTime;
			copperCableLossPerBlock = sCopperCableLossPerBlock;
			networksPerTick = sNetworksPerTick;
			networkAnalyzerMaxTraversedNetworks = sNetworkAnalyzerMaxTraversedNetworks;
			bonusChestEnabled = sBonusChestEnabled;
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
		c(o, "globalEuRateMultiplier", "Multiplier on EVERY generator's EU/t output. 1.0 = unchanged; 2.0 = twice the generation server-wide.");
		o.addProperty("globalEuRateMultiplier", globalEuRateMultiplier);
		c(o, "globalMachineSpeedMultiplier", "Machine speed multiplier (energy-neutral): higher = machines draw more EU/t but finish proportionally faster. 1.0 = unchanged.");
		o.addProperty("globalMachineSpeedMultiplier", globalMachineSpeedMultiplier);
		c(o, "solarEuPerTick", "Solar panel output in EU/t under clear daytime sky. The energy system's baseline (1).");
		o.addProperty("solarEuPerTick", solarEuPerTick);
		c(o, "daylightEuPerTick", "Evolved daylight-panel output in EU/t during the day.");
		o.addProperty("daylightEuPerTick", daylightEuPerTick);
		c(o, "moonlitEuPerTick", "Evolved moonlit-panel output in EU/t at night under clear sky.");
		o.addProperty("moonlitEuPerTick", moonlitEuPerTick);
		c(o, "moonlitWeatherEuPerTick", "Moonlit-panel EU/t at night during rain/thunder (a weather trickle).");
		o.addProperty("moonlitWeatherEuPerTick", moonlitWeatherEuPerTick);
		c(o, "fuelEuPerTick", "Fuel (solid-burnable) generator output in EU/t while burning.");
		o.addProperty("fuelEuPerTick", fuelEuPerTick);
		c(o, "geothermalEuPerTick", "Geothermal (lava) generator output in EU/t while burning lava.");
		o.addProperty("geothermalEuPerTick", geothermalEuPerTick);
		c(o, "geothermalBurnTicks", "Ticks of burn a geothermal generator gets per bucket of lava (20 ticks = 1 second).");
		o.addProperty("geothermalBurnTicks", geothermalBurnTicks);
		c(o, "waterMillEuPerTick", "Water mill EU/t per adjacent water block on its four sides (0..4 EU/t total).");
		o.addProperty("waterMillEuPerTick", waterMillEuPerTick);
		c(o, "windMillMaxBaseEuPerTick", "Wind mill clear-sky height cap in EU/t (base grows with altitude up to this).");
		o.addProperty("windMillMaxBaseEuPerTick", windMillMaxBaseEuPerTick);
		c(o, "windMillMaxEuPerTick", "Hard cap on wind mill EU/t after the weather multiplier.");
		o.addProperty("windMillMaxEuPerTick", windMillMaxEuPerTick);
		c(o, "windMillRainFactor", "Wind mill output multiplier while it is raining (not thundering).");
		o.addProperty("windMillRainFactor", windMillRainFactor);
		c(o, "windMillThunderFactor", "Wind mill output multiplier while it is thundering.");
		o.addProperty("windMillThunderFactor", windMillThunderFactor);
		c(o, "windMillSampleTicks", "How often (ticks) a wind mill re-samples height/sky/weather; rate is cached in between.");
		o.addProperty("windMillSampleTicks", windMillSampleTicks);
		c(o, "windMillEvolveTicks", "Active open-sky ticks (with rotor + evolution chip) to evolve a base wind mill into its T2 branch.");
		o.addProperty("windMillEvolveTicks", windMillEvolveTicks);
		c(o, "highAltWindMillMaxBaseEuPerTick", "High-altitude wind mill (T2) clear-sky height cap in EU/t.");
		o.addProperty("highAltWindMillMaxBaseEuPerTick", highAltWindMillMaxBaseEuPerTick);
		c(o, "highAltWindMillBlocksPerBase", "Blocks of height above sea level per +1 EU/t of base on the high-altitude T2 variant.");
		o.addProperty("highAltWindMillBlocksPerBase", highAltWindMillBlocksPerBase);
		c(o, "highAltWindMillMaxEuPerTick", "Hard cap on high-altitude T2 wind mill EU/t after the weather multiplier.");
		o.addProperty("highAltWindMillMaxEuPerTick", highAltWindMillMaxEuPerTick);
		c(o, "stormWindMillMaxBaseEuPerTick", "Storm wind mill (T2) clear-sky height cap in EU/t before the weather multiplier.");
		o.addProperty("stormWindMillMaxBaseEuPerTick", stormWindMillMaxBaseEuPerTick);
		c(o, "stormWindMillRainFactor", "Storm T2 wind mill output multiplier while it is raining.");
		o.addProperty("stormWindMillRainFactor", stormWindMillRainFactor);
		c(o, "stormWindMillThunderFactor", "Storm T2 wind mill output multiplier while it is thundering.");
		o.addProperty("stormWindMillThunderFactor", stormWindMillThunderFactor);
		c(o, "stormWindMillMaxEuPerTick", "Hard cap on storm T2 wind mill EU/t after the weather multiplier.");
		o.addProperty("stormWindMillMaxEuPerTick", stormWindMillMaxEuPerTick);
		c(o, "solarTransparentFactor", "Output multiplier when a solar panel sees sky through a translucent block (leaves, cobweb).");
		o.addProperty("solarTransparentFactor", solarTransparentFactor);
		c(o, "solarSnowFactor", "Output multiplier under snow (a snow layer above, or snowfall in a cold biome).");
		o.addProperty("solarSnowFactor", solarSnowFactor);
		c(o, "solarEvolveTicks", "Active sky-time ticks needed to evolve a base solar panel into its T2 branch.");
		o.addProperty("solarEvolveTicks", solarEvolveTicks);
		c(o, "pumpEuPerBucket", "EU the pump spends per bucket of fluid it moves (extract + push).");
		o.addProperty("pumpEuPerBucket", pumpEuPerBucket);
		c(o, "fluidTankCapacity", "Portable fluid tank capacity in mB (1000 mB = 1 bucket). Applies to newly placed tanks.");
		o.addProperty("fluidTankCapacity", fluidTankCapacity);
		c(o, "teleporterBuffer", "Teleporter station EU buffer. Applies to newly placed stations.");
		o.addProperty("teleporterBuffer", teleporterBuffer);
		c(o, "teleporterBaseCost", "Flat EU part of a jump's price (paid even for a short hop).");
		o.addProperty("teleporterBaseCost", teleporterBaseCost);
		c(o, "teleporterCostPerBlock", "Added EU per block of straight-line distance to the target station.");
		o.addProperty("teleporterCostPerBlock", teleporterCostPerBlock);
		c(o, "teleporterWarmupTicks", "Warmup before a jump fires (20 ticks = 1 second). Cancelled by damage.");
		o.addProperty("teleporterWarmupTicks", teleporterWarmupTicks);
		c(o, "teleporterCooldownTicks", "Per-player anti-spam lockout after landing (ticks).");
		o.addProperty("teleporterCooldownTicks", teleporterCooldownTicks);
		c(o, "teleporterWarmupCancelRadius", "Moving further than this many blocks from where warmup started cancels the jump.");
		o.addProperty("teleporterWarmupCancelRadius", teleporterWarmupCancelRadius);
		c(o, "teleporterMaxPoints", "Max stations one teleport remote can hold.");
		o.addProperty("teleporterMaxPoints", teleporterMaxPoints);
		c(o, "batteryBoxBuffer", "Battery Box EU buffer. Applies to newly placed blocks (already-placed keep their capacity until the chunk reloads).");
		o.addProperty("batteryBoxBuffer", batteryBoxBuffer);
		c(o, "maceratorBuffer", "Macerator EU buffer. Applies to newly placed blocks.");
		o.addProperty("maceratorBuffer", maceratorBuffer);
		c(o, "machineBuffer", "Shared EU buffer for electric furnace / compressor / extractor. Applies to newly placed blocks.");
		o.addProperty("machineBuffer", machineBuffer);
		c(o, "pumpBuffer", "Pump EU buffer. Applies to newly placed blocks.");
		o.addProperty("pumpBuffer", pumpBuffer);
		c(o, "generatorBuffer", "Fuel generator EU buffer. Applies to newly placed blocks.");
		o.addProperty("generatorBuffer", generatorBuffer);
		c(o, "geothermalBuffer", "Geothermal generator EU buffer. Applies to newly placed blocks.");
		o.addProperty("geothermalBuffer", geothermalBuffer);
		c(o, "waterMillBuffer", "Water mill EU buffer. Applies to newly placed blocks.");
		o.addProperty("waterMillBuffer", waterMillBuffer);
		c(o, "windMillBuffer", "Wind mill (T1) EU buffer. Applies to newly placed blocks.");
		o.addProperty("windMillBuffer", windMillBuffer);
		c(o, "t2WindMillBuffer", "Shared EU buffer for both T2 wind mills (high-altitude + storm). Applies to newly placed blocks.");
		o.addProperty("t2WindMillBuffer", t2WindMillBuffer);
		c(o, "solarBuffer", "Solar panel EU buffer. Applies to newly placed blocks.");
		o.addProperty("solarBuffer", solarBuffer);
		c(o, "cableBuffer", "Per-cable EU buffer. Applies to newly placed cables.");
		o.addProperty("cableBuffer", cableBuffer);
		c(o, "itemPipeItemsPerTransfer", "Items an item-pipe network moves per transfer. With the interval below this sets throughput.");
		o.addProperty("itemPipeItemsPerTransfer", itemPipeItemsPerTransfer);
		c(o, "itemPipeTransferIntervalTicks", "Server ticks between item-pipe transfers (20 = once per second).");
		o.addProperty("itemPipeTransferIntervalTicks", itemPipeTransferIntervalTicks);
		c(o, "lvPouchCapacity", "Battery Pouch item-storage capacity in weight units (one ordinary item = 1).");
		o.addProperty("lvPouchCapacity", lvPouchCapacity);
		c(o, "lvPouchBuffer", "Battery Pouch EU buffer.");
		o.addProperty("lvPouchBuffer", lvPouchBuffer);
		c(o, "lvPouchDrainPerSecond", "EU the pouch drains per second while carried and holding items (locks at 0 EU until recharged).");
		o.addProperty("lvPouchDrainPerSecond", lvPouchDrainPerSecond);
		c(o, "energyPackBuffer", "Energy Pack (worn) EU buffer.");
		o.addProperty("energyPackBuffer", energyPackBuffer);
		c(o, "energyPackInputRate", "Max EU/t the Energy Pack accepts while charging in a slot.");
		o.addProperty("energyPackInputRate", energyPackInputRate);
		c(o, "energyPackOutputRate", "Max EU/t the worn Energy Pack hands out to powered items in the inventory.");
		o.addProperty("energyPackOutputRate", energyPackOutputRate);
		c(o, "electricDrillBuffer", "Electric Drill EU buffer.");
		o.addProperty("electricDrillBuffer", electricDrillBuffer);
		c(o, "electricDrillEuPerBlock", "EU the drill spends per block mined at powered speed (below this it mines at hand speed for free).");
		o.addProperty("electricDrillEuPerBlock", electricDrillEuPerBlock);
		c(o, "electricDrillInputRate", "Max EU/t the drill accepts while charging in a slot.");
		o.addProperty("electricDrillInputRate", electricDrillInputRate);
		c(o, "electricDrillTorchEuCost", "EU the drill spends to place a torch on right-click.");
		o.addProperty("electricDrillTorchEuCost", electricDrillTorchEuCost);
		c(o, "stockFrameScanIntervalTicks", "How often (ticks) a Stock Display Frame rescans the container behind it.");
		o.addProperty("stockFrameScanIntervalTicks", stockFrameScanIntervalTicks);
		c(o, "machineEuPerTick", "Base EU/t a processing machine draws while running (energy per operation = this x its duration).");
		o.addProperty("machineEuPerTick", machineEuPerTick);
		c(o, "maceratorDuration", "Ticks a macerator takes per operation at 1.0 speed.");
		o.addProperty("maceratorDuration", maceratorDuration);
		c(o, "electricFurnaceDuration", "Ticks an electric furnace takes per smelt at 1.0 speed.");
		o.addProperty("electricFurnaceDuration", electricFurnaceDuration);
		c(o, "compressorDuration", "Ticks a compressor takes per operation at 1.0 speed.");
		o.addProperty("compressorDuration", compressorDuration);
		c(o, "extractorDuration", "Ticks an extractor takes per operation at 1.0 speed.");
		o.addProperty("extractorDuration", extractorDuration);
		c(o, "ironFurnaceCookTime", "Ticks the (fuel-based) iron furnace takes to smelt one item. Vanilla furnace = 200.");
		o.addProperty("ironFurnaceCookTime", ironFurnaceCookTime);
		c(o, "copperCableLossPerBlock", "Fraction of throughput lost per copper cable block traversed (0.02 = 2% per block).");
		o.addProperty("copperCableLossPerBlock", copperCableLossPerBlock);
		c(o, "networksPerTick", "Max awake energy networks processed per server tick; the rest are deferred round-robin.");
		o.addProperty("networksPerTick", networksPerTick);
		c(o, "networkAnalyzerMaxTraversedNetworks", "Cap on networks the Network Analyzer's Traverse mode walks (visualization only, never affects energy).");
		o.addProperty("networkAnalyzerMaxTraversedNetworks", networkAnalyzerMaxTraversedNetworks);
		c(o, "bonusChestEnabled", "When true, mod starter items are injected into the vanilla bonus chest at world creation (vanilla loot kept). false = purely vanilla bonus chest.");
		o.addProperty("bonusChestEnabled", bonusChestEnabled);
		return o;
	}

	/** Add an inline {@code _comment_<field>} doc string immediately before its field (Gson keeps insertion
	 * order, so the comment renders on the line above). Ignored on read — {@link #getInt}/{@link #getFloat}
	 * only ever request the real field keys. */
	private static void c(JsonObject o, String field, String text) {
		o.addProperty("_comment_" + field, text);
	}
}
