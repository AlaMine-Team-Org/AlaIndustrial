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

/** Blueprints about farm and wind field tools (MOD-797). Listed in {@link BlueprintCatalog#ALL}. */
public final class FieldToolsBlueprints {

	private static final String CRAFTING_TABLE = "minecraft:crafting_table";
	private static final String OAK_LEAVES = "minecraft:oak_leaves";

	/**
	 * The wind gauge made at a crafting table, then held against a short tower where the air is calm and
	 * the mill feeds nothing, the same mill on a tall tower feeding the battery box, and the tall tower
	 * under leaves, where the closed sky kills the wind even at height.
	 */
	public static final BlueprintDef WIND_GAUGE = new BlueprintDef("wind_gauge",
			List.of("alaindustrial:wind_gauge"),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+minecraft:stick", "+minecraft:stick", "+minecraft:glass_pane",
									"+alaindustrial:tin_ingot", "+alaindustrial:tin_ingot",
									"+alaindustrial:wooden_gear", "+alaindustrial:wooden_gear",
									"-alaindustrial:wind_gauge")),
					lowTowerStep(),
					highTowerStep(),
					leafedTowerStep()));

	/**
	 * The garden drone made at a crafting table, then a station whose drone slot is empty and whose wheat
	 * stays young, the same field with the drone at work ripening the wheat, and the iron hoe going into
	 * the station (0, position 1), which the drone borrows from it.
	 */
	public static final BlueprintDef GARDEN_DRONE = new BlueprintDef("garden_drone",
			List.of("alaindustrial:garden_drone"),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+minecraft:copper_ingot", "+minecraft:copper_ingot",
									"+minecraft:copper_ingot", "+minecraft:copper_ingot", "+minecraft:iron_ingot",
									"+minecraft:iron_ingot", "+alaindustrial:electronic_circuit", "+minecraft:hopper",
									"-alaindustrial:garden_drone")),
					new Step(dronelessField(), 0, 1, List.of(), Verdict.BAD, List.of()),
					new Step(droneField(true, "3", "7"), 0, 1, List.of(Flow.energy(2, 0)), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:garden_drone", 0, true))),
					new Step(droneField(true, "3", "7"), 0, 1, List.of(Flow.energy(2, 0)), Verdict.NONE,
							List.of(new ItemMotion("minecraft:iron_hoe", 0, true)))));

	/**
	 * The soul vessel made at a crafting table; the small mob repeller on its line, whose slot takes any
	 * vessel, even an empty one (0, position 2); a full vessel going in, which the repeller consumes; the
	 * repeller grown into the medium one on a gold line.
	 */
	public static final BlueprintDef SOUL_VESSEL = new BlueprintDef("soul_vessel",
			List.of("alaindustrial:soul_vessel"),
			List.of(
					new Step(List.of(table()), 0, 1, List.of(), Verdict.NONE,
							motions(0, "+alaindustrial:silver_plate", "+alaindustrial:silver_plate",
									"+alaindustrial:silver_plate", "+alaindustrial:silver_plate",
									"+minecraft:glass", "+minecraft:glass", "+minecraft:glass",
									"+minecraft:soul_sand", "+alaindustrial:electronic_circuit",
									"-alaindustrial:soul_vessel")),
					new Step(repellerLine(repeller(MOB_REPELLER, true, false), COPPER_CABLE), 0, 2,
							List.of(Flow.energy(2, 0)), Verdict.NONE, List.of()),
					new Step(repellerLine(repeller(MOB_REPELLER, true, false), COPPER_CABLE), 0, 2,
							List.of(Flow.energy(2, 0)), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:soul_vessel", 0, true))),
					new Step(repellerLine(repeller(MOB_REPELLER_MV, false, true), GOLD_CABLE), 0, 2,
							List.of(Flow.energy(2, 0)), Verdict.GOOD, List.of())));

	/** The crafting table every recipe of this group is made at. */
	private static Placement table() {
		return new Placement(CRAFTING_TABLE, 0, 0, 0, Map.of());
	}

	/** A wind mill with its rotor at ({@code y}), facing west so its back, where the cable goes, is in view. */
	private static Placement millOn(int y) {
		return new Placement(WIND_MILL, 0, y, 0, Map.of("facing", "west")).holding("alaindustrial:windmill_rotor");
	}

	/**
	 * A wind mill on a stone tower of {@code height} blocks: 0..height-1 stone, {@code height} the mill,
	 * then its cable down the side to the battery box on the ground.
	 */
	private static List<Placement> towerRig(int height) {
		List<Placement> all = new ArrayList<>();
		for (int y = 0; y < height; y++) {
			all.add(stone(0, y, 0));
		}
		all.add(millOn(height));
		all.add(cableOf(COPPER_CABLE, 1, height, 0, "west", "down"));
		for (int y = height - 1; y >= 1; y--) {
			all.add(cableOf(COPPER_CABLE, 1, y, 0, "up", "down"));
		}
		all.add(cableOf(COPPER_CABLE, 1, 0, 0, "up", "east"));
		all.add(batteryBox("west"));
		return all;
	}

	/** The mill on a two-block tower (its index is 2): the gauge reads calm air, no energy reaches the box. */
	private static Step lowTowerStep() {
		return new Step(towerRig(2), 2, 2, -1, -1, Verdict.BAD,
				List.of(new ItemMotion("alaindustrial:wind_gauge", 2, true)));
	}

	/** The same mill on an eight-block tower (its index is 8): the wind reaches it and feeds the box. */
	private static Step highTowerStep() {
		List<Placement> rig = towerRig(8);
		return new Step(rig, 8, 2, 8, rig.size() - 1, Verdict.GOOD);
	}

	/** The tall tower with oak leaves right over the mill: the closed sky kills the wind. */
	private static Step leafedTowerStep() {
		List<Placement> rig = towerRig(8);
		rig.add(new Placement(OAK_LEAVES, 0, 9, 0, Map.of()));
		return new Step(rig, rig.size() - 1, 3, -1, -1, Verdict.BAD);
	}

	/**
	 * The drone field with an empty drone slot: 0 station, 1 cable, 2 generator, 3..5 farmland, 6..8 wheat
	 * that stays young for the whole step.
	 */
	private static List<Placement> dronelessField() {
		Placement station = new Placement(GARDEN_DRONE_STATION, 0, 0, 0, Map.of()).holding(
				"minecraft:wheat_seeds", "minecraft:bone_meal", "minecraft:air", "minecraft:air",
				"minecraft:air", "minecraft:air", "minecraft:air", "minecraft:iron_hoe");
		List<Placement> all = new ArrayList<>(List.of(station,
				cableOf(COPPER_CABLE, -1, 0, 0, "west", "east"), generatorAt(-2)));
		for (int x = 1; x <= 3; x++) {
			all.add(new Placement("minecraft:farmland", x, 0, 0, Map.of("moisture", "7")));
		}
		for (int x = 1; x <= 3; x++) {
			all.add(new Placement("minecraft:wheat", x, 1, 0, Map.of("age", "3")));
		}
		return all;
	}

	/** Item motions on block {@code index}: each entry is "+id" for an input or "-id" for an output. */
	private static List<ItemMotion> motions(int index, String... entries) {
		List<ItemMotion> all = new ArrayList<>();
		for (String entry : entries) {
			all.add(new ItemMotion(entry.substring(1), index, entry.charAt(0) == '+'));
		}
		return all;
	}

	private FieldToolsBlueprints() {
	}
}
