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

/**
 * Blueprints about the player's workshop and the reactor room's inside parts (MOD-797): the monitoring wall,
 * the two two-block benches, the industrial workbench and what goes into the reactor room. Listed in
 * {@link BlueprintCatalog#ALL}.
 */
public final class WorkshopBlueprints {

	private static final String MONITOR_CORE = "alaindustrial:monitor_core";
	private static final String MONITOR_PANEL = "alaindustrial:monitor_panel";
	private static final String SMART_WIRE = "alaindustrial:smart_wire";
	private static final String CAPACITY_CARD = "alaindustrial:capacity_card";
	private static final String TABLE_BLOCK = "alaindustrial:upgrade_table";
	private static final String DRILL_COLUMN_MODULE = "alaindustrial:drill_column_module";
	private static final String ELECTRIC_DRILL = "alaindustrial:electric_drill";
	private static final String STATION_BLOCK = "alaindustrial:workstation";
	private static final String WORKBENCH_BLOCK = "alaindustrial:industrial_workbench";
	private static final String REACTOR_LAMP = "alaindustrial:reactor_lamp";
	private static final String REACTOR_DOOR = "alaindustrial:reactor_door";
	private static final String REACTOR_BUTTON = "alaindustrial:reactor_button";
	private static final String FUEL_ROD = "alaindustrial:uranium_fuel_rod";
	private static final String EMPTY_FUEL_ROD = "alaindustrial:empty_fuel_rod";

	/**
	 * The monitor core alone; a cable against its front, which leaves it dark; the cable moved to its side,
	 * lighting it; a capacity card going in; a smart wire to a chest; two panels on top taking a diamond and
	 * an iron ingot as their filters. Indices: 0 core, 1 cable, 2 generator, 3 smart wire, 4 chest,
	 * 5 and 6 panels.
	 */
	public static final BlueprintDef MONITORING = new BlueprintDef("monitoring",
			List.of(MONITOR_CORE, MONITOR_PANEL, "alaindustrial:capacity_card", "alaindustrial:smart_wire"),
			List.of(
					new Step(List.of(core(0, false, Map.of())), 0, 1, -1, -1, Verdict.NONE),
					new Step(List.of(core(0, false, Map.of()), cableOf(COPPER_CABLE, 0, 0, 1, "east"),
							new Placement(GENERATOR, 1, 0, 1, Map.of("facing", "south", "lit", "true"))), 0, 1, 2, 1,
							Verdict.BAD),
					new Step(coreFeed(core(0, false, Map.of("lit", "true"))), 0, 1, 2, 0, Verdict.GOOD),
					new Step(coreFeed(core(0, true, Map.of("cards", "1"))), 0, 1, List.of(Flow.energy(2, 0)),
							Verdict.NONE, List.of(new ItemMotion(CAPACITY_CARD, 0, true))),
					new Step(wiredWall(), 3, 2, List.of(Flow.energy(2, 0)), Verdict.NONE,
							List.of(new ItemMotion("minecraft:diamond", 4, true),
									new ItemMotion("minecraft:iron_ingot", 4, true))),
					new Step(join(wiredWall(), List.of(panel(0), panel(1))), 5, 3, List.of(Flow.energy(2, 0)),
							Verdict.GOOD,
							List.of(new ItemMotion("minecraft:diamond", 5, true),
									new ItemMotion("minecraft:iron_ingot", 6, true)))));

	/**
	 * One upgrade table casing; two casings side by side, which stay casings; one stacked on the other, which
	 * makes the table; a cable against the lower half's front, which leaves it dark; the cable on its side,
	 * fitting the column bore module onto a drill; that drill taking a three-high column of stone.
	 * Indices: 0 lower half, 1 upper half, 2 cable, 3 generator, 4..6 the stone column (last step).
	 */
	public static final BlueprintDef UPGRADE_TABLE = new BlueprintDef("upgrade_table",
			List.of(TABLE_BLOCK, DRILL_COLUMN_MODULE),
			List.of(
					new Step(List.of(casing(TABLE_BLOCK, 0)), 0, 1, -1, -1, Verdict.NONE),
					new Step(List.of(casing(TABLE_BLOCK, 0), casing(TABLE_BLOCK, 1)), 1, 1, -1, -1,
							Verdict.BAD),
					new Step(halves(TABLE_BLOCK, false, false), 1, 1, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(TABLE_BLOCK, 1, true))),
					new Step(join(halves(TABLE_BLOCK, false, false), frontFeed()), 2, 2, 3, 2, Verdict.BAD,
							List.of(new ItemMotion(ELECTRIC_DRILL, 1, true))),
					new Step(poweredTable(true), 1, 1, List.of(Flow.energy(3, 0)), Verdict.GOOD,
							List.of(new ItemMotion(ELECTRIC_DRILL, 1, true),
									new ItemMotion(DRILL_COLUMN_MODULE, 1, true),
									new ItemMotion(ELECTRIC_DRILL, 1, false))),
					new Step(join(poweredTable(false), stoneColumn()), 5, 3, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(ELECTRIC_DRILL, 5, true),
									new ItemMotion("minecraft:cobblestone", 4, false),
									new ItemMotion("minecraft:cobblestone", 5, false),
									new ItemMotion("minecraft:cobblestone", 6, false)))));

	/**
	 * One workstation casing; a second stacked on it, assembling the station; a gold cable to the upper half,
	 * which takes no energy; the cable on the lower half's side, lighting the screens; the lit station where
	 * skills are learned. Indices: 0 lower half, 1 upper half, 2 cable, 3 reinforced store.
	 */
	public static final BlueprintDef WORKSTATION = new BlueprintDef("workstation",
			List.of(STATION_BLOCK),
			List.of(
					new Step(List.of(casing(STATION_BLOCK, 0)), 0, 1, -1, -1, Verdict.NONE),
					new Step(halves(STATION_BLOCK, false, false), 1, 1, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(STATION_BLOCK, 1, true))),
					new Step(join(halves(STATION_BLOCK, false, false), List.of(cableOf(GOLD_CABLE, 1, 1, 0, "west",
							"east"), new Placement(CESU, 2, 1, 0, Map.of("facing", "east")))), 2, 2, 3, 2, Verdict.BAD),
					new Step(poweredStation(false, true), 0, 1, 3, 0, Verdict.GOOD),
					new Step(poweredStation(true, false), 1, 1, 3, 0, Verdict.NONE)));

	/**
	 * The industrial workbench made around a crafting table; crafting sticks on it, which it remembers; the
	 * bench broken, which forgets; two benches, each remembering its own craft (sticks and a door).
	 * Indices: 0 the bench (air once broken), 1 the second bench.
	 */
	public static final BlueprintDef INDUSTRIAL_WORKBENCH = new BlueprintDef("industrial_workbench",
			List.of(WORKBENCH_BLOCK),
			List.of(
					new Step(List.of(bench(0)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:crafting_table", 0, true),
									new ItemMotion("alaindustrial:tin_ingot", 0, true),
									new ItemMotion("alaindustrial:wooden_gear", 0, true),
									new ItemMotion("minecraft:oak_planks", 0, true))),
					new Step(List.of(bench(0)), 0, 1, List.of(), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:oak_planks", 0, true),
									new ItemMotion("minecraft:stick", 0, false))),
					new Step(List.of(new Placement("minecraft:air", 0, 0, 0, Map.of())), 0, 1, List.of(), Verdict.BAD,
							List.of(new ItemMotion(WORKBENCH_BLOCK, 0, false))),
					new Step(List.of(bench(0), bench(2)), 1, 1, List.of(), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:oak_planks", 0, true),
									new ItemMotion("minecraft:stick", 0, false),
									new ItemMotion("minecraft:oak_planks", 1, true),
									new ItemMotion("minecraft:oak_door", 1, false)))));

	/**
	 * Inside the reactor room: fuel rods into a column on a casing block; the rods wearing out and the spent
	 * cladding taken out first; a plain fluid pipe through the wall, which keeps the room from forming; the
	 * reactor inlet in its place, the room forming; a lamp in the ceiling; the airlock door with a reactor
	 * button. Indices before the room: 0 column, 1 casing. In the room: see {@link BlueprintRigs#room}
	 * (10 the column), then {@link #feed}: 11 wall cell, 12 inner pipe, 13 outer pipe, 14 tank, 15 lamp,
	 * 16 and 17 door halves, 18 button.
	 */
	public static final BlueprintDef REACTOR_CORE = new BlueprintDef("reactor_core",
			List.of(FUEL_ROD_ASSEMBLY, REACTOR_PORT, REACTOR_LAMP, REACTOR_DOOR, REACTOR_BUTTON),
			List.of(
					new Step(List.of(column("0", "0", Map.of("rods", "4", "fuelled", "4")), floorCasing()), 0, 1,
							List.of(), Verdict.NONE, List.of(new ItemMotion(FUEL_ROD, 0, true))),
					new Step(List.of(column("4", "4", Map.of("fuelled", "0")), floorCasing()), 0, 1, List.of(),
							Verdict.NONE, List.of(new ItemMotion(EMPTY_FUEL_ROD, 0, false))),
					new Step(room(REACTOR_GLASS, false, false,
							feed(fluidPipeOf(FLUID_PIPE, 1, 1, 4, "south", "neutral", "north", "neutral"))), 11, 2,
							List.of(Flow.water(14, 11)), Verdict.BAD, List.of()),
					new Step(room(REACTOR_GLASS, false, true, feed(shell(REACTOR_PORT, 1, 1, 4, false, true))), 11, 2,
							List.of(Flow.water(14, 10)), Verdict.GOOD, List.of()),
					new Step(room(REACTOR_GLASS, false, true, feed(shell(REACTOR_PORT, 1, 1, 4, false, true),
							shell(REACTOR_LAMP, 2, 4, 2, false, true))), 15, 3, List.of(Flow.water(14, 10)),
							Verdict.GOOD, List.of()),
					new Step(room(REACTOR_GLASS, true, false, feed(shell(REACTOR_PORT, 1, 1, 4, true, false),
							shell(REACTOR_LAMP, 2, 4, 2, true, false), door("lower", 1), door("upper", 2),
							reactorButton())), 18, 4, List.of(Flow.water(14, 10)), Verdict.GOOD, List.of())));

	private WorkshopBlueprints() {
	}

	/** The monitor core at the origin facing the viewer, holding {@code cards} cards; {@code late} laid over late. */
	private static Placement core(int cards, boolean lit, Map<String, String> late) {
		return new Placement(MONITOR_CORE, 0, 0, 0, Map.of("facing", "south", "cards", Integer.toString(cards),
				"lit", Boolean.toString(lit)), late);
	}

	/** {@code core}, a copper cable on its east side, a generator: indices 0, 1, 2. */
	private static List<Placement> coreFeed(Placement core) {
		return List.of(core, cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2));
	}

	/** The powered core with one card, a smart wire west of it and a chest at its end: indices 0..4. */
	private static List<Placement> wiredWall() {
		return join(coreFeed(core(1, true, Map.of())),
				List.of(cableOf(SMART_WIRE, -1, 0, 0, "west", "east"), chest(-2)));
	}

	/** A monitor panel on top of cell ({@code x}, 0, 0), screen toward the viewer, lit once its filter is in. */
	private static Placement panel(int x) {
		return new Placement(MONITOR_PANEL, x, 1, 0, Map.of("facing", "south", "lit", "false"),
				Map.of("lit", "true"));
	}

	/** A loose casing of a two-block bench at ({@code x}, 0, 0). */
	private static Placement casing(String id, int x) {
		return new Placement(id, x, 0, 0, Map.of("part", "single", "facing", "south"));
	}

	/** The assembled bench at the origin: 0 lower half, 1 upper half; {@code litLater} switches both on. */
	private static List<Placement> halves(String id, boolean lit, boolean litLater) {
		Map<String, String> late = litLater ? Map.of("lit", "true") : Map.of();
		String on = Boolean.toString(lit);
		return List.of(new Placement(id, 0, 0, 0, Map.of("part", "lower", "facing", "south", "lit", on), late),
				new Placement(id, 0, 1, 0, Map.of("part", "upper", "facing", "south", "lit", on), late));
	}

	/** The upgrade table fed from its east side by a generator: 0, 1 halves, 2 cable, 3 generator. */
	private static List<Placement> poweredTable(boolean litLater) {
		return join(halves(TABLE_BLOCK, false, litLater), sideFeed(COPPER_CABLE, generatorAt(2)));
	}

	/** The workstation fed from its lower half's east side by a reinforced store: 0, 1 halves, 2 cable, 3 store. */
	private static List<Placement> poweredStation(boolean lit, boolean litLater) {
		return join(halves(STATION_BLOCK, lit, litLater), sideFeed(GOLD_CABLE, cesu("east")));
	}

	/** A cable of {@code cable} on the east side of the lower half, then {@code source} at (2, 0, 0). */
	private static List<Placement> sideFeed(String cable, Placement source) {
		return List.of(cableOf(cable, 1, 0, 0, "west", "east"), source);
	}

	/** A copper cable in front of the lower half with no arm toward it, and a generator east of the cable. */
	private static List<Placement> frontFeed() {
		return List.of(cableOf(COPPER_CABLE, 0, 0, 1, "east"),
				new Placement(GENERATOR, 1, 0, 1, Map.of("facing", "south", "lit", "true")));
	}

	/** Three stone blocks stacked west of the table, the middle one the drill's target. */
	private static List<Placement> stoneColumn() {
		return List.of(stone(-2, 0, 0), stone(-2, 1, 0), stone(-2, 2, 0));
	}

	private static Placement bench(int x) {
		return new Placement(WORKBENCH_BLOCK, x, 0, 0, Map.of());
	}

	/** The reactor column at its room cell (1, 1, 2), dry, with {@code late} laid over late in the step. */
	private static Placement column(String rods, String fuelled, Map<String, String> late) {
		return new Placement(FUEL_ROD_ASSEMBLY, 1, 1, 2, Map.of("rods", rods, "fuelled", fuelled, "water", "0"),
				late);
	}

	/** The casing block under the column, the same cell the room's floor fills later. */
	private static Placement floorCasing() {
		return shell(REACTOR_CASING, 1, 0, 2, false, false);
	}

	/**
	 * The water feed through the south wall, then {@code extra}: 11 {@code wallCell}, 12 reinforced pipe
	 * inside to the column, 13 fluid pipe outside, 14 water tank, 15.. extra.
	 */
	private static List<Placement> feed(Placement wallCell, Placement... extra) {
		return join(List.of(wallCell,
				fluidPipeOf(REINFORCED_FLUID_PIPE, 1, 1, 3, "south", "extract", "north", "insert"),
				fluidPipeOf(FLUID_PIPE, 1, 1, 5, "south", "extract", "north", "insert"),
				tankWith(1, 1, 6, "minecraft:water")), List.of(extra));
	}

	/** One half of the airlock door in the south wall at (3, {@code y}, 4), a doorway on the inner floor. */
	private static Placement door(String half, int y) {
		return new Placement(REACTOR_DOOR, 3, y, 4, Map.of("half", half, "facing", "south", "formed", "true"));
	}

	/** A reactor button on the south wall's face beside the door, pressed late in the step. */
	private static Placement reactorButton() {
		return new Placement(REACTOR_BUTTON, 2, 1, 5,
				Map.of("face", "wall", "facing", "south", "powered", "false"), Map.of("powered", "true"));
	}
}
