package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Reviewed screen-title keys of every menu-opening block entity (MOD-712) — the reference {@link
 * BlockEntityDisplayNameSnapshotScenarios} compares against.
 *
 * <p>Written by that scenario, and only on the explicit command below (ADR-032); never by
 * {@code regen.py}, a hook or a merge driver. The same file serves both gametest lanes.
 *
 * <p>Update command (absolute directory; Fabric lane):
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:machine_char_display_name*
 *   -Dalaindustrial.referenceLines.writeDir=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing; review the diff, then commit it together
 * with the change that is meant to move it.
 */
final class BlockEntityDisplayNameSnapshot {

	private BlockEntityDisplayNameSnapshot() {}

	static final List<String> LINES = List.of(
			"generator @generator: block.alaindustrial.generator",
			"geothermal_generator @geothermal_generator: block.alaindustrial.geothermal_generator",
			"solar_panel @solar_panel: block.alaindustrial.solar_panel",
			"moonlit_solar_panel @moonlit_solar_panel: block.alaindustrial.moonlit_solar_panel",
			"daylight_solar_panel @daylight_solar_panel: block.alaindustrial.daylight_solar_panel",
			"radiant_solar_panel @radiant_solar_panel: block.alaindustrial.radiant_solar_panel",
			"water_mill @water_mill: block.alaindustrial.water_mill",
			"wind_mill @wind_mill: block.alaindustrial.wind_mill",
			"high_altitude_wind_mill @high_altitude_wind_mill:"
					+ " block.alaindustrial.high_altitude_wind_mill",
			"storm_wind_mill @storm_wind_mill: block.alaindustrial.storm_wind_mill",
			"lightning_rod_generator @lightning_rod_generator:"
					+ " block.alaindustrial.lightning_rod_generator",
			"creative_energy_source @creative_energy_source: block.alaindustrial.creative_energy_source",
			"mob_wheel_controller @mob_wheel_controller: block.alaindustrial.mob_wheel_controller",
			"macerator @macerator: block.alaindustrial.macerator",
			"component_repair_bench @component_repair_bench: block.alaindustrial.component_repair_bench",
			"upgrade_table @upgrade_table: container.alaindustrial.upgrade_table",
			"electric_furnace @electric_furnace: block.alaindustrial.electric_furnace",
			"iron_furnace @iron_furnace: block.alaindustrial.iron_furnace",
			"extractor @extractor: block.alaindustrial.extractor",
			"compressor @compressor: block.alaindustrial.compressor",
			"recycler @recycler: block.alaindustrial.recycler",
			"canning_machine @canning_machine: block.alaindustrial.canning_machine",
			"sawmill @sawmill: block.alaindustrial.sawmill",
			"assembler @assembler: block.alaindustrial.assembler",
			"polymerizer @polymerizer: block.alaindustrial.polymerizer",
			"vulcanizer @vulcanizer: block.alaindustrial.vulcanizer",
			"alloy_smelter @alloy_smelter: block.alaindustrial.alloy_smelter",
			"galvanic_bath @galvanic_bath: block.alaindustrial.galvanic_bath",
			"electric_heater @electric_heater: block.alaindustrial.electric_heater",
			"thermal_centrifuge @thermal_centrifuge: block.alaindustrial.thermal_centrifuge",
			"block_breaker @block_breaker: block.alaindustrial.block_breaker",
			"distillation_column @distillation_column: block.alaindustrial.distillation_column",
			"pump @pump: block.alaindustrial.pump",
			"fermenter @fermenter: block.alaindustrial.fermenter",
			"monitor_core @monitor_core: block.alaindustrial.monitor_core",
			"battery_box @battery_box: block.alaindustrial.battery_box",
			"cesu @cesu: block.alaindustrial.cesu",
			"charge_pad @charge_pad: block.alaindustrial.charge_pad",
			"energy_condenser @energy_condenser: block.alaindustrial.energy_condenser",
			"iron_chest @iron_chest: block.alaindustrial.iron_chest",
			"storage_module @storage_module: block.alaindustrial.storage_module",
			"silver_chest @silver_chest: block.alaindustrial.silver_chest",
			"gold_chest @gold_chest: block.alaindustrial.gold_chest",
			"electrum_chest @electrum_chest: block.alaindustrial.electrum_chest",
			"diamond_chest @diamond_chest: block.alaindustrial.diamond_chest",
			"shielding_chest @shielding_chest: block.alaindustrial.shielding_chest",
			"reactor_controller @reactor_controller: block.alaindustrial.reactor_controller",
			"incubator @incubator: block.alaindustrial.incubator",
			"garden_drone_station @garden_drone_station: block.alaindustrial.garden_drone_station",
			"sprinkler @sprinkler: block.alaindustrial.sprinkler",
			"mob_repeller @mob_repeller: block.alaindustrial.mob_repeller",
			"mob_repeller_mv @mob_repeller_mv: block.alaindustrial.mob_repeller_mv",
			"mob_repeller_hv @mob_repeller_hv: block.alaindustrial.mob_repeller_hv");
}
