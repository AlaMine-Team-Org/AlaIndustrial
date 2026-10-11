package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;
import static dev.alaindustrial.client.blueprint.BlueprintRigs.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.List;
import java.util.Map;

/**
 * Blueprints about the reactor room (MOD-797). Listed in {@link BlueprintCatalog#ALL}. The room is the
 * smallest one, 5x5x5; its west wall has a window, so the column inside stays in view.
 */
public final class ReactorBlueprints {

	/**
	 * The controller, the room formed around it with a reactor-glass window, the same room with one plain
	 * glass pane (not formed), the outlet with a cable to a store, the lever that runs it, the lever off.
	 * Indices: see {@link BlueprintRigs#room}; 11 outlet, 12 cable, 13 store, 14 lever.
	 */
	public static final BlueprintDef REACTOR_ROOM = new BlueprintDef("reactor_room",
			List.of(REACTOR_CONTROLLER, REACTOR_GLASS, REACTOR_OUTLET, REACTOR_LEVER),
			List.of(
					new Step(List.of(controller(false, false)), 0, 1, -1, -1, Verdict.NONE),
					new Step(room(REACTOR_GLASS, false, true, List.of()), 1, 2, -1, -1, Verdict.GOOD),
					new Step(room("minecraft:glass", false, false, List.of()), 1, 2, -1, -1, Verdict.BAD),
					new Step(room(REACTOR_GLASS, true, false, outletLine(null)), 11, 3, -1, -1, Verdict.NONE),
					new Step(room(REACTOR_GLASS, true, false, outletLine(true)), 14, 4, List.of(Flow.energy(11, 13)),
							Verdict.GOOD, List.of()),
					new Step(room(REACTOR_GLASS, true, false, outletLine(false)), 14, 4, -1, -1, Verdict.BAD)));

	/**
	 * Steam from the column up through the ceiling inlet to the nozzle: the nozzle alone, the line working,
	 * a fluid pipe outside (no steam), the reinforced pipe inside, a plain pipe inside melted into lava, and
	 * a block over the nozzle's mouth. Indices: see {@link #exhaust}.
	 */
	public static final BlueprintDef STEAM_EXHAUST = new BlueprintDef("steam_exhaust",
			List.of(STEAM_NOZZLE, STEAM_PIPE, REINFORCED_STEAM_PIPE),
			List.of(
					new Step(List.of(nozzle()), 0, 1, -1, -1, Verdict.NONE),
					new Step(exhaust(STEAM_PIPE, inner(REINFORCED_STEAM_PIPE, 3), false), 19, 2,
							List.of(Flow.water(15, 10), Flow.steam(10, 20)), Verdict.GOOD, List.of()),
					new Step(exhaust(FLUID_PIPE, inner(REINFORCED_STEAM_PIPE, 3), false), 19, 2,
							List.of(Flow.water(15, 10)), Verdict.BAD, List.of()),
					new Step(exhaust(STEAM_PIPE, inner(REINFORCED_STEAM_PIPE, 3), false), 17, 3,
							List.of(Flow.water(15, 10), Flow.steam(10, 20)), Verdict.GOOD, List.of()),
					new Step(exhaust(STEAM_PIPE, new Placement("minecraft:lava", 1, 3, 2, Map.of()), false), 17, 3,
							List.of(Flow.water(15, 10)), Verdict.BAD, List.of()),
					new Step(exhaust(STEAM_PIPE, inner(REINFORCED_STEAM_PIPE, 3), true), 20, 1,
							List.of(Flow.water(15, 10)), Verdict.BAD, List.of())));

	private ReactorBlueprints() {
	}

	/** Outlet in the south wall, electrum cable out of it, a store taking energy on its front; a lever if set. */
	private static List<Placement> outletLine(Boolean lever) {
		List<Placement> line = new java.util.ArrayList<>(List.of(shell(REACTOR_OUTLET, 3, 1, 4, true, false),
				cableOf(ELECTRUM_CABLE, 3, 1, 5, "north", "south"),
				new Placement(CESU, 3, 1, 6, Map.of("facing", "north"))));
		if (lever != null) {
			line.add(reactorLever(lever));
		}
		return line;
	}

	private static Placement nozzle() {
		return new Placement(STEAM_NOZZLE, 1, 6, 2, Map.of("facing", "up"));
	}

	/** A reinforced steam pipe above the column at height {@code y}, feeding upward. */
	private static Placement inner(String id, int y) {
		return fluidPipeOf(id, 1, y, 2, "down", "neutral", "up", "insert");
	}

	/**
	 * The formed room running, with 11 lever, 12 water inlet, 13 reinforced fluid pipe, 14 fluid pipe,
	 * 15 water tank, 16 lower inner steam pipe, 17 {@code upper}, 18 ceiling inlet, 19 outer pipe of
	 * {@code outer}, 20 nozzle, 21 a stone over it when {@code blocked}.
	 */
	private static List<Placement> exhaust(String outer, Placement upper, boolean blocked) {
		List<Placement> rest = new java.util.ArrayList<>(List.of(reactorLever(true),
				shell(REACTOR_PORT, 1, 1, 4, true, false),
				fluidPipeOf(REINFORCED_FLUID_PIPE, 1, 1, 3, "south", "extract", "north", "insert"),
				fluidPipeOf(FLUID_PIPE, 1, 1, 5, "south", "extract", "north", "insert"),
				tankWith(1, 1, 6, "minecraft:water"),
				fluidPipeOf(REINFORCED_STEAM_PIPE, 1, 2, 2, "down", "neutral", "up", "neutral"), upper,
				shell(REACTOR_PORT, 1, 4, 2, true, false),
				fluidPipeOf(outer, 1, 5, 2, "down", "extract", "up", "neutral"),
				nozzle()));
		if (blocked) {
			rest.add(stone(1, 7, 2));
		}
		return room(REACTOR_GLASS, true, false, rest);
	}
}
