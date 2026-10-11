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

/**
 * Blueprints about hand tools, armour sets, the electromagnets and the forge hammer (MOD-797). Most subjects
 * here are items: a sheet shows the block that makes them, charges them, or what they work on. Listed in
 * {@link BlueprintCatalog#ALL}.
 */
public final class ToolGearBlueprints {

	private static final String MOD = "alaindustrial:";

	private static final String SCYTHE_WOOD = MOD + "scythe_wood";
	private static final String SCYTHE_IRON = MOD + "scythe_iron";
	private static final String SCYTHE_NETHERITE = MOD + "scythe_netherite";

	private static final String TEMPERED_IRON = MOD + "tempered_iron";
	private static final String TI_PICKAXE = MOD + "tempered_iron_pickaxe";
	private static final String TI_AXE = MOD + "tempered_iron_axe";
	private static final String TI_SHOVEL = MOD + "tempered_iron_shovel";
	private static final String TI_HOE = MOD + "tempered_iron_hoe";
	private static final String TI_SWORD = MOD + "tempered_iron_sword";
	private static final String TI_CHESTPLATE = MOD + "tempered_iron_chestplate";

	private static final String SHIELDING_POUCH = MOD + "shielding_pouch";
	private static final String ELECTROMAGNET = MOD + "electromagnet";
	private static final String ELECTROMAGNET_ADVANCED = MOD + "electromagnet_advanced";
	private static final String MAGNET_FILTER_MODULE = MOD + "magnet_filter_module";
	private static final String MODULE_BLANK = MOD + "module_blank";
	private static final String FORGE_HAMMER = MOD + "forge_hammer";

	private static final String CRAFTING_TABLE = "minecraft:crafting_table";
	private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

	/**
	 * The wooden scythe on grass, a poppy and a fern (3..5; 4 the poppy) beside a patch of grass blocks
	 * (0..2), then the patch cleared in one swing; a wheat field (farmland 3..5, wheat 6..8) cut without
	 * Shift, where the ripe wheat stays; with Shift the ripe wheat is gone and the young one (6) keeps
	 * growing; the netherite scythe at a row of leaves (7..9; 8 the middle) for the widest swath.
	 */
	public static final BlueprintDef SCYTHES = new BlueprintDef("scythes",
			List.of(SCYTHE_WOOD, MOD + "scythe_stone", MOD + "scythe_copper", SCYTHE_IRON, MOD + "scythe_gold",
					MOD + "scythe_tempered_iron", MOD + "scythe_diamond", SCYTHE_NETHERITE),
			List.of(
					new Step(patch(false), 4, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion(SCYTHE_WOOD, 4, true),
									new ItemMotion("minecraft:poppy", 4, false),
									new ItemMotion("minecraft:wheat_seeds", 3, false))),
					new Step(patch(true), 1, 1, -1, -1, Verdict.GOOD),
					new Step(join(patch(true), field("7", "3", "7")), 6, 2, List.of(), Verdict.BAD,
							List.of(new ItemMotion(SCYTHE_IRON, 6, true))),
					new Step(join(patch(true), field(null, "3", null)), 6, 2, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(SCYTHE_IRON, 3, true),
									new ItemMotion("minecraft:wheat", 3, false),
									new ItemMotion("minecraft:wheat_seeds", 3, false))),
					new Step(join(join(patch(true), field(null, "3", null)), leaves()), 8, 3, List.of(),
							Verdict.NONE,
							List.of(new ItemMotion(SCYTHE_NETHERITE, 8, true),
									new ItemMotion("minecraft:oak_sapling", 8, false),
									new ItemMotion("minecraft:stick", 7, false)))));

	/**
	 * An iron ingot tempered in a blast furnace (0); the electric furnace (1) doing the same on energy from a
	 * generator (3) over a cable (2); the tools on iron ore, a log, dirt and cobweb (0..3); the tempered
	 * pickaxe getting nothing from diamond ore (0) and a diamond pickaxe getting the diamond; the hoe and
	 * the chestplate mended with tempered iron at an anvil (0).
	 */
	public static final BlueprintDef TEMPERED_IRON_GEAR = new BlueprintDef("tempered_iron_gear",
			temperedSubjects(),
			List.of(
					new Step(List.of(new Placement("minecraft:blast_furnace", 0, 0, 0,
							Map.of("facing", "south", "lit", "false"), Map.of("lit", "true"))), 0, 1, List.of(),
							Verdict.NONE,
							List.of(new ItemMotion("minecraft:iron_ingot", 0, true),
									new ItemMotion(TEMPERED_IRON, 0, false))),
					new Step(List.of(new Placement("minecraft:blast_furnace", 0, 0, 0,
							Map.of("facing", "south", "lit", "false")), machine(ELECTRIC_FURNACE, 2, 0, false, true),
							cableOf(COPPER_CABLE, 3, 0, 0, "west", "east"), generatorAt(4)), 1, 2,
							List.of(Flow.energy(3, 1)), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:iron_ingot", 1, true),
									new ItemMotion(TEMPERED_IRON, 1, false))),
					new Step(List.of(at("minecraft:iron_ore", 0), at("minecraft:oak_log", 1), at("minecraft:dirt", 2),
							at("minecraft:cobweb", 3)), 0, 3, List.of(), Verdict.NONE,
							List.of(new ItemMotion(TI_PICKAXE, 0, true), new ItemMotion("minecraft:raw_iron", 0, false),
									new ItemMotion(TI_AXE, 1, true), new ItemMotion(TI_SHOVEL, 2, true),
									new ItemMotion(TI_SWORD, 3, true), new ItemMotion("minecraft:string", 3, false))),
					new Step(diamondOre(), 0, 4, List.of(), Verdict.BAD,
							List.of(new ItemMotion(TI_PICKAXE, 0, true))),
					new Step(diamondOre(), 0, 4, List.of(), Verdict.GOOD,
							List.of(new ItemMotion("minecraft:diamond_pickaxe", 0, true),
									new ItemMotion("minecraft:diamond", 0, false))),
					new Step(List.of(new Placement("minecraft:anvil", 0, 0, 0, Map.of("facing", "east"))), 0, 5,
							List.of(), Verdict.NONE,
							List.of(new ItemMotion(TI_HOE, 0, true), new ItemMotion(TEMPERED_IRON, 0, true),
									new ItemMotion(TI_HOE, 0, false), new ItemMotion(TI_CHESTPLATE, 0, true),
									new ItemMotion(TEMPERED_IRON, 0, true), new ItemMotion(TI_CHESTPLATE, 0, false)))));

	/**
	 * A leather chestplate dipped in slime at a crafting table (0); the insulated set at a live copper cable
	 * (1) between a generator (0) and a furnace (2); the insulated set at a crater of irradiated soil (0..2;
	 * 0 the strongest cell), which it does not stop, and the shielding suit there instead; the shielding
	 * pouch, then the fluxweave suit, charging in a battery box (2) fed by a generator (0).
	 */
	public static final BlueprintDef PROTECTIVE_SUITS = new BlueprintDef("protective_suits",
			suitSubjects(),
			List.of(
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:leather_chestplate", 0, true),
									new ItemMotion("minecraft:slime_ball", 0, true),
									new ItemMotion(MOD + "insulated_chestplate", 0, false))),
					new Step(List.of(generator(true), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"),
							machine(ELECTRIC_FURNACE, 2, 0, true, false)), 1, 2, List.of(Flow.energy(0, 2)),
							Verdict.GOOD, suitInto("insulated", 1)),
					new Step(crater(false), 0, 3, List.of(), Verdict.BAD, suitInto("insulated", 0)),
					new Step(crater(false), 0, 3, List.of(), Verdict.GOOD, suitInto("shielding", 0)),
					new Step(chargingLine(), 2, 4, List.of(Flow.energy(0, 2)), Verdict.NONE,
							List.of(new ItemMotion(SHIELDING_POUCH, 2, true))),
					new Step(chargingLine(), 2, 5, List.of(Flow.energy(0, 2)), Verdict.GOOD,
							suitInto("fluxweave", 2))));

	/**
	 * The electromagnet crafted at a crafting table (0); a battery box (0) with nothing feeding it, so the
	 * magnet does not charge; the same box (2) fed by a generator (0) over a cable (1); the blank module and
	 * the filter module crafted (0); the advanced electromagnet built around the first one (0).
	 */
	public static final BlueprintDef ELECTROMAGNETS = new BlueprintDef("electromagnets",
			List.of(ELECTROMAGNET, ELECTROMAGNET_ADVANCED, MAGNET_FILTER_MODULE, MODULE_BLANK),
			List.of(
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion(MOD + "copper_coil", 0, true),
									new ItemMotion(MOD + "electronic_circuit", 0, true),
									new ItemMotion("minecraft:lapis_lazuli", 0, true),
									new ItemMotion(ELECTROMAGNET, 0, false))),
					new Step(List.of(batteryBox("west")), 0, 2, List.of(), Verdict.BAD,
							List.of(new ItemMotion(ELECTROMAGNET, 0, true))),
					new Step(chargingLine(), 2, 2, List.of(Flow.energy(0, 2)), Verdict.GOOD,
							List.of(new ItemMotion(ELECTROMAGNET, 2, true))),
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 3, List.of(), Verdict.NONE,
							List.of(new ItemMotion(MOD + "electronic_circuit", 0, true),
									new ItemMotion("minecraft:redstone", 0, true),
									new ItemMotion(MODULE_BLANK, 0, false), new ItemMotion("minecraft:paper", 0, true),
									new ItemMotion(MAGNET_FILTER_MODULE, 0, false))),
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 4, List.of(), Verdict.GOOD,
							List.of(new ItemMotion(ELECTROMAGNET, 0, true),
									new ItemMotion(MOD + "electrum_reinforced_plate", 0, true),
									new ItemMotion(MOD + "advanced_circuit", 0, true),
									new ItemMotion(MOD + "energy_crystal", 0, true),
									new ItemMotion(ELECTROMAGNET_ADVANCED, 0, false)))));

	/**
	 * The forge hammer crafted at a crafting table (0); hammer and iron ingot giving an iron plate with the
	 * hammer coming back; copper, gold and tin ingots hammered the same way; the compressor (0) alone and
	 * dark, so a silver ingot gets nowhere; the compressor fed by a generator (2) over a cable (1), pressing
	 * silver and nickel plates.
	 */
	public static final BlueprintDef FORGE_HAMMER_PLATES = new BlueprintDef("forge_hammer",
			List.of(FORGE_HAMMER, MOD + "iron_plate", MOD + "copper_plate", MOD + "gold_plate", MOD + "tin_plate",
					MOD + "silver_plate", MOD + "nickel_plate"),
			List.of(
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.NONE,
							List.of(new ItemMotion("minecraft:iron_ingot", 0, true),
									new ItemMotion("minecraft:stick", 0, true),
									new ItemMotion(FORGE_HAMMER, 0, false))),
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.GOOD,
							joinMotions(hammered("minecraft:iron_ingot", "iron_plate"),
									List.of(new ItemMotion(FORGE_HAMMER, 0, false)))),
					new Step(List.of(at(CRAFTING_TABLE, 0)), 0, 1, List.of(), Verdict.NONE,
							joinMotions(hammered("minecraft:copper_ingot", "copper_plate"),
									hammered("minecraft:gold_ingot", "gold_plate"),
									hammered(MOD + "tin_ingot", "tin_plate"))),
					new Step(List.of(machine(COMPRESSOR, 0, 0, false, false)), 0, 2, List.of(), Verdict.BAD,
							List.of(new ItemMotion(MOD + "silver_ingot", 0, true))),
					new Step(List.of(machine(COMPRESSOR, 0, 0, false, true),
							cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), generatorAt(2)), 0, 2,
							List.of(Flow.energy(2, 0)), Verdict.GOOD,
							List.of(new ItemMotion(MOD + "silver_ingot", 0, true),
									new ItemMotion(MOD + "silver_plate", 0, false),
									new ItemMotion(MOD + "nickel_ingot", 0, true),
									new ItemMotion(MOD + "nickel_plate", 0, false)))));

	private ToolGearBlueprints() {
	}

	private static Placement at(String id, int x) {
		return new Placement(id, x, 0, 0, Map.of());
	}

	/** Grass blocks at x 0..2 (indices 0..2); unless {@code cut}, short grass, a poppy and a fern on them (3..5). */
	private static List<Placement> patch(boolean cut) {
		List<Placement> all = new ArrayList<>();
		for (int x = 0; x <= 2; x++) {
			all.add(new Placement("minecraft:grass_block", x, 0, 0, Map.of("snowy", "false")));
		}
		if (!cut) {
			all.add(new Placement("minecraft:short_grass", 0, 1, 0, Map.of()));
			all.add(new Placement("minecraft:poppy", 1, 1, 0, Map.of()));
			all.add(new Placement("minecraft:fern", 2, 1, 0, Map.of()));
		}
		return all;
	}

	/** Moist farmland at x 0..2, z 2 (indices 0..2), then wheat of each given age on it (null: none). */
	private static List<Placement> field(String... ages) {
		List<Placement> all = new ArrayList<>();
		for (int x = 0; x <= 2; x++) {
			all.add(new Placement("minecraft:farmland", x, 0, 2, Map.of("moisture", "7")));
		}
		for (int x = 0; x <= 2; x++) {
			if (ages[x] != null) {
				all.add(new Placement("minecraft:wheat", x, 1, 2, Map.of("age", ages[x])));
			}
		}
		return all;
	}

	/** Oak leaves on the cleared patch, x 0..2 at height 1. */
	private static List<Placement> leaves() {
		List<Placement> all = new ArrayList<>();
		for (int x = 0; x <= 2; x++) {
			all.add(new Placement("minecraft:oak_leaves", x, 1, 0, Map.of("persistent", "true")));
		}
		return all;
	}

	/** Diamond ore and obsidian side by side (indices 0, 1). */
	private static List<Placement> diamondOre() {
		return List.of(at("minecraft:diamond_ore", 0), at("minecraft:obsidian", 1));
	}

	/** Generator (0), copper cable (1), battery box taking energy on its west face (2). */
	private static List<Placement> chargingLine() {
		return List.of(generator(true), cableOf(COPPER_CABLE, 1, 0, 0, "west", "east"), batteryBox("west"));
	}

	/** The four pieces of the {@code set} suit going into block {@code block}, helmet first. */
	private static List<ItemMotion> suitInto(String set, int block) {
		List<ItemMotion> motions = new ArrayList<>();
		for (String piece : PIECES) {
			motions.add(new ItemMotion(MOD + set + "_" + piece, block, true));
		}
		return motions;
	}

	/** The hammer and {@code ingot} going into the crafting table (0), the plate {@code plate} coming out. */
	private static List<ItemMotion> hammered(String ingot, String plate) {
		return List.of(new ItemMotion(FORGE_HAMMER, 0, true), new ItemMotion(ingot, 0, true),
				new ItemMotion(MOD + plate, 0, false));
	}

	@SafeVarargs
	private static List<ItemMotion> joinMotions(List<ItemMotion>... parts) {
		List<ItemMotion> all = new ArrayList<>();
		for (List<ItemMotion> part : parts) {
			all.addAll(part);
		}
		return all;
	}

	private static List<String> temperedSubjects() {
		List<String> all = new ArrayList<>(List.of(TEMPERED_IRON, TI_PICKAXE, TI_AXE, TI_SHOVEL, TI_HOE, TI_SWORD));
		for (String piece : PIECES) {
			all.add(MOD + "tempered_iron_" + piece);
		}
		return all;
	}

	private static List<String> suitSubjects() {
		List<String> all = new ArrayList<>();
		for (String set : List.of("fluxweave", "insulated", "shielding")) {
			for (String piece : PIECES) {
				all.add(MOD + set + "_" + piece);
			}
		}
		all.add(SHIELDING_POUCH);
		return all;
	}
}
