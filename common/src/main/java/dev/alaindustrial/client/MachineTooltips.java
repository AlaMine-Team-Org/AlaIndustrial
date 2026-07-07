package dev.alaindustrial.client;

import dev.alaindustrial.Config;
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
import dev.alaindustrial.item.NetworkAnalyzerItem;
import dev.alaindustrial.item.NetworkScanData;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Loader-neutral hover-tooltip content for machine block items and the Network Analyzer (MOD-022). The
 * body uses only vanilla + neutral {@link Config}/{@link ModDataComponents} — the client "is shift held"
 * check is passed in as a boolean so this class links no client-only types and stays server-safe in
 * {@code common}. Each loader's client hooks its own tooltip event and calls {@link #append}: Fabric via
 * {@code ItemTooltipCallback}, NeoForge via {@code ItemTooltipEvent}.
 */
public final class MachineTooltips {
	private MachineTooltips() {
	}

	/** Append Ala Industrial tooltip lines for {@code stack} (machine stats or analyzer reading). */
	public static void append(ItemStack stack, List<Component> lines, boolean shiftDown) {
		if (stack.getItem() instanceof NetworkAnalyzerItem) {
			addNetworkAnalyzerTooltip(stack, lines, shiftDown);
			return;
		}
		if (!(stack.getItem() instanceof BlockItem bi)) {
			return;
		}
		Block block = bi.getBlock();
		if (!isMachineBlock(block)) {
			return;
		}
		addBasicTooltip(block, lines);
		if (shiftDown) {
			addDetailedTooltip(block, lines);
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/**
	 * Tooltip for the Network Analyzer tool. The usage line is always shown; the last scan (stored on the
	 * item as {@link ModDataComponents#NETWORK_SCAN} the moment it is used) is replayed under [SHIFT] so
	 * the reading persists in the inventory after the actionbar message fades (MOD-016).
	 */
	private static void addNetworkAnalyzerTooltip(ItemStack stack, List<Component> lines, boolean shiftDown) {
		lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.usage")
				.withStyle(ChatFormatting.GRAY));
		NetworkScanData scan = stack.get(ModDataComponents.NETWORK_SCAN.get());
		if (scan == null) {
			return;
		}
		if (shiftDown) {
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.last_scan")
					.withStyle(ChatFormatting.AQUA));
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.cables", scan.cables())
					.withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.endpoints",
					scan.producers(), scan.consumers()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.flow",
					scan.supply(), scan.demand()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.moved", scan.moved())
					.withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
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
			lines.add(tt("cable_loss", cableLossPercent()));
		}
	}

	private static Component tt(String key, Object value) {
		return Component.translatable("tooltip.alaindustrial." + key, value)
				.withStyle(ChatFormatting.GRAY);
	}

	/**
	 * Copper-cable loss as a percent-per-block string, sourced live from {@link Config#copperCableLossPerBlock}
	 * so the tooltip can never drift from the actual model. Locale.ROOT + trailing-zero trim yields "1.25".
	 */
	private static String cableLossPercent() {
		double pct = Config.copperCableLossPerBlock * 100.0;
		String s = String.format(java.util.Locale.ROOT, "%.3f", pct);
		if (s.contains(".")) {
			s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return s;
	}

	private static Component tier() {
		return Component.translatable("tooltip.alaindustrial.tier_lv")
				.withStyle(ChatFormatting.GREEN);
	}
}
