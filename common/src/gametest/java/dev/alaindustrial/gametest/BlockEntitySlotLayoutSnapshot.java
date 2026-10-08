package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Reviewed inventory layout of every block entity (MOD-701) — the reference
 * {@link BlockEntitySlotLayoutSnapshotScenarios} compares against. Written by that scenario,
 * and only on the explicit command below; never by {@code regen.py}, a hook or a merge driver.
 * The same file on both Minecraft lines: a difference between them is a finding.
 *
 * <p>Update command (absolute path; Fabric lane):
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:save_format_game_test_slot_layout*
 *   -Dalaindustrial.slotLayoutSnapshot.writeTo=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest/BlockEntitySlotLayoutSnapshot.java"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing; review the diff, then commit it together with the
 * change that moved the slots.
 */
final class BlockEntitySlotLayoutSnapshot {

	private BlockEntitySlotLayoutSnapshot() {}

	static final List<String> LINES = List.of(
			"alloy_smelter: size=9 upgrades=4 battery=8 menu=yes overclockable=yes batteryFed=yes",
			"assembler: size=14 upgrades=10 battery=- menu=yes overclockable=yes batteryFed=no",
			"battery_box: size=6 upgrades=2 battery=- menu=yes overclockable=no batteryFed=no",
			"block_breaker: size=5 upgrades=1 battery=- menu=yes overclockable=no batteryFed=no",
			"canning_machine: size=8 upgrades=3 battery=7 menu=yes overclockable=yes batteryFed=yes",
			"cesu: size=6 upgrades=2 battery=- menu=yes overclockable=no batteryFed=no",
			"charge_pad: size=0 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"component_repair_bench: size=7 upgrades=2 battery=6 menu=yes overclockable=yes batteryFed=yes",
			"compressor: size=7 upgrades=2 battery=6 menu=yes overclockable=yes batteryFed=yes",
			"copper_cable: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"creative_energy_source: size=1 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"crystal_farm_controller: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"daylight_solar_panel: size=5 upgrades=1 battery=- menu=yes overclockable=no batteryFed=no",
			"diamond_chest: size=108 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"distillation_column: size=11 upgrades=6 battery=10 menu=yes overclockable=yes batteryFed=yes",
			"distillation_column_segment: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"electric_furnace: size=7 upgrades=2 battery=6 menu=yes overclockable=yes batteryFed=yes",
			"electric_heater: size=0 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"electrum_chest: size=81 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"energy_condenser: size=1 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"extractor: size=7 upgrades=2 battery=6 menu=yes overclockable=yes batteryFed=yes",
			"fermenter: size=10 upgrades=6 battery=- menu=yes overclockable=yes batteryFed=no",
			"fluid_pipe: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"fluid_tank: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"fuel_rod_assembly: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"galvanic_bath: size=10 upgrades=5 battery=9 menu=yes overclockable=yes batteryFed=yes",
			"garden_drone_station: size=13 upgrades=8 battery=12 menu=yes overclockable=no batteryFed=yes",
			"generator: size=5 upgrades=1 battery=- menu=yes overclockable=no batteryFed=no",
			"geothermal_generator: size=6 upgrades=2 battery=- menu=yes overclockable=no batteryFed=no",
			"gold_chest: size=54 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"high_altitude_wind_mill: size=5 upgrades=1 battery=- menu=yes overclockable=no batteryFed=no",
			"incubator: size=9 upgrades=5 battery=- menu=yes overclockable=yes batteryFed=no",
			"industrial_workbench: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"iron_chest: size=36 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"iron_furnace: size=3 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"item_pipe: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"kok_sagyz_root: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"lightning_rod_generator: size=5 upgrades=1 battery=- menu=yes overclockable=no batteryFed=no",
			"macerator: size=7 upgrades=2 battery=6 menu=yes overclockable=yes batteryFed=yes",
			"mob_repeller: size=6 upgrades=1 battery=5 menu=yes overclockable=no batteryFed=yes",
			"mob_repeller_hv: size=6 upgrades=1 battery=5 menu=yes overclockable=no batteryFed=yes",
			"mob_repeller_mv: size=6 upgrades=1 battery=5 menu=yes overclockable=no batteryFed=yes",
			"mob_wheel_controller: size=1 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"monitor_core: size=10 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"monitor_panel: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"moonlit_solar_panel: size=4 upgrades=0 battery=- menu=yes overclockable=no batteryFed=no",
			"piezo_plate: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"polymerizer: size=8 upgrades=3 battery=7 menu=yes overclockable=yes batteryFed=yes",
			"pump: size=9 upgrades=4 battery=8 menu=yes overclockable=no batteryFed=yes",
			"radiant_solar_panel: size=4 upgrades=0 battery=- menu=yes overclockable=no batteryFed=no",
			"reactor_controller: size=0 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"reactor_door: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"reactor_outlet: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"reactor_port: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"recycler: size=9 upgrades=4 battery=8 menu=yes overclockable=no batteryFed=yes",
			"sawmill: size=7 upgrades=2 battery=6 menu=yes overclockable=yes batteryFed=yes",
			"shielding_chest: size=36 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"silver_chest: size=45 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"smart_wire: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"solar_panel: size=5 upgrades=1 battery=- menu=yes overclockable=no batteryFed=no",
			"sprinkler: size=2 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"steam_nozzle: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"storage_module: size=27 upgrades=- battery=- menu=yes overclockable=no batteryFed=no",
			"storm_wind_mill: size=5 upgrades=1 battery=- menu=yes overclockable=no batteryFed=no",
			"teleporter: size=0 upgrades=- battery=- menu=no overclockable=no batteryFed=no",
			"thermal_centrifuge: size=7 upgrades=2 battery=6 menu=yes overclockable=yes batteryFed=yes",
			"upgrade_table: size=7 upgrades=2 battery=6 menu=yes overclockable=yes batteryFed=yes",
			"vulcanizer: size=8 upgrades=3 battery=7 menu=yes overclockable=yes batteryFed=yes",
			"water_mill: size=5 upgrades=1 battery=- menu=yes overclockable=no batteryFed=no",
			"wind_mill: size=6 upgrades=2 battery=- menu=yes overclockable=no batteryFed=no",
			"workstation: size=- upgrades=- battery=- menu=no overclockable=no batteryFed=no");
}
