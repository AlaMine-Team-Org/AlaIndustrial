package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Platform-neutral sound handle (MOD-022 facade). NeoForge freezes the vanilla {@code SOUND_EVENT}
 * registry before mod construction, so a direct {@code Registry.register} (fine on Fabric) throws
 * {@code Registry is already frozen} there. Each loader binds {@link #MACERATOR_GRIND} during its own
 * registration — Fabric via an eager {@code Registry.register}, NeoForge via a {@code DeferredRegister}
 * holder (itself a {@link Supplier}) — and content reads it lazily through {@code .get()}.
 */
public final class ModSounds {

	/** The registry id, shared by both loaders' registration. */
	public static final Identifier MACERATOR_GRIND_ID = Industrialization.id("macerator_grind");

	/** Bound once per loader before any block entity plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> MACERATOR_GRIND = () -> {
		throw new IllegalStateException("ModSounds.MACERATOR_GRIND read before its loader bound it");
	};

	/** Build the event instance both loaders register (variable range, like vanilla machine sounds). */
	public static SoundEvent createMaceratorGrind() {
		return SoundEvent.createVariableRangeEvent(MACERATOR_GRIND_ID);
	}

	/** The registry id for the generator/geothermal ambient engine hum. */
	public static final Identifier GENERATOR_HUM_ID = Industrialization.id("generator_hum");

	/** Bound once per loader before any block plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> GENERATOR_HUM = () -> {
		throw new IllegalStateException("ModSounds.GENERATOR_HUM read before its loader bound it");
	};

	/** Build the generator-hum event instance both loaders register. */
	public static SoundEvent createGeneratorHum() {
		return SoundEvent.createVariableRangeEvent(GENERATOR_HUM_ID);
	}

	/** The registry id for the electric furnace ambient fire-roar hum. */
	public static final Identifier ELECTRIC_FURNACE_HUM_ID = Industrialization.id("electric_furnace");

	/** Bound once per loader before any block plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> ELECTRIC_FURNACE_HUM = () -> {
		throw new IllegalStateException("ModSounds.ELECTRIC_FURNACE_HUM read before its loader bound it");
	};

	/** Build the electric-furnace-hum event instance both loaders register. */
	public static SoundEvent createElectricFurnaceHum() {
		return SoundEvent.createVariableRangeEvent(ELECTRIC_FURNACE_HUM_ID);
	}

	/** The registry id for the solar panel ambient hum (lit-less generators — pattern C). */
	public static final Identifier SOLAR_PANEL_HUM_ID = Industrialization.id("solar_panel_hum");

	/** Bound once per loader before any block plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> SOLAR_PANEL_HUM = () -> {
		throw new IllegalStateException("ModSounds.SOLAR_PANEL_HUM read before its loader bound it");
	};

	/**
	 * Build the solar-panel-hum event instance both loaders register. Fixed 10-block audible range
	 * (vs the generator's variable {@code /16}): solar farms place many sources close together, and
	 * each stacks into the others, so each panel is tuned shorter-range than a lone generator to keep
	 * a farm from becoming a wall of sound. Per-block {@code humVolume()} (0.28) sets the loudness.
	 */
	public static SoundEvent createSolarPanelHum() {
		return SoundEvent.createFixedRangeEvent(SOLAR_PANEL_HUM_ID, 10.0f);
	}

	/** The registry id for the iron chest open sound (lid lifts). */
	public static final Identifier IRON_CHEST_OPEN_ID = Industrialization.id("iron_chest_open");

	/** Bound once per loader before the chest BE plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> IRON_CHEST_OPEN = () -> {
		throw new IllegalStateException("ModSounds.IRON_CHEST_OPEN read before its loader bound it");
	};

	/** Build the iron-chest-open event instance both loaders register. */
	public static SoundEvent createIronChestOpen() {
		return SoundEvent.createVariableRangeEvent(IRON_CHEST_OPEN_ID);
	}

	/** The registry id for the iron chest close sound (lid drops). */
	public static final Identifier IRON_CHEST_CLOSE_ID = Industrialization.id("iron_chest_close");

	/** Bound once per loader before the chest BE plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> IRON_CHEST_CLOSE = () -> {
		throw new IllegalStateException("ModSounds.IRON_CHEST_CLOSE read before its loader bound it");
	};

	/** Build the iron-chest-close event instance both loaders register. */
	public static SoundEvent createIronChestClose() {
		return SoundEvent.createVariableRangeEvent(IRON_CHEST_CLOSE_ID);
	}

	/** The registry id for the scythe swing/cut sound (MOD-068), played once per successful AOE clear. */
	public static final Identifier SCYTHE_SWING_ID = Industrialization.id("scythe_swing");

	/** Bound once per loader before the scythe plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> SCYTHE_SWING = () -> {
		throw new IllegalStateException("ModSounds.SCYTHE_SWING read before its loader bound it");
	};

	/** Build the scythe-swing event instance both loaders register. */
	public static SoundEvent createScytheSwing() {
		return SoundEvent.createVariableRangeEvent(SCYTHE_SWING_ID);
	}

	/** The registry id for the extractor working loop (MOD-143) — a single lit machine, pattern A. */
	public static final Identifier EXTRACTOR_HUM_ID = Industrialization.id("extractor_hum");

	/** Bound once per loader before any block plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> EXTRACTOR_HUM = () -> {
		throw new IllegalStateException("ModSounds.EXTRACTOR_HUM read before its loader bound it");
	};

	/** Build the extractor-hum event instance both loaders register (variable range, like the macerator). */
	public static SoundEvent createExtractorHum() {
		return SoundEvent.createVariableRangeEvent(EXTRACTOR_HUM_ID);
	}

	/** The registry id for the water-mill working loop (MOD-143) — lit-less passive generator, pattern C. */
	public static final Identifier WATER_MILL_HUM_ID = Industrialization.id("water_mill_hum");

	/** Bound once per loader before any block plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> WATER_MILL_HUM = () -> {
		throw new IllegalStateException("ModSounds.WATER_MILL_HUM read before its loader bound it");
	};

	/**
	 * Build the water-mill-hum event instance both loaders register. Fixed 12-block audible range (like
	 * the solar panel's, not the generator's variable {@code /16}): water mills are passive generators
	 * players line up in rows along a channel, so each is tuned to a short fixed range to keep a mill row
	 * from becoming a wall of sound. Per-block {@code humVolume()} sets the loudness.
	 */
	public static SoundEvent createWaterMillHum() {
		return SoundEvent.createFixedRangeEvent(WATER_MILL_HUM_ID, 12.0f);
	}

	/** The registry id for the wind-mill working loop (MOD-143) — lit-less passive generator, pattern C. */
	public static final Identifier WIND_MILL_HUM_ID = Industrialization.id("wind_mill_hum");

	/** Bound once per loader before any block plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> WIND_MILL_HUM = () -> {
		throw new IllegalStateException("ModSounds.WIND_MILL_HUM read before its loader bound it");
	};

	/**
	 * Build the wind-mill-hum event instance both loaders register. Fixed 12-block audible range, same
	 * reasoning as the water mill: wind farms stack many rotors close together, so each is tuned to a
	 * short fixed range rather than the lone generator's variable radius.
	 */
	public static SoundEvent createWindMillHum() {
		return SoundEvent.createFixedRangeEvent(WIND_MILL_HUM_ID, 12.0f);
	}

	private ModSounds() {
	}
}
