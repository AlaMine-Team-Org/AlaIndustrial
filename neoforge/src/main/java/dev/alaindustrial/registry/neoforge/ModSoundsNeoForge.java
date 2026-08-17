package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge sound registration (MOD-022 facade). NeoForge freezes the vanilla {@code SOUND_EVENT}
 * registry before mod construction, so the neutral {@link ModSounds} cannot self-register there (unlike
 * Fabric). This {@link DeferredRegister} registers on the mod bus and binds the neutral
 * {@link ModSounds#MACERATOR_GRIND} handle to the deferred holder (itself a {@code Supplier<SoundEvent>}).
 */
public final class ModSoundsNeoForge {
	public static final DeferredRegister<SoundEvent> SOUNDS =
			DeferredRegister.create(Registries.SOUND_EVENT, Industrialization.MOD_ID);

	public static final DeferredHolder<SoundEvent, SoundEvent> MACERATOR_GRIND =
			SOUNDS.register("macerator_grind", ModSounds::createMaceratorGrind);

	public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_HUM =
			SOUNDS.register("generator_hum", ModSounds::createGeneratorHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> ELECTRIC_FURNACE_HUM =
			SOUNDS.register("electric_furnace", ModSounds::createElectricFurnaceHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> SOLAR_PANEL_HUM =
			SOUNDS.register("solar_panel_hum", ModSounds::createSolarPanelHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> IRON_CHEST_OPEN =
			SOUNDS.register("iron_chest_open", ModSounds::createIronChestOpen);

	public static final DeferredHolder<SoundEvent, SoundEvent> IRON_CHEST_CLOSE =
			SOUNDS.register("iron_chest_close", ModSounds::createIronChestClose);

	public static final DeferredHolder<SoundEvent, SoundEvent> SCYTHE_SWING =
			SOUNDS.register("scythe_swing", ModSounds::createScytheSwing);

	public static final DeferredHolder<SoundEvent, SoundEvent> EXTRACTOR_HUM =
			SOUNDS.register("extractor_hum", ModSounds::createExtractorHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> WATER_MILL_HUM =
			SOUNDS.register("water_mill_hum", ModSounds::createWaterMillHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> WIND_MILL_HUM =
			SOUNDS.register("wind_mill_hum", ModSounds::createWindMillHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> COMPRESSOR_HUM =
			SOUNDS.register("compressor_hum", ModSounds::createCompressorHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> GARDEN_DRONE_FLY =
			SOUNDS.register("garden_drone_fly", ModSounds::createGardenDroneFly);

	public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_HUM =
			SOUNDS.register("pump_hum", ModSounds::createPumpHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> CANNING_MACHINE_HUM =
			SOUNDS.register("canning_machine_hum", ModSounds::createCanningMachineHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> GALVANIC_BATH_HUM =
			SOUNDS.register("galvanic_bath_hum", ModSounds::createGalvanicBathHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> SAWMILL_HUM =
			SOUNDS.register("sawmill_hum", ModSounds::createSawmillHum);

	public static final DeferredHolder<SoundEvent, SoundEvent> POLYMERIZER_HUM =
			SOUNDS.register("polymerizer_hum", ModSounds::createPolymerizerHum);

	/** Bind the neutral handles to the deferred holders. Called from the {@code @Mod} ctor after register. */
	public static void init() {
		ModSounds.MACERATOR_GRIND = MACERATOR_GRIND;
		ModSounds.GENERATOR_HUM = GENERATOR_HUM;
		ModSounds.ELECTRIC_FURNACE_HUM = ELECTRIC_FURNACE_HUM;
		ModSounds.SOLAR_PANEL_HUM = SOLAR_PANEL_HUM;
		ModSounds.IRON_CHEST_OPEN = IRON_CHEST_OPEN;
		ModSounds.IRON_CHEST_CLOSE = IRON_CHEST_CLOSE;
		ModSounds.SCYTHE_SWING = SCYTHE_SWING;
		ModSounds.EXTRACTOR_HUM = EXTRACTOR_HUM;
		ModSounds.WATER_MILL_HUM = WATER_MILL_HUM;
		ModSounds.WIND_MILL_HUM = WIND_MILL_HUM;
		ModSounds.COMPRESSOR_HUM = COMPRESSOR_HUM;
		ModSounds.GARDEN_DRONE_FLY = GARDEN_DRONE_FLY;
		ModSounds.PUMP_HUM = PUMP_HUM;
		ModSounds.CANNING_MACHINE_HUM = CANNING_MACHINE_HUM;
		ModSounds.GALVANIC_BATH_HUM = GALVANIC_BATH_HUM;
		ModSounds.SAWMILL_HUM = SAWMILL_HUM;
		ModSounds.POLYMERIZER_HUM = POLYMERIZER_HUM;
	}

	private ModSoundsNeoForge() {
	}
}
