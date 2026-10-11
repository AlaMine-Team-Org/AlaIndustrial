package dev.alaindustrial.client.blueprint;

import static dev.alaindustrial.client.blueprint.BlueprintParts.*;

import dev.alaindustrial.client.blueprint.BlueprintDef.Flow;
import dev.alaindustrial.client.blueprint.BlueprintDef.ItemMotion;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.client.blueprint.BlueprintDef.Verdict;
import java.util.List;
import java.util.Map;

/** Blueprints about hand tools, lights and crops (MOD-797). Listed in {@link BlueprintCatalog#ALL}. */
public final class GearBlueprints {

	private static final String ELECTRIC_DRILL = "alaindustrial:electric_drill";
	private static final String ELECTRIC_CHAINSAW = "alaindustrial:electric_chainsaw";
	private static final String ELECTRIC_SHOVEL = "alaindustrial:electric_shovel";
	private static final String ELECTRIC_HOE = "alaindustrial:electric_hoe";
	private static final String INDUSTRIAL_LIGHT = "alaindustrial:industrial_light";
	private static final String URANIUM_TORCH = "alaindustrial:enriched_uranium_torch";
	private static final String KOK_SAGYZ = "alaindustrial:kok_sagyz";
	private static final String KOK_SAGYZ_ROOT = "alaindustrial:kok_sagyz_root";
	private static final String KOK_SAGYZ_SEEDS = "alaindustrial:kok_sagyz_seeds";

	/**
	 * Each tool on the block it is for (ore, log and leaves, dirt, then that dirt tilled), then a charging
	 * station with no cable, red and empty, and the same station fed by a generator charging all four.
	 */
	public static final BlueprintDef POWER_TOOLS = new BlueprintDef("power_tools",
			List.of(ELECTRIC_DRILL, ELECTRIC_CHAINSAW, ELECTRIC_SHOVEL, ELECTRIC_HOE),
			List.of(
					new Step(List.of(at("minecraft:coal_ore", 0, 0, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion(ELECTRIC_DRILL, 0, true),
									new ItemMotion("minecraft:coal", 0, false))),
					new Step(woodsAnd(), 1, 2, List.of(), Verdict.NONE,
							List.of(new ItemMotion(ELECTRIC_CHAINSAW, 1, true),
									new ItemMotion("minecraft:oak_log", 1, false),
									new ItemMotion(ELECTRIC_CHAINSAW, 2, true))),
					new Step(woodsAnd(at("minecraft:dirt", 4, 0, 0)), 3, 3, List.of(), Verdict.NONE,
							List.of(new ItemMotion(ELECTRIC_SHOVEL, 3, true),
									new ItemMotion("minecraft:dirt", 3, false))),
					new Step(woodsAnd(new Placement("minecraft:farmland", 4, 0, 0, Map.of("moisture", "0"))), 3, 4,
							List.of(), Verdict.NONE, List.of(new ItemMotion(ELECTRIC_HOE, 3, true))),
					new Step(List.of(pad("empty", false)), 0, 5, List.of(), Verdict.BAD,
							List.of(new ItemMotion(ELECTRIC_DRILL, 0, true))),
					new Step(List.of(new Placement(GENERATOR, 2, 0, 2, Map.of("facing", "south", "lit", "true")),
							cableOf(COPPER_CABLE, 3, 0, 2, "west", "east", "east_low"), pad("charging", true)), 2, 5,
							List.of(Flow.energy(0, 2)), Verdict.GOOD,
							List.of(new ItemMotion(ELECTRIC_DRILL, 2, true), new ItemMotion(ELECTRIC_CHAINSAW, 2, true),
									new ItemMotion(ELECTRIC_SHOVEL, 2, true), new ItemMotion(ELECTRIC_HOE, 2, true)))));

	/**
	 * The industrial light on a block and still in place with the block gone; the uranium torch made from a
	 * torch and uranium dust; an ordinary torch washed off by water; the uranium torch burning in water.
	 * Water stands in the cells around the torch, not in its own: a water-logged state draws as a pool only.
	 */
	public static final BlueprintDef LIGHTS = new BlueprintDef("lights",
			List.of(INDUSTRIAL_LIGHT, URANIUM_TORCH),
			List.of(
					new Step(List.of(stone(0, 0, 0), light()), 1, 1, -1, -1, Verdict.NONE),
					new Step(List.of(light()), 0, 1, -1, -1, Verdict.GOOD),
					new Step(List.of(light(), stone(2, 0, 0), at(URANIUM_TORCH, 2, 1, 0)), 2, 2, List.of(),
							Verdict.NONE,
							List.of(new ItemMotion("minecraft:torch", 2, true),
									new ItemMotion("alaindustrial:uranium_dust", 2, true))),
					new Step(List.of(light(), stone(2, 0, 0), at(URANIUM_TORCH, 2, 1, 0), stone(4, 0, 0),
							water(4, 1, 0)), 4, 3, List.of(), Verdict.BAD,
							List.of(new ItemMotion("minecraft:torch", 4, true),
									new ItemMotion("minecraft:torch", 4, false))),
					new Step(List.of(light(), stone(2, 0, 0), at(URANIUM_TORCH, 2, 1, 0), stone(4, 0, 0),
							water(4, 1, 0), water(3, 1, 0), water(2, 1, -1), water(2, 2, 0)), 2, 2, -1, -1,
							Verdict.GOOD)));

	/**
	 * Kok-sagyz seeds on plain dirt, the ripe flower rooting two blocks down, the tip dug for a root, two
	 * roots in the macerator, then the flower torn off, which kills the plant.
	 */
	public static final BlueprintDef KOK_SAGYZ_FARM = new BlueprintDef("kok_sagyz_farm",
			List.of(KOK_SAGYZ_SEEDS, KOK_SAGYZ_ROOT),
			List.of(
					new Step(List.of(at("minecraft:dirt", 0, 0, 0), at("minecraft:dirt", 0, 1, 0), flower("0", "1")),
							2, 1, List.of(), Verdict.NONE, List.of(new ItemMotion(KOK_SAGYZ_SEEDS, 2, true))),
					new Step(List.of(root(0, true), root(1, false), flower("2", "3")), 0, 2, -1, -1, Verdict.NONE),
					new Step(List.of(root(0, true), root(1, false), flower("3", null)), 0, 2, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(KOK_SAGYZ_ROOT, 0, false))),
					new Step(List.of(root(0, true), root(1, false), flower("3", null),
							machine(MACERATOR, 2, 0, false, true)), 3, 4, List.of(Flow.item(0, 3, KOK_SAGYZ_ROOT)),
							Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:raw_rubber", 3, false),
									new ItemMotion("alaindustrial:inulin", 3, false))),
					new Step(List.of(root(0, true), root(1, false), at("minecraft:air", 0, 2, 0)), 2, 3, -1, -1,
							Verdict.BAD)));

	private GearBlueprints() {
	}

	private static Placement at(String id, int x, int y, int z) {
		return new Placement(id, x, y, z, Map.of());
	}

	/** Coal ore, an oak log with leaves on it, then {@code extra}: indices 0 ore, 1 log, 2 leaves, 3.. extra. */
	private static List<Placement> woodsAnd(Placement... extra) {
		return join(List.of(at("minecraft:coal_ore", 0, 0, 0), at("minecraft:oak_log", 2, 0, 0),
				at("minecraft:oak_leaves", 2, 1, 0)), List.of(extra));
	}

	/** A charging station at (4, 0, 2) in {@code state}, turning ready late in the step when {@code fills}. */
	private static Placement pad(String state, boolean fills) {
		return new Placement(CHARGE_PAD, 4, 0, 2, Map.of("state", state),
				fills ? Map.of("state", "ready") : Map.of());
	}

	private static Placement light() {
		return new Placement(INDUSTRIAL_LIGHT, 0, 1, 0, Map.of("facing", "up"));
	}

	private static Placement flower(String age, String lateAge) {
		return new Placement(KOK_SAGYZ, 0, 2, 0, Map.of("age", age),
				lateAge == null ? Map.of() : Map.of("age", lateAge));
	}

	private static Placement root(int y, boolean tip) {
		return new Placement(KOK_SAGYZ_ROOT, 0, y, 0, Map.of("tip", Boolean.toString(tip)));
	}
}
