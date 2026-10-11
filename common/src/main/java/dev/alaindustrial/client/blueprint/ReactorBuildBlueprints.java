package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;
import static dev.alaindustrial.client.blueprint.BlueprintRigs.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about building the reactor room (MOD-797). Listed in {@link BlueprintCatalog#ALL}.
 */
public final class ReactorBuildBlueprints {

	private static final String CRAFTING_TABLE = "minecraft:crafting_table";
	private static final String IRON_INGOT = "minecraft:iron_ingot";
	private static final String PISTON = "minecraft:piston";
	private static final String IRON_DOOR = "minecraft:iron_door";
	private static final String MACHINE_CASING = "alaindustrial:machine_casing";
	private static final String TEMPERED_IRON_PLATE = "alaindustrial:tempered_iron_plate";
	private static final String SHIELDING_PLATE = "alaindustrial:shielding_alloy_plate";
	private static final String REINFORCED_PLATE = "alaindustrial:shielding_alloy_reinforced_plate";
	private static final String ELECTRONIC_CIRCUIT = "alaindustrial:electronic_circuit";
	private static final String CONTROL_ROD_DRIVE = "alaindustrial:control_rod_drive";
	private static final String REACTOR_DOOR = "alaindustrial:reactor_door";
	private static final String REACTOR_BUTTON = "alaindustrial:reactor_button";

	/**
	 * The casing craft on the table (0, position 1): six tempered iron plates, two shielding plates and a
	 * machine casing; a flat wall of loose casing, its middle cell position 2; the same wall with the
	 * controller in its middle and one casing cell missing, which the controller refuses (BAD); the whole
	 * room forming late around its controller (position 3, GOOD).
	 * Wall indices: the flat wall's middle cell is 4, the holed wall's controller is 0.
	 */
	public static final BlueprintDef REACTOR_SHELL = new BlueprintDef("reactor_shell",
			List.of(REACTOR_CASING),
			List.of(
					new Step(List.of(table()), 0, 1, -1, -1, Verdict.NONE,
							motions("+" + TEMPERED_IRON_PLATE, "+" + TEMPERED_IRON_PLATE,
									"+" + TEMPERED_IRON_PLATE, "+" + TEMPERED_IRON_PLATE, "+" + TEMPERED_IRON_PLATE,
									"+" + TEMPERED_IRON_PLATE, "+" + SHIELDING_PLATE, "+" + SHIELDING_PLATE,
									"+" + MACHINE_CASING, "-" + REACTOR_CASING)),
					new Step(looseWall(), 4, 2, -1, -1, Verdict.NONE),
					new Step(holedWall(), 0, 3, -1, -1, Verdict.BAD),
					new Step(room(REACTOR_GLASS, false, true, List.of()), 0, 3, -1, -1, Verdict.GOOD)));

	/**
	 * The control rod drive craft on the table (0, position 1); the airlock door craft around a drive
	 * (GOOD); the formed room with a real doorway at the inner floor level and a button beside it
	 * (position 2, GOOD); the same room with both door halves lying in the floor ring, which is no
	 * doorway (BAD). Room indices: see {@link BlueprintRigs#room}, then 11 the lower door half,
	 * 12 the upper half, 13 the button.
	 */
	public static final BlueprintDef DOOR_DRIVE = new BlueprintDef("door_drive",
			List.of(CONTROL_ROD_DRIVE),
			List.of(
					new Step(List.of(table()), 0, 1, -1, -1, Verdict.NONE,
							motions("+" + SHIELDING_PLATE, "+" + SHIELDING_PLATE, "+" + SHIELDING_PLATE,
									"+" + IRON_INGOT, "+" + IRON_INGOT, "+" + ELECTRONIC_CIRCUIT,
									"+" + TEMPERED_IRON_PLATE, "+" + TEMPERED_IRON_PLATE, "+" + PISTON,
									"-" + CONTROL_ROD_DRIVE)),
					new Step(List.of(table()), 0, 1, -1, -1, Verdict.GOOD,
							motions("+" + SHIELDING_PLATE, "+" + SHIELDING_PLATE, "+" + REACTOR_GLASS,
									"+" + INSULATED_COPPER_CABLE, "+" + INSULATED_COPPER_CABLE,
									"+" + IRON_DOOR, "+" + REINFORCED_PLATE, "+" + REINFORCED_PLATE,
									"+" + CONTROL_ROD_DRIVE, "-" + REACTOR_DOOR)),
					new Step(room(REACTOR_GLASS, true, false, doorway()), 11, 2, -1, -1, Verdict.GOOD),
					new Step(room(REACTOR_GLASS, false, false, flatDoorway()), 11, 2, -1, -1,
							Verdict.BAD)));

	private ReactorBuildBlueprints() {
	}

	/** A crafting table at the origin, the block every craft step points its item motions at. */
	private static Placement table() {
		return new Placement(CRAFTING_TABLE, 0, 0, 0, Map.of());
	}

	/** Item motions on the table (block 0): each entry is "+id" for an input or "-id" for an output. */
	private static List<ItemMotion> motions(String... entries) {
		List<ItemMotion> all = new ArrayList<>();
		for (String entry : entries) {
			all.add(new ItemMotion(entry.substring(1), 0, entry.charAt(0) == '+'));
		}
		return all;
	}

	/** A flat 3x3 wall of loose casing at z 0; its middle cell (1, 1, 0) is index 4. */
	private static List<Placement> looseWall() {
		List<Placement> all = new ArrayList<>();
		for (int y = 0; y <= 2; y++) {
			for (int x = 0; x <= 2; x++) {
				all.add(shell(REACTOR_CASING, x, y, 0, false, false));
			}
		}
		return all;
	}

	/** {@link #looseWall()} with the controller in the middle cell and the casing cell (2, 1, 0) missing. */
	private static List<Placement> holedWall() {
		List<Placement> all = new ArrayList<>(List.of(
				new Placement(REACTOR_CONTROLLER, 1, 1, 0, Map.of("facing", "south", "formed", "false"))));
		for (Placement cell : looseWall()) {
			boolean middle = cell.x() == 1 && cell.y() == 1;
			boolean hole = cell.x() == 2 && cell.y() == 1;
			if (!middle && !hole) {
				all.add(cell);
			}
		}
		return all;
	}

	/**
	 * The airlock parts of a formed room's south wall: 11 the lower door half at the inner floor level,
	 * 12 the upper half above it, 13 the reactor button beside them, pressed late in the step.
	 */
	private static List<Placement> doorway() {
		return List.of(door("lower", 3, 1, 4, true), door("upper", 3, 2, 4, true), reactorButton());
	}

	/** The doorway's parts with both halves lying flat in the floor ring, so the wall stays plain casing. */
	private static List<Placement> flatDoorway() {
		return List.of(door("lower", 2, 0, 2, false), door("upper", 3, 0, 2, false), reactorButton());
	}

	/** One half of the airlock door at ({@code x}, {@code y}, {@code z}), its face toward the viewer. */
	private static Placement door(String half, int x, int y, int z, boolean formed) {
		return new Placement(REACTOR_DOOR, x, y, z,
				Map.of("half", half, "facing", "south", "formed", Boolean.toString(formed)));
	}

	/** A reactor button on the south wall's face beside the door, pressed late in the step. */
	private static Placement reactorButton() {
		return new Placement(REACTOR_BUTTON, 2, 1, 5,
				Map.of("face", "wall", "facing", "south", "powered", "false"), Map.of("powered", "true"));
	}
}
