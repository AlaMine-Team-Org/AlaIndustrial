package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;
import static dev.alaindustrial.client.blueprint.BlueprintRigs.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about items of the nuclear and the organic branch (MOD-797): the uranium chain up to the fuel
 * rod, what the incubator makes, its chips, cans and vacuum capsules, and the bio fluids. Listed in
 * {@link BlueprintCatalog#ALL}.
 */
public final class NuclearBioBlueprints {

	private static final String RAW_URANIUM = "alaindustrial:raw_uranium";
	private static final String URANIUM_DUST = "alaindustrial:uranium_dust";
	private static final String REFINED_URANIUM = "alaindustrial:refined_uranium";
	private static final String URANIUM_INGOT = "alaindustrial:uranium_ingot";
	private static final String URANIUM_SHAVINGS = "alaindustrial:uranium_shavings";
	private static final String EMPTY_FUEL_ROD = "alaindustrial:empty_fuel_rod";
	private static final String URANIUM_FUEL_ROD = "alaindustrial:uranium_fuel_rod";
	private static final String DEPLETED_URANIUM = "alaindustrial:depleted_uranium";

	private static final String IRRADIATED_DIAMOND = "alaindustrial:irradiated_diamond";
	private static final String IRRADIATED_SLAG = "alaindustrial:irradiated_slag";
	private static final String UNSTABLE_ISOTOPE = "alaindustrial:unstable_isotope";
	private static final String MUTAGEN_DUST = "alaindustrial:mutagen_dust";

	private static final String CHIP_CREATE = "alaindustrial:mutation_chip_create";
	private static final String CHIP_DUPLICATE = "alaindustrial:mutation_chip_duplicate";
	private static final String CHIP_TRANSFORM = "alaindustrial:mutation_chip_transform";
	private static final String EMPTY_CHIP = "alaindustrial:empty_chip";

	private static final String EMPTY_CAN = "alaindustrial:empty_can";
	private static final String CANNED_RATION = "alaindustrial:canned_ration";
	private static final String VACUUM_CAPSULE = "alaindustrial:vacuum_capsule";
	private static final String FILLED_CAPSULE = "alaindustrial:filled_vacuum_capsule";

	private static final String BIOMASS = "alaindustrial:biomass";
	private static final String BIOFUEL_BUCKET = "alaindustrial:biofuel_bucket";
	private static final String NUTRIENT_BUCKET = "alaindustrial:nutrient_solution_bucket";

	private static final String CRAFTING_TABLE = "minecraft:crafting_table";
	private static final String COLUMN = "alaindustrial:distillation_column";
	private static final String COLUMN_MIDDLE = "alaindustrial:distillation_column_middle";
	private static final String COLUMN_TOP = "alaindustrial:distillation_column_top";

	/**
	 * Raw uranium through the macerator (0) into dust and the dust smelted into an ingot in the electric
	 * furnace (4); the thermal centrifuge (0, on its heater 1, lever 5) splitting dust into concentrate,
	 * then an ingot into depleted uranium; the compressor (0) with its cable (1) turned away, starving, then
	 * connected, pressing two concentrates into enriched uranium; the crafting table (0) joining the
	 * cladding and the enriched uranium into a fuel rod.
	 */
	public static final BlueprintDef URANIUM_FUEL = new BlueprintDef("uranium_fuel",
			List.of(RAW_URANIUM, URANIUM_DUST, URANIUM_INGOT, URANIUM_SHAVINGS, DEPLETED_URANIUM, REFINED_URANIUM,
					EMPTY_FUEL_ROD, URANIUM_FUEL_ROD),
			List.of(
					new Step(oreLine(), 0, 1,
							List.of(Flow.energy(2, 0), Flow.energy(2, 4), Flow.item(0, 4, URANIUM_DUST)), Verdict.NONE,
							List.of(new ItemMotion(RAW_URANIUM, 0, true), new ItemMotion(URANIUM_DUST, 0, false),
									new ItemMotion(URANIUM_DUST, 4, true), new ItemMotion(URANIUM_INGOT, 4, false))),
					new Step(spinningCentrifuge(), 0, 2, List.of(Flow.energy(4, 1), Flow.energy(4, 0)), Verdict.GOOD,
							List.of(new ItemMotion(URANIUM_DUST, 0, true), new ItemMotion(URANIUM_SHAVINGS, 0, false),
									new ItemMotion(URANIUM_SHAVINGS, 0, false))),
					new Step(spinningCentrifuge(), 0, 2, List.of(Flow.energy(4, 1), Flow.energy(4, 0)), Verdict.NONE,
							List.of(new ItemMotion(URANIUM_INGOT, 0, true), new ItemMotion(DEPLETED_URANIUM, 0, false),
									new ItemMotion(DEPLETED_URANIUM, 0, false))),
					new Step(pressLine(false), 0, 3, List.of(), Verdict.BAD,
							List.of(new ItemMotion(URANIUM_SHAVINGS, 0, true),
									new ItemMotion(URANIUM_SHAVINGS, 0, true))),
					new Step(pressLine(true), 0, 3, List.of(Flow.energy(2, 0)), Verdict.GOOD,
							List.of(new ItemMotion(URANIUM_SHAVINGS, 0, true),
									new ItemMotion(URANIUM_SHAVINGS, 0, true),
									new ItemMotion(REFINED_URANIUM, 0, false))),
					new Step(List.of(table()), 0, 4, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(EMPTY_FUEL_ROD, 0, true), new ItemMotion(REFINED_URANIUM, 0, true),
									new ItemMotion(URANIUM_FUEL_ROD, 0, false)))));

	/**
	 * The incubator (0, dome 1, cable 2, generator 3) with a creation chip and uranium making an irradiated
	 * diamond from a diamond, mutagen dust from lapis, an unstable isotope from its own depleted uranium,
	 * and irradiated slag from a failed try; then the base with no dome (0, cable 1, generator 2) standing
	 * dark, and glass going onto it to raise the dome.
	 */
	public static final BlueprintDef RADIATION_PRODUCTS = new BlueprintDef("radiation_products",
			List.of(IRRADIATED_DIAMOND, MUTAGEN_DUST, UNSTABLE_ISOTOPE, IRRADIATED_SLAG),
			List.of(
					new Step(incubatorRig(true), 0, 1, List.of(Flow.energy(3, 0)), Verdict.NONE,
							List.of(new ItemMotion(CHIP_CREATE, 0, true), new ItemMotion(URANIUM_INGOT, 0, true),
									new ItemMotion("minecraft:diamond", 0, true),
									new ItemMotion(IRRADIATED_DIAMOND, 0, false))),
					new Step(incubatorRig(true), 0, 1, List.of(Flow.energy(3, 0)), Verdict.NONE,
							List.of(new ItemMotion("minecraft:lapis_lazuli", 0, true),
									new ItemMotion(MUTAGEN_DUST, 0, false))),
					new Step(incubatorRig(true), 0, 1, List.of(Flow.energy(3, 0)), Verdict.GOOD,
							List.of(new ItemMotion(DEPLETED_URANIUM, 0, false),
									new ItemMotion(DEPLETED_URANIUM, 0, true),
									new ItemMotion(UNSTABLE_ISOTOPE, 0, false))),
					new Step(incubatorRig(true), 0, 1, List.of(Flow.energy(3, 0)), Verdict.NONE,
							List.of(new ItemMotion("minecraft:diamond", 0, true),
									new ItemMotion(IRRADIATED_SLAG, 0, false))),
					new Step(domelessRig(), 0, 2, List.of(Flow.energy(2, 0)), Verdict.BAD,
							List.of(new ItemMotion("minecraft:diamond", 0, true))),
					new Step(incubatorRig(true), 1, 2, List.of(Flow.energy(3, 0)), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:glass", 1, true)))));

	/**
	 * The crafting table (0) making an empty chip, then a duplication chip from it; the incubator (0, dome 1,
	 * cable 2, generator 3) doubling an iron ingot with the duplication chip and turning sand red with the
	 * transformation chip; a creation chip that has nothing to make from iron, the machine dark; the same chip
	 * making a resonant shard from amethyst.
	 */
	public static final BlueprintDef MUTATION_CHIPS = new BlueprintDef("mutation_chips",
			List.of(EMPTY_CHIP, CHIP_DUPLICATE, CHIP_TRANSFORM, CHIP_CREATE),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:glass", 0, true),
									new ItemMotion("alaindustrial:copper_coil", 0, true),
									new ItemMotion("alaindustrial:electronic_circuit", 0, true),
									new ItemMotion(EMPTY_CHIP, 0, false))),
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion(EMPTY_CHIP, 0, true),
									new ItemMotion("minecraft:amethyst_shard", 0, true),
									new ItemMotion("alaindustrial:uranium_plate", 0, true),
									new ItemMotion(CHIP_DUPLICATE, 0, false))),
					new Step(incubatorRig(true), 0, 2, List.of(Flow.energy(3, 0)), Verdict.GOOD,
							List.of(new ItemMotion(CHIP_DUPLICATE, 0, true), new ItemMotion(URANIUM_INGOT, 0, true),
									new ItemMotion("minecraft:iron_ingot", 0, true),
									new ItemMotion("minecraft:iron_ingot", 0, false),
									new ItemMotion("minecraft:iron_ingot", 0, false))),
					new Step(incubatorRig(true), 0, 2, List.of(Flow.energy(3, 0)), Verdict.NONE,
							List.of(new ItemMotion(CHIP_TRANSFORM, 0, true), new ItemMotion("minecraft:sand", 0, true),
									new ItemMotion("minecraft:red_sand", 0, false))),
					new Step(incubatorRig(false), 0, 2, List.of(Flow.energy(3, 0)), Verdict.BAD,
							List.of(new ItemMotion(CHIP_CREATE, 0, true),
									new ItemMotion("minecraft:iron_ingot", 0, true))),
					new Step(incubatorRig(true), 0, 2, List.of(Flow.energy(3, 0)), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:amethyst_shard", 0, true),
									new ItemMotion("alaindustrial:resonant_shard", 0, false)))));

	/**
	 * The crafting table (0) making empty cans from tin plates and a glass pane; the canning machine (0) fed
	 * through its front by a cable (1), dark, then fed from the side, turning food and cans into rations; the
	 * pump (0) on a lava lake (2 the cell it draws, cable 5, generator 6) filling a vacuum capsule; the
	 * geothermal generator (0) taking the filled capsule and lighting up.
	 */
	public static final BlueprintDef CANNED_GOODS = new BlueprintDef("canned_goods",
			List.of(EMPTY_CAN, CANNED_RATION, VACUUM_CAPSULE, FILLED_CAPSULE),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:tin_plate", 0, true),
									new ItemMotion("minecraft:glass_pane", 0, true),
									new ItemMotion(EMPTY_CAN, 0, false))),
					new Step(List.of(machine(CANNING_MACHINE, 0, 0, false, false),
							cableOf(COPPER_CABLE, 0, 0, 1, "north", "east"), frontGenerator()), 0, 2,
							List.of(Flow.energy(2, 1)), Verdict.BAD,
							List.of(new ItemMotion("minecraft:carrot", 0, true), new ItemMotion(EMPTY_CAN, 0, true))),
					new Step(List.of(machine(CANNING_MACHINE, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 2,
							List.of(Flow.energy(2, 0)), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:carrot", 0, true),
									new ItemMotion("minecraft:bread", 0, true), new ItemMotion(EMPTY_CAN, 0, true),
									new ItemMotion(CANNED_RATION, 0, false))),
					new Step(lake(pump("north", false, true), cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
							generatorAt(4)), 0, 3, List.of(Flow.lava(2, 0), Flow.energy(6, 0)), Verdict.NONE,
							List.of(new ItemMotion(VACUUM_CAPSULE, 0, true), new ItemMotion(FILLED_CAPSULE, 0, false))),
					new Step(List.of(geothermal(0, false, true)), 0, 4, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(FILLED_CAPSULE, 0, true),
									new ItemMotion(VACUUM_CAPSULE, 0, false)))));

	/**
	 * The fermenter (0, on stone 1, cable 2, generator 3) brewing wheat and water into a biofuel bucket and,
	 * sometimes, biomass; the distillation tower (0 base, 1 middle, 2 top) taking biofuel with no power, dark,
	 * then with a cable (3) and generator (4), giving nutrient solution at its top; the sprinkler (0) given
	 * biofuel, standing still, then given nutrient solution, spraying the wheat ripe.
	 */
	public static final BlueprintDef BIO_FLUIDS = new BlueprintDef("bio_fluids",
			List.of(BIOFUEL_BUCKET, BIOMASS, NUTRIENT_BUCKET),
			List.of(
					new Step(List.of(fermenter(false, true), stone(0, 0, 0),
							cableOf(COPPER_CABLE, 1, 1, 0, "west", "east"), generatorAt(2, 1)), 0, 1,
							List.of(Flow.energy(3, 0)), Verdict.NONE,
							List.of(new ItemMotion("minecraft:wheat", 0, true),
									new ItemMotion("minecraft:water_bucket", 0, true),
									new ItemMotion("minecraft:bucket", 0, true),
									new ItemMotion(BIOFUEL_BUCKET, 0, false),
									new ItemMotion(BIOMASS, 0, false))),
					new Step(tower(false), 1, 2, List.of(), Verdict.BAD,
							List.of(new ItemMotion(BIOFUEL_BUCKET, 1, true))),
					new Step(join(tower(true), List.of(cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2))),
							1, 2, List.of(Flow.energy(4, 0)), Verdict.GOOD,
							List.of(new ItemMotion(BIOFUEL_BUCKET, 1, true),
									new ItemMotion(NUTRIENT_BUCKET, 2, false))),
					new Step(join(List.of(sprinkler(false), stone(0, 0, 0)), wheatField(false)), 0, 3, List.of(),
							Verdict.BAD, List.of(new ItemMotion(BIOFUEL_BUCKET, 0, true))),
					new Step(join(List.of(sprinkler(true), stone(0, 0, 0)), wheatField(true)), 0, 3, List.of(),
							Verdict.GOOD,
							List.of(new ItemMotion(NUTRIENT_BUCKET, 0, true),
									new ItemMotion("minecraft:bucket", 0, false)))));

	private NuclearBioBlueprints() {
	}

	/** A vanilla crafting table at the origin. */
	private static Placement table() {
		return new Placement(CRAFTING_TABLE, 0, 0, 0, Map.of());
	}

	/** A burning generator at (1, 0, 1), beside the cable in front of a machine. */
	private static Placement frontGenerator() {
		return new Placement(GENERATOR, 1, 0, 1, Map.of("facing", "south", "lit", "true"));
	}

	/** Macerator (0), cable (1), generator (2), cable (3), electric furnace (4); both machines light up late. */
	private static List<Placement> oreLine() {
		return List.of(machine(MACERATOR, 0, 0, false, true), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				generatorAt(2), cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
				machine(ELECTRIC_FURNACE, 4, 0, false, true));
	}

	/**
	 * The thermal centrifuge (0) on a warming heater (1), cables (2, 3), a generator (4) and a lever held on
	 * (5): a copy of the redstone blueprint's rig, which is private to its own group.
	 */
	private static List<Placement> spinningCentrifuge() {
		return List.of(centrifuge(true), heater(true), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east", "up"),
				cableOf(COPPER_CABLE, 1, 1, 0, "west", "down"), generatorAt(2),
				new Placement("minecraft:lever", -1, 1, 0,
						Map.of("face", "wall", "facing", "west", "powered", "true")));
	}

	/** Compressor (0), cable (1), generator (2); the cable reaches the compressor only when {@code linked}. */
	private static List<Placement> pressLine(boolean linked) {
		Placement cable = linked ? cableOf(COPPER_CABLE, 1, 0, 0, "west", "east")
				: cableOf(COPPER_CABLE, 1, 0, 0, "east");
		return List.of(machine(COMPRESSOR, 0, 0, false, linked), cable, generatorAt(2));
	}

	/** The incubator base (0) with no dome, a cable (1) and a generator (2). */
	private static List<Placement> domelessRig() {
		return List.of(incubator(false), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2));
	}

	/** The formed distillation tower: 0 base, 1 middle, 2 top, lighting up late when {@code runs}. */
	private static List<Placement> tower(boolean runs) {
		Map<String, String> late = runs ? Map.of("lit", "true") : Map.of();
		List<Placement> all = new ArrayList<>();
		all.add(new Placement(COLUMN, 0, 0, 0, Map.of("formed", "true", "lit", "false"), late));
		all.add(new Placement(COLUMN_MIDDLE, 0, 1, 0, Map.of("lit", "false"), late));
		all.add(new Placement(COLUMN_TOP, 0, 2, 0, Map.of("lit", "false"), late));
		return all;
	}
}
