package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;
import static dev.alaindustrial.client.blueprint.BlueprintRigs.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.List;
import java.util.Map;

/** Blueprints about making, moving and storing energy (MOD-797). Listed in {@link BlueprintCatalog#ALL}. */
public final class EnergyBlueprints {

	/** Generator → copper cable → electric furnace, then the cable is taken out and the furnace stops. */
	public static final BlueprintDef ENERGY_BASICS = new BlueprintDef("energy_basics",
			List.of(GENERATOR, COPPER_CABLE, ELECTRIC_FURNACE),
			List.of(
					new Step(List.of(generatorLightingUp()), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("minecraft:coal", 0, true),
									new ItemMotion("minecraft:oak_log", 0, true))),
					new Step(List.of(generator(true), cable(true, false)), 1, 2, -1, -1, Verdict.NONE),
					new Step(List.of(generator(true), cable(true, true), furnaceLightingUp()), 2, 3, 0, 2, Verdict.GOOD,
							List.of(new ItemMotion("minecraft:raw_iron", 2, true),
									new ItemMotion("minecraft:iron_ingot", 2, false))),
					new Step(List.of(generator(true), furnace(false)), 1, 3, -1, -1, Verdict.BAD)));

	/**
	 * Solar panel → cable → battery box → cable → macerator; then the box turned so the cables meet its
	 * sides, which do not connect. The box takes energy on its front ({@code facing}) and gives it from the
	 * back; the cable meets the half-height panel with a low arm.
	 */
	public static final BlueprintDef SOLAR_STORAGE = new BlueprintDef("solar_storage",
			List.of(SOLAR_PANEL, BATTERY_BOX),
			List.of(
					new Step(List.of(solarPanel()), 0, 1, -1, -1, Verdict.NONE),
					new Step(List.of(solarPanel(), cableAt(1, 0, true, true, true, false), batteryBox("west")),
							2, 2, 0, 2, Verdict.NONE),
					new Step(List.of(solarPanel(), cableAt(1, 0, true, true, true, false), batteryBox("west"),
							cableAt(3, 0, true, false, true, false), machine(MACERATOR, 4, 0, false, true)),
							2, 2, 2, 4, Verdict.GOOD,
							List.of(new ItemMotion("minecraft:raw_iron", 4, true),
									new ItemMotion("alaindustrial:iron_dust", 4, false))),
					new Step(List.of(solarPanel(), cableAt(1, 0, true, true, false, false), batteryBox("north"),
							cableAt(3, 0, false, false, true, false), machine(MACERATOR, 4, 0, false, false)),
							2, 2, -1, -1, Verdict.BAD)));

	/**
	 * The cable grades one after another in the same line (generator, cable, furnace), then a tin line
	 * with one gold piece: the whole network takes the gold losses.
	 */
	public static final BlueprintDef CABLE_GRADES = new BlueprintDef("cable_grades",
			List.of(TIN_CABLE, INSULATED_COPPER_CABLE, GOLD_CABLE, ELECTRUM_CABLE),
			List.of(
					gradeStep(TIN_CABLE, 1),
					gradeStep(INSULATED_COPPER_CABLE, 2),
					gradeStep(GOLD_CABLE, 3),
					gradeStep(ELECTRUM_CABLE, 4),
					new Step(List.of(generator(true), cableOf(TIN_CABLE, 1, 0, 0, "west", "east"),
							cableOf(GOLD_CABLE, 2, 0, 0, "west", "east"), cableOf(TIN_CABLE, 3, 0, 0, "west", "east"),
							machine(ELECTRIC_FURNACE, 4, 0, true, false)), 2, 3, 0, 4, Verdict.BAD)));

	/**
	 * The reinforced store fed by gold cable from a geothermal generator, then a charge pad behind it
	 * (charging, then ready), then the store turned so the cables meet its sides.
	 */
	public static final BlueprintDef MV_STORAGE = new BlueprintDef("mv_storage",
			List.of(CESU, CHARGE_PAD),
			List.of(
					new Step(List.of(cesu("west")), 0, 1, -1, -1, Verdict.NONE),
					new Step(List.of(cesu("west"), geothermal(0, true, false),
							cableOf(GOLD_CABLE, 1, 0, 0, "west", "east")), 2, 2, 1, 0, Verdict.NONE),
					new Step(List.of(cesu("west"), geothermal(0, true, false),
							cableOf(GOLD_CABLE, 1, 0, 0, "west", "east"),
							cableOf(GOLD_CABLE, 3, 0, 0, "west", "east", "east_low"), chargePad("charging")),
							4, 3, 0, 4, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:electric_drill", 4, true),
									new ItemMotion("alaindustrial:energy_pack", 4, true))),
					new Step(List.of(cesu("west"), geothermal(0, true, false),
							cableOf(GOLD_CABLE, 1, 0, 0, "west", "east"),
							cableOf(GOLD_CABLE, 3, 0, 0, "west", "east", "east_low"), chargePad("ready")),
							4, 3, -1, -1, Verdict.GOOD),
					new Step(List.of(cesu("north"), geothermal(0, true, false), cableOf(GOLD_CABLE, 1, 0, 0, "west"),
							cableOf(GOLD_CABLE, 3, 0, 0, "east", "east_low"), chargePad("empty")),
							0, 1, -1, -1, Verdict.BAD)));

	/** The insulated grades in the same line, then one bare piece in it (bad) and the line all in rubber. */
	public static final BlueprintDef INSULATED_CABLES = new BlueprintDef("insulated_cables",
			List.of(INSULATED_TIN_CABLE, INSULATED_GOLD_CABLE, INSULATED_ELECTRUM_CABLE),
			List.of(
					gradeStep(INSULATED_TIN_CABLE, 1),
					gradeStep(INSULATED_GOLD_CABLE, 2),
					gradeStep(INSULATED_ELECTRUM_CABLE, 3),
					new Step(goldLine(GOLD_CABLE), 2, 4, 0, 4, Verdict.BAD),
					new Step(goldLine(INSULATED_GOLD_CABLE), 2, 2, 0, 4, Verdict.GOOD)));

	/**
	 * The solar panel grows into the daylight panel with a chip, a solid block over it stops it, the moonlit
	 * panel grows with the other chip, and a resonance chip turns the daylight panel into the concentrator
	 * (its mirrors are drawn by a block-entity renderer, so the sheet shows the collector only).
	 */
	public static final BlueprintDef SOLAR_TIERS = new BlueprintDef("solar_tiers",
			List.of(DAYLIGHT_SOLAR_PANEL, MOONLIT_SOLAR_PANEL, RADIANT_SOLAR_PANEL),
			List.of(
					new Step(List.of(solarPanel()), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:alignment_chip_day", 0, true))),
					new Step(List.of(panelAt(DAYLIGHT_SOLAR_PANEL, 0)), 0, 2, -1, -1, Verdict.NONE),
					new Step(List.of(panelAt(DAYLIGHT_SOLAR_PANEL, 0), new Placement(TIN_BLOCK, 0, 1, 0, Map.of())),
							0, 2, -1, -1, Verdict.BAD),
					new Step(List.of(panelAt(DAYLIGHT_SOLAR_PANEL, 0), panelAt(MOONLIT_SOLAR_PANEL, 2)),
							1, 3, -1, -1, Verdict.NONE),
					new Step(List.of(panelAt(DAYLIGHT_SOLAR_PANEL, 0), panelAt(MOONLIT_SOLAR_PANEL, 2)),
							0, 2, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:resonance_chip", 0, true))),
					new Step(List.of(new Placement(RADIANT_SOLAR_PANEL, 0, 0, 0,
							Map.of("assembled", "false", "facing", "south")), panelAt(MOONLIT_SOLAR_PANEL, 2)),
							0, 4, -1, -1, Verdict.GOOD)));

	/**
	 * The geothermal generator on a lava bucket, powering a canning machine, then a cable against the
	 * generator's front, which gives no energy.
	 */
	public static final BlueprintDef LAVA_POWER = new BlueprintDef("lava_power",
			List.of(GEOTHERMAL_GENERATOR, CANNING_MACHINE),
			List.of(
					new Step(List.of(geothermal(0, false, true)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("minecraft:lava_bucket", 0, true),
									new ItemMotion("minecraft:bucket", 0, false))),
					new Step(List.of(geothermal(0, true, false), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
							machine(CANNING_MACHINE, 2, 0, false, true)), 2, 2, 0, 2, Verdict.GOOD,
							List.of(new ItemMotion("minecraft:carrot", 2, true),
									new ItemMotion("alaindustrial:empty_can", 2, true),
									new ItemMotion("alaindustrial:canned_ration", 2, false))),
					new Step(List.of(geothermal(0, true, false), cableOf(COPPER_CABLE, 0, 0, 1),
							machine(CANNING_MACHINE, 2, 0, false, false)), 0, 1, -1, -1, Verdict.BAD)));

	/**
	 * The wind turbine with its rotor (energy leaves by the back), a roof over it, then the water mill with
	 * its wheel in water, and a block in the wheel's way. Both face west so their back, where the cable
	 * goes, is in view.
	 */
	public static final BlueprintDef WIND_AND_WATER = new BlueprintDef("wind_and_water",
			List.of(WIND_MILL, WATER_MILL),
			List.of(
					new Step(List.of(mill(WIND_MILL)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:windmill_rotor", 0, true))),
					new Step(List.of(mill(WIND_MILL), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
							batteryBox("west")),
							0, 1, 0, 2, Verdict.GOOD),
					new Step(List.of(mill(WIND_MILL), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
							batteryBox("west"),
							new Placement(TIN_BLOCK, 0, 1, 0, Map.of())), 0, 1, -1, -1, Verdict.BAD),
					new Step(List.of(mill(WATER_MILL)), 0, 2, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:water_mill_wheel", 0, true))),
					new Step(waterMill(), 0, 2, List.of(Flow.water(3, 0), Flow.energy(0, 2)), Verdict.GOOD,
							List.of()),
					new Step(blockedWaterMill(), 0, 2, List.of(), Verdict.BAD, List.of())));

	/**
	 * The lightning rod generator taking its copper tip, powering a battery box in a storm, a roof over it;
	 * then piezo plates on a floor feeding a cable, a plate laid straight on a store (not an output), and a
	 * cable hidden two blocks under the plate.
	 */
	public static final BlueprintDef EVENT_POWER = new BlueprintDef("event_power",
			List.of(LIGHTNING_ROD, PIEZO_PLATE),
			List.of(
					new Step(List.of(rod(false, true)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:lightning_rod_conductor_tip", 0, true))),
					new Step(List.of(rod(true, false), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
							batteryBox("west")),
							0, 1, 0, 2, Verdict.GOOD),
					new Step(List.of(rod(false, false), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
							batteryBox("west"),
							new Placement(TIN_BLOCK, 0, 1, 0, Map.of())), 0, 1, -1, -1, Verdict.BAD),
					new Step(List.of(stone(0, 0, 0), stone(1, 0, 0), stone(2, 0, 0), plate(0, 1), plate(1, 1),
							cableOf(COPPER_CABLE, 2, 1, 0, "west", "west_low", "east"), boxAt(3, 1, 0)),
							4, 2, 4, 6, Verdict.NONE),
					new Step(List.of(boxAt(0, 0, 0), new Placement(PIEZO_PLATE, 0, 1, 0, Map.of())),
							1, 2, -1, -1, Verdict.BAD),
					new Step(List.of(plate(0, 2), stone(0, 1, 0), cableOf(COPPER_CABLE, 0, 0, 0, "east"), boxAt(1, 0,
							0)),
							0, 2, 0, 3, Verdict.GOOD)));

	/**
	 * The sky and tempest mills (evolved wind turbines), two mills too close, an energy condenser catching
	 * their surplus through its top, a cable at its side, then the repair bench fixing a worn rotor.
	 */
	public static final BlueprintDef WIND_UPKEEP = new BlueprintDef("wind_upkeep",
			List.of(HIGH_ALTITUDE_WIND_MILL, STORM_WIND_MILL, ENERGY_CONDENSER, COMPONENT_REPAIR_BENCH),
			List.of(
					new Step(List.of(t2Mill(HIGH_ALTITUDE_WIND_MILL, 0, true)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:windmill_rotor", 0, true))),
					new Step(List.of(t2Mill(HIGH_ALTITUDE_WIND_MILL, 0, true), t2Mill(STORM_WIND_MILL, 2, true)),
							1, 2, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:windmill_rotor", 1, true))),
					new Step(List.of(t2Mill(HIGH_ALTITUDE_WIND_MILL, 0, false), t2Mill(STORM_WIND_MILL, 1, false)),
							1, 2, -1, -1, Verdict.BAD),
					new Step(List.of(t2Mill(HIGH_ALTITUDE_WIND_MILL, 0, true), t2Mill(STORM_WIND_MILL, 2, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "south"),
							cableOf(COPPER_CABLE, 1, 0, 1, "north", "south", "up"),
							cableOf(COPPER_CABLE, 1, 0, 2, "west", "north"),
							new Placement(ENERGY_CONDENSER, 1, 1, 1, Map.of("lit", "false"), Map.of("lit", "true"))),
							5, 3, List.of(Flow.energy(0, 5), Flow.energy(1, 5)), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:energy_clot_i", 5, false))),
					new Step(List.of(t2Mill(HIGH_ALTITUDE_WIND_MILL, 0, true), t2Mill(STORM_WIND_MILL, 2, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "south"),
							cableOf(COPPER_CABLE, 1, 0, 1, "north", "south"),
							cableOf(COPPER_CABLE, 1, 0, 2, "west", "north"),
							new Placement(ENERGY_CONDENSER, 2, 0, 1, Map.of("lit", "false"))),
							5, 3, List.of(), Verdict.BAD, List.of()),
					new Step(List.of(machine(COMPONENT_REPAIR_BENCH, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 4, 2, 0, Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:windmill_rotor", 0, true),
									new ItemMotion("alaindustrial:iron_plate", 0, true),
									new ItemMotion("alaindustrial:windmill_rotor", 0, false)))));

	/**
	 * The mob repeller grown with soul vessels: the medium one starves on copper cable and runs on gold, the
	 * large one needs electrum.
	 */
	public static final BlueprintDef REPELLER_TIERS = new BlueprintDef("repeller_tiers",
			List.of(MOB_REPELLER_MV, MOB_REPELLER_HV),
			List.of(
					new Step(repellerLine(repeller(MOB_REPELLER, true, false), COPPER_CABLE), 0, 1,
							List.of(Flow.energy(2, 0)), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:soul_vessel", 0, true))),
					new Step(repellerLine(repeller(MOB_REPELLER_MV, false, false), COPPER_CABLE), 0, 1, -1, -1,
							Verdict.BAD),
					new Step(repellerLine(repeller(MOB_REPELLER_MV, false, true), GOLD_CABLE), 1, 2,
							List.of(Flow.energy(2, 0)), Verdict.GOOD, List.of()),
					new Step(repellerLine(repeller(MOB_REPELLER_MV, true, false), GOLD_CABLE), 0, 1,
							List.of(Flow.energy(2, 0)), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:soul_vessel", 0, true))),
					new Step(repellerLine(repeller(MOB_REPELLER_HV, false, true), ELECTRUM_CABLE), 0, 1,
							List.of(Flow.energy(2, 0)), Verdict.GOOD, List.of())));

	private EnergyBlueprints() {
	}
}
