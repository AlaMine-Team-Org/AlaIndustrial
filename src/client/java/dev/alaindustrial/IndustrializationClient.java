package dev.alaindustrial;

import dev.alaindustrial.block.BatteryBoxBlock;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.CompressorBlock;
import dev.alaindustrial.block.DaylightSolarPanelBlock;
import dev.alaindustrial.block.ElectricFurnaceBlock;
import dev.alaindustrial.block.ExtractorBlock;
import dev.alaindustrial.block.GeneratorBlock;
import dev.alaindustrial.block.GeothermalGeneratorBlock;
import dev.alaindustrial.block.MaceratorBlock;
import dev.alaindustrial.block.MoonlitSolarPanelBlock;
import dev.alaindustrial.block.PumpBlock;
import dev.alaindustrial.block.SolarPanelBlock;
import dev.alaindustrial.client.CompressorScreen;
import dev.alaindustrial.client.ElectricFurnaceScreen;
import dev.alaindustrial.client.SolarPanelScreen;
import dev.alaindustrial.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Client entrypoint for Industrialization. Binds machine menus to their screens
 * and registers the tooltip provider that shows EU/capacity stats on hover.
 */
public class IndustrializationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.<dev.alaindustrial.menu.GeneratorMenu, dev.alaindustrial.client.GeneratorScreen>register(
				ModMenus.GENERATOR, dev.alaindustrial.client.GeneratorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.MaceratorMenu, dev.alaindustrial.client.MaceratorScreen>register(
				ModMenus.MACERATOR, dev.alaindustrial.client.MaceratorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.ElectricFurnaceMenu, ElectricFurnaceScreen>register(
				ModMenus.ELECTRIC_FURNACE, ElectricFurnaceScreen::new);
		MenuScreens.<dev.alaindustrial.menu.ExtractorMenu, dev.alaindustrial.client.ExtractorScreen>register(
				ModMenus.EXTRACTOR, dev.alaindustrial.client.ExtractorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.CompressorMenu, CompressorScreen>register(
				ModMenus.COMPRESSOR, CompressorScreen::new);
		MenuScreens.<dev.alaindustrial.menu.SolarPanelMenu, SolarPanelScreen>register(
				ModMenus.SOLAR_PANEL, SolarPanelScreen::new);
		MenuScreens.<dev.alaindustrial.menu.MoonlitSolarPanelMenu, dev.alaindustrial.client.MoonlitSolarPanelScreen>register(
				ModMenus.MOONLIT_SOLAR_PANEL, dev.alaindustrial.client.MoonlitSolarPanelScreen::new);
		MenuScreens.<dev.alaindustrial.menu.BatteryBoxMenu, dev.alaindustrial.client.BatteryBoxScreen>register(
				ModMenus.BATTERY_BOX, dev.alaindustrial.client.BatteryBoxScreen::new);
		MenuScreens.<dev.alaindustrial.menu.DaylightSolarPanelMenu, dev.alaindustrial.client.DaylightSolarPanelScreen>register(
				ModMenus.DAYLIGHT_SOLAR_PANEL, dev.alaindustrial.client.DaylightSolarPanelScreen::new);
		MenuScreens.<dev.alaindustrial.menu.GeothermalGeneratorMenu, dev.alaindustrial.client.GeothermalGeneratorScreen>register(
				ModMenus.GEOTHERMAL_GENERATOR, dev.alaindustrial.client.GeothermalGeneratorScreen::new);

		registerTooltips();
		dev.alaindustrial.client.NetworkVisualizationClient.init();

		Industrialization.LOGGER.info("Industrialization client initialized.");
	}

	private static void registerTooltips() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!(stack.getItem() instanceof BlockItem bi)) return;
			Block block = bi.getBlock();
			if (!isMachineBlock(block)) return;

			addBasicTooltip(block, lines);
			if (Minecraft.getInstance().hasShiftDown()) {
				addDetailedTooltip(block, lines);
			} else {
				lines.add(Component.translatable("tooltip.alaindustrial.hold_shift")
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		});
	}

	private static boolean isMachineBlock(Block block) {
		return block instanceof SolarPanelBlock
				|| block instanceof DaylightSolarPanelBlock
				|| block instanceof MoonlitSolarPanelBlock
				|| block instanceof GeneratorBlock
				|| block instanceof GeothermalGeneratorBlock
				|| block instanceof MaceratorBlock
				|| block instanceof ElectricFurnaceBlock
				|| block instanceof CompressorBlock
				|| block instanceof ExtractorBlock
				|| block instanceof PumpBlock
				|| block instanceof BatteryBoxBlock
				|| block instanceof CableBlock;
	}

	private static void addBasicTooltip(Block block, List<Component> lines) {
		if (block instanceof SolarPanelBlock) {
			lines.add(tt("energy_output_day", Config.solarEuPerTick));
			lines.add(tt("capacity", Config.solarBuffer));
		} else if (block instanceof DaylightSolarPanelBlock) {
			lines.add(tt("energy_output_day_only", Config.daylightEuPerTick));
			lines.add(tt("capacity", Config.solarBuffer));
		} else if (block instanceof MoonlitSolarPanelBlock) {
			lines.add(tt("energy_output_night_only", Config.moonlitEuPerTick));
			lines.add(tt("capacity", Config.solarBuffer));
		} else if (block instanceof GeneratorBlock) {
			lines.add(tt("energy_output_fuel", Config.fuelEuPerTick));
			lines.add(tt("capacity", Config.generatorBuffer));
		} else if (block instanceof GeothermalGeneratorBlock) {
			lines.add(tt("energy_output_lava", Config.geothermalEuPerTick));
			lines.add(tt("capacity", Config.geothermalBuffer));
		} else if (block instanceof MaceratorBlock) {
			lines.add(tt("energy_input", Config.machineEuPerTickEffective()));
			lines.add(tt("duration_ticks", Config.scaledDuration(Config.maceratorDuration)));
		} else if (block instanceof ElectricFurnaceBlock) {
			lines.add(tt("energy_input", Config.machineEuPerTickEffective()));
			lines.add(tt("duration_ticks", Config.scaledDuration(Config.electricFurnaceDuration)));
		} else if (block instanceof CompressorBlock) {
			lines.add(tt("energy_input", Config.machineEuPerTickEffective()));
			lines.add(tt("duration_ticks", Config.scaledDuration(Config.compressorDuration)));
		} else if (block instanceof ExtractorBlock) {
			lines.add(tt("energy_input", Config.machineEuPerTickEffective()));
			lines.add(tt("duration_ticks", Config.scaledDuration(Config.extractorDuration)));
		} else if (block instanceof PumpBlock) {
			lines.add(tt("pump_cost", Config.pumpEuPerBucket));
		} else if (block instanceof BatteryBoxBlock) {
			lines.add(tt("capacity", Config.batteryBoxBuffer));
			lines.add(tier());
		} else if (block instanceof CableBlock) {
			lines.add(tier());
			lines.add(tt("buffer", Config.cableBuffer));
		}
	}

	private static void addDetailedTooltip(Block block, List<Component> lines) {
		if (block instanceof SolarPanelBlock) {
			lines.add(tier());
			lines.add(tt("solar_day", Config.solarEuPerTick));
			lines.add(tt("solar_night", 0));
			lines.add(Component.translatable("tooltip.alaindustrial.solar_chip_hint")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else if (block instanceof DaylightSolarPanelBlock) {
			lines.add(tier());
			lines.add(tt("solar_day", Config.daylightEuPerTick));
			lines.add(tt("solar_night", 0));
		} else if (block instanceof MoonlitSolarPanelBlock) {
			lines.add(tier());
			lines.add(tt("solar_day", 0));
			lines.add(tt("solar_night", Config.moonlitEuPerTick));
		} else if (block instanceof GeneratorBlock) {
			lines.add(tier());
		} else if (block instanceof GeothermalGeneratorBlock) {
			lines.add(tier());
			lines.add(tt("geo_burn_ticks", Config.geothermalBurnTicks));
		} else if (block instanceof MaceratorBlock) {
			lines.add(tier());
			lines.add(tt("buffer", Config.maceratorBuffer));
			lines.add(tt("energy_per_op",
					Config.machineEuPerTickEffective() * Config.scaledDuration(Config.maceratorDuration)));
		} else if (block instanceof ElectricFurnaceBlock) {
			lines.add(tier());
			lines.add(tt("buffer", Config.machineBuffer));
			lines.add(tt("energy_per_op",
					Config.machineEuPerTickEffective() * Config.scaledDuration(Config.electricFurnaceDuration)));
		} else if (block instanceof CompressorBlock) {
			lines.add(tier());
			lines.add(tt("buffer", Config.machineBuffer));
			lines.add(tt("energy_per_op",
					Config.machineEuPerTickEffective() * Config.scaledDuration(Config.compressorDuration)));
		} else if (block instanceof ExtractorBlock) {
			lines.add(tier());
			lines.add(tt("buffer", Config.machineBuffer));
			lines.add(tt("energy_per_op",
					Config.machineEuPerTickEffective() * Config.scaledDuration(Config.extractorDuration)));
		} else if (block instanceof PumpBlock) {
			lines.add(tier());
		} else if (block instanceof BatteryBoxBlock) {
			lines.add(Component.translatable("tooltip.alaindustrial.battery_box_io")
					.withStyle(ChatFormatting.GRAY));
		} else if (block instanceof CableBlock) {
			lines.add(Component.translatable("tooltip.alaindustrial.cable_no_loss")
					.withStyle(ChatFormatting.GRAY));
		}
	}

	private static Component tt(String key, Object value) {
		return Component.translatable("tooltip.alaindustrial." + key, value)
				.withStyle(ChatFormatting.GRAY);
	}

	private static Component tier() {
		return Component.translatable("tooltip.alaindustrial.tier_lv")
				.withStyle(ChatFormatting.GREEN);
	}
}
