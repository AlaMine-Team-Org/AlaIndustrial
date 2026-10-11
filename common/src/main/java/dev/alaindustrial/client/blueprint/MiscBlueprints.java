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
 * Blueprints about things that are mostly items (MOD-797): the oil buckets, the evolution chips, the
 * engraved lab plates, the random jump chip and the creative energy source with the cable breaker. Listed
 * in {@link BlueprintCatalog#ALL}.
 */
public final class MiscBlueprints {

	private static final String OIL = "alaindustrial:oil";
	private static final String OIL_FIRE = "alaindustrial:oil_fire";
	private static final String OIL_BUCKET = "alaindustrial:oil_bucket";
	private static final String DIESEL_BUCKET = "alaindustrial:diesel_bucket";
	private static final String FUEL_OIL_BUCKET = "alaindustrial:fuel_oil_bucket";
	private static final String COLUMN = "alaindustrial:distillation_column";
	private static final String COLUMN_MIDDLE = "alaindustrial:distillation_column_middle";
	private static final String COLUMN_TOP = "alaindustrial:distillation_column_top";

	private static final String DAY_CHIP = "alaindustrial:alignment_chip_day";
	private static final String NIGHT_CHIP = "alaindustrial:alignment_chip_night";

	private static final String TELEPORTER = "alaindustrial:teleporter";
	private static final String TELEPORTER_CAPSULE = "alaindustrial:teleporter_capsule";
	private static final String TELEPORTER_REMOTE = "alaindustrial:teleporter_remote";
	private static final String RTP_CHIP = "alaindustrial:rtp_chip";
	private static final String SPATIAL_CRYSTAL = "alaindustrial:spatial_crystal";
	private static final String CRAFTING_TABLE = "minecraft:crafting_table";

	private static final String CREATIVE_SOURCE = "alaindustrial:creative_energy_source";
	private static final String CABLE_BREAKER = "alaindustrial:cable_breaker";

	private static final String BROKEN_PLATE = "alaindustrial:broken_engraved_plate_";
	private static final String PLATE = "alaindustrial:engraved_plate_";
	private static final String FRAME = "minecraft:polished_deepslate";

	/**
	 * Oil in a pool on stone scooped with a bucket; fire beside the pool setting every cell burning; lava
	 * beside it leaving the oil alone; then the distillation tower: an oil bucket into the unpowered tower
	 * (nothing happens), the powered tower turning it into diesel (top) and fuel oil (base), and fuel oil
	 * poured in again, cracked into diesel. Indices: pool steps 0..2 oil or burning oil, 3..6 floor,
	 * 7 the fire or lava; tower steps 0 base, 1 middle, 2 top, 3 cable, 4 generator.
	 */
	public static final BlueprintDef LIQUID_FUELS = new BlueprintDef("liquid_fuels",
			List.of(OIL_BUCKET, DIESEL_BUCKET, FUEL_OIL_BUCKET),
			List.of(
					new Step(pool(OIL, null), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:bucket", 0, true), new ItemMotion(OIL_BUCKET, 0, false))),
					new Step(pool(OIL_FIRE, "minecraft:fire"), 7, 2, -1, -1, Verdict.BAD),
					new Step(pool(OIL, "minecraft:lava"), 7, 3, -1, -1, Verdict.GOOD),
					new Step(tower(false, false), 1, 4, List.of(), Verdict.BAD,
							List.of(new ItemMotion(OIL_BUCKET, 1, true))),
					new Step(poweredTower(false), 1, 4, List.of(Flow.energy(4, 0)), Verdict.GOOD,
							List.of(new ItemMotion(OIL_BUCKET, 1, true), new ItemMotion(DIESEL_BUCKET, 2, false),
									new ItemMotion(FUEL_OIL_BUCKET, 0, false))),
					new Step(poweredTower(true), 1, 4, List.of(Flow.energy(4, 0)), Verdict.NONE,
							List.of(new ItemMotion(FUEL_OIL_BUCKET, 1, true),
									new ItemMotion(DIESEL_BUCKET, 2, false)))));

	/**
	 * A solar panel (0) taking the solar chip; the same panel under a stone block (1), which stops it
	 * growing; the daylight panel it grows into; a second solar panel (1) taking the lunar chip and
	 * growing into the moonlit panel; then a wind turbine (2) taking the solar chip too.
	 */
	public static final BlueprintDef EVOLUTION_CHIPS = new BlueprintDef("evolution_chips",
			List.of(DAY_CHIP, NIGHT_CHIP),
			List.of(
					new Step(List.of(solarPanel()), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion(DAY_CHIP, 0, true))),
					new Step(List.of(solarPanel(), stone(0, 1, 0)), 0, 1, -1, -1, Verdict.BAD),
					new Step(List.of(panelAt(DAYLIGHT_SOLAR_PANEL, 0)), 0, 1, -1, -1, Verdict.GOOD),
					new Step(List.of(panelAt(DAYLIGHT_SOLAR_PANEL, 0), panelAt(SOLAR_PANEL, 2)), 1, 2, List.of(),
							Verdict.NONE, List.of(new ItemMotion(NIGHT_CHIP, 1, true))),
					new Step(List.of(panelAt(DAYLIGHT_SOLAR_PANEL, 0), panelAt(MOONLIT_SOLAR_PANEL, 2)), 1, 2, -1, -1,
							Verdict.GOOD),
					new Step(List.of(panelAt(DAYLIGHT_SOLAR_PANEL, 0), panelAt(MOONLIT_SOLAR_PANEL, 2), windMillAt(4)),
							2, 3, List.of(), Verdict.NONE, List.of(new ItemMotion(DAY_CHIP, 2, true)))));

	/**
	 * The lab plaque in its polished deepslate frame: the broken plate (0) and the digits 3, 0, 4 (1..3),
	 * frame 4..11; a shovel getting nothing from the 3; a pickaxe getting the plate; then two digit plates
	 * (0, 1) set into a stone brick wall as a house number.
	 */
	public static final BlueprintDef ENGRAVED_PLATES = new BlueprintDef("engraved_plates",
			plates(),
			List.of(
					new Step(plaque(), 0, 1, -1, -1, Verdict.NONE),
					new Step(plaque(), 1, 2, -1, -1, Verdict.NONE),
					new Step(plaque(), 1, 2, List.of(), Verdict.BAD,
							List.of(new ItemMotion("minecraft:iron_shovel", 1, true))),
					new Step(plaque(), 1, 2, List.of(), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:wooden_pickaxe", 1, true),
									new ItemMotion(PLATE + "3", 1, false))),
					new Step(houseNumber(), 0, 3, -1, -1, Verdict.NONE)));

	/**
	 * A crafting table (0) making the spatial crystal and then the random jump chip; the chip going into a
	 * powered teleporter station, which lights its turquoise corners; the remote's random jump refused in
	 * the Nether and working in the Overworld. Indices: 0 station, 1 and 2 capsule, 3 cable, 4 generator,
	 * 5..7 floor.
	 */
	public static final BlueprintDef RANDOM_JUMP = new BlueprintDef("random_jump",
			List.of(RTP_CHIP, SPATIAL_CRYSTAL),
			List.of(
					new Step(List.of(craftingTable()), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:amethyst_shard", 0, true),
									new ItemMotion("minecraft:ender_pearl", 0, true),
									new ItemMotion("minecraft:glowstone_dust", 0, true),
									new ItemMotion(SPATIAL_CRYSTAL, 0, false))),
					new Step(List.of(craftingTable()), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("alaindustrial:resonance_coil", 0, true),
									new ItemMotion("alaindustrial:empty_chip", 0, true),
									new ItemMotion("minecraft:chorus_fruit", 0, true),
									new ItemMotion(RTP_CHIP, 0, false))),
					new Step(jumpRig(false, true, "minecraft:grass_block"), 0, 2, List.of(Flow.energy(4, 0)),
							Verdict.NONE, List.of(new ItemMotion(RTP_CHIP, 0, true))),
					new Step(jumpRig(true, false, "minecraft:netherrack"), 0, 2, List.of(Flow.energy(4, 0)),
							Verdict.BAD, List.of(new ItemMotion(TELEPORTER_REMOTE, 0, true))),
					new Step(jumpRig(true, false, "minecraft:grass_block"), 0, 2, List.of(Flow.energy(4, 0)),
							Verdict.GOOD, List.of(new ItemMotion(TELEPORTER_REMOTE, 0, true)))));

	/**
	 * The creative energy source (0) alone; feeding an electric furnace (2) through a copper cable (1);
	 * switched off, the furnace dark; a cable breaker clamped onto the cable, the line still running; the
	 * breaker opened, the furnace going out; then the breaker closed again and a drill charging in the
	 * source's slot.
	 */
	public static final BlueprintDef TEST_SOURCE = new BlueprintDef("test_source",
			List.of(CREATIVE_SOURCE, CABLE_BREAKER),
			List.of(
					new Step(List.of(source(true)), 0, 1, -1, -1, Verdict.NONE),
					new Step(List.of(source(true), cable(true, true), furnaceLightingUp()), 2, 2, 0, 2, Verdict.GOOD,
							List.of(new ItemMotion("minecraft:raw_iron", 2, true),
									new ItemMotion("minecraft:iron_ingot", 2, false))),
					new Step(List.of(source(false), cable(true, true), furnace(false)), 0, 1, -1, -1, Verdict.BAD),
					new Step(List.of(source(true), cable(true, true), furnace(true)), 1, 3, 0, 2, Verdict.NONE,
							List.of(new ItemMotion(CABLE_BREAKER, 1, true))),
					new Step(List.of(source(true), cable(true, true), furnaceGoingOut()), 1, 3, -1, -1,
							Verdict.NONE),
					new Step(List.of(source(true), cable(true, true), furnace(true)), 0, 1, 0, 2, Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:electric_drill", 0, true),
									new ItemMotion("alaindustrial:electric_drill", 0, false)))));

	private MiscBlueprints() {
	}

	/** 0..2 three cells of {@code cell}, 3..6 a stone floor under them, 7 {@code neighbour} west of the pool. */
	private static List<Placement> pool(String cell, String neighbour) {
		List<Placement> all = new ArrayList<>(List.of(at(cell, 0, 0, 0), at(cell, 1, 0, 0), at(cell, 0, 0, 1),
				stone(-1, -1, 0), stone(0, -1, 0), stone(1, -1, 0), stone(0, -1, 1)));
		if (neighbour != null) {
			all.add(at(neighbour, -1, 0, 0));
		}
		return all;
	}

	/** The assembled tower: 0 base, 1 middle, 2 top; lit from the start or once its input went in. */
	private static List<Placement> tower(boolean lit, boolean litLater) {
		Map<String, String> late = litLater ? Map.of("lit", "true") : Map.of();
		String on = Boolean.toString(lit);
		return List.of(new Placement(COLUMN, 0, 0, 0, Map.of("formed", "true", "lit", on), late),
				new Placement(COLUMN_MIDDLE, 0, 1, 0, Map.of("lit", on), late),
				new Placement(COLUMN_TOP, 0, 2, 0, Map.of("lit", on), late));
	}

	/** The tower with a cable (3) at its base and a generator (4); {@code running} is lit from the start. */
	private static List<Placement> poweredTower(boolean running) {
		return join(tower(running, !running), List.of(cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)));
	}

	/** A wind turbine at ({@code x}, 0, 0) facing west with its wooden rotor in. */
	private static Placement windMillAt(int x) {
		return new Placement(WIND_MILL, x, 0, 0, Map.of("facing", "west")).holding("alaindustrial:windmill_rotor");
	}

	/** Every engraved plate: the ten digits, then the seven broken ones. */
	private static List<String> plates() {
		List<String> all = new ArrayList<>();
		for (int digit = 0; digit <= 9; digit++) {
			all.add(PLATE + digit);
		}
		for (String letter : List.of("b", "d", "k", "m", "p", "r", "w")) {
			all.add(BROKEN_PLATE + letter);
		}
		return all;
	}

	/** An engraved plate at ({@code x}, 1, 0), its face toward the viewer. */
	private static Placement plate(String id, int x) {
		return new Placement(id, x, 1, 0, Map.of("facing", "south"));
	}

	/** 0 broken plate, 1..3 the digits 3, 0, 4, 4..7 the frame under them, 8..11 the frame over them. */
	private static List<Placement> plaque() {
		List<Placement> all = new ArrayList<>(List.of(plate(BROKEN_PLATE + "b", 0), plate(PLATE + "3", 1),
				plate(PLATE + "0", 2), plate(PLATE + "4", 3)));
		for (int y : new int[] {0, 2}) {
			for (int x = 0; x <= 3; x++) {
				all.add(at(FRAME, x, y, 0));
			}
		}
		return all;
	}

	/** 0 and 1 the plates 1 and 2 in a stone brick wall four blocks wide and three high. */
	private static List<Placement> houseNumber() {
		List<Placement> all = new ArrayList<>(List.of(plate(PLATE + "1", 1), plate(PLATE + "2", 2)));
		for (int x = 0; x <= 3; x++) {
			for (int y = 0; y <= 2; y++) {
				if (y != 1 || x == 0 || x == 3) {
					all.add(at("minecraft:stone_bricks", x, y, 0));
				}
			}
		}
		return all;
	}

	private static Placement craftingTable() {
		return at(CRAFTING_TABLE, 0, 0, 0);
	}

	/**
	 * The formed station at the origin with its clear capsule (1, 2), a cable (3) and a generator (4) east of
	 * it, and three blocks of {@code floor} under them (5..7); the station's upgrade shows from the start
	 * ({@code upgraded}) or once the chip went in ({@code upgradedLater}).
	 */
	private static List<Placement> jumpRig(boolean upgraded, boolean upgradedLater, String floor) {
		Placement station = new Placement(TELEPORTER, 0, 0, 0,
				Map.of("facing", "south", "formed", "true", "upgraded", Boolean.toString(upgraded)),
				upgradedLater ? Map.of("upgraded", "true") : Map.of());
		List<Placement> all = new ArrayList<>(List.of(station, capsuleGlass(1, "middle"), capsuleGlass(2, "top"),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)));
		for (int x = 0; x <= 2; x++) {
			all.add(at(floor, x, -1, 0));
		}
		return all;
	}

	/** One clear-glass cell of the capsule over the station, at height {@code y}, door toward the viewer. */
	private static Placement capsuleGlass(int y, String part) {
		return new Placement(TELEPORTER_CAPSULE, 0, y, 0, Map.of("part", part, "facing", "south", "glass", "clear"));
	}

	/** The creative energy source at the origin, switched on or off. */
	private static Placement source(boolean on) {
		return new Placement(CREATIVE_SOURCE, 0, 0, 0, Map.of("lit", Boolean.toString(on)));
	}

	/** The electric furnace at (2, 0, 0), running at first and going dark late in the step. */
	private static Placement furnaceGoingOut() {
		return new Placement(ELECTRIC_FURNACE, 2, 0, 0, Map.of("facing", "south", "lit", "true"),
				Map.of("lit", "false"));
	}

	private static Placement at(String id, int x, int y, int z) {
		return new Placement(id, x, y, z, Map.of());
	}
}
