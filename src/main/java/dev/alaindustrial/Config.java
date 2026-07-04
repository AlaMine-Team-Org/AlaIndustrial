package dev.alaindustrial;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.GsonHelper;

/**
 * Tunable balance knobs (v0.2 defaults), loaded from {@code config/alaindustrial.json}.
 * Generators/machines/storage/cables read these at runtime, so a server can rebalance without a
 * code change. Missing keys fall back to the v0.2 default, so the file is forward/backward safe.
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

	// --- Pump (LV, EU-powered lava mover) ---
	/** EU spent per bucket of lava the pump moves (extract + push). */
	public static int pumpEuPerBucket = 100;

	// --- Storage / per-block buffers (EU) ---
	public static int batteryBoxBuffer = 20_000;
	public static int maceratorBuffer = 800;
	/** Shared buffer for electric furnace / compressor / extractor. */
	public static int machineBuffer = 800;
	public static int generatorBuffer = 4000;
	public static int geothermalBuffer = 4000;
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

	/** Effective machine drain per tick after the speed multiplier (E_op stays ~constant). */
	public static int machineEuPerTickEffective() {
		return Math.max(1, Math.round(machineEuPerTick * globalMachineSpeedMultiplier));
	}

	/** Scale a base duration (ticks) by the speed multiplier: faster machine -> fewer ticks. */
	public static int scaledDuration(int baseTicks) {
		return Math.max(1, Math.round(baseTicks / globalMachineSpeedMultiplier));
	}

	/** Load once at startup and re-load on every {@code /reload} (datapack reload). */
	public static void register() {
		load();
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> load());
	}

	/** Load the config file, or write the v0.2 defaults if it does not exist yet. */
	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("alaindustrial.json");
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
					generatorBuffer = GsonHelper.getAsInt(o, "generatorBuffer", generatorBuffer);
					geothermalBuffer = GsonHelper.getAsInt(o, "geothermalBuffer", geothermalBuffer);
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
		o.addProperty("solarTransparentFactor", solarTransparentFactor);
		o.addProperty("solarSnowFactor", solarSnowFactor);
		o.addProperty("solarEvolveTicks", solarEvolveTicks);
		o.addProperty("pumpEuPerBucket", pumpEuPerBucket);
		o.addProperty("batteryBoxBuffer", batteryBoxBuffer);
		o.addProperty("maceratorBuffer", maceratorBuffer);
		o.addProperty("machineBuffer", machineBuffer);
		o.addProperty("generatorBuffer", generatorBuffer);
		o.addProperty("geothermalBuffer", geothermalBuffer);
		o.addProperty("solarBuffer", solarBuffer);
		o.addProperty("cableBuffer", cableBuffer);
		o.addProperty("machineEuPerTick", machineEuPerTick);
		o.addProperty("maceratorDuration", maceratorDuration);
		o.addProperty("electricFurnaceDuration", electricFurnaceDuration);
		o.addProperty("compressorDuration", compressorDuration);
		o.addProperty("extractorDuration", extractorDuration);
		o.addProperty("copperCableLossPerBlock", copperCableLossPerBlock);
		o.addProperty("networksPerTick", networksPerTick);
		return o;
	}
}
