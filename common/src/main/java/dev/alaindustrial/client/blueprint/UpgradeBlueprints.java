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
 * Blueprints about ores, building blocks and the parts that go into machines: rotors, wheels, conductor
 * tips and upgrade chips (MOD-797). Listed in {@link BlueprintCatalog#ALL}.
 */
public final class UpgradeBlueprints {

	private static final String TIN_ORE = "alaindustrial:tin_ore";
	private static final String SILVER_ORE = "alaindustrial:silver_ore";
	private static final String NICKEL_ORE = "alaindustrial:nickel_ore";
	private static final String SULFUR_ORE = "alaindustrial:sulfur_ore";
	private static final String URANIUM_ORE = "alaindustrial:uranium_ore";
	private static final String PALLADIUM_ORE = "alaindustrial:palladium_ore";

	private static final String REINFORCED_GLASS = "alaindustrial:reinforced_glass";
	private static final String TEMPERED_IRON_BARS = "alaindustrial:tempered_iron_bars";
	private static final String TEMPERED_IRON_LADDER = "alaindustrial:tempered_iron_ladder";
	private static final String TEMPERED_IRON_TRAPDOOR = "alaindustrial:tempered_iron_trapdoor";
	private static final String TEMPERED_IRON_CHAIN = "alaindustrial:tempered_iron_chain";

	private static final String ROTOR = "alaindustrial:windmill_rotor";
	private static final String ROTOR_REINFORCED = "alaindustrial:windmill_rotor_reinforced";
	private static final String ROTOR_ADVANCED = "alaindustrial:windmill_rotor_advanced";
	private static final String WHEEL = "alaindustrial:water_mill_wheel";
	private static final String WHEEL_REINFORCED = "alaindustrial:water_mill_wheel_reinforced";
	private static final String WHEEL_ADVANCED = "alaindustrial:water_mill_wheel_advanced";

	private static final String TIP = "alaindustrial:lightning_rod_conductor_tip";
	private static final String TIP_REINFORCED = "alaindustrial:lightning_rod_conductor_tip_reinforced";
	private static final String TIP_ADVANCED = "alaindustrial:lightning_rod_conductor_tip_advanced";

	private static final String OVERCLOCKER_I = "alaindustrial:overclocker_chip_i";
	private static final String OVERCLOCKER_II = "alaindustrial:overclocker_chip_ii";
	private static final String OVERCLOCKER_III = "alaindustrial:overclocker_chip_iii";
	private static final String MUTE_CHIP = "alaindustrial:mute_chip";

	/**
	 * Tin, silver and nickel ore over their deepslate twins (0 tin), mined with a stone pickaxe; sulfur and
	 * uranium ore (1 uranium), where a stone pickaxe gets nothing and an iron one gets raw uranium;
	 * palladium ore in nether stone (1), needing a diamond pickaxe; then raw tin through the macerator
	 * (0) into two dusts and the dust smelted in the electric furnace (4).
	 */
	public static final BlueprintDef MOD_ORES = new BlueprintDef("mod_ores",
			List.of(TIN_ORE, SILVER_ORE, NICKEL_ORE, SULFUR_ORE, URANIUM_ORE, PALLADIUM_ORE),
			List.of(
					new Step(oreWall(), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:stone_pickaxe", 0, true),
									new ItemMotion("alaindustrial:raw_tin", 0, false),
									new ItemMotion("alaindustrial:raw_nickel", 2, false))),
					new Step(deepOres(), 1, 2, List.of(), Verdict.BAD,
							List.of(new ItemMotion("minecraft:stone_pickaxe", 1, true))),
					new Step(deepOres(), 1, 2, List.of(), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:iron_pickaxe", 1, true),
									new ItemMotion("alaindustrial:raw_uranium", 1, false),
									new ItemMotion("alaindustrial:raw_sulfur", 0, false))),
					new Step(List.of(at("minecraft:netherrack", 0, 0, 0), at(PALLADIUM_ORE, 1, 0, 0),
							at("minecraft:basalt", 2, 0, 0), at("minecraft:blackstone", 1, -1, 0),
							at("minecraft:netherrack", 1, 1, 0)), 1, 3, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:diamond_pickaxe", 1, true),
									new ItemMotion("alaindustrial:raw_palladium", 1, false))),
					new Step(List.of(machine(MACERATOR, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 4,
							List.of(Flow.energy(2, 0)), Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:raw_tin", 0, true),
									new ItemMotion("alaindustrial:tin_dust", 0, false),
									new ItemMotion("alaindustrial:tin_dust", 0, false))),
					new Step(List.of(machine(MACERATOR, 0, 0, true, false),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2),
							cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"),
							machine(ELECTRIC_FURNACE, 4, 0, false, true)), 4, 5,
							List.of(Flow.energy(2, 0), Flow.energy(2, 4), Flow.item(0, 4, "alaindustrial:tin_dust")),
							Verdict.GOOD,
							List.of(new ItemMotion("alaindustrial:tin_dust", 4, true),
									new ItemMotion("alaindustrial:tin_ingot", 4, false)))));

	/**
	 * Reinforced glass made from glass and tempered iron plates set in a stone window (0 the window cell);
	 * plain glass gone from the window next to TNT (0 is air there), the reinforced pane holding beside
	 * it; tempered iron bars joining between two stones (1); the chain (1) holding a lantern; the ladder
	 * (0) on a wall and the trapdoor (3) opened by a lever.
	 */
	public static final BlueprintDef BUILDING_BLOCKS = new BlueprintDef("building_blocks",
			List.of(REINFORCED_GLASS, TEMPERED_IRON_BARS, TEMPERED_IRON_LADDER, TEMPERED_IRON_TRAPDOOR,
					TEMPERED_IRON_CHAIN),
			List.of(
					new Step(window(REINFORCED_GLASS, false), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:glass", 0, true),
									new ItemMotion("alaindustrial:tempered_iron_plate", 0, true))),
					new Step(window("minecraft:air", true), 0, 1, -1, -1, Verdict.BAD),
					new Step(window(REINFORCED_GLASS, true), 0, 1, -1, -1, Verdict.GOOD),
					new Step(barsRow(), 1, 2, -1, -1, Verdict.NONE),
					new Step(List.of(stone(1, 3, 0), at(TEMPERED_IRON_CHAIN, 1, 2, 0, "axis", "y"),
							at("minecraft:lantern", 1, 1, 0, "hanging", "true")), 1, 3, -1, -1, Verdict.NONE),
					new Step(ladderAndTrapdoor(), 3, 4, -1, -1, Verdict.GOOD)));

	/**
	 * The wind turbine (0) facing west with a cable and a battery box at its back: the wooden rotor going
	 * in, swapped for the reinforced and then the advanced one, the turbine with its worn rotor gone and
	 * no energy; then the water mill (0) with water around its wheel taking the wheel grades in turn.
	 */
	public static final BlueprintDef ROTORS_AND_WHEELS = new BlueprintDef("rotors_and_wheels",
			List.of(ROTOR, ROTOR_REINFORCED, ROTOR_ADVANCED, WHEEL, WHEEL_REINFORCED, WHEEL_ADVANCED),
			List.of(
					new Step(windLine(ROTOR), 0, 1, 0, 2, Verdict.NONE,
							List.of(new ItemMotion(ROTOR, 0, true))),
					new Step(windLine(ROTOR_REINFORCED), 0, 2, 0, 2, Verdict.NONE,
							List.of(new ItemMotion(ROTOR, 0, false), new ItemMotion(ROTOR_REINFORCED, 0, true))),
					new Step(windLine(ROTOR_ADVANCED), 0, 3, 0, 2, Verdict.GOOD,
							List.of(new ItemMotion(ROTOR_REINFORCED, 0, false),
									new ItemMotion(ROTOR_ADVANCED, 0, true))),
					new Step(windLine(null), 0, 1, -1, -1, Verdict.BAD),
					new Step(waterLine(WHEEL_REINFORCED), 0, 4, List.of(Flow.water(3, 0), Flow.energy(0, 2)),
							Verdict.NONE,
							List.of(new ItemMotion(WHEEL, 0, true), new ItemMotion(WHEEL, 0, false),
									new ItemMotion(WHEEL_REINFORCED, 0, true))),
					new Step(waterLine(WHEEL_ADVANCED), 0, 5, List.of(Flow.water(3, 0), Flow.energy(0, 2)),
							Verdict.GOOD,
							List.of(new ItemMotion(WHEEL_REINFORCED, 0, false),
									new ItemMotion(WHEEL_ADVANCED, 0, true)))));

	/**
	 * The lightning rod generator (0) taking the copper tip; the tipped rod with no cable, its store full;
	 * the same rod draining into a battery box (2) through a cable; then the copper tip swapped for the
	 * reinforced and the reinforced for the advanced one.
	 */
	public static final BlueprintDef LIGHTNING_TIPS = new BlueprintDef("lightning_tips",
			List.of(TIP, TIP_REINFORCED, TIP_ADVANCED),
			List.of(
					new Step(List.of(rod(false, true)), 0, 1, -1, -1, Verdict.NONE,
							List.of(new ItemMotion(TIP, 0, true))),
					new Step(List.of(new Placement(LIGHTNING_ROD, 0, 0, 0, Map.of("tipped", "true", "lit", "false"))),
							0, 1, -1, -1, Verdict.BAD),
					new Step(rodLine(), 0, 1, 0, 2, Verdict.GOOD),
					new Step(rodLine(), 0, 2, 0, 2, Verdict.NONE,
							List.of(new ItemMotion(TIP, 0, false), new ItemMotion(TIP_REINFORCED, 0, true))),
					new Step(rodLine(), 0, 3, 0, 2, Verdict.GOOD,
							List.of(new ItemMotion(TIP_REINFORCED, 0, false), new ItemMotion(TIP_ADVANCED, 0, true)))));

	/**
	 * A generator feeding the macerator (2) through a cable (1): the first overclocker going in, the second
	 * replacing it, the third starving on copper cable, the same on gold cable, then the mute chip.
	 */
	public static final BlueprintDef MACHINE_CHIPS = new BlueprintDef("machine_chips",
			List.of(OVERCLOCKER_I, OVERCLOCKER_II, OVERCLOCKER_III, MUTE_CHIP),
			List.of(
					new Step(chipLine(COPPER_CABLE, false, true), 2, 1, 0, 2, Verdict.NONE,
							chipItems(OVERCLOCKER_I, true)),
					new Step(chipLine(COPPER_CABLE, true, false), 2, 2, 0, 2, Verdict.NONE,
							chipItems(OVERCLOCKER_II, true)),
					new Step(chipLine(COPPER_CABLE, false, false), 1, 3, 0, 2, Verdict.BAD,
							chipItems(OVERCLOCKER_III, false)),
					new Step(chipLine(GOLD_CABLE, false, true), 1, 3, 0, 2, Verdict.GOOD,
							chipItems(null, true)),
					new Step(chipLine(COPPER_CABLE, true, false), 2, 4, 0, 2, Verdict.NONE,
							chipItems(MUTE_CHIP, true))));

	private UpgradeBlueprints() {
	}

	private static Placement at(String id, int x, int y, int z) {
		return new Placement(id, x, y, z, Map.of());
	}

	private static Placement at(String id, int x, int y, int z, String key, String value) {
		return new Placement(id, x, y, z, Map.of(key, value));
	}

	/** Tin, silver and nickel ore in a row over their deepslate twins: indices 0..2 ore, 3..5 deepslate. */
	private static List<Placement> oreWall() {
		return List.of(at(TIN_ORE, 0, 1, 0), at(SILVER_ORE, 1, 1, 0), at(NICKEL_ORE, 2, 1, 0),
				at("alaindustrial:deepslate_tin_ore", 0, 0, 0), at("alaindustrial:deepslate_silver_ore", 1, 0, 0),
				at("alaindustrial:deepslate_nickel_ore", 2, 0, 0));
	}

	/** Sulfur (0) and uranium (1) ore over their deepslate twins (2, 3). */
	private static List<Placement> deepOres() {
		return List.of(at(SULFUR_ORE, 0, 1, 0), at(URANIUM_ORE, 1, 1, 0),
				at("alaindustrial:deepslate_sulfur_ore", 0, 0, 0), at("alaindustrial:deepslate_uranium_ore", 1, 0, 0));
	}

	/** A 3x3 stone wall with {@code middle} in its centre (index 0), and TNT in front of it when {@code tnt}. */
	private static List<Placement> window(String middle, boolean tnt) {
		List<Placement> all = new ArrayList<>(List.of(at(middle, 1, 1, 0)));
		for (int x = 0; x <= 2; x++) {
			for (int y = 0; y <= 2; y++) {
				if (x != 1 || y != 1) {
					all.add(stone(x, y, 0));
				}
			}
		}
		if (tnt) {
			all.add(at("minecraft:tnt", 1, 0, 1));
		}
		return all;
	}

	/** Stone (0), three joined tempered iron bars (1..3), stone (4). */
	private static List<Placement> barsRow() {
		List<Placement> all = new ArrayList<>(List.of(stone(0, 0, 0)));
		for (int x = 1; x <= 3; x++) {
			all.add(new Placement(TEMPERED_IRON_BARS, x, 0, 0, Map.of("west", "true", "east", "true",
					"north", "false", "south", "false")));
		}
		all.add(stone(4, 0, 0));
		return all;
	}

	/**
	 * 0..1 ladder on a two-block stone wall (2, 4), 3 a trapdoor on stone (5) that opens late in the step
	 * as the lever (6) beside it, on stone (7), is switched on.
	 */
	private static List<Placement> ladderAndTrapdoor() {
		return List.of(at(TEMPERED_IRON_LADDER, 0, 0, 1, "facing", "south"),
				at(TEMPERED_IRON_LADDER, 0, 1, 1, "facing", "south"), stone(0, 0, 0),
				new Placement(TEMPERED_IRON_TRAPDOOR, 2, 1, 0, Map.of("facing", "south", "half", "bottom",
						"open", "false"), Map.of("open", "true")),
				stone(0, 1, 0), stone(2, 0, 0),
				new Placement("minecraft:lever", 3, 1, 0, Map.of("face", "floor", "facing", "south",
						"powered", "false"), Map.of("powered", "true")),
				stone(3, 0, 0));
	}

	/** The wind turbine facing west holding {@code rotor} (null: empty), a cable and a battery box. */
	private static List<Placement> windLine(String rotor) {
		Placement mill = new Placement(WIND_MILL, 0, 0, 0, Map.of("facing", "west"));
		return List.of(rotor == null ? mill : mill.holding(rotor), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
				batteryBox("west"));
	}

	/** The water mill facing west holding {@code wheel}, a cable, a battery box, flowing water at 3..5. */
	private static List<Placement> waterLine(String wheel) {
		return List.of(new Placement(WATER_MILL, 0, 0, 0, Map.of("facing", "west")).holding(wheel),
				cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), batteryBox("west"),
				water(-1, -1, 0), water(-1, 0, -1), water(-1, 0, 1));
	}

	/** The tipped lightning rod (0) discharging through a cable into a battery box (2). */
	private static List<Placement> rodLine() {
		return List.of(rod(true, false), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), batteryBox("west"));
	}

	/** Generator (0), {@code cable} (1), macerator (2), lit now or late in the step. */
	private static List<Placement> chipLine(String cable, boolean lit, boolean litLater) {
		return List.of(generatorAt(0), cableOf(cable, 1, 0, 0, "west", "east"),
				machine(MACERATOR, 2, 0, lit, litLater));
	}

	/** {@code chip} (null: none) and raw iron into the macerator, iron dust out when {@code works}. */
	private static List<ItemMotion> chipItems(String chip, boolean works) {
		List<ItemMotion> items = new ArrayList<>();
		if (chip != null) {
			items.add(new ItemMotion(chip, 2, true));
		}
		items.add(new ItemMotion("minecraft:raw_iron", 2, true));
		if (works) {
			items.add(new ItemMotion("alaindustrial:iron_dust", 2, false));
		}
		return items;
	}
}
