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

/** Blueprints about moving items and fluids (MOD-797). Listed in {@link BlueprintCatalog#ALL}. */
public final class LogisticsBlueprints {

	/**
	 * A pipe between two chests: plain on both sides nothing moves; set with the wrench it takes from the
	 * first chest and puts into the second (the item rides the pipe); the advanced pipe is faster; one plain
	 * pipe slows an advanced line. Chests are drawn by their block-entity renderer.
	 */
	public static final BlueprintDef ITEM_PIPES = new BlueprintDef("item_pipes",
			List.of(ITEM_PIPE, ITEM_PIPE_ADVANCED),
			List.of(
					new Step(List.of(chest(0), pipe(ITEM_PIPE, 1, "extract", "insert"), chest(2)), 1, 1,
							List.of(Flow.item(0, 2, "minecraft:iron_ingot")), Verdict.NONE,
							List.of(new ItemMotion("minecraft:iron_ingot", 0, true),
									new ItemMotion("minecraft:iron_ingot", 2, false))),
					new Step(List.of(chest(0), pipe(ITEM_PIPE, 1, "neutral", "neutral"), chest(2)),
							1, 1, -1, -1, Verdict.BAD),
					new Step(List.of(chest(0), pipe(ITEM_PIPE, 1, "extract", "insert"), chest(2)), 1, 1,
							List.of(Flow.item(0, 2, "minecraft:iron_ingot")), Verdict.GOOD, List.of()),
					new Step(List.of(chest(0), pipe(ITEM_PIPE_ADVANCED, 1, "extract", "insert"), chest(2)), 1, 2,
							List.of(Flow.item(0, 2, "minecraft:iron_ingot"), Flow.item(0, 2, "minecraft:copper_ingot")),
							Verdict.GOOD, List.of()),
					new Step(List.of(chest(0), pipe(ITEM_PIPE_ADVANCED, 1, "extract", "neutral"),
							pipe(ITEM_PIPE, 2, "neutral", "neutral"), pipe(ITEM_PIPE_ADVANCED, 3, "neutral", "insert"),
							chest(4)), 2, 1, List.of(Flow.item(0, 4, "minecraft:iron_ingot")),
							Verdict.BAD, List.of())));

	/**
	 * A pump drawing from a small lava lake at its front, powered by a generator, a fluid pipe carrying the
	 * lava into a geothermal generator, then the pump turned away from the lake, which stops it.
	 */
	public static final BlueprintDef FLUIDS = new BlueprintDef("fluids",
			List.of(PUMP, FLUID_PIPE),
			List.of(
					new Step(lake(pump("north", false, true)), 0, 1, List.of(Flow.lava(2, 0)), Verdict.NONE,
							List.of()),
					new Step(lake(pump("north", true, false),
							generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east")),
							0, 1, List.of(Flow.energy(5, 0), Flow.lava(2, 0)), Verdict.NONE, List.of()),
					new Step(lake(pump("north", true, false),
							generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
							fluidPipe("extract", null)), 7, 2, List.of(Flow.energy(5, 0), Flow.lava(2, 0)),
							Verdict.NONE, List.of()),
					new Step(lake(pump("north", true, false),
							generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
							fluidPipe("extract", "insert"), geothermal(4, false, true)), 7, 2,
							List.of(Flow.energy(5, 0), Flow.lava(2, 0), Flow.lava(0, 8)), Verdict.GOOD, List.of()),
					new Step(lake(pump("south", false, false),
							generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
							fluidPipe("extract", "insert"), geothermal(4, false, false)), 0, 1, List.of(),
							Verdict.BAD, List.of())));

	/**
	 * The advanced fluid pipe moving water between tanks, one plain pipe slowing the line, then the
	 * reinforced pipe inside a reactor wall feeding a reactor column, and lava where a plain pipe melted.
	 */
	public static final BlueprintDef PIPE_GRADES = new BlueprintDef("pipe_grades",
			List.of(FLUID_PIPE_ADVANCED, REINFORCED_FLUID_PIPE),
			List.of(
					new Step(List.of(tankWith(0, 0, 0, "minecraft:water"),
							fluidPipeOf(FLUID_PIPE_ADVANCED, 1, 0, 0, "west", "extract", "east", "neutral"),
							fluidPipeOf(FLUID_PIPE_ADVANCED, 2, 0, 0, "west", "neutral", "east", "insert"),
							tankWith(3, 0, 0, null)), 1, 1, List.of(Flow.water(0, 3)), Verdict.GOOD, List.of()),
					new Step(List.of(tankWith(0, 0, 0, "minecraft:water"),
							fluidPipeOf(FLUID_PIPE_ADVANCED, 1, 0, 0, "west", "extract", "east", "neutral"),
							fluidPipeOf(FLUID_PIPE, 2, 0, 0, "west", "neutral", "east", "neutral"),
							fluidPipeOf(FLUID_PIPE_ADVANCED, 3, 0, 0, "west", "neutral", "east", "insert"),
							tankWith(4, 0, 0, null)), 2, 1, List.of(Flow.water(0, 4)), Verdict.BAD, List.of()),
					new Step(reactorFeed(fluidPipeOf(REINFORCED_FLUID_PIPE, 3, 0, 0, "west", "extract", "east",
							"insert")),
							4, 2, List.of(Flow.water(0, 5)), Verdict.GOOD, List.of()),
					new Step(reactorFeed(new Placement("minecraft:lava", 3, 0, 0, Map.of())), 4, 2,
							List.of(Flow.water(0, 2)), Verdict.BAD, List.of())));

	/** Tank, plain pipe, reactor inlet with casing above, {@code inside}, reactor column. */
	private static List<Placement> reactorFeed(Placement inside) {
		return List.of(tankWith(0, 0, 0, "minecraft:water"),
				fluidPipeOf(FLUID_PIPE, 1, 0, 0, "west", "extract", "east", "insert"),
				new Placement(REACTOR_PORT, 2, 0, 0, Map.of("formed", "false", "edge", "false")),
				new Placement(REACTOR_CASING, 2, 1, 0, Map.of("formed", "false", "edge", "false")),
				inside,
				new Placement(FUEL_ROD_ASSEMBLY, 4, 0, 0, Map.of("rods", "4", "fuelled", "4", "water", "2")));
	}

	/**
	 * The chest tiers: a copper chest ringed with iron makes the iron chest, then each tier is made from the
	 * one before, up to the diamond chest that TNT does not break.
	 */
	public static final BlueprintDef CHEST_TIERS = new BlueprintDef("chest_tiers",
			List.of(IRON_CHEST, SILVER_CHEST, GOLD_CHEST, ELECTRUM_CHEST, DIAMOND_CHEST),
			List.of(
					new Step(chestRow(1), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:copper_chest", 0, true),
									new ItemMotion("minecraft:iron_ingot", 0, true))),
					new Step(chestRow(2), 1, 2, List.of(Flow.item(0, 1, IRON_CHEST)), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:silver_ingot", 1, true))),
					new Step(chestRow(3), 2, 3, List.of(Flow.item(1, 2, SILVER_CHEST)), Verdict.NONE,
							List.of(new ItemMotion("minecraft:gold_ingot", 2, true))),
					new Step(chestRow(4), 3, 4, List.of(Flow.item(2, 3, GOLD_CHEST)), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:electrum_ingot", 3, true))),
					new Step(join(chestRow(5), List.of(new Placement("minecraft:tnt", 5, 0, 0, Map.of()))), 4, 5,
							List.of(Flow.item(3, 4, ELECTRUM_CHEST)), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:diamond", 4, true)))));

	/**
	 * The wrench setting one pipe side per click, then taking a machine apart, and the network analyzer on a
	 * broken line and a mended one.
	 */
	public static final BlueprintDef TOOL_KIT = new BlueprintDef("tool_kit",
			List.of(WRENCH, NETWORK_ANALYZER),
			List.of(
					new Step(List.of(chest(0), pipe(ITEM_PIPE, 1, "neutral", "neutral"), chest(2)), 1, 1, -1, -1,
							Verdict.BAD),
					new Step(List.of(chest(0), new Placement(ITEM_PIPE, 1, 0, 0,
							Map.of("west_mode", "neutral", "east_mode", "neutral"), Map.of("west_mode", "extract")),
							chest(2)), 1, 1, List.of(), Verdict.NONE, List.of(new ItemMotion(WRENCH, 1, true))),
					new Step(List.of(chest(0), new Placement(ITEM_PIPE, 1, 0, 0,
							Map.of("west_mode", "extract", "east_mode", "neutral"), Map.of("east_mode", "insert")),
							chest(2)), 1, 1, List.of(Flow.item(0, 2, "minecraft:iron_ingot")), Verdict.GOOD,
							List.of(new ItemMotion(WRENCH, 1, true))),
					new Step(List.of(machine(MACERATOR, 1, 0, false, false)), 0, 2, List.of(), Verdict.NONE,
							List.of(new ItemMotion(WRENCH, 0, true), new ItemMotion(MACERATOR, 0, false))),
					new Step(List.of(generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west"),
							machine(ELECTRIC_FURNACE, 3, 0, false, false)), 1, 3, List.of(), Verdict.BAD,
							List.of(new ItemMotion(NETWORK_ANALYZER, 1, true))),
					new Step(List.of(generatorAt(0), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
							cableOf(COPPER_CABLE, 2, 0, 0, "west", "east"), machine(ELECTRIC_FURNACE, 3, 0, false,
									true)),
							1, 3, List.of(Flow.energy(0, 3)), Verdict.GOOD,
							List.of(new ItemMotion(NETWORK_ANALYZER, 1, true)))));

	private LogisticsBlueprints() {
	}
}
