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

	/** The registry id for the solar panel ambient hum (lit-less generators — pattern C). */
	public static final Identifier SOLAR_PANEL_HUM_ID = Industrialization.id("solar_panel_hum");

	/** Bound once per loader before any block plays it; unbound = loud failure, never a silent NPE. */
	public static Supplier<SoundEvent> SOLAR_PANEL_HUM = () -> {
		throw new IllegalStateException("ModSounds.SOLAR_PANEL_HUM read before its loader bound it");
	};

	/** Build the solar-panel-hum event instance both loaders register. */
	public static SoundEvent createSolarPanelHum() {
		return SoundEvent.createVariableRangeEvent(SOLAR_PANEL_HUM_ID);
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

	private ModSounds() {
	}
}
