package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Reviewed owner, statistics, panel and evolution answers of every machine after a save (MOD-712) —
 * the reference {@link MachineCapabilityRoundTripScenarios} compares against.
 *
 * <p>Written by that scenario, and only on the explicit command below (ADR-032); never by
 * {@code regen.py}, a hook or a merge driver. The same file serves both gametest lanes.
 *
 * <p>Update command (absolute directory; Fabric lane):
 * <pre>
 * JAVA_TOOL_OPTIONS="-Dfabric-api.gametest.filter=*:machine_char_capability*
 *   -Dalaindustrial.referenceLines.writeDir=&lt;repo&gt;/common/src/gametest/java/dev/alaindustrial/gametest"
 *   ./gradlew :fabric:runGameTest
 * </pre>
 * The run fails on purpose after writing; review the diff, then commit it together
 * with the change that is meant to move it.
 */
final class MachineCapabilitySnapshot {

	private MachineCapabilitySnapshot() {}

	static final List<String> LINES = List.of(
			"generator: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"generator keys: -EvolveProgress -EvolveChip",
			"geothermal_generator: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"geothermal_generator keys: -EvolveProgress -EvolveChip",
			"solar_panel: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=0/0 rate=2 dur=200 evolve=37/1",
			"solar_panel keys: all",
			"moonlit_solar_panel: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"moonlit_solar_panel keys: -EvolveProgress -EvolveChip",
			"daylight_solar_panel: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=0/0 rate=2 dur=200 evolve=37/1",
			"daylight_solar_panel keys: all",
			"radiant_solar_panel: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"radiant_solar_panel keys: -EvolveProgress -EvolveChip",
			"water_mill: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"water_mill keys: -EvolveProgress -EvolveChip",
			"wind_mill: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=0/0 rate=2 dur=200 evolve=37/1",
			"wind_mill keys: all",
			"high_altitude_wind_mill: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1"
					+ " mute=1 stats=1 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"high_altitude_wind_mill keys: -EvolveProgress -EvolveChip",
			"storm_wind_mill: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"storm_wind_mill keys: -EvolveProgress -EvolveChip",
			"lightning_rod_generator: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1"
					+ " mute=1 stats=1 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"lightning_rod_generator keys: -EvolveProgress -EvolveChip",
			"creative_energy_source: owner=null active=3 items=5 eu=11/13/17/19 panel=0 mute=0 stats=0"
					+ " oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"creative_energy_source keys: -Owner -OwnerName -EvolveProgress -EvolveChip",
			"macerator: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"macerator keys: -EvolveProgress -EvolveChip",
			"component_repair_bench: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=1/2 rate=16 dur=160 evolve=-1/-1",
			"component_repair_bench keys: -EvolveProgress -EvolveChip",
			"upgrade_table: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/2 rate=16 dur=160 evolve=-1/-1",
			"upgrade_table keys: -EvolveProgress -EvolveChip",
			"teleporter: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=0 mute=0 stats=0"
					+ " oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"teleporter keys: -EvolveProgress -EvolveChip",
			"electric_furnace: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"electric_furnace keys: -EvolveProgress -EvolveChip",
			"extractor: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"extractor keys: -EvolveProgress -EvolveChip",
			"compressor: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"compressor keys: -EvolveProgress -EvolveChip",
			"recycler: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"recycler keys: -EvolveProgress -EvolveChip",
			"canning_machine: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"canning_machine keys: -EvolveProgress -EvolveChip",
			"sawmill: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1 oc=1/3"
					+ " rate=4 dur=160 evolve=-1/-1",
			"sawmill keys: -EvolveProgress -EvolveChip",
			"assembler: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/3 rate=24 dur=160 evolve=-1/-1",
			"assembler keys: -EvolveProgress -EvolveChip",
			"polymerizer: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"polymerizer keys: -EvolveProgress -EvolveChip",
			"vulcanizer: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"vulcanizer keys: -EvolveProgress -EvolveChip",
			"alloy_smelter: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/2 rate=16 dur=160 evolve=-1/-1",
			"alloy_smelter keys: -EvolveProgress -EvolveChip",
			"galvanic_bath: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"galvanic_bath keys: -EvolveProgress -EvolveChip",
			"electric_heater: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=0 mute=0"
					+ " stats=0 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"electric_heater keys: -EvolveProgress -EvolveChip",
			"thermal_centrifuge: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"thermal_centrifuge keys: -EvolveProgress -EvolveChip",
			"distillation_column: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"distillation_column keys: -EvolveProgress -EvolveChip",
			"pump: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1 oc=0/0"
					+ " rate=2 dur=200 evolve=-1/-1",
			"pump keys: -EvolveProgress -EvolveChip",
			"fermenter: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/3 rate=4 dur=160 evolve=-1/-1",
			"fermenter keys: -EvolveProgress -EvolveChip",
			"battery_box: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"battery_box keys: -EvolveProgress -EvolveChip",
			"cesu: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1 oc=0/0"
					+ " rate=2 dur=200 evolve=-1/-1",
			"cesu keys: -EvolveProgress -EvolveChip",
			"charge_pad: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=0 mute=0 stats=0"
					+ " oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"charge_pad keys: -EvolveProgress -EvolveChip",
			"energy_condenser: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=0 mute=0"
					+ " stats=0 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"energy_condenser keys: -EvolveProgress -EvolveChip",
			"reactor_controller: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=0 mute=0"
					+ " stats=0 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"reactor_controller keys: -EvolveProgress -EvolveChip",
			"incubator: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=1/2 rate=16 dur=160 evolve=-1/-1",
			"incubator keys: -EvolveProgress -EvolveChip",
			"garden_drone_station: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"garden_drone_station keys: -EvolveProgress -EvolveChip",
			"sprinkler: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=0 mute=0 stats=0"
					+ " oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"sprinkler keys: -EvolveProgress -EvolveChip",
			"mob_repeller: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1 stats=1"
					+ " oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"mob_repeller keys: -EvolveProgress -EvolveChip",
			"mob_repeller_mv: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"mob_repeller_mv keys: -EvolveProgress -EvolveChip",
			"mob_repeller_hv: owner=Characterizer active=3 items=5 eu=11/13/17/19 panel=1 mute=1"
					+ " stats=1 oc=0/0 rate=2 dur=200 evolve=-1/-1",
			"mob_repeller_hv keys: -EvolveProgress -EvolveChip");
}
