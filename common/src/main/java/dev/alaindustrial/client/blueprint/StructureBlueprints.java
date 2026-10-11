package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about multiblock structures (MOD-797): the teleporter capsule, the mob wheel, the distillation
 * tower, the crystal greenhouse and the assembled mirror concentrator. Listed in {@link BlueprintCatalog#ALL}.
 *
 * <p>A block whose look comes from its block-entity renderer (the mob wheel drive, the concentrator core) is
 * declared formed outright, never through late props: the scene keeps one block entity per placement, made
 * from the state it was first drawn with.
 */
public final class StructureBlueprints {

	private static final String TELEPORTER = "alaindustrial:teleporter";
	private static final String TELEPORTER_CAPSULE = "alaindustrial:teleporter_capsule";
	private static final String TELEPORTER_REMOTE = "alaindustrial:teleporter_remote";
	private static final String WHEEL_DRIVE = "alaindustrial:mob_wheel_controller";
	private static final String WHEEL_FRAME = "alaindustrial:mob_wheel_frame";
	private static final String WHEEL_GATE = "alaindustrial:mob_wheel_gate";
	private static final String WHEEL_ROTOR = "alaindustrial:mob_wheel_rotor";
	private static final String COLUMN = "alaindustrial:distillation_column";
	private static final String COLUMN_MIDDLE = "alaindustrial:distillation_column_middle";
	private static final String COLUMN_TOP = "alaindustrial:distillation_column_top";
	private static final String RECTIFICATION = "alaindustrial:rectification_section";
	private static final String FARM_CONTROLLER = "alaindustrial:crystal_farm_controller";
	private static final String FARM_FLOOR = "alaindustrial:crystal_farm_floor";
	private static final String FARM_GLASS = "alaindustrial:crystal_farm_glass";
	private static final String FARM_DOOR = "alaindustrial:crystal_farm_door";
	private static final String SEEDBED = "alaindustrial:crystal_seedbed";
	private static final String RADIANT = "alaindustrial:radiant_solar_panel";
	private static final String SECTION = "alaindustrial:concentrator_section";
	private static final String SHARD = "minecraft:amethyst_shard";

	/** The far corner of the smallest greenhouse (a 5x5x5 shell around 27 cells of air). */
	private static final int ROOM = 4;

	/**
	 * The station, one glass on it (no capsule), two glass assembled into the capsule, a cable at the door
	 * side (no energy), the cable moved to the side and charging, then the remote bound to two stations.
	 * Indices: 0 station, 1 capsule middle, 2 capsule top, 3 cable, 4 generator, 5..7 the second capsule.
	 */
	public static final BlueprintDef TELEPORTER_LINK = new BlueprintDef("teleporter",
			List.of(TELEPORTER, TELEPORTER_REMOTE),
			List.of(
					new Step(List.of(station(0, false)), 0, 1, -1, -1, Verdict.NONE),
					new Step(List.of(station(0, false), new Placement("minecraft:glass", 0, 1, 0, Map.of())),
							1, 2, -1, -1, Verdict.BAD),
					new Step(capsule(0), 1, 2, -1, -1, Verdict.GOOD),
					new Step(join(capsule(0), List.of(cableOf(COPPER_CABLE, 0, 0, 1, "east"), gen(1, 0, 1))),
							3, 3, -1, -1, Verdict.BAD),
					new Step(poweredCapsule(), 3, 3, 4, 0, Verdict.GOOD),
					new Step(join(poweredCapsule(), capsule(-2)), 1, 4, List.of(Flow.energy(4, 0)), Verdict.NONE,
							List.of(new ItemMotion(TELEPORTER_REMOTE, 1, true),
									new ItemMotion(TELEPORTER_REMOTE, 6, true)))));

	/**
	 * The drive in the back corner, the eleven frame posts, the wheel in the middle, the gate with one post
	 * missing (nothing assembles), all fourteen blocks assembled, then a cable to a battery box and a hay
	 * bale beside it. Indices: 0 drive, then rotor and gate once placed, then the posts; the post missing in
	 * step 4 is the last one (13 in steps 5 and 6); 14 cable, 15 battery box, 16 hay bale.
	 */
	public static final BlueprintDef MOB_WHEEL = new BlueprintDef("mob_wheel",
			List.of(WHEEL_DRIVE, WHEEL_FRAME, WHEEL_GATE, WHEEL_ROTOR),
			List.of(
					new Step(List.of(drive(false)), 0, 1, -1, -1, Verdict.NONE),
					new Step(wheel(false, false, false, true), 1, 2, -1, -1, Verdict.NONE),
					new Step(wheel(false, true, false, true), 1, 3, -1, -1, Verdict.NONE),
					new Step(wheel(false, true, true, false), 2, 4, -1, -1, Verdict.BAD),
					new Step(wheel(true, true, true, true), 13, 2, -1, -1, Verdict.GOOD),
					new Step(join(wheel(true, true, true, true), List.of(cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
							boxAt(4, 0, 0), new Placement("minecraft:hay_block", -1, 0, 1, Map.of("axis", "y")))),
							16, 5, 0, 15, Verdict.GOOD)));

	/**
	 * One loose segment, two stacked (not a tower), three assembled into the tower, a cable at the middle
	 * (no energy), a pump on oil and a powered base making diesel and fuel oil, then the rectification
	 * section on top. Indices: 0 base, 1 middle, 2 top, 3 section (step 6); the rig follows the tower:
	 * cable, generator, pump, stone, oil.
	 */
	public static final BlueprintDef OIL_REFINING = new BlueprintDef("oil_refining",
			List.of(COLUMN, RECTIFICATION),
			List.of(
					new Step(List.of(segment(0)), 0, 1, -1, -1, Verdict.NONE),
					new Step(List.of(segment(0), segment(1)), 1, 1, -1, -1, Verdict.BAD),
					new Step(tower(false, false), 0, 1, -1, -1, Verdict.GOOD),
					new Step(join(tower(false, false), List.of(stone(-1, 0, 0), stone(-2, 0, 0),
							cableOf(COPPER_CABLE, -1, 1, 0, "west"), gen(-2, 1, 0))), 5, 2, -1, -1, Verdict.BAD),
					refiningStep(false),
					refiningStep(true)));

	/**
	 * The controller, the room with a hole in its roof (not sealed), the room sealed, a seedbed fed shards,
	 * an amethyst cluster growing with optional energy, then a seedbed outside the room growing nothing.
	 * Indices: see {@link #greenhouse}; 12 seedbed, 13 cluster, 14 and 15 cables, 16 generator.
	 */
	public static final BlueprintDef CRYSTAL_FARM = new BlueprintDef("crystal_farm",
			List.of(FARM_CONTROLLER, FARM_FLOOR, FARM_GLASS, FARM_DOOR, SEEDBED),
			List.of(
					new Step(List.of(farmController(false, false)), 0, 1, -1, -1, Verdict.NONE),
					new Step(greenhouse(false, false, true, List.of()), 0, 1, -1, -1, Verdict.BAD),
					new Step(greenhouse(false, true, false, List.of()), 3, 2, -1, -1, Verdict.GOOD),
					new Step(greenhouse(true, false, false, List.of(seedbed(2, 1, 2, true, true))), 12, 3,
							List.of(), Verdict.NONE, List.of(new ItemMotion(SHARD, 12, true))),
					new Step(greenhouse(true, false, false, List.of(seedbed(2, 1, 2, true, false),
							new Placement("minecraft:amethyst_cluster", 2, 2, 2, Map.of("facing", "up")),
							cableOf(COPPER_CABLE, 2, 2, ROOM + 1, "north", "down"),
							cableOf(COPPER_CABLE, 2, 1, ROOM + 1, "up", "down"), gen(2, 0, ROOM + 1))), 13, 4,
							List.of(Flow.energy(16, 0)), Verdict.GOOD, List.of(new ItemMotion(SHARD, 13, false))),
					new Step(greenhouse(true, false, false, List.of(seedbed(-2, 0, 2, false, false))), 12, 3,
							List.of(), Verdict.BAD, List.of(new ItemMotion(SHARD, 12, true)))));

	/**
	 * The grown concentrator alone, three bottom sections around it, six of seven (nothing assembles), the
	 * seventh assembling the machine, a cable at the top tier (no energy), then a cable at the bottom tier
	 * feeding a battery box. Indices: 0 core, 1 right, 2 back, 3 back right, 4 top, 5 top right, 6 top back,
	 * 7 top back right (step 3 lacks the top, so its later cells shift down by one); 8.. the cable rig.
	 */
	public static final BlueprintDef MIRROR_CONCENTRATOR = new BlueprintDef("mirror_concentrator",
			List.of(SECTION),
			List.of(
					new Step(List.of(core(false)), 0, 1, -1, -1, Verdict.NONE),
					new Step(List.of(core(false), cell(0, 0, 1, null), cell(1, 0, 0, null), cell(0, 0, 0, null)),
							1, 2, -1, -1, Verdict.NONE),
					new Step(List.of(core(false), cell(0, 0, 1, null), cell(1, 0, 0, null), cell(0, 0, 0, null),
							cell(0, 1, 1, null), cell(1, 1, 0, null), cell(0, 1, 0, null)), 0, 1, -1, -1, Verdict.BAD),
					new Step(concentrator(), 4, 2, -1, -1, Verdict.GOOD),
					new Step(join(concentrator(), List.of(stone(-2, 0, 1), boxFacingEast(-2, 1, 1),
							cableOf(COPPER_CABLE, -1, 1, 1, "west"))), 10, 3, -1, -1, Verdict.BAD),
					new Step(join(concentrator(), List.of(cableOf(COPPER_CABLE, -1, 0, 1, "west", "east"),
							boxFacingEast(-2, 0, 1))), 8, 3, 1, 9, Verdict.GOOD)));

	private StructureBlueprints() {
	}

	/** A burning coal generator at ({@code x}, {@code y}, {@code z}). */
	private static Placement gen(int x, int y, int z) {
		return new Placement(GENERATOR, x, y, z, Map.of("facing", "south", "lit", "true"));
	}

	/** A battery box at ({@code x}, {@code y}, {@code z}) taking energy on its east face. */
	private static Placement boxFacingEast(int x, int y, int z) {
		return new Placement(BATTERY_BOX, x, y, z, Map.of("facing", "east"));
	}

	/** A teleporter station at ({@code x}, 0, 0), door side toward the viewer. */
	private static Placement station(int x, boolean formed) {
		return new Placement(TELEPORTER, x, 0, 0,
				Map.of("facing", "south", "formed", Boolean.toString(formed), "upgraded", "false"));
	}

	/** A formed station at ({@code x}, 0, 0) with its clear-glass capsule: station, middle, top. */
	private static List<Placement> capsule(int x) {
		return List.of(station(x, true),
				capsuleGlass(x, 1, "middle"), capsuleGlass(x, 2, "top"));
	}

	/** One clear-glass cell of a capsule at ({@code x}, {@code y}, 0), door toward the viewer. */
	private static Placement capsuleGlass(int x, int y, String part) {
		return new Placement(TELEPORTER_CAPSULE, x, y, 0, Map.of("part", part, "facing", "south", "glass", "clear"));
	}

	/** The capsule at the origin, a cable on its east side, a generator: 0..2 capsule, 3 cable, 4 generator. */
	private static List<Placement> poweredCapsule() {
		return join(capsule(0), List.of(cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)));
	}

	/** The mob wheel drive in the back right corner (2, 0, 0), canonical frame (facing south). */
	private static Placement drive(boolean formed) {
		return new Placement(WHEEL_DRIVE, 2, 0, 0, Map.of("facing", "south", "formed", Boolean.toString(formed)));
	}

	/**
	 * Drive, then the rotor and the gate if placed, then the frame posts: the four corner columns three high,
	 * less the drive's cell; the front-left top post (0, 2, 2) comes last and is left out unless {@code full}.
	 */
	private static List<Placement> wheel(boolean formed, boolean rotor, boolean gate, boolean full) {
		String f = Boolean.toString(formed);
		List<Placement> all = new java.util.ArrayList<>(List.of(drive(formed)));
		if (rotor) {
			all.add(new Placement(WHEEL_ROTOR, 1, 1, 1, Map.of("facing", "south", "formed", f)));
		}
		if (gate) {
			all.add(new Placement(WHEEL_GATE, 1, 0, 2, Map.of("facing", "south", "formed", f, "open", "false")));
		}
		int[][] corners = {{2, 0}, {2, 2}, {0, 0}, {0, 2}};
		for (int[] corner : corners) {
			for (int y = 0; y <= 2; y++) {
				boolean driveCell = corner[0] == 2 && corner[1] == 0 && y == 0;
				boolean lastPost = corner[0] == 0 && corner[1] == 2 && y == 2;
				if (!driveCell && (full || !lastPost)) {
					all.add(new Placement(WHEEL_FRAME, corner[0], y, corner[1], Map.of("formed", f)));
				}
			}
		}
		return all;
	}

	/** A loose distillation segment at (0, {@code y}, 0). */
	private static Placement segment(int y) {
		return new Placement(COLUMN, 0, y, 0, Map.of("formed", "false", "lit", "false"));
	}

	/** The assembled tower, base first, lighting up late when {@code runs}; the section on top if set. */
	private static List<Placement> tower(boolean runs, boolean section) {
		Map<String, String> late = runs ? Map.of("lit", "true") : Map.of();
		List<Placement> all = new java.util.ArrayList<>(List.of(
				new Placement(COLUMN, 0, 0, 0, Map.of("formed", "true", "lit", "false"), late),
				new Placement(COLUMN_MIDDLE, 0, 1, 0, Map.of("lit", "false"), late),
				new Placement(COLUMN_TOP, 0, 2, 0, Map.of("lit", "false"), late)));
		if (section) {
			all.add(new Placement(RECTIFICATION, 0, 3, 0, Map.of("lit", "false"), late));
		}
		return all;
	}

	/**
	 * The running tower ({@code section} on top or not) with its rig west of it: a cable under the pump
	 * feeding the base and the pump, a generator, the pump facing an oil pool on stone.
	 */
	private static Step refiningStep(boolean section) {
		List<Placement> blocks = join(tower(true, section), List.of(
				cableOf(COPPER_CABLE, -1, 0, 0, "east", "up", "south"), gen(-1, 0, 1),
				new Placement(PUMP, -1, 1, 0, Map.of("facing", "west", "lit", "false"), Map.of("lit", "true")),
				stone(-2, 0, 0), new Placement("alaindustrial:oil", -2, 1, 0, Map.of())));
		int rig = section ? 4 : 3;
		return new Step(blocks, section ? 3 : rig, section ? 3 : 2,
				List.of(Flow.energy(rig + 1, 0), Flow.energy(rig + 1, rig + 2),
						Flow.item(rig + 2, 1, "alaindustrial:oil_bucket")),
				Verdict.GOOD,
				List.of(new ItemMotion("alaindustrial:diesel_bucket", 2, false),
						new ItemMotion("alaindustrial:fuel_oil_bucket", 0, false)));
	}

	/** The greenhouse controller in the middle of the south wall, panel out. */
	private static Placement farmController(boolean formed, boolean formedLater) {
		return new Placement(FARM_CONTROLLER, 2, 2, ROOM, Map.of("facing", "south", "formed",
				Boolean.toString(formed)), formedLater ? Map.of("formed", "true") : Map.of());
	}

	/** A shell block of {@code id} at its cell, edge flag from the cell; {@code formedLater} seals it. */
	private static Placement shellCell(String id, int x, int y, int z, boolean formed, boolean formedLater) {
		int faces = (x == 0 || x == ROOM ? 1 : 0) + (y == 0 || y == ROOM ? 1 : 0) + (z == 0 || z == ROOM ? 1 : 0);
		return new Placement(id, x, y, z,
				Map.of("formed", Boolean.toString(formed), "edge", Boolean.toString(faces >= 2)),
				formedLater ? Map.of("formed", "true") : Map.of());
	}

	/**
	 * The 5x5x5 greenhouse with its parts in a fixed order: 0 controller, 1 and 2 the door's lower and upper
	 * half beside it, 3..11 the 3x3 glazing of the west wall (3 its middle), then {@code rest} from 12 on,
	 * then decking in every shell cell still free; {@code hole} leaves the roof cell (3, 4, 3) empty.
	 */
	private static List<Placement> greenhouse(boolean formed, boolean formedLater, boolean hole,
			List<Placement> rest) {
		List<Placement> all = new java.util.ArrayList<>(List.of(farmController(formed, formedLater),
				door("lower"), door("upper"), shellCell(FARM_GLASS, 0, 2, 2, formed, formedLater)));
		for (int y = 1; y <= 3; y++) {
			for (int z = 1; z <= 3; z++) {
				if (y != 2 || z != 2) {
					all.add(shellCell(FARM_GLASS, 0, y, z, formed, formedLater));
				}
			}
		}
		all.addAll(rest);
		java.util.Set<List<Integer>> taken = new java.util.HashSet<>();
		for (Placement p : all) {
			taken.add(List.of(p.x(), p.y(), p.z()));
		}
		if (hole) {
			taken.add(List.of(3, ROOM, 3));
		}
		for (int x = 0; x <= ROOM; x++) {
			for (int y = 0; y <= ROOM; y++) {
				for (int z = 0; z <= ROOM; z++) {
					boolean wall = x == 0 || x == ROOM || y == 0 || y == ROOM || z == 0 || z == ROOM;
					if (wall && !taken.contains(List.of(x, y, z))) {
						all.add(shellCell(FARM_FLOOR, x, y, z, formed, formedLater));
					}
				}
			}
		}
		return all;
	}

	/** One half of the greenhouse door in the south wall at x = 1, closed, panel on the outer face. */
	private static Placement door(String half) {
		return new Placement(FARM_DOOR, 1, half.equals("lower") ? 1 : 2, ROOM,
				Map.of("facing", "north", "half", half, "hinge", "left", "open", "false"));
	}

	/** A crystal seedbed; {@code tended} inside a sealed room, {@code fed} goes from empty to charged. */
	private static Placement seedbed(int x, int y, int z, boolean tended, boolean fed) {
		String t = Boolean.toString(tended);
		return fed
				? new Placement(SEEDBED, x, y, z, Map.of("charges", "0", "tended", t), Map.of("charges", "4"))
				: new Placement(SEEDBED, x, y, z, Map.of("charges", "4", "tended", t));
	}

	/** The mirror concentrator core at (1, 0, 1), its structure facing the viewer. */
	private static Placement core(boolean assembled) {
		return new Placement(RADIANT, 1, 0, 1, Map.of("assembled", Boolean.toString(assembled), "facing", "south"));
	}

	/** A concentrator section at its cell, loose when {@code part} is null. */
	private static Placement cell(int x, int y, int z, String part) {
		return new Placement(SECTION, x, y, z, Map.of("part", part == null ? "loose" : part, "facing", "south"));
	}

	/** The assembled 2x2x2 concentrator: 0 core, 1 right, 2 back, 3 back right, 4..7 the top tier. */
	private static List<Placement> concentrator() {
		return List.of(core(true), cell(0, 0, 1, "right"), cell(1, 0, 0, "back"), cell(0, 0, 0, "back_right"),
				cell(1, 1, 1, "top"), cell(0, 1, 1, "top_right"), cell(1, 1, 0, "top_back"),
				cell(0, 1, 0, "top_back_right"));
	}
}
