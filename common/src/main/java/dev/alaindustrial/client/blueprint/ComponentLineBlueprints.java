package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about material component chains (MOD-797). Listed in {@link BlueprintCatalog#ALL}.
 */
public final class ComponentLineBlueprints {

	private static final String AI = "alaindustrial:";
	private static final String CRAFTING_TABLE = "minecraft:crafting_table";
	private static final String RAW_SULFUR = AI + "raw_sulfur";
	private static final String SULFUR_DUST = AI + "sulfur_dust";
	private static final String RAW_RUBBER = AI + "raw_rubber";
	private static final String RUBBER = AI + "rubber";
	private static final String INULIN = AI + "inulin";
	private static final String KOK_SAGYZ_ROOT = AI + "kok_sagyz_root";
	private static final String OIL_BUCKET = AI + "oil_bucket";
	private static final String AMETHYST_SHARD = "minecraft:amethyst_shard";
	private static final String SILVER_DUST = AI + "silver_dust";
	private static final String SILVER_PLATE = AI + "silver_plate";
	private static final String REDSTONE = "minecraft:redstone";
	private static final String REINFORCED_AMETHYST = AI + "reinforced_amethyst";
	private static final String ADVANCED_CIRCUIT = AI + "advanced_circuit";
	private static final String ELECTRUM_PLATE = AI + "electrum_plate";
	private static final String ENERGY_CRYSTAL = AI + "energy_crystal";
	private static final String CAPACITY_CARD = AI + "capacity_card";
	private static final String MONITOR_CORE = AI + "monitor_core";
	private static final String MONITOR_PANEL = AI + "monitor_panel";
	private static final String GOLD_INGOT = "minecraft:gold_ingot";
	private static final String EMPTY_CHIP = AI + "empty_chip";
	private static final String CLOT_I = AI + "energy_clot_i";
	private static final String CLOT_II = AI + "energy_clot_ii";
	private static final String CLOT_III = AI + "energy_clot_iii";
	private static final String CHIP_I = AI + "overclocker_chip_i";
	private static final String CHIP_II = AI + "overclocker_chip_ii";

	/**
	 * The macerator line (0, position 1) grinding raw sulfur into two dust, then two kok-sagyz roots
	 * into raw rubber and inulin; the vulcanizer over plain stone, which waits for heat (1, position 2,
	 * BAD); the electric heater under it, whose heat gives three rubber (0, position 3, GOOD); the
	 * polymerizer as the second source of raw rubber (0, position 4).
	 */
	public static final BlueprintDef RUBBER_MATERIALS = new BlueprintDef("rubber_materials",
			List.of(RAW_SULFUR, SULFUR_DUST, RAW_RUBBER, RUBBER, INULIN),
			List.of(
					new Step(maceratorLine(), 0, 1, 2, 0, Verdict.GOOD,
							motions(0, "+" + RAW_SULFUR, "-" + SULFUR_DUST, "-" + SULFUR_DUST)),
					new Step(maceratorLine(), 0, 1, 2, 0, Verdict.GOOD,
							motions(0, "+" + KOK_SAGYZ_ROOT, "+" + KOK_SAGYZ_ROOT, "-" + RAW_RUBBER,
									"-" + INULIN)),
					new Step(vulcanizerRig(false), 1, 2, 4, 1, Verdict.BAD,
							motions(1, "+" + RAW_RUBBER, "+" + SULFUR_DUST)),
					new Step(vulcanizerRig(true), 0, 3, List.of(Flow.energy(4, 0), Flow.energy(4, 1)),
							Verdict.GOOD,
							motions(1, "+" + RAW_RUBBER, "+" + SULFUR_DUST, "+" + SULFUR_DUST,
									"+" + SULFUR_DUST, "+" + SULFUR_DUST, "-" + RUBBER, "-" + RUBBER,
									"-" + RUBBER)),
					new Step(List.of(machine(POLYMERIZER, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 4, 2, 0,
							Verdict.GOOD, motions(0, "+" + OIL_BUCKET, "-" + RAW_RUBBER))));

	/**
	 * The alloy smelter dark with no cable (0, position 1, BAD) and powered, melting four amethyst
	 * shards and silver dust into one reinforced amethyst (GOOD); the capacity card and the monitor
	 * core crafted on the table (0, position 2); the finished wall of the lit core and two panel
	 * tiles (0, position 3).
	 */
	public static final BlueprintDef MONITOR_PARTS = new BlueprintDef("monitor_parts",
			List.of(REINFORCED_AMETHYST),
			List.of(
					new Step(List.of(machine(ALLOY_SMELTER, 0, 0, false, false)), 0, 1, -1, -1, Verdict.BAD,
							motions(0, "+" + AMETHYST_SHARD, "+" + AMETHYST_SHARD, "+" + SILVER_DUST)),
					new Step(List.of(machine(ALLOY_SMELTER, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 1, 2, 0,
							Verdict.GOOD,
							motions(0, "+" + AMETHYST_SHARD, "+" + AMETHYST_SHARD, "+" + AMETHYST_SHARD,
									"+" + AMETHYST_SHARD, "+" + SILVER_DUST, "-" + REINFORCED_AMETHYST)),
					new Step(List.of(table()), 0, 2, List.of(), Verdict.GOOD,
							motions(0, "+" + SILVER_PLATE, "+" + SILVER_PLATE, "+" + SILVER_PLATE,
									"+" + SILVER_PLATE, "+" + REDSTONE, "+" + REDSTONE, "+" + REDSTONE,
									"+" + REINFORCED_AMETHYST, "+" + ADVANCED_CIRCUIT, "-" + CAPACITY_CARD)),
					new Step(List.of(table()), 0, 2, List.of(), Verdict.GOOD,
							motions(0, "+" + REINFORCED_AMETHYST, "+" + REINFORCED_AMETHYST,
									"+" + REINFORCED_AMETHYST, "+" + REINFORCED_AMETHYST, "+" + ELECTRUM_PLATE,
									"+" + ELECTRUM_PLATE, "+" + ADVANCED_CIRCUIT, "+" + ADVANCED_CIRCUIT,
									"+" + ENERGY_CRYSTAL, "-" + MONITOR_CORE)),
					new Step(monitorWall(), 0, 3, List.of(Flow.energy(2, 0)), Verdict.NONE,
							List.of(new ItemMotion(MONITOR_PANEL, 3, true),
									new ItemMotion(MONITOR_PANEL, 4, true)))));

	/**
	 * Two sky mills feeding one energy condenser: the surplus freezing into a clot, then the same
	 * slot giving the second and the third tier as the bank fills (5, position 1); a clot pushed
	 * into a battery box, which stores nothing (6, position 2, BAD); the clot and the chip ladder
	 * crafted on the table (0, position 3, GOOD).
	 */
	public static final BlueprintDef ENERGY_CLOTS = new BlueprintDef("energy_clots",
			List.of(CLOT_I, CLOT_II, CLOT_III),
			List.of(
					new Step(condenserRig(true), 5, 1, List.of(Flow.energy(0, 5), Flow.energy(1, 5)),
							Verdict.NONE, motions(5, "-" + CLOT_I)),
					new Step(condenserRig(false), 5, 1, List.of(Flow.energy(0, 5), Flow.energy(1, 5)),
							Verdict.NONE, motions(5, "-" + CLOT_II)),
					new Step(condenserRig(false), 5, 1, List.of(Flow.energy(0, 5), Flow.energy(1, 5)),
							Verdict.NONE, motions(5, "-" + CLOT_III)),
					new Step(join(condenserRig(false), List.of(batteryBox("south"))), 6, 2,
							List.of(Flow.energy(0, 5), Flow.energy(1, 5)), Verdict.BAD,
							motions(6, "+" + CLOT_I)),
					new Step(List.of(table()), 0, 3, List.of(), Verdict.GOOD,
							motions(0, "+" + CLOT_I, "+" + EMPTY_CHIP, "+" + GOLD_INGOT, "+" + GOLD_INGOT,
									"+" + REDSTONE, "-" + CHIP_I, "+" + CLOT_II, "+" + CHIP_I,
									"+" + GOLD_INGOT, "+" + GOLD_INGOT, "+" + GOLD_INGOT, "-" + CHIP_II))));

	private ComponentLineBlueprints() {
	}

	/** A crafting table at the origin, the block every craft step points its item motions at. */
	private static Placement table() {
		return new Placement(CRAFTING_TABLE, 0, 0, 0, Map.of());
	}

	/** Item motions on block {@code index}: each entry is "+id" for an input or "-id" for an output. */
	private static List<ItemMotion> motions(int index, String... entries) {
		List<ItemMotion> all = new ArrayList<>();
		for (String entry : entries) {
			all.add(new ItemMotion(entry.substring(1), index, entry.charAt(0) == '+'));
		}
		return all;
	}

	/** The macerator at the west end of a copper line: 0 the machine, 1 the cable, 2 the generator. */
	private static List<Placement> maceratorLine() {
		return List.of(machine(MACERATOR, 0, 0, false, true),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2));
	}

	/**
	 * The vulcanizer rig: 0 the block under the machine (the electric heater when {@code heated}, else
	 * plain stone), 1 the vulcanizer, which lights up late in the step only when heated, 2 the cable at
	 * the heater's side, 3 the cable rising to the machine, 4 the generator.
	 */
	private static List<Placement> vulcanizerRig(boolean heated) {
		return List.of(heated ? heater(true) : stone(0, 0, 0),
				new Placement(VULCANIZER, 0, 1, 0, Map.of("facing", "south", "lit", "false"),
						heated ? Map.of("lit", "true") : Map.of()),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "east", "up"),
				cableOf(COPPER_CABLE, 1, 1, 0, "west", "down"), generatorAt(2));
	}

	/**
	 * The finished monitoring wall: 0 the lit core with one card, 1 its cable, 2 the generator, 3 and 4 panel tiles
	 * on top, the same arrangement the monitoring sheet places them in.
	 */
	private static List<Placement> monitorWall() {
		return List.of(new Placement(MONITOR_CORE, 0, 0, 0,
				Map.of("facing", "south", "cards", "1", "lit", "true")),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2),
				new Placement(MONITOR_PANEL, 0, 1, 0, Map.of("facing", "south", "lit", "false")),
				new Placement(MONITOR_PANEL, 1, 1, 0, Map.of("facing", "south", "lit", "false")));
	}

	/**
	 * Two sky mills feeding one condenser through cable corners, the wind-upkeep arrangement: 0 and
	 * 1 the mills, 2..4 the cables, 5 the condenser, which lights up late in the step when
	 * {@code lightsLate} and burns steadily otherwise.
	 */
	private static List<Placement> condenserRig(boolean lightsLate) {
		return List.of(t2Mill(HIGH_ALTITUDE_WIND_MILL, 0, true), t2Mill(HIGH_ALTITUDE_WIND_MILL, 2, true),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "south"),
				cableOf(COPPER_CABLE, 1, 0, 1, "north", "south", "up"),
				cableOf(COPPER_CABLE, 1, 0, 2, "west", "north"),
				new Placement(ENERGY_CONDENSER, 1, 1, 1,
						lightsLate ? Map.of("lit", "false") : Map.of("lit", "true"),
						lightsLate ? Map.of("lit", "true") : Map.of()));
	}
}
