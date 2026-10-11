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

/** Blueprints about processing machines (MOD-797). Listed in {@link BlueprintCatalog#ALL}. */
public final class MachineBlueprints {

	/**
	 * Macerator, compressor and extractor with what goes in and comes out (taken from their recipes), then a
	 * cable against a machine's front, which does not connect, and the same cable moved to its side.
	 */
	public static final BlueprintDef ORE_PROCESSING = new BlueprintDef("ore_processing",
			List.of(MACERATOR, COMPRESSOR, EXTRACTOR),
			List.of(
					new Step(List.of(machine(MACERATOR, 0, 0, false, true)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("minecraft:raw_iron", 0, true),
									new ItemMotion("alaindustrial:iron_dust", 0, false))),
					new Step(List.of(machine(MACERATOR, 0, 0, false, false), machine(COMPRESSOR, 2, 0, false, true)),
							1, 2, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:diamond_dust", 1, true),
									new ItemMotion("minecraft:diamond", 1, false))),
					new Step(List.of(machine(MACERATOR, 0, 0, false, false), machine(COMPRESSOR, 2, 0, false, false),
							machine(EXTRACTOR, 4, 0, false, true)), 2, 3, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("minecraft:poppy", 2, true),
									new ItemMotion("minecraft:red_dye", 2, false))),
					new Step(List.of(machine(MACERATOR, 0, 0, false, false), cableAt(0, 1, false, false, false, false)),
							0, 1, -1, -1, Verdict.BAD),
					new Step(List.of(machine(MACERATOR, 0, 0, true, true),
							cableArms(1, 0, 0, "west"), cableArms(-1, 0, 0, "east"), cableArms(0, 0, -1, "south"),
							cableArms(0, 1, 0, "down"), cableArms(0, -1, 0, "up")),
							0, 1, 1, 0, Verdict.GOOD)));

	/**
	 * The iron furnace on fuel (a cable does not connect to it), then the alloy smelter on energy with two
	 * of its recipes.
	 */
	public static final BlueprintDef SMELTING = new BlueprintDef("smelting",
			List.of(IRON_FURNACE, ALLOY_SMELTER),
			List.of(
					new Step(List.of(machine(IRON_FURNACE, 0, 0, false, true)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("minecraft:coal", 0, true),
									new ItemMotion("minecraft:raw_iron", 0, true),
									new ItemMotion("minecraft:iron_ingot", 0, false))),
					new Step(List.of(machine(IRON_FURNACE, 0, 0, false, false), cableOf(COPPER_CABLE, 1, 0, 0, "east"),
							generatorAt(2)), 0, 1, -1, -1, Verdict.BAD),
					new Step(List.of(machine(IRON_FURNACE, 0, 0, false, false), cableOf(COPPER_CABLE, 1, 0, 0, "east"),
							generatorAt(2), cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
							machine(ALLOY_SMELTER, 4, 0, false, true)), 4, 2, 2, 4, Verdict.GOOD,
							List.of(new ItemMotion("minecraft:copper_ingot", 4, true),
									new ItemMotion("alaindustrial:tin_ingot", 4, true),
									new ItemMotion("alaindustrial:bronze_ingot", 4, false))),
					new Step(List.of(machine(IRON_FURNACE, 0, 0, false, false), cableOf(COPPER_CABLE, 1, 0, 0, "east"),
							generatorAt(2), cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
							machine(ALLOY_SMELTER, 4, 0, true, false)), 4, 2, 2, 4, Verdict.NONE,
							List.of(new ItemMotion("minecraft:gold_ingot", 4, true),
									new ItemMotion("alaindustrial:silver_ingot", 4, true),
									new ItemMotion("alaindustrial:electrum_ingot", 4, false)))));

	/**
	 * A tank feeding water through a pipe into the galvanic bath, which makes flux thread; the bath with no
	 * water; then the advanced tank in the same place. Tanks draw their glass only: the scene has no fluid
	 * in a block entity.
	 */
	public static final BlueprintDef WATER_BATH = new BlueprintDef("water_bath",
			List.of(FLUID_TANK, FLUID_TANK_ADVANCED, GALVANIC_BATH),
			List.of(
					new Step(List.of(tankAt(FLUID_TANK)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("minecraft:water_bucket", 0, true))),
					new Step(bathLine(FLUID_TANK, true), 2, 2, List.of(Flow.water(0, 2), Flow.energy(4, 2)),
							Verdict.GOOD, bathItems()),
					new Step(List.of(bath(false), cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"), generatorAt(4)),
							0, 2, 2, 0, Verdict.BAD),
					new Step(bathLine(FLUID_TANK_ADVANCED, true), 0, 3, List.of(Flow.water(0, 2), Flow.energy(4, 2)),
							Verdict.GOOD, bathItems())));

	/** The electric heater under a vulcanizer, warming up and making rubber; then beside it, which does nothing. */
	public static final BlueprintDef HEAT = new BlueprintDef("heat",
			List.of(ELECTRIC_HEATER),
			List.of(
					new Step(List.of(heater(true), new Placement(VULCANIZER, 0, 1, 0,
							Map.of("facing", "south", "lit", "false"), Map.of("lit", "true")),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 1, 3, 0, Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:raw_rubber", 1, true),
									new ItemMotion("alaindustrial:sulfur_dust", 1, true),
									new ItemMotion("alaindustrial:rubber", 1, false))),
					new Step(List.of(heater(false), new Placement(VULCANIZER, -1, 0, 0,
							Map.of("facing", "south", "lit", "false")),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 1, 3, 0, Verdict.BAD)));

	/**
	 * The block breaker with a stone pickaxe breaking cobblestone, an iron pickaxe it refuses; the assembler
	 * crafting from a storage module beside it, and the module touching it only by a corner.
	 */
	public static final BlueprintDef AUTOMATION = new BlueprintDef("automation",
			List.of(BLOCK_BREAKER, ASSEMBLER),
			List.of(
					new Step(breaker(true), 0, 1, 3, 0, Verdict.GOOD,
							List.of(new ItemMotion("minecraft:stone_pickaxe", 0, true),
									new ItemMotion("minecraft:cobblestone", 1, false))),
					new Step(breaker(false), 0, 1, 3, 0, Verdict.BAD,
							List.of(new ItemMotion("minecraft:iron_pickaxe", 0, true))),
					new Step(assembler(0), 0, 2, List.of(Flow.energy(3, 0), Flow.item(1, 0, "minecraft:oak_planks")),
							Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:assembly_blueprint", 0, true),
									new ItemMotion("minecraft:stick", 0, false))),
					new Step(assembler(-1), 0, 2, 3, 0, Verdict.BAD)));

	/**
	 * The polymerizer turning oil into raw rubber, then the vulcanizer over a campfire turning raw rubber and
	 * sulfur into rubber, and the vulcanizer on a plain block, which waits for heat.
	 */
	public static final BlueprintDef RUBBER_LINE = new BlueprintDef("rubber_line",
			List.of(POLYMERIZER, VULCANIZER),
			List.of(
					new Step(List.of(machine(POLYMERIZER, 0, 0, false, false)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:oil_bucket", 0, true),
									new ItemMotion("minecraft:bucket", 0, false))),
					new Step(List.of(machine(POLYMERIZER, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 1, 2, 0, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:raw_rubber", 0, false))),
					new Step(rubberLine(true), 6, 2, List.of(Flow.energy(2, 0), Flow.energy(2, 6)), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:raw_rubber", 6, true),
									new ItemMotion("alaindustrial:sulfur_dust", 6, true),
									new ItemMotion("alaindustrial:rubber", 6, false))),
					new Step(rubberLine(false), 6, 2, List.of(Flow.energy(2, 0), Flow.energy(2, 6)), Verdict.BAD,
							List.of())));

	/**
	 * The fermenter turning crops and water into biofuel (out through the bottom only), a pipe on its side
	 * that cannot take biofuel, then the sprinkler feeding a wheat field from a tank of nutrient solution,
	 * and a cable that does not connect to it.
	 */
	public static final BlueprintDef BIO_FARM = new BlueprintDef("bio_farm",
			List.of(FERMENTER, SPRINKLER),
			List.of(
					new Step(List.of(fermenter(false, true), stone(0, 0, 0)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("minecraft:wheat", 0, true),
									new ItemMotion("minecraft:water_bucket", 0, true),
									new ItemMotion("minecraft:bucket", 0, false))),
					new Step(List.of(fermenter(false, true), cableOf(COPPER_CABLE, 1, 1, 0, "west", "east"),
							generatorAt(2, 1), fluidPipeOf(FLUID_PIPE, 0, 0, 0, "up", "extract", "east", "insert"),
							tankWith(1, 0, 0, "alaindustrial:biofuel")), 0, 1, List.of(Flow.energy(2, 0)), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:wheat", 0, true),
									new ItemMotion("alaindustrial:biomass", 0, false))),
					new Step(List.of(fermenter(false, false), cableOf(COPPER_CABLE, 1, 1, 0, "west", "east"),
							generatorAt(2, 1), stone(0, 0, 0),
							fluidPipeOf(FLUID_PIPE, -1, 1, 0, "east", "extract", "west", "insert"),
							tankWith(-2, 1, 0, null)), 0, 1, -1, -1, Verdict.BAD),
					new Step(join(List.of(sprinkler(true), stone(0, 0, 0),
							tankWith(-2, 1, 0, "alaindustrial:nutrient_solution"),
							fluidPipeOf(FLUID_PIPE, -1, 1, 0, "west", "extract", "east", "insert")), wheatField(true)),
							0, 2, List.of(), Verdict.GOOD, List.of()),
					new Step(join(List.of(sprinkler(false), stone(0, 0, 0), cableOf(COPPER_CABLE, 1, 1, 0, "east"),
							generatorAt(2, 1)), wheatField(false)), 0, 2, -1, -1, Verdict.BAD)));

	/**
	 * The sawmill (planks, then sawdust), the recycler fed three kinds of junk (rich slag) and one kind
	 * (poor slag), then the sawmill's planks piped into two storage modules that joined into one store.
	 */
	public static final BlueprintDef WOOD_AND_WASTE = new BlueprintDef("wood_and_waste",
			List.of(SAWMILL, RECYCLER, STORAGE_MODULE),
			List.of(
					new Step(List.of(machine(SAWMILL, 0, 0, false, true)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("minecraft:oak_log", 0, true),
									new ItemMotion("minecraft:oak_planks", 0, false))),
					new Step(List.of(machine(SAWMILL, 0, 0, false, true)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("minecraft:oak_log", 0, true),
									new ItemMotion("alaindustrial:sawdust", 0, false))),
					new Step(sawmillAndRecycler("3"), 4, 2, 2, 4, Verdict.GOOD,
							List.of(new ItemMotion("minecraft:cobblestone", 4, true),
									new ItemMotion("minecraft:iron_pickaxe", 4, true),
									new ItemMotion("alaindustrial:sawdust", 4, true),
									new ItemMotion("alaindustrial:slag_rich", 4, false))),
					new Step(sawmillAndRecycler("1"), 4, 2, 2, 4, Verdict.BAD,
							List.of(new ItemMotion("minecraft:cobblestone", 4, true),
									new ItemMotion("alaindustrial:slag_poor", 4, false))),
					new Step(List.of(machine(SAWMILL, 0, 0, false, true), cableOf(COPPER_CABLE, 1, 0, 0, "west",
							"east"),
							generatorAt(2), pipe(ITEM_PIPE, -1, "insert", "extract"), storageModule(-2, "west"),
							storageModule(-3, "east")), 4, 3,
							List.of(Flow.energy(2, 0), Flow.item(0, 4, "minecraft:oak_planks")), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:oak_log", 0, true)))));

	/**
	 * The thermal centrifuge splitting uranium dust: with a heater but no redstone signal it stands still,
	 * with a lever on it runs; then the small mob repeller, which a redstone signal switches off.
	 */
	public static final BlueprintDef REDSTONE_CONTROL = new BlueprintDef("redstone_control",
			List.of(THERMAL_CENTRIFUGE, MOB_REPELLER),
			List.of(
					new Step(List.of(centrifuge(false), stone(0, 0, 0)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:uranium_dust", 0, true),
									new ItemMotion("alaindustrial:uranium_shavings", 0, false))),
					new Step(centrifugeRig(false), 0, 1, -1, -1, Verdict.BAD),
					new Step(join(centrifugeRig(true), List.of(new Placement("minecraft:lever", -1, 1, 0,
							Map.of("face", "wall", "facing", "west", "powered", "true")))), 0, 1,
							List.of(Flow.energy(4, 1), Flow.energy(4, 0)), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:uranium_dust", 0, true),
									new ItemMotion("alaindustrial:uranium_shavings", 0, false))),
					new Step(List.of(new Placement(MOB_REPELLER, 0, 0, 0, Map.of("lit", "false"), Map.of("lit",
							"true")),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 2, 2, 0, Verdict.NONE),
					new Step(List.of(new Placement(MOB_REPELLER, 0, 0, 0, Map.of("lit", "false")),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2),
							new Placement("minecraft:lever", 0, 1, 0,
									Map.of("face", "floor", "facing", "south", "powered", "true"))),
							0, 2, -1, -1, Verdict.BAD)));

	/**
	 * Irradiated soil and the geiger counter, water speeding the decay, then dug soil in a plain chest (still
	 * glowing) and in the shielding chest (safe).
	 */
	public static final BlueprintDef RADIATION = new BlueprintDef("radiation",
			List.of(SHIELDING_CHEST, IRRADIATED_SOIL, GEIGER_COUNTER),
			List.of(
					new Step(crater(false), 0, 1, -1, -1, Verdict.NONE),
					new Step(crater(false), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion(GEIGER_COUNTER, 0, true))),
					new Step(crater(true, water(0, 1, 0)), 0, 1, -1, -1, Verdict.GOOD),
					new Step(crater(false, modChest(IRON_CHEST, 4)), 3, 2, List.of(Flow.item(2, 3, IRRADIATED_SOIL)),
							Verdict.BAD, List.of()),
					new Step(crater(false, modChest(SHIELDING_CHEST, 4)), 3, 2,
							List.of(Flow.item(2, 3, IRRADIATED_SOIL)), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:shielding_alloy_plate", 3, true)))));

	/**
	 * The trellis on moist farmland (paused on dry), harvested and fruiting again, then the garden drone
	 * station tending wheat, which harvests only with a hoe.
	 */
	public static final BlueprintDef GARDEN = new BlueprintDef("garden",
			List.of(TRELLIS, GARDEN_DRONE_STATION),
			List.of(
					new Step(trellisOn(true, "0", "1"), 2, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:cotton_seeds", 2, true))),
					new Step(trellisOn(false, "2", null), 1, 1, -1, -1, Verdict.BAD),
					new Step(trellisOn(true, "6", "4"), 2, 1, List.of(), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:cotton_fiber", 2, false))),
					new Step(droneField(false, "3", "5"), 0, 2,
							List.of(Flow.energy(2, 0), Flow.item(0, 7, "minecraft:bone_meal")), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:garden_drone", 0, true),
									new ItemMotion("minecraft:wheat_seeds", 0, true))),
					new Step(droneField(false, "7", null), 0, 2, 2, 0, Verdict.BAD),
					new Step(droneField(true, "7", "0"), 0, 2,
							List.of(Flow.energy(2, 0), Flow.item(7, 0, "minecraft:wheat")), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:iron_hoe", 0, true)))));

	/**
	 * The incubator: glass on the base becomes the dome, a chip and uranium arm it, a diamond is duplicated,
	 * and without a chip it does not start.
	 */
	public static final BlueprintDef MUTATION = new BlueprintDef("mutation",
			List.of(INCUBATOR),
			List.of(
					new Step(List.of(incubator(false)), 0, 1, -1, -1, Verdict.NONE),
					new Step(List.of(incubator(false), dome()), 1, 2, List.of(), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:glass", 1, true))),
					new Step(incubatorRig(false), 0, 1, List.of(Flow.energy(3, 0)), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:mutation_chip_duplicate", 0, true),
									new ItemMotion("alaindustrial:uranium_ingot", 0, true))),
					new Step(incubatorRig(true), 0, 1, List.of(Flow.energy(3, 0)), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:diamond", 0, true),
									new ItemMotion("minecraft:diamond", 0, false),
									new ItemMotion("minecraft:diamond", 0, false))),
					new Step(incubatorRig(false), 0, 1, List.of(), Verdict.BAD,
							List.of(new ItemMotion("minecraft:diamond", 0, true)))));

	private static List<Placement> rubberLine(boolean heated) {
		return List.of(machine(POLYMERIZER, 0, 0, true, false), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				generatorAt(2), heated
						? new Placement("minecraft:campfire", 4, 0, 0, Map.of("lit", "true", "facing", "south"))
						: stone(4, 0, 0),
				cableOf(COPPER_CABLE, 3, 0, 0, "west", "up"), cableOf(COPPER_CABLE, 3, 1, 0, "down", "east"),
				new Placement(VULCANIZER, 4, 1, 0, Map.of("facing", "south", "lit", "false"),
						heated ? Map.of("lit", "true") : Map.of()));
	}

	private static List<Placement> sawmillAndRecycler(String kinds) {
		return List.of(machine(SAWMILL, 0, 0, false, false), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				generatorAt(2), cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"), recycler(kinds));
	}

	/** Centrifuge over a heater, both fed by one cable from a generator; {@code runs} lights them up. */
	private static List<Placement> centrifugeRig(boolean runs) {
		return List.of(centrifuge(runs), heater(runs), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east", "up"),
				cableOf(COPPER_CABLE, 1, 1, 0, "west", "down"), generatorAt(2));
	}

	private MachineBlueprints() {
	}
}
