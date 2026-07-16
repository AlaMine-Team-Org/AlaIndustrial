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
import dev.alaindustrial.item.AnalyzerMode;
import dev.alaindustrial.item.ElectricDrillItem;
import dev.alaindustrial.item.EnergyPackItem;
import dev.alaindustrial.item.ItemEnergy;
import dev.alaindustrial.item.NetworkAnalyzerItem;
import dev.alaindustrial.item.NetworkScanData;
import dev.alaindustrial.item.PouchContents;
import dev.alaindustrial.item.PouchItem;
import dev.alaindustrial.registry.ModContent;
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
		boolean detailed = shiftDown || AlaClientConfig.alwaysDetailedTooltips;
		if (stack.getItem() instanceof NetworkAnalyzerItem) {
			addNetworkAnalyzerTooltip(stack, lines, detailed);
			return;
		}
		if (stack.getItem() instanceof PouchItem) {
			addPouchTooltip(stack, lines, detailed);
			return;
		}
		if (stack.getItem() instanceof EnergyPackItem) {
			addEnergyPackTooltip(stack, lines);
			return;
		}
		if (stack.getItem() instanceof ElectricDrillItem) {
			addElectricDrillTooltip(stack, lines);
			return;
		}
		// Plain-item components (not BlockItem) — the windmill rotor is the only such item with a
		// tooltip. Its line describes what it does in the wind mill, not standalone stats.
		if (stack.is(ModContent.WINDMILL_ROTOR.get())) {
			addRotorTooltip(lines, detailed);
			return;
		}
		if (!(stack.getItem() instanceof BlockItem bi)) {
			return;
		}
		Block block = bi.getBlock();
		if (!isMachineBlock(block)) {
			return;
		}
		if (!AlaClientConfig.showEuNumbers) {
			if (detailed) {
				addNonNumericTooltip(block, lines);
			} else {
				lines.add(Component.translatable("tooltip.alaindustrial.hold_shift")
						.withStyle(ChatFormatting.DARK_GRAY));
			}
			return;
		}
		addBasicTooltip(block, lines);
		if (detailed) {
			addDetailedTooltip(block, lines);
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static void addNonNumericTooltip(Block block, List<Component> lines) {
		if (block instanceof BatteryBoxBlock) {
			lines.add(tier());
			lines.add(Component.translatable("tooltip.alaindustrial.battery_box_io")
					.withStyle(ChatFormatting.GRAY));
		} else if (block instanceof CableBlock) {
			lines.add(tier());
		} else if (block instanceof SolarPanelBlock) {
			lines.add(tier());
			lines.add(Component.translatable("tooltip.alaindustrial.solar_chip_hint")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else if (block instanceof DaylightSolarPanelBlock
				|| block instanceof MoonlitSolarPanelBlock
				|| block instanceof GeneratorBlock
				|| block instanceof GeothermalGeneratorBlock
				|| block instanceof MaceratorBlock
				|| block instanceof ElectricFurnaceBlock
				|| block instanceof CompressorBlock
				|| block instanceof ExtractorBlock
				|| block instanceof PumpBlock) {
			lines.add(tier());
		}
	}

	/**
	 * Tooltip for the Network Analyzer tool. The usage line is always shown; the last scan (stored on the
	 * item as {@link ModDataComponents#NETWORK_SCAN} the moment it is used) is replayed under [SHIFT] so
	 * the reading persists in the inventory after the actionbar message fades (MOD-016). The active mode
	 * (TRAVERSE / STOP_AT_STORAGE, MOD-047) is shown right under the usage line so the player always knows
	 * which behaviour a cable scan will use.
	 */
	private static void addNetworkAnalyzerTooltip(ItemStack stack, List<Component> lines, boolean shiftDown) {
		lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.usage")
				.withStyle(ChatFormatting.GRAY));
		AnalyzerMode mode = stack.get(ModDataComponents.NETWORK_ANALYZER_MODE.get());
		if (mode == null) {
			mode = AnalyzerMode.TRAVERSE;
		}
		lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.mode_label",
						Component.translatable("tooltip.alaindustrial.network_analyzer.mode." + mode.getSerializedName()))
				.withStyle(ChatFormatting.AQUA));
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
					scan.producers(), scan.consumers(), scan.storage()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.flow",
					scan.supply(), scan.demand()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.alaindustrial.network_analyzer.moved", scan.moved())
					.withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.hold_shift")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/**
	 * Tooltip text for the Battery Pouch (MOD-052): usage, EU charge (red DEPLETED at 0 — the lock state
	 * must be readable at a glance), tier. The contents grid and the weight bar are visual — see
	 * {@code PouchClientTooltip} (bundle-style tooltip image, player request).
	 */
	private static void addPouchTooltip(ItemStack stack, List<Component> lines, boolean detailed) {
		// Two short list lines instead of one long sentence — long single-line tooltips stretch the
		// box, and other locales run even longer (player feedback).
			lines.add(Component.translatable("tooltip.alaindustrial.battery_pouch.usage_insert")
					.withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("tooltip.alaindustrial.battery_pouch.usage_extract")
				.withStyle(ChatFormatting.GRAY));
		long eu = ItemEnergy.get(stack);
		long cap = ItemEnergy.capacity(stack);
		if (eu <= 0) {
			lines.add(Component.translatable("tooltip.alaindustrial.battery_pouch.depleted")
					.withStyle(ChatFormatting.RED));
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.battery_pouch.charge", eu, cap)
					.withStyle(ChatFormatting.GOLD));
			if (cap > 0 && eu * 10 < cap) {
				lines.add(Component.translatable("tooltip.alaindustrial.battery_pouch.low_charge")
						.withStyle(ChatFormatting.RED));
			}
		}
		// No tier line: the Battery Pouch is a tier-less consumer item; its charge state and item
		// bar (LV gold) already convey everything the player needs.
	}

	/**
	 * Tooltip text for the Energy Pack (MOD-065): what it does while worn, then its EU charge — same
	 * shape as the pouch tooltip (gold charge line, red DEPLETED at 0). No [SHIFT] gate: the pack has
	 * no second layer of detail to hide, and its charge is the one thing the player checks.
	 *
	 * <p>The usage text is split over two short lines: the equipment tooltip already carries the
	 * "When on Chest / +2 Armor" block, and one long sentence on top of that stretched the box across
	 * half the screen (player feedback).
	 */
	private static void addEnergyPackTooltip(ItemStack stack, List<Component> lines) {
		lines.add(Component.translatable("tooltip.alaindustrial.energy_pack.usage")
				.withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("tooltip.alaindustrial.energy_pack.usage_charges")
				.withStyle(ChatFormatting.GRAY));
		long eu = ItemEnergy.get(stack);
		long cap = ItemEnergy.capacity(stack);
		if (eu <= 0) {
			lines.add(Component.translatable("tooltip.alaindustrial.energy_pack.depleted")
					.withStyle(ChatFormatting.RED));
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.energy_pack.charge", eu, cap)
					.withStyle(ChatFormatting.GOLD));
		}
	}

	/**
	 * Tooltip for the Electric Drill (MOD-079): what it does, then its EU charge — same shape as the
	 * Energy Pack tooltip (gold charge line, red DEPLETED at 0). The usage line names the per-block EU
	 * cost so the player can gauge how many blocks a full charge is worth; no [SHIFT] gate — the charge
	 * is the one thing worth checking on a powered tool.
	 */
	private static void addElectricDrillTooltip(ItemStack stack, List<Component> lines) {
		lines.add(Component.translatable("tooltip.alaindustrial.electric_drill.usage", Config.electricDrillEuPerBlock)
				.withStyle(ChatFormatting.GRAY));
		long eu = ItemEnergy.get(stack);
		long cap = ItemEnergy.capacity(stack);
		if (eu <= 0) {
			lines.add(Component.translatable("tooltip.alaindustrial.electric_drill.depleted")
					.withStyle(ChatFormatting.RED));
		} else {
			lines.add(Component.translatable("tooltip.alaindustrial.electric_drill.charge", eu, cap)
					.withStyle(ChatFormatting.GOLD));
		}
	}

	/**
	 * Tooltip for the wooden rotor — describes its role in the wind mill: required to generate,
	 * and the base EU/t a T1 wind mill produces at full height. Numbers come from {@link Config} so
	 * the tooltip stays in sync with the balance knobs (the rotor itself carries no stats — it is
	 * a gate, the mill's output depends on height/weather).
	 */
	private static void addRotorTooltip(List<Component> lines, boolean detailed) {
		// Always-on line: what it does.
		lines.add(Component.translatable("tooltip.alaindustrial.rotor_role")
				.withStyle(ChatFormatting.GRAY));
		// Base T1 output at full height (the cap), so a player can compare rotors without placing.
		lines.add(tt("rotor_output", Config.windMillMaxEuPerTick));
		if (detailed) {
			lines.add(tier());
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
	 * so the tooltip can never drift from the actual model. Locale.ROOT + trailing-zero trim yields "2".
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
