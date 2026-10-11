package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import java.util.List;
import java.util.Map;

/**
 * Multi-block arrangements the blueprint groups reuse across steps (MOD-797): a row of chest tiers, a soil
 * crater, a repeller line, a trellis bed, a drone field, the incubator rig. Single pieces live in
 * {@link BlueprintParts}.
 */
final class BlueprintRigs {

	private BlueprintRigs() {
	}

	/** A mod chest at ({@code x}, 0, 0) facing the viewer. */
	static Placement modChest(String id, int x) {
		return new Placement(id, x, 0, 0, Map.of("facing", "south"));
	}

	/** The first {@code count} chest tiers in a row, iron first. */
	static List<Placement> chestRow(int count) {
		String[] tiers = {IRON_CHEST, SILVER_CHEST, GOLD_CHEST, ELECTRUM_CHEST, DIAMOND_CHEST};
		List<Placement> row = new java.util.ArrayList<>();
		for (int i = 0; i < count; i++) {
			row.add(modChest(tiers[i], i));
		}
		return row;
	}

	/** Irradiated soil at ({@code x}, 0, 0) of {@code level}, fading to {@code later} (-1: no change). */
	static Placement soil(int x, int level, int later) {
		return new Placement(IRRADIATED_SOIL, x, 0, 0, Map.of("intensity", Integer.toString(level)),
				later < 0 ? Map.of() : Map.of("intensity", Integer.toString(later)));
	}

	/** A soil crater of three cells, strongest first, then {@code rest}: indices 0..2 soil, 3.. rest. */
	static List<Placement> crater(boolean fading, Placement... rest) {
		return join(List.of(soil(0, 3, fading ? 2 : -1), soil(1, 2, -1), soil(2, 1, -1)), List.of(rest));
	}

	/** A mob repeller of grade {@code id} at the origin. */
	static Placement repeller(String id, boolean lit, boolean litLater) {
		return new Placement(id, 0, 0, 0, Map.of("lit", Boolean.toString(lit)),
				litLater ? Map.of("lit", "true") : Map.of());
	}

	/** Repeller at 0, cable of {@code cable} at 1, reinforced store at 2 giving energy west. */
	static List<Placement> repellerLine(Placement repeller, String cable) {
		return List.of(repeller, cableOf(cable, 1, 0, 0, "west", "east"), cesu("east"));
	}

	/** [water,] farmland, trellis lower and upper half of {@code age}, ripening to {@code lateAge} (null: no). */
	static List<Placement> trellisOn(boolean wet, String age, String lateAge) {
		Map<String, String> late = lateAge == null ? Map.of() : Map.of("age", lateAge);
		List<Placement> all = new java.util.ArrayList<>();
		if (wet) {
			all.add(water(-1, 0, 0));
		}
		all.add(new Placement("minecraft:farmland", 0, 0, 0, Map.of("moisture", wet ? "7" : "0")));
		all.add(new Placement(TRELLIS, 0, 1, 0, Map.of("half", "lower", "age", age), late));
		all.add(new Placement(TRELLIS, 0, 2, 0, Map.of("half", "upper", "age", age), late));
		return all;
	}

	/** 0 station, 1 cable, 2 generator, 3..5 farmland, 6..8 wheat of {@code age} ripening to {@code lateAge}. */
	static List<Placement> droneField(boolean hoe, String age, String lateAge) {
		Placement station = new Placement(GARDEN_DRONE_STATION, 0, 0, 0, Map.of());
		station = hoe
				? station.holding("minecraft:wheat_seeds", "minecraft:bone_meal", "minecraft:air", "minecraft:air",
						"minecraft:air", "minecraft:air", "alaindustrial:garden_drone", "minecraft:iron_hoe")
				: station.holding("minecraft:wheat_seeds", "minecraft:bone_meal", "minecraft:air", "minecraft:air",
						"minecraft:air", "minecraft:air", "alaindustrial:garden_drone");
		List<Placement> all = new java.util.ArrayList<>(List.of(station,
				cableOf(COPPER_CABLE, -1, 0, 0, "west", "east"), generatorAt(-2)));
		for (int x = 1; x <= 3; x++) {
			all.add(new Placement("minecraft:farmland", x, 0, 0, Map.of("moisture", "7")));
		}
		for (int x = 1; x <= 3; x++) {
			all.add(new Placement("minecraft:wheat", x, 1, 0, Map.of("age", age),
					lateAge == null ? Map.of() : Map.of("age", lateAge)));
		}
		return all;
	}

	/** The incubator base at the origin. */
	static Placement incubator(boolean litLater) {
		return machine(INCUBATOR, 0, 0, false, litLater);
	}

	/** The incubator dome on top of the base. */
	static Placement dome() {
		return new Placement(INCUBATOR_DOME, 0, 1, 0, Map.of());
	}

	/** Base, dome, cable, generator. */
	static List<Placement> incubatorRig(boolean litLater) {
		return List.of(incubator(litLater), dome(), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2));
	}

	static final String REACTOR_CONTROLLER = "alaindustrial:reactor_controller";
	static final String REACTOR_GLASS = "alaindustrial:reactor_glass";
	static final String REACTOR_OUTLET = "alaindustrial:reactor_outlet";
	static final String REACTOR_LEVER = "alaindustrial:reactor_lever";
	static final String STEAM_PIPE = "alaindustrial:steam_pipe";
	static final String REINFORCED_STEAM_PIPE = "alaindustrial:reinforced_steam_pipe";
	static final String STEAM_NOZZLE = "alaindustrial:steam_nozzle";

	/** The far corner of the smallest reactor room (a 5x5x5 shell); a shell cell has a coordinate at 0 or here. */
	private static final int ROOM = 4;

	/** A shell block of {@code id} at its cell, edge flag from the cell; {@code formedLater} accepts the room. */
	static Placement shell(String id, int x, int y, int z, boolean formed, boolean formedLater) {
		int faces = (x == 0 || x == ROOM ? 1 : 0) + (y == 0 || y == ROOM ? 1 : 0) + (z == 0 || z == ROOM ? 1 : 0);
		return new Placement(id, x, y, z,
				Map.of("formed", Boolean.toString(formed), "edge", Boolean.toString(faces >= 2)),
				formedLater ? Map.of("formed", "true") : Map.of());
	}

	/** The controller in the middle of the south wall, face out. */
	static Placement controller(boolean formed, boolean formedLater) {
		return new Placement(REACTOR_CONTROLLER, 2, 2, ROOM, Map.of("facing", "south",
				"formed", Boolean.toString(formed)), formedLater ? Map.of("formed", "true") : Map.of());
	}

	/** The reactor lever on the controller's face. */
	static Placement reactorLever(boolean on) {
		return new Placement(REACTOR_LEVER, 2, 2, ROOM + 1,
				Map.of("face", "wall", "facing", "south", "powered", Boolean.toString(on)));
	}

	/**
	 * The room with its parts in a fixed order: 0 controller, 1..9 the 3x3 window in the west wall (1 its
	 * middle, holding {@code windowMiddle}, the rest reactor glass), 10 a fuelled column right behind it,
	 * then {@code rest} from 11 on, then reactor casing in every shell cell still free.
	 */
	static List<Placement> room(String windowMiddle, boolean formed, boolean formedLater, List<Placement> rest) {
		List<Placement> all = new java.util.ArrayList<>(List.of(controller(formed, formedLater)));
		all.add(shell(windowMiddle, 0, 2, 2, formed, formedLater));
		for (int y = 1; y <= 3; y++) {
			for (int z = 1; z <= 3; z++) {
				if (y != 2 || z != 2) {
					all.add(shell(REACTOR_GLASS, 0, y, z, formed, formedLater));
				}
			}
		}
		all.add(new Placement(FUEL_ROD_ASSEMBLY, 1, 1, 2, Map.of("rods", "4", "fuelled", "4", "water", "2")));
		all.addAll(rest);
		java.util.Set<List<Integer>> taken = new java.util.HashSet<>();
		for (Placement p : all) {
			taken.add(List.of(p.x(), p.y(), p.z()));
		}
		for (int x = 0; x <= ROOM; x++) {
			for (int y = 0; y <= ROOM; y++) {
				for (int z = 0; z <= ROOM; z++) {
					boolean wall = x == 0 || x == ROOM || y == 0 || y == ROOM || z == 0 || z == ROOM;
					if (wall && !taken.contains(List.of(x, y, z))) {
						all.add(shell(REACTOR_CASING, x, y, z, formed, formedLater));
					}
				}
			}
		}
		return all;
	}
}
