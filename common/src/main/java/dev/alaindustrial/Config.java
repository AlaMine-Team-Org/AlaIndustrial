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
	 * <p>{@code 0.0125} = 1.25%/block (PROPOSED, tune by playtest — source of truth PERFORMANCE.md):
	 * ~1 EU/t lost on a 10-cable line at the 8 EU/t fuel-generator baseline, ~4 EU/t at a full 32 EU
	 * LV packet; a 1–2 cable hop at a full packet floors to zero.
	 */
	public static double copperCableLossPerBlock = 0.0125;

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
